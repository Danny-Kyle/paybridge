package com.academy.paybridge.compliance.repository;

import com.academy.paybridge.compliance.domain.SanctionsEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SanctionsEntryRepository extends JpaRepository<SanctionsEntry, UUID> {
    boolean existsByNameNormalized(String nameNormalized);
}