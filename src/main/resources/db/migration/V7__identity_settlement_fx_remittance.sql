CREATE TABLE customer_identity (
 issuer VARCHAR(300) NOT NULL, subject VARCHAR(150) NOT NULL, customer_id UUID NOT NULL UNIQUE REFERENCES customer(id),
 PRIMARY KEY(issuer,subject)
);
CREATE TABLE settlement_float (
 id INTEGER PRIMARY KEY CHECK (id=1), cash_minor BIGINT NOT NULL CHECK(cash_minor>=0),
 reserved_minor BIGINT NOT NULL CHECK(reserved_minor>=0 AND reserved_minor<=cash_minor)
);
INSERT INTO settlement_float VALUES(1,0,0);
CREATE TABLE settlement_topup(reference VARCHAR(64) PRIMARY KEY,amount_minor BIGINT NOT NULL CHECK(amount_minor>0),created_at TIMESTAMPTZ NOT NULL);
CREATE TABLE settlement_reservation(operation_id UUID PRIMARY KEY,amount_minor BIGINT NOT NULL CHECK(amount_minor>0),status VARCHAR(20) NOT NULL CHECK(status IN ('RESERVED','CAPTURED','RELEASED')));
CREATE TABLE settlement_reconciliation(id UUID PRIMARY KEY,statement_id VARCHAR(100) NOT NULL UNIQUE,reported_minor BIGINT NOT NULL CHECK(reported_minor>=0),book_minor BIGINT NOT NULL,difference_minor BIGINT NOT NULL,status VARCHAR(10) NOT NULL CHECK(status IN ('MATCHED','BREAK')),created_at TIMESTAMPTZ NOT NULL);
CREATE TABLE fx_quote(id UUID PRIMARY KEY,customer_id UUID NOT NULL,source_minor BIGINT NOT NULL CHECK(source_minor>0),fee_minor BIGINT NOT NULL CHECK(fee_minor>=0 AND fee_minor<source_minor),destination_minor BIGINT NOT NULL CHECK(destination_minor>0),ngn_per_usd NUMERIC(24,8) NOT NULL CHECK(ngn_per_usd>0),expires_at TIMESTAMPTZ NOT NULL,status VARCHAR(10) NOT NULL CHECK(status IN ('OPEN','ACCEPTED')),accepted_operation_id UUID UNIQUE,created_at TIMESTAMPTZ NOT NULL);
CREATE TABLE remittance(id UUID PRIMARY KEY,customer_id UUID NOT NULL,idempotency_key VARCHAR(64) NOT NULL,source_usd_minor BIGINT NOT NULL CHECK(source_usd_minor>0),destination_ngn_minor BIGINT NOT NULL CHECK(destination_ngn_minor>=0),beneficiary_name VARCHAR(100) NOT NULL,bank_code VARCHAR(6) NOT NULL,account_number VARCHAR(10) NOT NULL,status VARCHAR(30) NOT NULL CHECK(status IN ('AWAITING_FUNDING','FUNDED','PAYOUT_PENDING','PAID_OUT','PAYOUT_FAILED')),quote_id UUID UNIQUE,payout_reference VARCHAR(60) NOT NULL UNIQUE,attempt INTEGER NOT NULL CHECK(attempt>0),created_at TIMESTAMPTZ NOT NULL,converted_at TIMESTAMPTZ,UNIQUE(customer_id,idempotency_key));
CREATE INDEX ix_remittance_work ON remittance(status);
CREATE INDEX ix_remittance_customer_day ON remittance(customer_id,converted_at);
CREATE TABLE remittance_funding(provider VARCHAR(20) NOT NULL,provider_reference VARCHAR(80) NOT NULL,remittance_id UUID NOT NULL UNIQUE REFERENCES remittance(id),amount_minor BIGINT NOT NULL CHECK(amount_minor>0),created_at TIMESTAMPTZ NOT NULL,PRIMARY KEY(provider,provider_reference));
CREATE TABLE local_payout_lock(id INTEGER PRIMARY KEY);
INSERT INTO local_payout_lock VALUES(1);
CREATE TABLE local_provider_payout(reference VARCHAR(60) PRIMARY KEY,outcome VARCHAR(10) NOT NULL CHECK(outcome IN ('SUCCESS','FAILED','UNKNOWN')),created_at TIMESTAMPTZ NOT NULL);
