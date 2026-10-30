CREATE TABLE customer (
                          id           UUID PRIMARY KEY,
                          first_name   VARCHAR(100) NOT NULL,
                          last_name    VARCHAR(100) NOT NULL,
                          email        VARCHAR(254) NOT NULL,
                          phone_number VARCHAR(20)  NOT NULL,
                          kyc_status   VARCHAR(20)  NOT NULL,
                          created_at   TIMESTAMP WITH TIME ZONE  NOT NULL,
                          updated_at   TIMESTAMP WITH TIME ZONE  NOT NULL,
                          version      BIGINT       NOT NULL DEFAULT 0,
                          CONSTRAINT chk_customer_kyc CHECK (kyc_status IN ('PENDING','VERIFIED','REJECTED'))
);
CREATE UNIQUE INDEX uq_customer_email ON customer (email);
CREATE UNIQUE INDEX uq_customer_phone ON customer (phone_number);

CREATE TABLE next_of_kin (
                             id                      UUID PRIMARY KEY,
                             customer_id             UUID        NOT NULL REFERENCES customer(id),
                             next_of_kin_customer_id UUID        NOT NULL REFERENCES customer(id),
                             relationship            VARCHAR(20) NOT NULL,
                             created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
                             updated_at              TIMESTAMP WITH TIME ZONE NOT NULL,
                             version                 BIGINT      NOT NULL DEFAULT 0,
                             CONSTRAINT uq_next_of_kin_customer UNIQUE (customer_id),          -- one NOK per customer
                             CONSTRAINT chk_nok_not_self CHECK (customer_id <> next_of_kin_customer_id),
                             CONSTRAINT chk_nok_relationship CHECK (relationship IN ('PARENT','SPOUSE','CHILD','SIBLING','OTHER'))
);
CREATE TABLE account (
                         id             UUID PRIMARY KEY,
                         customer_id    UUID         NOT NULL,          -- NO foreign key: cross-module reference by id
                         account_number VARCHAR(10)  NOT NULL,
                         account_name   VARCHAR(200) NOT NULL,
                         kind           VARCHAR(10)  NOT NULL,          -- INTERNAL (we hold the money) | EXTERNAL (another bank)
                         bank_code      VARCHAR(10)  NOT NULL,
                         bank_name      VARCHAR(100) NOT NULL,
                         currency       VARCHAR(3)   NOT NULL,
                         balance_minor  BIGINT       NOT NULL DEFAULT 0,
                         status         VARCHAR(10)  NOT NULL,
                         created_at     TIMESTAMP WITH TIME ZONE  NOT NULL,
                         updated_at     TIMESTAMP WITH TIME ZONE  NOT NULL,
                         version        BIGINT       NOT NULL DEFAULT 0,
                         CONSTRAINT chk_account_balance CHECK (balance_minor >= 0),
                         CONSTRAINT chk_account_kind    CHECK (kind IN ('INTERNAL','EXTERNAL')),
                         CONSTRAINT chk_account_status  CHECK (status IN ('ACTIVE','FROZEN','CLOSED')),
                         CONSTRAINT chk_account_ccy     CHECK (currency IN ('NGN','USD'))
);
CREATE UNIQUE INDEX uq_account_bank_number      ON account (bank_code, account_number);
CREATE UNIQUE INDEX uq_account_customer_currency ON account (customer_id, currency);
CREATE TABLE ledger_journal (
                                id          UUID PRIMARY KEY,
                                reference   VARCHAR(80) NOT NULL UNIQUE,      -- idempotency: the same reference can't post twice
                                type        VARCHAR(30) NOT NULL,
                                description VARCHAR(255),
                                posted_at   TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE ledger_entry (
                              id           UUID PRIMARY KEY,
                              journal_id   UUID        NOT NULL REFERENCES ledger_journal(id),
                              account_ref  VARCHAR(80) NOT NULL,            -- 'CUSTOMER:<accountId>' or 'SYSTEM:<NAME>'
                              direction    VARCHAR(6)  NOT NULL CHECK (direction IN ('DEBIT','CREDIT')),
                              amount_minor BIGINT      NOT NULL CHECK (amount_minor > 0),
                              currency     VARCHAR(3)  NOT NULL,
                              posted_at    TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_ledger_entry_account ON ledger_entry (account_ref);
CREATE INDEX ix_ledger_entry_journal ON ledger_entry (journal_id);
CREATE TABLE transfer (
                          id                              UUID PRIMARY KEY,
                          reference                       VARCHAR(50)  NOT NULL UNIQUE,    -- ours; also sent to Paystack
                          idempotency_key                 VARCHAR(64)  NOT NULL UNIQUE,    -- supplied by the client
                          sender_customer_id              UUID         NOT NULL,
                          sender_account_id               UUID         NOT NULL,
                          requested_recipient_customer_id UUID         NOT NULL,           -- who the sender asked to pay
                          destination_customer_id         UUID         NOT NULL,           -- who actually gets paid (may be the NOK)
                          destination_account_id          UUID         NOT NULL,
                          destination_account_number      VARCHAR(10)  NOT NULL,
                          destination_bank_code           VARCHAR(10)  NOT NULL,
                          amount_minor                    BIGINT       NOT NULL,
                          currency                        VARCHAR(3)   NOT NULL,
                          routing_outcome                 VARCHAR(30)  NOT NULL,
                          routing_reason                  VARCHAR(100) NOT NULL,
                          status                          VARCHAR(20)  NOT NULL,
                          gateway_transfer_code           VARCHAR(50),
                          failure_reason                  VARCHAR(255),
                          narration                       VARCHAR(255),
                          created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL, version BIGINT NOT NULL DEFAULT 0,
                          CONSTRAINT chk_transfer_amount  CHECK (amount_minor > 0),
                          CONSTRAINT chk_transfer_status  CHECK (status IN ('PENDING','PROCESSING','COMPLETED','FAILED','REVERSED','REJECTED')),
                          CONSTRAINT chk_transfer_routing CHECK (routing_outcome IN ('DIRECT','REDIRECTED_TO_NEXT_OF_KIN'))
);
CREATE INDEX ix_transfer_sender ON transfer (sender_customer_id, created_at);

CREATE TABLE webhook_event (            -- dedupe: Paystack can deliver the same event more than once
                               id          UUID PRIMARY KEY,
                               event_key   VARCHAR(120) NOT NULL UNIQUE,     -- e.g. "transfer.success:pb-abc..."
                               event_type  VARCHAR(50)  NOT NULL,
                               received_at TIMESTAMP WITH TIME ZONE  NOT NULL
);

CREATE TABLE transfer_recipient (       -- cache of Paystack recipient codes
                                    id             UUID PRIMARY KEY,
                                    bank_code      VARCHAR(10) NOT NULL,
                                    account_number VARCHAR(10) NOT NULL,
                                    recipient_code VARCHAR(50) NOT NULL,
                                    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
                                    CONSTRAINT uq_recipient UNIQUE (bank_code, account_number)
);
CREATE TABLE compliance_check (
                                  id                 UUID PRIMARY KEY,
                                  transfer_reference VARCHAR(50) NOT NULL UNIQUE,
                                  customer_id        UUID        NOT NULL,
                                  amount_minor       BIGINT      NOT NULL,
                                  currency           VARCHAR(3)  NOT NULL,
                                  decision           VARCHAR(10) NOT NULL CHECK (decision IN ('ALLOW','REVIEW','BLOCK')),
                                  reasons            TEXT,
                                  created_at         TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_compliance_customer_day ON compliance_check (customer_id, created_at);

CREATE TABLE sanctions_entry (
                                 id              UUID PRIMARY KEY,
                                 name_normalized VARCHAR(200) NOT NULL UNIQUE,
                                 source          VARCHAR(50)  NOT NULL
);
CREATE TABLE notification (
                              id          UUID PRIMARY KEY,
                              customer_id UUID         NOT NULL,
                              channel     VARCHAR(10)  NOT NULL,
                              type        VARCHAR(30)  NOT NULL,
                              subject     VARCHAR(200) NOT NULL,
                              body        TEXT         NOT NULL,
                              status      VARCHAR(10)  NOT NULL,
                              created_at  TIMESTAMP WITH TIME ZONE  NOT NULL,
                              updated_at  TIMESTAMP WITH TIME ZONE  NOT NULL,
                              version     BIGINT       NOT NULL DEFAULT 0
);
CREATE TABLE customer_identity (
 issuer VARCHAR(300) NOT NULL, subject VARCHAR(150) NOT NULL, customer_id UUID NOT NULL UNIQUE REFERENCES customer(id),
 PRIMARY KEY(issuer,subject)
);
CREATE TABLE settlement_float (
 id INTEGER PRIMARY KEY CHECK (id=1), cash_minor BIGINT NOT NULL CHECK(cash_minor>=0),
 reserved_minor BIGINT NOT NULL CHECK(reserved_minor>=0 AND reserved_minor<=cash_minor)
);
INSERT INTO settlement_float VALUES(1,0,0);
CREATE TABLE settlement_topup(reference VARCHAR(64) PRIMARY KEY,amount_minor BIGINT NOT NULL CHECK(amount_minor>0),created_at TIMESTAMP WITH TIME ZONE NOT NULL);
CREATE TABLE settlement_reservation(operation_id UUID PRIMARY KEY,amount_minor BIGINT NOT NULL CHECK(amount_minor>0),status VARCHAR(20) NOT NULL CHECK(status IN ('RESERVED','CAPTURED','RELEASED')));
CREATE TABLE settlement_reconciliation(id UUID PRIMARY KEY,statement_id VARCHAR(100) NOT NULL UNIQUE,reported_minor BIGINT NOT NULL CHECK(reported_minor>=0),book_minor BIGINT NOT NULL,difference_minor BIGINT NOT NULL,status VARCHAR(10) NOT NULL CHECK(status IN ('MATCHED','BREAK')),created_at TIMESTAMP WITH TIME ZONE NOT NULL);
CREATE TABLE fx_quote(id UUID PRIMARY KEY,customer_id UUID NOT NULL,source_minor BIGINT NOT NULL CHECK(source_minor>0),fee_minor BIGINT NOT NULL CHECK(fee_minor>=0 AND fee_minor<source_minor),destination_minor BIGINT NOT NULL CHECK(destination_minor>0),ngn_per_usd NUMERIC(24,8) NOT NULL CHECK(ngn_per_usd>0),expires_at TIMESTAMP WITH TIME ZONE NOT NULL,status VARCHAR(10) NOT NULL CHECK(status IN ('OPEN','ACCEPTED')),accepted_operation_id UUID UNIQUE,created_at TIMESTAMP WITH TIME ZONE NOT NULL);
CREATE TABLE remittance(id UUID PRIMARY KEY,customer_id UUID NOT NULL,idempotency_key VARCHAR(64) NOT NULL,source_usd_minor BIGINT NOT NULL CHECK(source_usd_minor>0),destination_ngn_minor BIGINT NOT NULL CHECK(destination_ngn_minor>=0),beneficiary_name VARCHAR(100) NOT NULL,bank_code VARCHAR(6) NOT NULL,account_number VARCHAR(10) NOT NULL,status VARCHAR(30) NOT NULL CHECK(status IN ('AWAITING_FUNDING','FUNDED','PAYOUT_PENDING','PAID_OUT','PAYOUT_FAILED')),quote_id UUID UNIQUE,payout_reference VARCHAR(60) NOT NULL UNIQUE,attempt INTEGER NOT NULL CHECK(attempt>0),created_at TIMESTAMP WITH TIME ZONE NOT NULL,converted_at TIMESTAMP WITH TIME ZONE,UNIQUE(customer_id,idempotency_key));
CREATE INDEX ix_remittance_work ON remittance(status);
CREATE INDEX ix_remittance_customer_day ON remittance(customer_id,converted_at);
CREATE TABLE remittance_funding(provider VARCHAR(20) NOT NULL,provider_reference VARCHAR(80) NOT NULL,remittance_id UUID NOT NULL UNIQUE REFERENCES remittance(id),amount_minor BIGINT NOT NULL CHECK(amount_minor>0),created_at TIMESTAMP WITH TIME ZONE NOT NULL,PRIMARY KEY(provider,provider_reference));
CREATE TABLE local_payout_lock(id INTEGER PRIMARY KEY);
INSERT INTO local_payout_lock VALUES(1);
CREATE TABLE local_provider_payout(reference VARCHAR(60) PRIMARY KEY,outcome VARCHAR(10) NOT NULL CHECK(outcome IN ('SUCCESS','FAILED','UNKNOWN')),created_at TIMESTAMP WITH TIME ZONE NOT NULL);
