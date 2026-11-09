package com.sunglassstore.service.impl;

import com.sunglassstore.config.ShiprocketConfig;
import com.sunglassstore.dto.request.CreateShipmentRequest;
import com.sunglassstore.dto.shiprocket.*;
import com.sunglassstore.entity.Order;
import com.sunglassstore.entity.OrderItem;
import com.sunglassstore.entity.Shipment;
import com.sunglassstore.entity.enums.OrderStatus;
import com.sunglassstore.entity.enums.ShipmentStatus;
import com.sunglassstore.exception.BadRequestException;
import com.sunglassstore.exception.ResourceNotFoundException;
import com.sunglassstore.email.event.ShipmentStatusEmailRequested;
import com.sunglassstore.repository.OrderRepository;
import com.sunglassstore.repository.ShipmentRepository;
import com.sunglassstore.service.DeliveryEstimateService;
import com.sunglassstore.service.OrderService;
import com.sunglassstore.service.ShiprocketClient;
import com.sunglassstore.service.ShipmentService;
import com.sunglassstore.shipping.ShiprocketDates;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ShipmentServiceImpl implements ShipmentService {

    private static final Logger log = LoggerFactory.getLogger(ShipmentServiceImpl.class);
    private static final DateTimeFormatter SR_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ShipmentRepository shipmentRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final ApplicationEventPublisher eventPublisher;
    private final ShiprocketClient shiprocketClient;
    private final ShiprocketConfig shiprocketConfig;
    private final DeliveryEstimateService deliveryEstimateService;

    @Value("${invoice.product.hsn:90041000}")
    private String hsnCode;

    // =====================================================================
    // Original (manual) shipment methods — unchanged
    // =====================================================================

    @Override
    @Transactional
    public Shipment createShipment(Long orderId, CreateShipmentRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        if (order.getOrderStatus() != OrderStatus.CONFIRMED &&
                order.getOrderStatus() != OrderStatus.PROCESSING) {
            throw new BadRequestException("Shipment can only be created for CONFIRMED or PROCESSING orders");
        }

        String trackingNumber = request.getTrackingNumber().trim();
        if (shipmentRepository.existsByTrackingNumberIgnoreCase(trackingNumber)) {
            throw new BadRequestException("This tracking number is already assigned to a shipment");
        }

        Shipment shipment = new Shipment();
        shipment.setOrder(order);
        shipment.setShippingProvider(request.getShippingProvider().trim());
        shipment.setTrackingNumber(trackingNumber);
        shipment.setExpectedDeliveryAt(request.getExpectedDeliveryAt());
        shipment.setShipmentStatus(ShipmentStatus.PENDING);

        if (order.getOrderStatus() == OrderStatus.CONFIRMED) {
            orderService.updateOrderStatus(orderId, OrderStatus.PROCESSING, "Shipment created");
        }

        return shipmentRepository.save(shipment);
    }

    @Override
    @Transactional
    public Shipment updateShipmentStatus(Long shipmentId, ShipmentStatus status) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found"));

        ShipmentStatus oldStatus = shipment.getShipmentStatus();
        validateTransition(oldStatus, status);
        shipment.setShipmentStatus(status);

        if (status == ShipmentStatus.SHIPPED) {
            if (shipment.getShippedAt() == null) shipment.setShippedAt(LocalDateTime.now());
            syncOrderStatus(shipment.getOrder(), OrderStatus.SHIPPED, "Order shipped");
        } else if (status == ShipmentStatus.DELIVERED) {
            shipment.setDeliveredAt(LocalDateTime.now());
            syncOrderStatus(shipment.getOrder(), OrderStatus.DELIVERED, "Order delivered");
        }

        Shipment saved = shipmentRepository.save(shipment);
        Order order = shipment.getOrder();
        eventPublisher.publishEvent(new ShipmentStatusEmailRequested(order.getUser().getEmail(),
                order.getUser().getName(), order.getOrderId(), shipment.getShipmentId(), status.name(),
                shipment.getShippingProvider(), shipment.getTrackingNumber(), shipment.getExpectedDeliveryAt()));
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Shipment> getShipments(Long orderId, Pageable pageable) {
        return shipmentRepository.findByOrderOrderId(orderId, pageable);
    }

    // =====================================================================
    // Shiprocket integration methods
    // =====================================================================

    @Override
    @Transactional
    public Shipment createShiprocketShipment(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        if (order.getOrderStatus() != OrderStatus.CONFIRMED &&
                order.getOrderStatus() != OrderStatus.PROCESSING) {
            throw new BadRequestException("Shipment can only be created for CONFIRMED or PROCESSING orders");
        }

        // Build the Shiprocket order request
        ShiprocketOrderRequest srRequest = buildShiprocketRequest(order);

        // Push to Shiprocket
        ShiprocketOrderResponse srResponse = shiprocketClient.createOrder(srRequest);
        log.info("Shiprocket order created: orderId={}, srOrderId={}, srShipmentId={}",
                orderId, srResponse.orderId(), srResponse.shipmentId());

        // Create our Shipment record
        Shipment shipment = new Shipment();
        shipment.setOrder(order);
        shipment.setShippingProvider("Shiprocket");
        shipment.setShiprocketOrderId(srResponse.orderId());
        shipment.setShiprocketShipmentId(srResponse.shipmentId());
        shipment.setShipmentStatus(ShipmentStatus.PENDING);

        // Expected delivery from Shiprocket's ETD for the courier it is likely to assign
        // (recommended, else fastest). Refined to the actual courier once the AWB is assigned.
        deliveryEstimateService.shipmentEta(order.getShippingPincode(), null).ifPresent(eta -> {
            shipment.setExpectedDeliveryAt(eta);
            log.info("Shiprocket expected delivery {} for order {} (pincode {})",
                    eta, orderId, order.getShippingPincode());
        });

        if (order.getOrderStatus() == OrderStatus.CONFIRMED) {
            orderService.updateOrderStatus(orderId, OrderStatus.PROCESSING, "Shiprocket shipment created");
        }

        return shipmentRepository.save(shipment);
    }

    @Override
    @Transactional
    public ShiprocketAwbResponse assignAwb(Long shipmentId) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found"));

        if (shipment.getShiprocketShipmentId() == null) {
            throw new BadRequestException("This shipment is not linked to Shiprocket");
        }
        if (shipment.getAwbCode() != null) {
            throw new BadRequestException("AWB already assigned: " + shipment.getAwbCode());
        }

        ShiprocketAwbResponse awbResponse = shiprocketClient.assignAwb(shipment.getShiprocketShipmentId());

        if (awbResponse.awbAssignStatus() == 1 && awbResponse.response() != null
                && awbResponse.response().data() != null) {
            var data = awbResponse.response().data();
            shipment.setAwbCode(data.awbCode());
            shipment.setCourierName(data.courierName());
            shipment.setTrackingNumber(data.awbCode()); // AWB doubles as tracking number
            shipment.setShippingProvider(data.courierName());
            shipment.setShipmentStatus(ShipmentStatus.PACKED);
            // Re-estimate with the courier that was actually assigned.
            deliveryEstimateService.shipmentEta(shipment.getOrder().getShippingPincode(), data.courierCompanyId())
                    .ifPresent(shipment::setExpectedDeliveryAt);
            shipmentRepository.save(shipment);
            log.info("AWB assigned: shipmentId={}, awb={}, courier={}",
                    shipmentId, data.awbCode(), data.courierName());
        }

        return awbResponse;
    }

    @Override
    @Transactional
    public Map<?, ?> schedulePickup(Long shipmentId) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found"));

        if (shipment.getShiprocketShipmentId() == null) {
            throw new BadRequestException("This shipment is not linked to Shiprocket");
        }

        Map<?, ?> result = shiprocketClient.schedulePickup(shipment.getShiprocketShipmentId());
        log.info("Pickup scheduled for shipmentId={}", shipmentId);
        return result;
    }

    @Override
    @Transactional
    public Map<?, ?> generateLabel(Long shipmentId) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found"));

        if (shipment.getShiprocketShipmentId() == null) {
            throw new BadRequestException("This shipment is not linked to Shiprocket");
        }

        Map<?, ?> result = shiprocketClient.generateLabel(shipment.getShiprocketShipmentId());

        // Extract label URL if present
        if (result != null && result.get("label_url") != null) {
            shipment.setLabelUrl(result.get("label_url").toString());
            shipmentRepository.save(shipment);
        }

        return result;
    }

    @Override
    @Transactional
    public ShiprocketTrackingResponse trackShipment(Long shipmentId) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found"));

        ShiprocketTrackingResponse tracking;
        if (shipment.getAwbCode() != null) {
            tracking = shiprocketClient.trackByAwb(shipment.getAwbCode());
        } else if (shipment.getShiprocketShipmentId() != null) {
            tracking = shiprocketClient.trackByShipmentId(shipment.getShiprocketShipmentId());
        } else {
            throw new BadRequestException("No AWB or Shiprocket shipment ID available for tracking");
        }

        // Live tracking carries the courier's latest ETD — keep the stored date in step with it.
        if (tracking != null && tracking.trackingData() != null
                && applyCourierEtd(shipment, tracking.trackingData().etd())) {
            shipmentRepository.save(shipment);
        }
        return tracking;
    }

    @Override
    @Transactional
    public void handleWebhook(String awbCode, String currentStatus) {
        handleWebhook(awbCode, currentStatus, null);
    }

    @Override
    @Transactional
    public void handleWebhook(String awbCode, String currentStatus, String etd) {
        if (awbCode == null || awbCode.isBlank()) {
            log.warn("Shiprocket webhook: missing AWB code");
            return;
        }

        Shipment shipment = shipmentRepository.findByAwbCode(awbCode).orElse(null);
        if (shipment == null) {
            log.warn("Shiprocket webhook: no shipment found for AWB {}", awbCode);
            return;
        }

        // The courier's revised ETD applies even when the status itself has not moved.
        boolean etdChanged = applyCourierEtd(shipment, etd);

        ShipmentStatus mappedStatus = mapShiprocketStatus(currentStatus);
        if (mappedStatus == null) {
            log.info("Shiprocket webhook: unmapped status '{}' for AWB {}", currentStatus, awbCode);
            if (etdChanged) shipmentRepository.save(shipment);
            return;
        }

        ShipmentStatus oldStatus = shipment.getShipmentStatus();
        if (oldStatus == mappedStatus) { // no status change
            if (etdChanged) shipmentRepository.save(shipment);
            return;
        }

        // Only move forward in the lifecycle (allow FAILED/RETURNED from any state)
        if (mappedStatus != ShipmentStatus.FAILED && mappedStatus != ShipmentStatus.RETURNED) {
            if (oldStatus.ordinal() >= mappedStatus.ordinal()) {
                log.info("Shiprocket webhook: ignoring backward transition {} -> {} for AWB {}",
                        oldStatus, mappedStatus, awbCode);
                if (etdChanged) shipmentRepository.save(shipment);
                return;
            }
        }

        shipment.setShipmentStatus(mappedStatus);

        if (mappedStatus == ShipmentStatus.SHIPPED && shipment.getShippedAt() == null) {
            shipment.setShippedAt(LocalDateTime.now());
            syncOrderStatus(shipment.getOrder(), OrderStatus.SHIPPED, "Shipped via " + shipment.getCourierName());
        } else if (mappedStatus == ShipmentStatus.DELIVERED) {
            shipment.setDeliveredAt(LocalDateTime.now());
            syncOrderStatus(shipment.getOrder(), OrderStatus.DELIVERED, "Delivered via " + shipment.getCourierName());
        }

        shipmentRepository.save(shipment);

        Order order = shipment.getOrder();
        eventPublisher.publishEvent(new ShipmentStatusEmailRequested(
                order.getUser().getEmail(), order.getUser().getName(),
                order.getOrderId(), shipment.getShipmentId(), mappedStatus.name(),
                shipment.getShippingProvider(), shipment.getTrackingNumber(),
                shipment.getExpectedDeliveryAt()));

        log.info("Shiprocket webhook: AWB {} updated {} -> {}", awbCode, oldStatus, mappedStatus);
    }

    // =====================================================================
    // Private helpers
    // =====================================================================

    /**
     * Applies a courier-supplied ETD to a shipment that is still in transit.
     * Returns true when the stored expected delivery date changed.
     */
    private boolean applyCourierEtd(Shipment shipment, String etd) {
        if (shipment.getDeliveredAt() != null) return false;
        ShipmentStatus status = shipment.getShipmentStatus();
        if (status == ShipmentStatus.DELIVERED || status == ShipmentStatus.RETURNED) return false;
        return ShiprocketDates.parseDateTime(etd)
                .filter(eta -> !eta.equals(shipment.getExpectedDeliveryAt()))
                .map(eta -> {
                    shipment.setExpectedDeliveryAt(eta);
                    log.info("Expected delivery for shipment {} updated to {}", shipment.getShipmentId(), eta);
                    return true;
                })
                .orElse(false);
    }

    private ShiprocketOrderRequest buildShiprocketRequest(Order order) {
        List<ShiprocketOrderRequest.Item> items = order.getItems().stream()
                .map(this::toShiprocketItem)
                .toList();

        // Names: Shiprocket expects first + last name separately
        String[] nameParts = splitName(order.getShippingName());

        return new ShiprocketOrderRequest(
                order.getOrderId().toString(),
                order.getPurchasedAt().format(SR_DATE),
                shiprocketConfig.pickupLocation(),
                "",  // channel_id — empty for ad-hoc orders
                "Shades World Order #" + order.getOrderId(),

                // Billing = Shipping (sunglasses store, same address)
                nameParts[0], nameParts[1],
                order.getShippingAddressLine1(),
                order.getShippingAddressLine2() != null ? order.getShippingAddressLine2() : "",
                order.getShippingCity(),
                order.getShippingPincode(),
                order.getShippingState(),
                order.getShippingCountry(),
                order.getUser().getEmail(),
                cleanPhone(order.getShippingPhone()),

                // Shipping = Billing
                true,
                "", "", "", "", "", "", "", "", "", "",

                // Order details
                items,
                "Prepaid", // all online payments
                order.getSubtotalAmount(),

                // Default dimensions for sunglasses box (cm / kg)
                new BigDecimal("20"),  // length cm
                new BigDecimal("12"),  // breadth cm
                new BigDecimal("8"),   // height cm
                new BigDecimal("0.3")  // weight kg
        );
    }

    private ShiprocketOrderRequest.Item toShiprocketItem(OrderItem item) {
        return new ShiprocketOrderRequest.Item(
                item.getProductName(),
                item.getSku(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getDiscountAmount(),
                item.getTaxAmount(),
                hsnCode
        );
    }

    private String[] splitName(String fullName) {
        if (fullName == null || fullName.isBlank()) return new String[]{"Customer", ""};
        String[] parts = fullName.trim().split("\\s+", 2);
        return parts.length == 2 ? parts : new String[]{parts[0], ""};
    }

    private String cleanPhone(String phone) {
        if (phone == null) return "";
        // Strip country code prefix if present
        String cleaned = phone.replaceAll("[^0-9]", "");
        if (cleaned.startsWith("91") && cleaned.length() > 10) {
            cleaned = cleaned.substring(cleaned.length() - 10);
        }
        return cleaned;
    }

    /**
     * Maps Shiprocket's current_status string to our ShipmentStatus enum.
     * Shiprocket status strings: https://apidocs.shiprocket.in/#652e7c53-5e8a-4e39-b6fe-0e7e3b9b5e3d
     */
    private ShipmentStatus mapShiprocketStatus(String srStatus) {
        if (srStatus == null) return null;
        return switch (srStatus.toUpperCase().trim()) {
            case "PICKUP SCHEDULED", "PICKUP GENERATED", "PICKUP QUEUED" -> ShipmentStatus.PACKED;
            case "PICKED UP", "SHIPPED", "IN TRANSIT" -> ShipmentStatus.SHIPPED;
            case "REACHED DESTINATION HUB", "RECEIVED AT ORIGIN CENTER" -> ShipmentStatus.IN_TRANSIT;
            case "OUT FOR DELIVERY" -> ShipmentStatus.OUT_FOR_DELIVERY;
            case "DELIVERED" -> ShipmentStatus.DELIVERED;
            case "UNDELIVERED", "LOST", "DAMAGED" -> ShipmentStatus.FAILED;
            case "RTO INITIATED", "RTO IN TRANSIT", "RTO DELIVERED", "RETURNED" -> ShipmentStatus.RETURNED;
            case "CANCELED", "CANCELLED" -> ShipmentStatus.FAILED;
            default -> null;
        };
    }

    private void syncOrderStatus(Order order, OrderStatus target, String note) {
        if (order.getOrderStatus() == target) return;
        orderService.updateOrderStatus(order.getOrderId(), target, note);
    }

    private void validateTransition(ShipmentStatus current, ShipmentStatus next) {
        if (current == next) throw new BadRequestException("Shipment is already in status " + current);
        boolean valid = switch (current) {
            case PENDING -> next == ShipmentStatus.PACKED || next == ShipmentStatus.FAILED;
            case PACKED -> next == ShipmentStatus.SHIPPED || next == ShipmentStatus.FAILED;
            case SHIPPED -> next == ShipmentStatus.IN_TRANSIT || next == ShipmentStatus.FAILED
                    || next == ShipmentStatus.RETURNED;
            case IN_TRANSIT -> next == ShipmentStatus.OUT_FOR_DELIVERY || next == ShipmentStatus.FAILED
                    || next == ShipmentStatus.RETURNED;
            case OUT_FOR_DELIVERY -> next == ShipmentStatus.DELIVERED || next == ShipmentStatus.FAILED
                    || next == ShipmentStatus.RETURNED;
            case FAILED -> next == ShipmentStatus.IN_TRANSIT || next == ShipmentStatus.OUT_FOR_DELIVERY;
            case DELIVERED -> next == ShipmentStatus.RETURNED;
            case RETURNED -> false;
        };
        if (!valid) throw new BadRequestException("Invalid shipment status transition: " + current + " to " + next);
    }
}
