package com.academy.paybridge.ledger.domain;

import com.academy.paybridge.ledger.api.Direction;
import com.academy.paybridge.shared.money.Currency;
import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "ledger_entry")
public class LedgerEntry {

    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "account_ref", nullable = false) private String accountRef;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Direction direction;
    @Column(name = "amount_minor", nullable = false) private long amountMinor;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Currency currency;
    @Column(name = "posted_at", nullable = false) private Instant postedAt;

    protected LedgerEntry() {}

    LedgerEntry(String accountRef, Direction direction, long amountMinor, Currency currency, Instant postedAt) {
        this.accountRef = accountRef;
        this.direction = direction;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.postedAt = postedAt;
    }

    public String getAccountRef() { return accountRef; }
    public Direction getDirection() { return direction; }
    public long getAmountMinor() { return amountMinor; }
    public Currency getCurrency() { return currency; }
}