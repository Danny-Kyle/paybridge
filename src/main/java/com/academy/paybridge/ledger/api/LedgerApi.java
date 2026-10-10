package com.academy.paybridge.ledger.api;

import com.academy.paybridge.shared.money.Currency;
import com.academy.paybridge.shared.money.Money;

public interface LedgerApi {
    void post(PostJournalCommand command);                        // idempotent on command.reference()
    Money balanceOf(String accountRef, Currency currency);        // credits minus debits (customer/liability view)
}