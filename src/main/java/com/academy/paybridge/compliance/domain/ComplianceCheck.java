package com.academy.paybridge.compliance.domain;

import com.academy.paybridge.compliance.api.Decision;
import com.academy.paybridge.compliance.api.ScreeningRequest;
import com.academy.paybridge.shared.money.Currency;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "compliance_check")
public class ComplianceCheck {

    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "transfer_reference", nullable = false, updatable = false) private String transferReference;
    @Column(name = "customer_id", nullable = false, updatable = false) private UUID customerId;
    @Column(name = "amount_minor", nullable = false, updatable = false) private long amountMinor;
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false) private Currency currency;
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false) private Decision decision;
    @Column(columnDefinition = "text") private String reasons;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected ComplianceCheck() {}

    public static ComplianceCheck of(ScreeningRequest r, Decision decision, List<String> reasons, Instant now) {
        ComplianceCheck c = new ComplianceCheck();
        c.transferReference = r.transferReference();
        c.customerId = r.senderCustomerId();
        c.amountMinor = r.amount().minorUnits();
        c.currency = r.amount().currency();
        c.decision = decision;
        c.reasons = String.join("; ", reasons);
        c.createdAt = now;
        return c;
    }
}