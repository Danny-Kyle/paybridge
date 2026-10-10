package com.academy.paybridge.ledger;

import com.academy.paybridge.ledger.api.*;
import com.academy.paybridge.ledger.domain.Journal;
import com.academy.paybridge.shared.exception.BusinessRuleException;
import com.academy.paybridge.shared.money.Currency;
import com.academy.paybridge.shared.money.Money;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JournalTest {

    private PostJournalCommand cmd(EntryLine... lines) {
        return new PostJournalCommand("ref-1", JournalType.INTERNAL_TRANSFER, "test", List.of(lines));
    }

    @Test void balancedJournalIsAccepted() {
        Journal j = Journal.create(cmd(EntryLine.debit("A", Money.ngn(100)), EntryLine.credit("B", Money.ngn(100))), Instant.now());
        assertThat(j.getEntries()).hasSize(2);
    }

    @Test void unbalancedJournalIsRejected() {
        assertThatThrownBy(() -> Journal.create(
                cmd(EntryLine.debit("A", Money.ngn(100)), EntryLine.credit("B", Money.ngn(90))), Instant.now()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("must equal");
    }

    @Test void singleLineIsRejected() {
        assertThatThrownBy(() -> Journal.create(cmd(EntryLine.debit("A", Money.ngn(100))), Instant.now()))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test void mixedCurrencyIsRejected() {
        assertThatThrownBy(() -> Journal.create(
                cmd(EntryLine.debit("A", Money.ngn(100)), EntryLine.credit("B", new Money(100, Currency.USD))), Instant.now()))
                .isInstanceOf(BusinessRuleException.class);
    }
}