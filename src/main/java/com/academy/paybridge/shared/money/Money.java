package com.academy.paybridge.shared.money;

import java.math.BigDecimal;
import java.util.Objects;

public record Money (long minorUnits, Currency currency) {
public Money{
    Objects.requireNonNull(currency, "Currency cannot be null");
}
    public static Money ngn(long kobo){
    return new Money(kobo, Currency.NGN);
    };
    public static Money usd (long cents){
        return new Money(cents, Currency.USD);
    };
    public static Money zero (Currency c){
        return new Money(0,c);
    };

    public Money plus(Money other){
        requireSame(other);
        return new Money(Math.addExact(minorUnits, other.minorUnits), currency);
    };
    public Money minus(Money other){
        requireSame(other);
        return new Money(Math.subtractExact(minorUnits, other.minorUnits), currency);
    };

    public boolean isGreaterThanOrEqualTo(Money other){
        requireSame(other);
        return minorUnits >= other.minorUnits;
    };
    public boolean isNegative(){
        return minorUnits < 0;
    };
    public boolean isZero(){
        return minorUnits == 0;
    };
    public boolean isPositive(){
        return minorUnits > 0;
    };
    public BigDecimal toMajorUnits(){
        return BigDecimal.valueOf(minorUnits, 2);
    };

    public void requireSame(Money other){
        if (currency != other.currency){
            throw new IllegalArgumentException("Currencies" + currency +"&" + other.currency + "do not match");
        }
    };
};

