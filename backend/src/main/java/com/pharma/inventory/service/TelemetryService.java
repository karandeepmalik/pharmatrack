package com.pharma.inventory.service;

import com.pharma.inventory.dto.TelemetryEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelemetryService {

    private final MeterRegistry meterRegistry;

    public void record(String username, TelemetryEvent event) {
        String name = sanitize(event.getEventName());
        String page = sanitize(event.getPage());

        // JSON-structured log line — Cloud Run ships stdout to Cloud Logging
        log.info("{{\"telemetry\":true,\"event\":\"{}\",\"user\":\"{}\",\"page\":\"{}\"}}",
                name, username, page);

        // Increment counter in Cloud Monitoring via Micrometer
        try {
            Counter.builder("pharmatrack_ui_events_total")
                    .description("UI telemetry events from the frontend")
                    .tag("event", name)
                    .tag("page", page)
                    .register(meterRegistry)
                    .increment();
        } catch (Exception e) {
            log.debug("Metrics registration failed: {}", e.getMessage());
        }
    }

    public void recordServiceEvent(String eventName, String... tags) {
        try {
            var builder = Counter.builder("pharmatrack_service_events_total")
                    .description("Backend service events")
                    .tag("event", sanitize(eventName));
            for (int i = 0; i + 1 < tags.length; i += 2) {
                builder = builder.tag(tags[i], sanitize(tags[i + 1]));
            }
            builder.register(meterRegistry).increment();
        } catch (Exception e) {
            log.debug("Service metrics registration failed: {}", e.getMessage());
        }
    }

    private String sanitize(String s) {
        if (s == null) return "unknown";
        return s.replaceAll("[^a-zA-Z0-9._/-]", "_").toLowerCase().substring(0, Math.min(s.length(), 64));
    }
}
