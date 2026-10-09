package com.academy.paybridge.account.domain;

import com.academy.paybridge.account.api.AccountKind;
import com.academy.paybridge.shared.audit.BaseEntity;
import com.academy.paybridge.shared.exception.BusinessRuleException;
import com.academy.paybridge.shared.money.Currency;
import com.academy.paybridge.shared.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "account")
public class Account extends BaseEntity {

    @Column(name = "customer_id", nullable = false, updatable = false) private UUID customerId;
    @Column(name = "account_number", nullable = false, updatable = false) private String accountNumber;
    @Column(name = "account_name", nullable = false) private String accountName;

    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false) private AccountKind kind;
    @Column(name = "bank_code", nullable = false, updatable = false) private String bankCode;
    @Column(name = "bank_name", nullable = false, updatable = false) private String bankName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false) private Currency currency;
    @Column(name = "balance_minor", nullable = false) private long balanceMinor;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private AccountStatus status;

    protected Account() {}

    public static Account openInternal(UUID customerId, String name, String accountNumber, Currency currency) {
        Account a = new Account();
        a.customerId = customerId;
        a.accountNumber = accountNumber;
        a.accountName = name;
        a.kind = AccountKind.INTERNAL;
        a.bankCode = "PBG";
        a.bankName = "Paybridge";
        a.currency = currency;
        a.balanceMinor = 0;
        a.status = AccountStatus.ACTIVE;
        return a;
    }

    public Money balance() { return new Money(balanceMinor, currency); }
    public boolean isInternal() { return kind == AccountKind.INTERNAL; }

    public void debit(Money amount) {
        requireUsable(amount);
        if (balanceMinor < amount.minorUnits())
            throw new BusinessRuleException("INSUFFICIENT_FUNDS", "Insufficient funds.");
        balanceMinor -= amount.minorUnits();
    }

    public void credit(Money amount) {
        requireUsable(amount);
        balanceMinor = Math.addExact(balanceMinor, amount.minorUnits());
    }

    public void freeze() { status = AccountStatus.FROZEN; }

    private void requireUsable(Money amount) {
        if (kind == AccountKind.EXTERNAL)
            throw new BusinessRuleException("EXTERNAL_ACCOUNT", "Balances of external accounts are not managed here.");
        if (status != AccountStatus.ACTIVE)
            throw new BusinessRuleException("ACCOUNT_NOT_ACTIVE", "Account is not active.");
        if (!amount.isPositive())
            throw new BusinessRuleException("INVALID_AMOUNT", "Amount must be positive.");
        if (amount.currency() != currency)
            throw new BusinessRuleException("CURRENCY_MISMATCH", "Account currency is " + currency + ".");
    }

    public UUID getCustomerId() { return customerId; }
    public String getAccountNumber() { return accountNumber; }
    public String getAccountName() { return accountName; }
    public AccountKind getKind() { return kind; }
    public String getBankCode() { return bankCode; }
    public String getBankName() { return bankName; }
    public Currency getCurrency() { return currency; }
    public AccountStatus getStatus() { return status; }
}