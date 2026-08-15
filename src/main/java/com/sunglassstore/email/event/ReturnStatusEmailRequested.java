package com.sunglassstore.email.event;

public record ReturnStatusEmailRequested(String email, String customerName, Long returnId,
                                          Long orderId, String status, String comments) {}
