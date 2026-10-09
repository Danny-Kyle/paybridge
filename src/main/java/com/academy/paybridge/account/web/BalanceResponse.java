package com.academy.paybridge.account.web;

import com.academy.paybridge.shared.money.Currency;
import com.academy.paybridge.shared.money.Money;

import java.math.BigDecimal;

public record BalanceResponse(long amountMinor, BigDecimal amount, Currency currency) {
    static BalanceResponse from(Money m) { return new BalanceResponse(m.minorUnits(), m.toMajorUnits(), m.currency()); }
}