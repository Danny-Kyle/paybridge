package com.academy.paybridge.account.service;

import com.academy.paybridge.account.domain.Account;
import com.academy.paybridge.account.repository.AccountRepository;
import com.academy.paybridge.shared.exception.ResourceNotFoundException;
import com.academy.paybridge.shared.money.Currency;
import com.academy.paybridge.shared.money.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accounts;
    private final AccountNumberGenerator numbers;

    public AccountService(AccountRepository accounts, AccountNumberGenerator numbers) {
        this.accounts = accounts;
        this.numbers = numbers;
    }

    @Transactional
    public Account openDefaultAccount(UUID customerId, String accountName) {       // idempotent
        return accounts.findByCustomerIdAndCurrency(customerId, Currency.NGN)
                .orElseGet(() -> accounts.save(
                        Account.openInternal(customerId, accountName, numbers.next(), Currency.NGN)));
    }

    @Transactional(readOnly = true)
    public Optional<Account> findPrimary(UUID customerId) {
        return accounts.findByCustomerIdAndCurrency(customerId, Currency.NGN);
    }

    @Transactional(readOnly = true)
    public Account getOrThrow(UUID accountId) {
        return accounts.findById(accountId).orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
    }

    @Transactional
    public void debit(UUID accountId, Money amount) { lock(accountId).debit(amount); }

    @Transactional
    public void credit(UUID accountId, Money amount) { lock(accountId).credit(amount); }

    private Account lock(UUID accountId) {
        return accounts.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
    }
}