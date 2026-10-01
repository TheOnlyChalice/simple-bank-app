package com.simplebank.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * The background job: every 30 seconds (app.scheduled-transfers.poll-interval-ms), runs the
 * scheduled transfers whose time has come. Turned off in tests (app.scheduled-transfers.enabled=false),
 * which call ScheduledTransferService.runDueTransfers(...) directly with a chosen time instead.
 */
@Component
@ConditionalOnProperty(name = "app.scheduled-transfers.enabled", havingValue = "true", matchIfMissing = true)
public class ScheduledTransferRunner {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTransferRunner.class);

    private final ScheduledTransferService scheduledTransfers;
    private final Clock clock;

    public ScheduledTransferRunner(ScheduledTransferService scheduledTransfers, Clock clock) {
        this.scheduledTransfers = scheduledTransfers;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${app.scheduled-transfers.initial-delay-ms:10000}",
               fixedDelayString = "${app.scheduled-transfers.poll-interval-ms:30000}")
    public void runDueTransfers() {
        try {
            int processed = scheduledTransfers.runDueTransfers(clock.instant());
            if (processed > 0) {
                log.info("Processed {} scheduled transfer(s)", processed);
            }
        } catch (RuntimeException error) {
            // Never let one bad run stop the schedule; the next run tries again
            log.error("Running scheduled transfers failed; will try again", error);
        }
    }
}
