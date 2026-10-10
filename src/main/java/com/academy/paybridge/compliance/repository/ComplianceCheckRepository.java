package com.academy.paybridge.compliance.repository;

import com.academy.paybridge.compliance.domain.ComplianceCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface ComplianceCheckRepository extends JpaRepository<ComplianceCheck, UUID> {

    @Query("""
            select coalesce(sum(c.amountMinor), 0) from ComplianceCheck c
            where c.customerId = :customerId
              and c.decision = com.academy.paybridge.compliance.api.Decision.ALLOW
              and c.createdAt >= :since
            """)
    long sumAllowedSince(@Param("customerId") UUID customerId, @Param("since") Instant since);
}