package com.sunglassstore.email;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.email.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class EmailOutboxScheduler {
    private final EmailOutboxDeliveryService deliveryService;

    @Scheduled(fixedDelayString = "${app.email.outbox.poll-delay-ms:2000}")
    public void dispatch() {
        int processed = 0;
        while (processed < 20 && deliveryService.deliverNext()) processed++;
    }
}
