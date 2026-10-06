package com.academy.paybridge.shared.audit;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;
import java.util.UUID;

@MappedSuperclass
@EntityListener
public abstract class BaseEntity {
    @Id
    @GeneratedValue
    UUID id;

    @CreatedDate
    @Column(updatable = false, name = "created-at", nullable = false)
    protected Instant createdAt;

    @Column(name = "updated-at")
    @LastModifiedDate
    protected Instant updatedAt;

    @Version
    protected Long version;
}
