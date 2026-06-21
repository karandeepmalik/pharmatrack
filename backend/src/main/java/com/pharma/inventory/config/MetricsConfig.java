package com.pharma.inventory.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    /** Enables @Timed annotation on Spring-managed beans. */
    @Bean
    TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    /** Applies common tags to every metric registered in this service. */
    @Bean
    MeterRegistryCustomizer<MeterRegistry> commonTags(
            @Value("${spring.application.name:pharmatrack}") String appName) {
        return registry -> registry.config()
                .commonTags("application", appName, "service", "pharmatrack-backend");
    }
}
