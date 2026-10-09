package com.academy.paybridge.account.service;

import com.academy.paybridge.account.repository.AccountRepository;
import com.academy.paybridge.shared.exception.BusinessRuleException;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
class AccountNumberGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private final AccountRepository accounts;

    AccountNumberGenerator(AccountRepository accounts) { this.accounts = accounts; }

    String next() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = String.valueOf(RANDOM.nextLong(1_000_000_000L, 10_000_000_000L));   // 10 digits
            if (!accounts.existsByAccountNumber(candidate)) return candidate;
        }
        throw new BusinessRuleException("ACCOUNT_NUMBER_EXHAUSTED", "Could not allocate an account number.");
    }
}