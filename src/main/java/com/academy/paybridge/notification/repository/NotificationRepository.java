package com.academy.paybridge.notification.repository;

import com.academy.paybridge.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {}