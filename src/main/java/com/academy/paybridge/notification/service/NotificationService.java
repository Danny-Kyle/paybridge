package com.academy.paybridge.notification.service;

import com.academy.paybridge.customer.api.CustomerApi;
import com.academy.paybridge.customer.api.CustomerSummary;
import com.academy.paybridge.notification.domain.Notification;
import com.academy.paybridge.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final CustomerApi customerApi;
    private final NotificationRepository repository;
    private final NotificationChannel channel;

    public NotificationService(CustomerApi customerApi, NotificationRepository repository, NotificationChannel channel) {
        this.customerApi = customerApi; this.repository = repository; this.channel = channel;
    }

    @Transactional
    public void send(UUID customerId, NotificationType type, String subject, String body) {
        CustomerSummary customer = customerApi.getCustomer(customerId);
        NotificationStatus status;
        try {
            channel.deliver(customer.email(), subject, body);
            status = NotificationStatus.SENT;
        } catch (RuntimeException e) {
            log.warn("Notification delivery failed for {}", customerId, e);
            status = NotificationStatus.FAILED;
        }
        repository.save(Notification.of(customerId, channel.name(), type, subject, body, status));
    }
}