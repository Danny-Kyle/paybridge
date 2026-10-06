package com.academy.paybridge.customer.repository;

import com.academy.paybridge.customer.domain.Customer;
import com.academy.paybridge.customer.domain.NextofKin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NextofKinRepository extends JpaRepository<NextofKin, UUID> {
    Optional<NextofKin> findByCustomerId(UUID customerId);
}
