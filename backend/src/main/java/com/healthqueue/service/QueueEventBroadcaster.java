package com.healthqueue.service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Server-Sent Events hub. When a queue changes we push a tiny "queue-update" signal to everyone watching
 * that doctor/date; clients then re-fetch their own (authorised) status. No personal data is ever sent here.
 */
@Component
public class QueueEventBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(QueueEventBroadcaster.class);
    private static final int MAX_SUBSCRIBERS = 2000;

    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final AtomicInteger total = new AtomicInteger();

    public SseEmitter subscribe(Long doctorId, LocalDate date) {
        if (total.get() >= MAX_SUBSCRIBERS) {
            throw com.healthqueue.web.ApiException.conflict("Too many live connections, please try again later");
        }
        String key = key(doctorId, date);
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        List<SseEmitter> list = emitters.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        total.incrementAndGet();
        Runnable cleanup = () -> {
            if (list.remove(emitter)) total.decrementAndGet();
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            cleanup.run();
        }
        return emitter;
    }

    /** Publish once the surrounding transaction has committed, so clients never read stale data. */
    public void publishAfterCommit(Long doctorId, LocalDate date) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish(doctorId, date);
                }
            });
        } else {
            publish(doctorId, date);
        }
    }

    void publish(Long doctorId, LocalDate date) {
        List<SseEmitter> list = emitters.get(key(doctorId, date));
        if (list == null) return;
        String payload = "{\"doctorId\":%d,\"date\":\"%s\"}".formatted(doctorId, date);
        for (SseEmitter e : list) {
            try {
                e.send(SseEmitter.event().name("queue-update").data(payload));
            } catch (IOException | IllegalStateException ex) {
                if (list.remove(e)) total.decrementAndGet();
            }
        }
    }

    /** Keeps idle connections open through proxies (nginx, ALB) and drops dead ones. */
    @Scheduled(fixedRate = 25_000)
    void heartbeat() {
        emitters.forEach((k, list) -> {
            for (SseEmitter e : list) {
                try {
                    e.send(SseEmitter.event().comment("keep-alive"));
                } catch (IOException | IllegalStateException ex) {
                    if (list.remove(e)) total.decrementAndGet();
                }
            }
        });
        log.debug("SSE subscribers: {}", total.get());
    }

    private static String key(Long doctorId, LocalDate date) {
        return doctorId + ":" + date;
    }
}
