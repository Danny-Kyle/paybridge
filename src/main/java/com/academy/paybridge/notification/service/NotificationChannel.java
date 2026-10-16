package com.academy.paybridge.notification.service;

public interface NotificationChannel {
    String name();
    void deliver(String to, String subject, String body);
}
