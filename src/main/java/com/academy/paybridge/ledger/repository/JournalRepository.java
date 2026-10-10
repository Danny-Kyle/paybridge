package com.academy.paybridge.ledger.repository;

import com.academy.paybridge.ledger.domain.Journal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JournalRepository extends JpaRepository<Journal, UUID> {
    boolean existsByReference(String reference);
}