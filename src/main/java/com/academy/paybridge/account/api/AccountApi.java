package com.academy.paybridge.account.api;

import com.academy.paybridge.shared.money.Money;

import java.util.Optional;
import java.util.UUID;

public interface AccountApi {
    Optional<AccountSummary> findPrimaryAccount(UUID customerId);
    Optional<Money> findBalance(UUID customerId);
    AccountSummary getAccount(UUID accountId);
    void debit(UUID accountId, Money amount);
    void credit(UUID accountId, Money amount);

}
