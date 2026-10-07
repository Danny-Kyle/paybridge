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
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @CreatedDate
    @Column(updatable = false, name = "created-at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated-at", nullable = false)
    @LastModifiedDate
    private Instant updatedAt;

    @Version
    protected Long version;

    public UUID getId() {
        return id;
    };

    public Instant getCreatedAt() {
        return createdAt;
    };
    public Instant getUpdatedAt() {
        return updatedAt;
    };

    @Override
    public boolean equals(Object o){
        if (this == o) return true;
        if (!(o instanceof BaseEntity other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode(){return getClass().hashCode();    }


}
