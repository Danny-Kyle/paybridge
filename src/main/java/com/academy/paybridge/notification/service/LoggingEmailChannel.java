package com.academy.paybridge.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class LoggingEmailChannel implements NotificationChannel {
    private static final Logger log = LoggerFactory.getLogger(LoggingEmailChannel.class);

    @Override public String name() { return "EMAIL"; }

    @Override public void deliver(String to, String subject, String body) {
        log.info("EMAIL to={} subject={} body={}", to, subject, body);
    }
}