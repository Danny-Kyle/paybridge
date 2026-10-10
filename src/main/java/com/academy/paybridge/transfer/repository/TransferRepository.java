package com.academy.paybridge.transfer.repository;

import com.academy.paybridge.transfer.domain.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {
    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);
    Optional<Transfer> findByReference(String reference);
}