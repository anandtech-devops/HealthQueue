package com.healthqueue.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed view of the {@code healthqueue.*} settings in application.yml (all env-driven). */
@ConfigurationProperties(prefix = "healthqueue")
public record AppProperties(Jwt jwt, Cors cors, Queue queue, Booking booking, String timezone, Demo demo) {

    public record Jwt(String secret, long expirationMinutes) {}

    public record Cors(List<String> allowedOrigins) {}

    public record Queue(int avgConsultationMinutes) {}

    public record Booking(int maxDaysAhead, boolean allowPastSlotsToday) {}

    public record Demo(boolean seedEnabled, String password) {}
}
