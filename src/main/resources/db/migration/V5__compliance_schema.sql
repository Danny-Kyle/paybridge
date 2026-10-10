CREATE TABLE compliance_check (
                                  id                 UUID PRIMARY KEY,
                                  transfer_reference VARCHAR(50) NOT NULL UNIQUE,
                                  customer_id        UUID        NOT NULL,
                                  amount_minor       BIGINT      NOT NULL,
                                  currency           VARCHAR(3)  NOT NULL,
                                  decision           VARCHAR(10) NOT NULL CHECK (decision IN ('ALLOW','REVIEW','BLOCK')),
                                  reasons            TEXT,
                                  created_at         TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_compliance_customer_day ON compliance_check (customer_id, created_at);

CREATE TABLE sanctions_entry (
                                 id              UUID PRIMARY KEY,
                                 name_normalized VARCHAR(200) NOT NULL UNIQUE,
                                 source          VARCHAR(50)  NOT NULL
);