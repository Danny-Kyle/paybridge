package com.academy.paybridge.account.listener;

import com.academy.paybridge.account.service.AccountService;
import com.academy.paybridge.customer.api.CustomerOnboarded;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class CustomerOnboardedListener {

    private final AccountService accounts;

    CustomerOnboardedListener(AccountService accounts) { this.accounts = accounts; }

    @org.springframework.modulith.events.ApplicationModuleListener
    void on(CustomerOnboarded event) {
        accounts.openDefaultAccount(event.customerId(), event.fullName());
    }
}