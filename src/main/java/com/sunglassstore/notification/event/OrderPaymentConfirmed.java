package com.sunglassstore.notification.event;

import java.math.BigDecimal;

public record OrderPaymentConfirmed(Long userId, Long orderId, String customerName, BigDecimal totalAmount) {}
