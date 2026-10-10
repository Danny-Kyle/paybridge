package com.academy.paybridge.transfer.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_event")
public class WebhookEvent {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "event_key", nullable = false) private String eventKey;
    @Column(name = "event_type", nullable = false) private String eventType;
    @Column(name = "received_at", nullable = false) private Instant receivedAt;

    protected WebhookEvent() {}

    public static WebhookEvent of(String key, String type, Instant now) {
        WebhookEvent e = new WebhookEvent();
        e.eventKey = key; e.eventType = type; e.receivedAt = now;
        return e;
    }
}