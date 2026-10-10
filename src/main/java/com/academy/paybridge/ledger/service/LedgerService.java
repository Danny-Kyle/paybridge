package com.academy.paybridge.ledger.service;

import com.academy.paybridge.ledger.api.LedgerApi;
import com.academy.paybridge.ledger.api.PostJournalCommand;
import com.academy.paybridge.ledger.domain.Journal;
import com.academy.paybridge.ledger.repository.JournalRepository;
import com.academy.paybridge.ledger.repository.LedgerEntryRepository;
import com.academy.paybridge.shared.money.Currency;
import com.academy.paybridge.shared.money.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
class LedgerService implements LedgerApi {

    private final JournalRepository journals;
    private final LedgerEntryRepository entries;
    private final Clock clock;

    LedgerService(JournalRepository journals, LedgerEntryRepository entries, Clock clock) {
        this.journals = journals;
        this.entries = entries;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void post(PostJournalCommand command) {
        if (journals.existsByReference(command.reference())) return;        // idempotent
        journals.save(Journal.create(command, clock.instant()));
    }

    @Override
    @Transactional(readOnly = true)
    public Money balanceOf(String accountRef, Currency currency) {
        return new Money(entries.netCredits(accountRef, currency), currency);
    }
}