package com.academy.paybridge.account.web;

import com.academy.paybridge.account.api.AccountApi;
import com.academy.paybridge.account.api.AccountSummary;
import com.academy.paybridge.shared.exception.ResourceNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
class AccountController {

    private final AccountApi accountApi;

    AccountController(AccountApi accountApi) { this.accountApi = accountApi; }

    @GetMapping("/by-customer/{customerId}")
    AccountSummary byCustomer(@PathVariable UUID customerId) {
        return accountApi.findPrimaryAccount(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", customerId));
    }

    @GetMapping("/by-customer/{customerId}/balance")
    BalanceResponse balance(@PathVariable UUID customerId) {
        return accountApi.findBalance(customerId).map(BalanceResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Balance", customerId));
    }
}