package com.sunglassstore.notification;

import com.sunglassstore.email.event.RefundCompletedEmailRequested;
import com.sunglassstore.email.event.ReturnStatusEmailRequested;
import com.sunglassstore.email.event.ShipmentStatusEmailRequested;
import com.sunglassstore.email.event.OrderCancelledEmailRequested;
import com.sunglassstore.entity.Notification;
import com.sunglassstore.entity.User;
import com.sunglassstore.entity.enums.NotificationStatus;
import com.sunglassstore.entity.enums.NotificationType;
import com.sunglassstore.notification.event.OrderPaymentConfirmed;
import com.sunglassstore.repository.NotificationRepository;
import com.sunglassstore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CustomerNotificationEventSubscriber {
    private final NotificationRepository notifications;
    private final UserRepository users;
    private final com.sunglassstore.service.CommunicationPreferenceService preferences;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onPaymentConfirmed(OrderPaymentConfirmed event) {
        users.findById(event.userId()).filter(user -> preferences.allowsInApp(user.getUserId(), com.sunglassstore.service.CommunicationPreferenceService.Topic.ORDER)).ifPresent(user -> create(user, "Order #" + event.orderId() + " confirmed",
                "Payment received. Your order total is INR " + event.totalAmount().setScale(2, RoundingMode.HALF_UP) + "."));
        notifyStaff("New paid order #" + event.orderId(), event.customerName() + " placed an order worth INR " + event.totalAmount() + ".");
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onShipment(ShipmentStatusEmailRequested event) {
        users.findByEmailIgnoreCase(event.email()).filter(user -> preferences.allowsInApp(user.getUserId(), com.sunglassstore.service.CommunicationPreferenceService.Topic.SHIPMENT)).ifPresent(user -> create(user, "Order #" + event.orderId() + " delivery update",
                "Your shipment is now " + friendly(event.status()) + ". Tracking: " + event.trackingNumber() + "."));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onReturn(ReturnStatusEmailRequested event) {
        users.findByEmailIgnoreCase(event.email()).filter(user -> preferences.allowsInApp(user.getUserId(), com.sunglassstore.service.CommunicationPreferenceService.Topic.RETURN_REFUND)).ifPresent(user -> create(user, "Return #" + event.returnId() + " update",
                "Your return for order #" + event.orderId() + " is " + friendly(event.status()) + "."));
        if ("REQUESTED".equals(event.status())) notifyStaff("New return request #" + event.returnId(),
                event.customerName() + " requested a return for order #" + event.orderId() + ".");
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onRefund(RefundCompletedEmailRequested event) {
        users.findByEmailIgnoreCase(event.email()).filter(user -> preferences.allowsInApp(user.getUserId(), com.sunglassstore.service.CommunicationPreferenceService.Topic.RETURN_REFUND)).ifPresent(user -> create(user, "Refund completed",
                "INR " + event.amount().setScale(2, RoundingMode.HALF_UP) + " was refunded for order #" + event.orderId() + "."));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onOrderCancelled(OrderCancelledEmailRequested event) {
        users.findByEmailIgnoreCase(event.email()).filter(user -> preferences.allowsInApp(user.getUserId(), com.sunglassstore.service.CommunicationPreferenceService.Topic.ORDER)).ifPresent(user -> create(user,
                "Order #" + event.orderId() + " cancelled",
                event.refundAmount().signum() > 0
                        ? "Your order was cancelled and INR " + event.refundAmount().setScale(2, RoundingMode.HALF_UP)
                            + " was recorded for refund."
                        : "Your order was cancelled. No payment refund was required."));
        notifyStaff("Order #" + event.orderId() + " cancelled",
                event.customerName() + " cancelled the order before fulfilment.");
    }

    private void notifyStaff(String subject, String message) {
        users.findActiveUsersByRoleNames(List.of("ADMIN", "SUPPORT")).forEach(user -> create(user, subject, message));
    }

    private void create(User user, String subject, String message) {
        Notification notification = new Notification();
        notification.setUser(user); notification.setNotificationType(NotificationType.IN_APP);
        notification.setSubject(subject); notification.setMessage(message);
        notification.setNotificationStatus(NotificationStatus.SENT); notification.setSentAt(LocalDateTime.now());
        notifications.save(notification);
    }

    private String friendly(String value) { return value.toLowerCase().replace('_', ' '); }
}
