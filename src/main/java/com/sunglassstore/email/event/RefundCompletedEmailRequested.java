package com.sunglassstore.email.event;

import java.math.BigDecimal;

public record RefundCompletedEmailRequested(String email, String customerName, Long refundId,
                                             Long orderId, BigDecimal amount) {}
