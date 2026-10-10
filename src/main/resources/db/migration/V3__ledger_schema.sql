CREATE TABLE ledger_journal (
                                id          UUID PRIMARY KEY,
                                reference   VARCHAR(80) NOT NULL UNIQUE,      -- idempotency: the same reference can't post twice
                                type        VARCHAR(30) NOT NULL,
                                description VARCHAR(255),
                                posted_at   TIMESTAMPTZ NOT NULL
);
CREATE TABLE ledger_entry (
                              id           UUID PRIMARY KEY,
                              journal_id   UUID        NOT NULL REFERENCES ledger_journal(id),
                              account_ref  VARCHAR(80) NOT NULL,            -- 'CUSTOMER:<accountId>' or 'SYSTEM:<NAME>'
                              direction    VARCHAR(6)  NOT NULL CHECK (direction IN ('DEBIT','CREDIT')),
                              amount_minor BIGINT      NOT NULL CHECK (amount_minor > 0),
                              currency     VARCHAR(3)  NOT NULL,
                              posted_at    TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_ledger_entry_account ON ledger_entry (account_ref);
CREATE INDEX ix_ledger_entry_journal ON ledger_entry (journal_id);