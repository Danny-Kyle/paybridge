package com.academy.paybridge.transfer.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transfer_recipient")
public class TransferRecipient {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "bank_code", nullable = false) private String bankCode;
    @Column(name = "account_number", nullable = false) private String accountNumber;
    @Column(name = "recipient_code", nullable = false) private String recipientCode;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected TransferRecipient() {}

    public static TransferRecipient of(String bankCode, String accountNumber, String code, Instant now) {
        TransferRecipient r = new TransferRecipient();
        r.bankCode = bankCode; r.accountNumber = accountNumber; r.recipientCode = code; r.createdAt = now;
        return r;
    }
    public String getRecipientCode() { return recipientCode; }
}