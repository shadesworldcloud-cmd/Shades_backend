package com.sunglassstore.service.impl;

import com.sunglassstore.repository.OrderRepository;
import com.sunglassstore.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class UnpaidOrderExpiryServiceImplTest {

    private UnpaidOrderExpiryServiceImpl service(OrderRepository orders, OrderService orderService) {
        UnpaidOrderExpiryServiceImpl service = new UnpaidOrderExpiryServiceImpl(orders, orderService);
        ReflectionTestUtils.setField(service, "expiryMinutes", 30L);
        return service;
    }

    @Test
    void releasesStockForEveryAbandonedOrder() {
        OrderRepository orders = mock(OrderRepository.class);
        OrderService orderService = mock(OrderService.class);
        when(orders.findUnpaidOrderIdsPlacedBefore(any())).thenReturn(List.of(11L, 12L));
        when(orderService.expireUnpaidOrder(anyLong())).thenReturn(true);

        assertEquals(2, service(orders, orderService).expireUnpaidOrders());
        verify(orderService).expireUnpaidOrder(11L);
        verify(orderService).expireUnpaidOrder(12L);
    }

    @Test
    void doesNotCountAnOrderThatTurnedOutToBeIneligible() {
        // expireUnpaidOrder re-checks under a row lock; a payment landing in between makes it
        // return false, and that order must not be reported as released.
        OrderRepository orders = mock(OrderRepository.class);
        OrderService orderService = mock(OrderService.class);
        when(orders.findUnpaidOrderIdsPlacedBefore(any())).thenReturn(List.of(11L, 12L));
        when(orderService.expireUnpaidOrder(11L)).thenReturn(false);
        when(orderService.expireUnpaidOrder(12L)).thenReturn(true);

        assertEquals(1, service(orders, orderService).expireUnpaidOrders());
    }

    @Test
    void oneFailingOrderDoesNotAbortTheSweep() {
        OrderRepository orders = mock(OrderRepository.class);
        OrderService orderService = mock(OrderService.class);
        when(orders.findUnpaidOrderIdsPlacedBefore(any())).thenReturn(List.of(11L, 12L, 13L));
        when(orderService.expireUnpaidOrder(11L)).thenReturn(true);
        when(orderService.expireUnpaidOrder(12L)).thenThrow(new IllegalStateException("row locked"));
        when(orderService.expireUnpaidOrder(13L)).thenReturn(true);

        assertEquals(2, service(orders, orderService).expireUnpaidOrders());
        verify(orderService).expireUnpaidOrder(13L);
    }

    @Test
    void expiresNothingWhenThereAreNoAbandonedOrders() {
        OrderRepository orders = mock(OrderRepository.class);
        OrderService orderService = mock(OrderService.class);
        when(orders.findUnpaidOrderIdsPlacedBefore(any())).thenReturn(List.of());

        assertEquals(0, service(orders, orderService).expireUnpaidOrders());
        verify(orderService, never()).expireUnpaidOrder(anyLong());
    }

    @Test
    void usesTheConfiguredReservationWindowAsTheCutoff() {
        OrderRepository orders = mock(OrderRepository.class);
        OrderService orderService = mock(OrderService.class);
        when(orders.findUnpaidOrderIdsPlacedBefore(any())).thenReturn(List.of());

        LocalDateTime before = LocalDateTime.now().minusMinutes(30);
        service(orders, orderService).expireUnpaidOrders();
        LocalDateTime after = LocalDateTime.now().minusMinutes(30);

        var captor = org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orders).findUnpaidOrderIdsPlacedBefore(captor.capture());
        LocalDateTime cutoff = captor.getValue();
        assertFalse(cutoff.isBefore(before.minusSeconds(5)), "cutoff should be ~30 minutes ago");
        assertFalse(cutoff.isAfter(after.plusSeconds(5)), "cutoff should be ~30 minutes ago");
    }

    @Test
    void anExplicitCutoffIsPassedStraightThrough() {
        OrderRepository orders = mock(OrderRepository.class);
        OrderService orderService = mock(OrderService.class);
        LocalDateTime cutoff = LocalDateTime.of(2026, 8, 6, 12, 0);
        when(orders.findUnpaidOrderIdsPlacedBefore(cutoff)).thenReturn(List.of(7L));
        when(orderService.expireUnpaidOrder(7L)).thenReturn(true);

        assertEquals(1, service(orders, orderService).expireUnpaidOrders(cutoff));
        verify(orders).findUnpaidOrderIdsPlacedBefore(cutoff);
    }
}
