package com.sunglassstore.service.impl;

import com.sunglassstore.repository.OrderRepository;
import com.sunglassstore.service.OrderService;
import com.sunglassstore.service.UnpaidOrderExpiryService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Releases stock held by orders that were created but never paid for.
 *
 * Stock is deducted when the order is created, not when it is paid — which is what stops two
 * customers both buying the last unit. The cost of that choice is that an abandoned checkout holds
 * its stock indefinitely: the shopper closes the tab between createOrder and the payment call, and
 * those units never come back. This sweep is the other half of that reservation model.
 *
 * The loop is deliberately NOT transactional. Each order is expired by
 * OrderService.expireUnpaidOrder, which carries its own transaction — so one unexpirable order
 * cannot roll back the rest, and no row lock is held across the whole sweep where it would block
 * live checkouts. Calling a @Transactional method on this class from inside this loop would be a
 * self-invocation and Spring's proxy would silently skip the transaction entirely.
 */
@Service
@RequiredArgsConstructor
public class UnpaidOrderExpiryServiceImpl implements UnpaidOrderExpiryService {

    private static final Logger log = LoggerFactory.getLogger(UnpaidOrderExpiryServiceImpl.class);

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    /** Generous enough to cover a slow card flow, short enough that stock is not parked for hours. */
    @Value("${app.orders.unpaid-expiry-minutes:30}")
    private long expiryMinutes;

    @Override
    public int expireUnpaidOrders() {
        return expireUnpaidOrders(LocalDateTime.now().minusMinutes(expiryMinutes));
    }

    @Override
    public int expireUnpaidOrders(LocalDateTime cutoff) {
        List<Long> candidates = orderRepository.findUnpaidOrderIdsPlacedBefore(cutoff);
        int expired = 0;
        for (Long orderId : candidates) {
            try {
                if (orderService.expireUnpaidOrder(orderId)) expired += 1;
            } catch (RuntimeException exception) {
                // A single order that cannot be expired must not stop the sweep.
                log.warn("Could not expire unpaid order {}: {}", orderId, exception.getMessage());
            }
        }
        if (expired > 0) log.info("Released stock from {} unpaid order(s) placed before {}", expired, cutoff);
        return expired;
    }
}
