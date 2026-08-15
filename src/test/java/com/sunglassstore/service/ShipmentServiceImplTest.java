package com.sunglassstore.service;

import com.sunglassstore.dto.request.CreateShipmentRequest;
import com.sunglassstore.email.event.ShipmentStatusEmailRequested;
import com.sunglassstore.entity.Order;
import com.sunglassstore.entity.Shipment;
import com.sunglassstore.entity.User;
import com.sunglassstore.entity.enums.OrderStatus;
import com.sunglassstore.entity.enums.ShipmentStatus;
import com.sunglassstore.exception.BadRequestException;
import com.sunglassstore.repository.OrderRepository;
import com.sunglassstore.repository.ShipmentRepository;
import com.sunglassstore.service.impl.ShipmentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShipmentServiceImplTest {
    private ShipmentRepository shipments;
    private OrderRepository orders;
    private OrderService orderService;
    private ApplicationEventPublisher publisher;
    private ShipmentServiceImpl service;

    @BeforeEach
    void setUp() {
        shipments = mock(ShipmentRepository.class);
        orders = mock(OrderRepository.class);
        orderService = mock(OrderService.class);
        publisher = mock(ApplicationEventPublisher.class);
        service = new ShipmentServiceImpl(shipments, orders, orderService, publisher);
    }

    @Test
    void createsShipmentWithTrackingAndExpectedDelivery() {
        Order order = order(OrderStatus.CONFIRMED);
        CreateShipmentRequest request = request("BlueDart", " BD-123 ");
        when(orders.findById(11L)).thenReturn(Optional.of(order));
        when(shipments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Shipment created = service.createShipment(11L, request);

        assertEquals("BD-123", created.getTrackingNumber());
        assertEquals(request.getExpectedDeliveryAt(), created.getExpectedDeliveryAt());
        verify(orderService).updateOrderStatus(11L, OrderStatus.PROCESSING, "Shipment created");
    }

    @Test
    void processingOrderDoesNotReceiveDuplicateProcessingTransition() {
        Order order = order(OrderStatus.PROCESSING);
        when(orders.findById(11L)).thenReturn(Optional.of(order));
        when(shipments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.createShipment(11L, request("Delhivery", "DL-9"));

        verifyNoInteractions(orderService);
    }

    @Test
    void duplicateTrackingNumberIsRejected() {
        when(orders.findById(11L)).thenReturn(Optional.of(order(OrderStatus.CONFIRMED)));
        when(shipments.existsByTrackingNumberIgnoreCase("ABC-1")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> service.createShipment(11L, request("Courier", "ABC-1")));
        verify(shipments, never()).save(any());
    }

    @Test
    void shipmentMustFollowDeliverySequence() {
        Shipment shipment = shipment(ShipmentStatus.PENDING);
        when(shipments.findById(3L)).thenReturn(Optional.of(shipment));

        assertThrows(BadRequestException.class,
                () -> service.updateShipmentStatus(3L, ShipmentStatus.DELIVERED));
        verify(shipments, never()).save(any());
    }

    @Test
    void shippedStatusSynchronizesOrderAndPublishesCustomerNotification() {
        Shipment shipment = shipment(ShipmentStatus.PACKED);
        when(shipments.findById(3L)).thenReturn(Optional.of(shipment));
        when(shipments.save(shipment)).thenReturn(shipment);

        Shipment updated = service.updateShipmentStatus(3L, ShipmentStatus.SHIPPED);

        assertNotNull(updated.getShippedAt());
        verify(orderService).updateOrderStatus(11L, OrderStatus.SHIPPED, "Order shipped");
        verify(publisher).publishEvent(any(ShipmentStatusEmailRequested.class));
    }

    @Test
    void alreadySynchronizedOrderDoesNotReceiveDuplicateStatusUpdate() {
        Shipment shipment = shipment(ShipmentStatus.PACKED);
        shipment.getOrder().setOrderStatus(OrderStatus.SHIPPED);
        when(shipments.findById(3L)).thenReturn(Optional.of(shipment));
        when(shipments.save(shipment)).thenReturn(shipment);

        service.updateShipmentStatus(3L, ShipmentStatus.SHIPPED);

        verifyNoInteractions(orderService);
    }

    private CreateShipmentRequest request(String provider, String tracking) {
        CreateShipmentRequest request = new CreateShipmentRequest();
        request.setShippingProvider(provider);
        request.setTrackingNumber(tracking);
        request.setExpectedDeliveryAt(LocalDateTime.now().plusDays(4));
        return request;
    }

    private Shipment shipment(ShipmentStatus status) {
        Shipment shipment = new Shipment();
        shipment.setShipmentId(3L);
        shipment.setOrder(order(OrderStatus.PROCESSING));
        shipment.setShippingProvider("BlueDart");
        shipment.setTrackingNumber("BD-123");
        shipment.setShipmentStatus(status);
        return shipment;
    }

    private Order order(OrderStatus status) {
        User user = new User();
        user.setName("Customer");
        user.setEmail("customer@example.com");
        Order order = new Order();
        order.setOrderId(11L);
        order.setUser(user);
        order.setOrderStatus(status);
        return order;
    }
}
