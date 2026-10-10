package com.academy.paybridge.transfer.repository;

import com.academy.paybridge.transfer.domain.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {
    boolean existsByEventKey(String eventKey);
}