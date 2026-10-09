package com.academy.paybridge.account.service;

import com.academy.paybridge.account.api.AccountApi;
import com.academy.paybridge.account.api.AccountSummary;
import com.academy.paybridge.account.domain.Account;
import com.academy.paybridge.shared.money.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
class DefaultAccountApi implements AccountApi {

    private final AccountService accounts;

    DefaultAccountApi(AccountService accounts) { this.accounts = accounts; }

    @Override
    public Optional<AccountSummary> findPrimaryAccount(UUID customerId) {
        return accounts.findPrimary(customerId).map(DefaultAccountApi::toSummary);
    }

    @Override
    public Optional<Money> findBalance(UUID customerId) {
        return accounts.findPrimary(customerId).filter(Account::isInternal).map(Account::balance);
    }

    @Override
    public AccountSummary getAccount(UUID accountId) { return toSummary(accounts.getOrThrow(accountId)); }

    @Override @Transactional
    public void debit(UUID accountId, Money amount) { accounts.debit(accountId, amount); }

    @Override @Transactional
    public void credit(UUID accountId, Money amount) { accounts.credit(accountId, amount); }

    private static AccountSummary toSummary(Account a) {
        return new AccountSummary(a.getId(), a.getCustomerId(), a.getAccountNumber(), a.getAccountName(),
                a.getBankCode(), a.getBankName(), a.getKind(), a.getCurrency());
    }
}