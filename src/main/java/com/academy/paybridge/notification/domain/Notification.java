package com.academy.paybridge.notification.domain;

import com.academy.paybridge.notification.service.NotificationStatus;
import com.academy.paybridge.notification.service.NotificationType;
import com.academy.paybridge.shared.audit.BaseEntity;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "notification")
public class Notification extends BaseEntity {

    @Column(name = "customer_id", nullable = false, updatable = false) private UUID customerId;
    @Column(nullable = false, updatable = false) private String channel;
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false) private NotificationType type;
    @Column(nullable = false, updatable = false) private String subject;
    @Column(nullable = false, updatable = false, columnDefinition = "text") private String body;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private NotificationStatus status;

    protected Notification() {}

    public static Notification of(UUID customerId, String channel, NotificationType type,
                                  String subject, String body, NotificationStatus status) {
        Notification n = new Notification();
        n.customerId = customerId; n.channel = channel; n.type = type;
        n.subject = subject; n.body = body; n.status = status;
        return n;
    }
}