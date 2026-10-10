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
                          created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0,
                          CONSTRAINT chk_transfer_amount  CHECK (amount_minor > 0),
                          CONSTRAINT chk_transfer_status  CHECK (status IN ('PENDING','PROCESSING','COMPLETED','FAILED','REVERSED','REJECTED')),
                          CONSTRAINT chk_transfer_routing CHECK (routing_outcome IN ('DIRECT','REDIRECTED_TO_NEXT_OF_KIN'))
);
CREATE INDEX ix_transfer_sender ON transfer (sender_customer_id, created_at);

CREATE TABLE webhook_event (            -- dedupe: Paystack can deliver the same event more than once
                               id          UUID PRIMARY KEY,
                               event_key   VARCHAR(120) NOT NULL UNIQUE,     -- e.g. "transfer.success:pb-abc..."
                               event_type  VARCHAR(50)  NOT NULL,
                               received_at TIMESTAMPTZ  NOT NULL
);

CREATE TABLE transfer_recipient (       -- cache of Paystack recipient codes
                                    id             UUID PRIMARY KEY,
                                    bank_code      VARCHAR(10) NOT NULL,
                                    account_number VARCHAR(10) NOT NULL,
                                    recipient_code VARCHAR(50) NOT NULL,
                                    created_at     TIMESTAMPTZ NOT NULL,
                                    CONSTRAINT uq_recipient UNIQUE (bank_code, account_number)
);