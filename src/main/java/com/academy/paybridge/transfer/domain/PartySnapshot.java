package com.academy.paybridge.transfer.domain;

import com.academy.paybridge.account.api.AccountSummary;
import com.academy.paybridge.shared.money.Money;

import java.util.Optional;
import java.util.UUID;

public record PartySnapshot(UUID customerId, Optional<AccountSummary> account, Optional<Money> balance) {}
