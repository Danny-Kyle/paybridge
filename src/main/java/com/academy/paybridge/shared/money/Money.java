package com.academy.paybridge.shared.money;

import java.math.BigDecimal;
import java.util.Objects;

public record Money (long amount, String currency) {
public Money{
    Objects.requireNonNull(currency, "Currency cannot be null");
}
    public static Money ngn(long kobo){};
    public static Money usd (long cents){};
    public static money zero (Currency c){};

    public Money plus(Money other);
    public Money minus(Money other);

    public boolean isGreaterThanOrEqualTo(Money other);
    public boolean isNegative();
    public BigDecimal toMajorUnits();
}

