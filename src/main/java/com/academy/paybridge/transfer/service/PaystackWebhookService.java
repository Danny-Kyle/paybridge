package com.academy.paybridge.transfer.service;

import com.academy.paybridge.transfer.client.paystack.PaystackWebhookPayload;
import com.academy.paybridge.transfer.domain.WebhookEvent;
import com.academy.paybridge.transfer.repository.WebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

@Service
public class PaystackWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PaystackWebhookService.class);

    private final JsonMapper jsonMapper;
    private final WebhookEventRepository webhookEvents;
    private final TransferService transferService;
    private final Clock clock;

    public PaystackWebhookService(JsonMapper jsonMapper, WebhookEventRepository webhookEvents,
                                  TransferService transferService, Clock clock) {
        this.jsonMapper = jsonMapper; this.webhookEvents = webhookEvents;
        this.transferService = transferService; this.clock = clock;
    }

    @Transactional
    public void handle(byte[] rawBody) {
        PaystackWebhookPayload payload = jsonMapper.readValue(rawBody, PaystackWebhookPayload.class);
        if (payload == null || payload.event() == null || payload.data() == null || payload.data().reference() == null) {
            log.warn("Ignoring malformed Paystack webhook");
            return;
        }
        String event = payload.event();
        if (!event.startsWith("transfer.")) return;                                // we only care about transfers

        String reference = payload.data().reference();
        String key = event + ":" + reference;
        if (webhookEvents.existsByEventKey(key)) return;                           // duplicate delivery
        webhookEvents.save(WebhookEvent.of(key, event, clock.instant()));

        String reason = payload.data().reason() != null ? payload.data().reason() : event;
        switch (event) {
            case "transfer.success"  -> transferService.onGatewaySuccess(reference);
            case "transfer.failed"   -> transferService.onGatewayFailure(reference, reason, false);
            case "transfer.reversed" -> transferService.onGatewayFailure(reference, reason, true);
            default -> log.info("Unhandled Paystack event {}", event);
        }
    }
}