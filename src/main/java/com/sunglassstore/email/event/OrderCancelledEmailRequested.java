package com.sunglassstore.email.event;

import java.math.BigDecimal;

public record OrderCancelledEmailRequested(String email, String customerName, Long orderId,
                                           BigDecimal refundAmount) {}
