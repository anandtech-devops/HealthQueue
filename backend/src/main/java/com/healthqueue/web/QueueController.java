package com.healthqueue.web;

import com.healthqueue.dto.Dtos.DailyQueueDto;
import com.healthqueue.dto.Dtos.QueueEntryDto;
import com.healthqueue.dto.Dtos.QueueSummaryDto;
import com.healthqueue.security.AuthUser;
import com.healthqueue.service.QueueEventBroadcaster;
import com.healthqueue.service.QueueService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/queue")
@Tag(name = "Queue")
public class QueueController {

    private final QueueService queue;
    private final QueueEventBroadcaster broadcaster;
    private final Clock clock;

    public QueueController(QueueService queue, QueueEventBroadcaster broadcaster, Clock clock) {
        this.queue = queue;
        this.broadcaster = broadcaster;
        this.clock = clock;
    }

    /** Full queue for a doctor/day (names included): the doctor themself or an admin only. */
    @GetMapping("/doctors/{doctorId}")
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
    public DailyQueueDto daily(@AuthenticationPrincipal AuthUser user, @PathVariable Long doctorId,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return queue.dailyQueue(doctorId, orToday(date), user);
    }

    @GetMapping("/doctors/{doctorId}/summary")
    public QueueSummaryDto summary(@PathVariable Long doctorId,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return queue.summary(doctorId, orToday(date));
    }

    @PostMapping("/doctors/{doctorId}/call-next")
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
    public QueueEntryDto callNext(@AuthenticationPrincipal AuthUser user, @PathVariable Long doctorId,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return queue.callNext(doctorId, orToday(date), user);
    }

    @PostMapping("/appointments/{id}/start")
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
    public QueueEntryDto start(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return queue.start(id, user);
    }

    @PostMapping("/appointments/{id}/complete")
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
    public QueueEntryDto complete(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return queue.complete(id, user);
    }

    @PostMapping("/appointments/{id}/skip")
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
    public QueueEntryDto skip(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return queue.skip(id, user);
    }

    /**
     * Server-Sent Events: emits "queue-update" whenever the doctor/date queue changes. The payload only
     * identifies the queue; clients then call the authenticated REST endpoints to get their own data.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam Long doctorId,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return broadcaster.subscribe(doctorId, date);
    }

    private LocalDate orToday(LocalDate date) {
        return date != null ? date : LocalDate.now(clock);
    }
}
