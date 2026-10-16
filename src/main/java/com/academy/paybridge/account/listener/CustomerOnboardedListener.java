package com.academy.paybridge.account.listener;

import com.academy.paybridge.account.service.AccountService;
import com.academy.paybridge.customer.api.CustomerOnboarded;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class CustomerOnboardedListener {

    private final AccountService accounts;

    CustomerOnboardedListener(AccountService accounts) { this.accounts = accounts; }

    @EventListener
    void on(CustomerOnboarded event) {
        accounts.openDefaultAccount(event.customerId(), event.fullName());
    }
}