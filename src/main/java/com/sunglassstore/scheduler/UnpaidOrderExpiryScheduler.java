package com.sunglassstore.scheduler;

import com.sunglassstore.service.UnpaidOrderExpiryService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically returns stock held by abandoned checkouts. Switchable off so tests and local runs
 * can drive the sweep explicitly instead of racing a timer, following the EmailOutboxScheduler
 * convention already used in this codebase.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.orders.unpaid-expiry.enabled", havingValue = "true", matchIfMissing = true)
public class UnpaidOrderExpiryScheduler {

    private final UnpaidOrderExpiryService expiryService;

    @Scheduled(fixedDelayString = "${app.orders.unpaid-expiry.poll-delay-ms:300000}",
            initialDelayString = "${app.orders.unpaid-expiry.initial-delay-ms:60000}")
    public void sweep() {
        expiryService.expireUnpaidOrders();
    }
}
