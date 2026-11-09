package com.sunglassstore.service;

import com.sunglassstore.config.ShiprocketConfig;
import com.sunglassstore.dto.request.CreateShipmentRequest;
import com.sunglassstore.dto.shiprocket.ShiprocketAwbResponse;
import com.sunglassstore.dto.shiprocket.ShiprocketTrackingResponse;
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
    private ShiprocketClient shiprocketClient;
    private ShiprocketConfig shiprocketConfig;
    private DeliveryEstimateService deliveryEstimates;
    private ShipmentServiceImpl service;

    @BeforeEach
    void setUp() {
        shipments = mock(ShipmentRepository.class);
        orders = mock(OrderRepository.class);
        orderService = mock(OrderService.class);
        publisher = mock(ApplicationEventPublisher.class);
        shiprocketClient = mock(ShiprocketClient.class);
        shiprocketConfig = mock(ShiprocketConfig.class);
        deliveryEstimates = mock(DeliveryEstimateService.class);
        service = new ShipmentServiceImpl(shipments, orders, orderService, publisher, shiprocketClient,
                shiprocketConfig, deliveryEstimates);
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

    // ---- Expected delivery date (Shiprocket ETD) ----

    @Test
    void assigningAwbReEstimatesDeliveryWithTheAssignedCourier() {
        Shipment shipment = shiprocketShipment(ShipmentStatus.PENDING);
        shipment.setExpectedDeliveryAt(LocalDateTime.of(2026, 11, 13, 20, 0));
        LocalDateTime courierEta = LocalDateTime.of(2026, 11, 16, 20, 0);
        when(shipments.findById(3L)).thenReturn(Optional.of(shipment));
        when(shiprocketClient.assignAwb(900L)).thenReturn(new ShiprocketAwbResponse(
                new ShiprocketAwbResponse.AwbData(new ShiprocketAwbResponse.AwbData.AwbDetails("AWB1", 24, "Delhivery")), 1));
        when(deliveryEstimates.shipmentEta("560001", 24)).thenReturn(Optional.of(courierEta));

        service.assignAwb(3L);

        assertEquals(courierEta, shipment.getExpectedDeliveryAt());
        assertEquals(ShipmentStatus.PACKED, shipment.getShipmentStatus());
        verify(deliveryEstimates).shipmentEta("560001", 24);
        verify(shipments).save(shipment);
    }

    @Test
    void assigningAwbKeepsExistingEstimateWhenShiprocketHasNoEta() {
        Shipment shipment = shiprocketShipment(ShipmentStatus.PENDING);
        LocalDateTime original = LocalDateTime.of(2026, 11, 13, 20, 0);
        shipment.setExpectedDeliveryAt(original);
        when(shipments.findById(3L)).thenReturn(Optional.of(shipment));
        when(shiprocketClient.assignAwb(900L)).thenReturn(new ShiprocketAwbResponse(
                new ShiprocketAwbResponse.AwbData(new ShiprocketAwbResponse.AwbData.AwbDetails("AWB1", 24, "Delhivery")), 1));
        when(deliveryEstimates.shipmentEta(any(), any())).thenReturn(Optional.empty());

        service.assignAwb(3L);

        assertEquals(original, shipment.getExpectedDeliveryAt());
    }

    @Test
    void webhookEtdUpdatesExpectedDeliveryEvenWithoutStatusChange() {
        Shipment shipment = shiprocketShipment(ShipmentStatus.SHIPPED);
        shipment.setAwbCode("AWB1");
        shipment.setExpectedDeliveryAt(LocalDateTime.of(2026, 11, 13, 20, 0));
        when(shipments.findByAwbCode("AWB1")).thenReturn(Optional.of(shipment));

        service.handleWebhook("AWB1", "IN TRANSIT", "2026-11-15 18:30:00");

        assertEquals(LocalDateTime.of(2026, 11, 15, 18, 30), shipment.getExpectedDeliveryAt());
        verify(shipments).save(shipment);
        verifyNoInteractions(publisher); // a revised date alone does not email the customer
    }

    @Test
    void webhookStatusChangeCarriesTheRevisedEtdIntoTheCustomerEmail() {
        Shipment shipment = shiprocketShipment(ShipmentStatus.PACKED);
        shipment.setAwbCode("AWB1");
        when(shipments.findByAwbCode("AWB1")).thenReturn(Optional.of(shipment));

        service.handleWebhook("AWB1", "PICKED UP", "Nov 17, 2026");

        assertEquals(LocalDateTime.of(2026, 11, 17, 20, 0), shipment.getExpectedDeliveryAt());
        assertEquals(ShipmentStatus.SHIPPED, shipment.getShipmentStatus());
        var event = org.mockito.ArgumentCaptor.forClass(ShipmentStatusEmailRequested.class);
        verify(publisher).publishEvent(event.capture());
        assertEquals(LocalDateTime.of(2026, 11, 17, 20, 0), event.getValue().expectedDeliveryAt());
    }

    @Test
    void webhookIgnoresEtdOnceDelivered() {
        Shipment shipment = shiprocketShipment(ShipmentStatus.DELIVERED);
        shipment.setAwbCode("AWB1");
        LocalDateTime original = LocalDateTime.of(2026, 11, 13, 20, 0);
        shipment.setExpectedDeliveryAt(original);
        shipment.setDeliveredAt(LocalDateTime.of(2026, 11, 12, 14, 0));
        when(shipments.findByAwbCode("AWB1")).thenReturn(Optional.of(shipment));

        service.handleWebhook("AWB1", "DELIVERED", "2026-11-20 18:00:00");

        assertEquals(original, shipment.getExpectedDeliveryAt());
        verify(shipments, never()).save(any());
    }

    @Test
    void webhookWithBlankOrGarbageEtdLeavesEstimateUntouched() {
        Shipment shipment = shiprocketShipment(ShipmentStatus.SHIPPED);
        shipment.setAwbCode("AWB1");
        LocalDateTime original = LocalDateTime.of(2026, 11, 13, 20, 0);
        shipment.setExpectedDeliveryAt(original);
        when(shipments.findByAwbCode("AWB1")).thenReturn(Optional.of(shipment));

        service.handleWebhook("AWB1", "IN TRANSIT", "");
        service.handleWebhook("AWB1", "IN TRANSIT", "not a date");
        service.handleWebhook("AWB1", "IN TRANSIT");

        assertEquals(original, shipment.getExpectedDeliveryAt());
        verify(shipments, never()).save(any());
    }

    @Test
    void liveTrackingRefreshesExpectedDelivery() {
        Shipment shipment = shiprocketShipment(ShipmentStatus.SHIPPED);
        shipment.setAwbCode("AWB1");
        when(shipments.findById(3L)).thenReturn(Optional.of(shipment));
        when(shiprocketClient.trackByAwb("AWB1")).thenReturn(new ShiprocketTrackingResponse(
                new ShiprocketTrackingResponse.TrackingData(1, 6, null, null, null, "2026-11-18 20:00:00")));

        service.trackShipment(3L);

        assertEquals(LocalDateTime.of(2026, 11, 18, 20, 0), shipment.getExpectedDeliveryAt());
        verify(shipments).save(shipment);
    }

    private Shipment shiprocketShipment(ShipmentStatus status) {
        Shipment shipment = shipment(status);
        shipment.setShiprocketShipmentId(900L);
        shipment.getOrder().setShippingPincode("560001");
        return shipment;
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
