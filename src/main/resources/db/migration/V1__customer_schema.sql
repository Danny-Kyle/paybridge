CREATE TABLE customer (
                          id           UUID PRIMARY KEY,
                          first_name   VARCHAR(100) NOT NULL,
                          last_name    VARCHAR(100) NOT NULL,
                          email        VARCHAR(254) NOT NULL,
                          phone_number VARCHAR(20)  NOT NULL,
                          kyc_status   VARCHAR(20)  NOT NULL,
                          created_at   TIMESTAMPTZ  NOT NULL,
                          updated_at   TIMESTAMPTZ  NOT NULL,
                          version      BIGINT       NOT NULL DEFAULT 0,
                          CONSTRAINT chk_customer_kyc CHECK (kyc_status IN ('PENDING','VERIFIED','REJECTED'))
);
CREATE UNIQUE INDEX uq_customer_email ON customer (lower(email));
CREATE UNIQUE INDEX uq_customer_phone ON customer (phone_number);

CREATE TABLE next_of_kin (
                             id                      UUID PRIMARY KEY,
                             customer_id             UUID        NOT NULL REFERENCES customer(id),
                             next_of_kin_customer_id UUID        NOT NULL REFERENCES customer(id),
                             relationship            VARCHAR(20) NOT NULL,
                             created_at              TIMESTAMPTZ NOT NULL,
                             updated_at              TIMESTAMPTZ NOT NULL,
                             version                 BIGINT      NOT NULL DEFAULT 0,
                             CONSTRAINT uq_next_of_kin_customer UNIQUE (customer_id),          -- one NOK per customer
                             CONSTRAINT chk_nok_not_self CHECK (customer_id <> next_of_kin_customer_id),
                             CONSTRAINT chk_nok_relationship CHECK (relationship IN ('PARENT','SPOUSE','CHILD','SIBLING','OTHER'))
);