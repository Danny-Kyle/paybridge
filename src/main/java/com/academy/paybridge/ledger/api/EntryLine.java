package com.academy.paybridge.ledger.api;

import com.academy.paybridge.shared.money.Money;

public record EntryLine(String accountRef, Direction direction, Money amount) {
    public static EntryLine debit(String accountRef, Money amount)  { return new EntryLine(accountRef, Direction.DEBIT, amount); }
    public static EntryLine credit(String accountRef, Money amount) { return new EntryLine(accountRef, Direction.CREDIT, amount); }
}