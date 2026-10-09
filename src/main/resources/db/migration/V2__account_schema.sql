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
                         created_at     TIMESTAMPTZ  NOT NULL,
                         updated_at     TIMESTAMPTZ  NOT NULL,
                         version        BIGINT       NOT NULL DEFAULT 0,
                         CONSTRAINT chk_account_balance CHECK (balance_minor >= 0),
                         CONSTRAINT chk_account_kind    CHECK (kind IN ('INTERNAL','EXTERNAL')),
                         CONSTRAINT chk_account_status  CHECK (status IN ('ACTIVE','FROZEN','CLOSED')),
                         CONSTRAINT chk_account_ccy     CHECK (currency IN ('NGN','USD'))
);
CREATE UNIQUE INDEX uq_account_bank_number      ON account (bank_code, account_number);
CREATE UNIQUE INDEX uq_account_customer_currency ON account (customer_id, currency);