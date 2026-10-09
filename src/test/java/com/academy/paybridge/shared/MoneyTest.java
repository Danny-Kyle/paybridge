package com.academy.paybridge.shared;

import com.academy.paybridge.shared.money.Currency;
import com.academy.paybridge.shared.money.Money;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test void addsAndSubtracts() {
        assertThat(Money.ngn(500).plus(Money.ngn(250))).isEqualTo(Money.ngn(750));
        assertThat(Money.ngn(500).minus(Money.ngn(250))).isEqualTo(Money.ngn(250));
    }

    @Test void rejectsCurrencyMismatch() {
        assertThatThrownBy(() -> Money.ngn(1).plus(new Money(1, Currency.USD)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void convertsToMajorUnits() {
        assertThat(Money.ngn(12345).toMajorUnits().toPlainString()).isEqualTo("123.45");
    }

    @Test void throwsOnOverflow() {
        assertThatThrownBy(() -> Money.ngn(Long.MAX_VALUE).plus(Money.ngn(1)))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test void comparesAmounts() {
        assertThat(Money.ngn(100).isGreaterThanOrEqualTo(Money.ngn(100))).isTrue();
        assertThat(Money.ngn(99).isGreaterThanOrEqualTo(Money.ngn(100))).isFalse();
    }
}