package com.simplebank.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * Turns on Spring's @Scheduled background jobs (used by ScheduledTransferRunner), and
 * provides the clock the app reads "now" from. A Clock bean (rather than calling
 * Instant.now() everywhere) keeps time in one place, in UTC.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
