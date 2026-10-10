package com.academy.paybridge.ledger.domain;

import com.academy.paybridge.ledger.api.Direction;
import com.academy.paybridge.ledger.api.EntryLine;
import com.academy.paybridge.ledger.api.JournalType;
import com.academy.paybridge.ledger.api.PostJournalCommand;
import com.academy.paybridge.shared.exception.BusinessRuleException;
import com.academy.paybridge.shared.money.Currency;
import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "ledger_journal")
public class Journal {

    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, updatable = false) private String reference;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private JournalType type;
    private String description;
    @Column(name = "posted_at", nullable = false) private Instant postedAt;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "journal_id", nullable = false, updatable = false)
    private List<LedgerEntry> entries = new ArrayList<>();

    protected Journal() {}

    public static Journal create(PostJournalCommand cmd, Instant now) {
        List<EntryLine> lines = cmd.lines();
        if (lines == null || lines.size() < 2)
            throw new BusinessRuleException("JOURNAL_TOO_SHORT", "A journal needs at least two lines.");

        Currency currency = lines.get(0).amount().currency();
        long debits = 0, credits = 0;
        for (EntryLine l : lines) {
            if (l.amount().currency() != currency)
                throw new BusinessRuleException("JOURNAL_MIXED_CURRENCY", "All lines must use one currency.");
            if (!l.amount().isPositive())
                throw new BusinessRuleException("JOURNAL_BAD_AMOUNT", "Line amounts must be positive.");
            if (l.direction() == Direction.DEBIT) debits += l.amount().minorUnits();
            else credits += l.amount().minorUnits();
        }
        if (debits != credits)
            throw new BusinessRuleException("JOURNAL_UNBALANCED", "Debits (" + debits + ") must equal credits (" + credits + ").");

        Journal j = new Journal();
        j.reference = cmd.reference();
        j.type = cmd.type();
        j.description = cmd.description();
        j.postedAt = now;
        for (EntryLine l : lines)
            j.entries.add(new LedgerEntry(l.accountRef(), l.direction(), l.amount().minorUnits(), currency, now));
        return j;
    }

    public UUID getId() { return id; }
    public String getReference() { return reference; }
    public List<LedgerEntry> getEntries() { return List.copyOf(entries); }
}