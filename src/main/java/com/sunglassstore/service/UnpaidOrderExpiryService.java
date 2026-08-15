package com.sunglassstore.service;

import java.time.LocalDateTime;

public interface UnpaidOrderExpiryService {

    /** Expires unpaid orders older than the configured reservation window. Returns how many. */
    int expireUnpaidOrders();

    /** Expires unpaid orders placed before an explicit cutoff. Returns how many. */
    int expireUnpaidOrders(LocalDateTime cutoff);
}
