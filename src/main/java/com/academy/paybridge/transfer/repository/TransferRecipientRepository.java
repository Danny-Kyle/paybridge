package com.academy.paybridge.transfer.repository;

import com.academy.paybridge.transfer.domain.TransferRecipient;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface TransferRecipientRepository extends JpaRepository<TransferRecipient, UUID> {
    Optional<TransferRecipient> findByBankCodeAndAccountNumber(String bankCode, String accountNumber);
}