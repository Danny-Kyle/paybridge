package com.academy.paybridge.ledger.repository;

import com.academy.paybridge.ledger.domain.LedgerEntry;
import com.academy.paybridge.shared.money.Currency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    @Query("""
            select coalesce(sum(case when e.direction = com.academy.paybridge.ledger.api.Direction.CREDIT
                                     then e.amountMinor else -e.amountMinor end), 0)
            from LedgerEntry e
            where e.accountRef = :ref and e.currency = :currency
            """)
    long netCredits(@Param("ref") String accountRef, @Param("currency") Currency currency);
}