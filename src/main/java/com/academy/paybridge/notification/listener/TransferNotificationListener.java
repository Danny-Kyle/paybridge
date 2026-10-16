package com.academy.paybridge.notification.listener;

import com.academy.paybridge.notification.service.NotificationService;
import com.academy.paybridge.notification.service.NotificationType;
import com.academy.paybridge.shared.money.Money;
import com.academy.paybridge.transfer.api.TransferCompleted;
import com.academy.paybridge.transfer.api.TransferFailed;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class TransferNotificationListener {

    private final NotificationService notifications;

    TransferNotificationListener(NotificationService notifications) { this.notifications = notifications; }

    @ApplicationModuleListener
    void on(TransferCompleted e) {
        String amount = format(e.amount());
        notifications.send(e.senderCustomerId(), NotificationType.TRANSFER_SENT,
                "Transfer sent", "Your transfer of " + amount + " was completed.");
        notifications.send(e.destinationCustomerId(), NotificationType.TRANSFER_RECEIVED,
                "Money received", "You received " + amount + ".");
        if (e.redirected()) {
            notifications.send(e.originalRecipientCustomerId(), NotificationType.TRANSFER_REDIRECTED,
                    "Transfer routed to your next of kin",
                    "A transfer of " + amount + " intended for you was routed to your next of kin.");
        }
    }

    @ApplicationModuleListener
    void on(TransferFailed e) {
        notifications.send(e.senderCustomerId(), NotificationType.TRANSFER_FAILED,
                "Transfer failed", "Your transfer failed and has been refunded. Reason: " + e.reason());
    }

    private static String format(Money m) { return m.currency() + " " + m.toMajorUnits().toPlainString(); }
}