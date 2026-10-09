package com.academy.paybridge.account.api;

import java.util.Currency;
import java.util.UUID;

public record AccountSummary(UUID accountId, UUID customerId, String accountNumber, String accountName,
                             String bankCode, String bankName, AccountKind kind, Currency currency) {}