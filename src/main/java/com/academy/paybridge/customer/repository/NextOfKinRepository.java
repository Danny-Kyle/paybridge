package com.academy.paybridge.customer.repository;

import com.academy.paybridge.customer.domain.NextOfKin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NextOfKinRepository extends JpaRepository<NextOfKin, UUID> {
    Optional<NextOfKin> findByCustomerId(UUID customerId);
}
