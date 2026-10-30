# PayBridge implementation: settlement → remittance → FX, with OIDC security

Prepared 16 October 2026 against the supplied archive, baseline HEAD `4bedfc4`. This is a separate implementation copy: your uploaded source and its Git history were not overwritten. Read `docs/TEST-RESULTS.md` for what actually ran and what remains unverified.

## Decisions and questions

You selected **prefunded payouts**, **Keycloak/OIDC**, and **local adapters first**.

The implementation uses these explicit defaults:

| Decision | Current implementation | Question to answer before a real provider adapter |
|---|---|---|
| Settlement model | Local NGN float, reservations, capture/release and closing-balance reconciliation | Which provider/float account, statement format and reconciliation cutoff? |
| Corridor | Inbound USD, external NGN beneficiary | Which USD funding rail verifies funds and the sender? |
| FX pricing | Configurable demo NGN 1,500 per USD; USD 1 fee; round destination down to whole kobo | Whose rate, how fresh, what spread, and who bears FX exposure? |
| Quote validity | 60 seconds; consumed only after verified funding | How long should a funded customer have to accept? |
| Failed payout | Preserve the already-converted NGN entitlement; authorized retry uses a new attempt | Is a USD refund promised? If so, who bears reverse-conversion fees/rate changes? |
| Unknown payout | Retain float reservation and pending state | Which provider verification/retry rules prove finality? |
| Beneficiary | Explicit name, bank code and ten-digit account; no automatic next-of-kin routing | Will remittances use a verified/versioned beneficiary registry and routing consent? |
| Limits | USD 10,000 per remittance, USD 20,000 converted per Lagos day, plus existing NGN screening rules | What limits apply across local transfers and remittances, KYC tiers and currencies? |
| Operators | TREASURY controls local funding/payout simulation; COMPLIANCE verifies KYC | Who may approve treasury movements and KYC, and is maker/checker required? |
| Identity mapping | Immutable `(issuer, sub)` mapping to a customer | How will existing seeded customers be linked after operator identity verification? |

These are executable local policies, not inferred business requirements. In particular, conversion is not hedging; this demo does not acquire USD/NGN liquidity from an FX counterparty. The remittance amounts are segregated payables per operation, not a general spendable USD wallet feature.

## How to use this code

The archive contains the whole revised application and the implementation code in the appendix below. Start from this copy on a new branch. If merging into your own working tree, compare files carefully and preserve your later edits. There is one additive migration, V7; V1–V6 are unchanged. Never edit an applied migration to avoid checksum errors.

The new code uses JdbcTemplate for explicit SQL and row locks, alongside your existing JPA ledger/customer modules. Both participate in the same datasource transaction. Module APIs remain the boundary: remittance calls SettlementApi, FxApi, CustomerApi, ComplianceApi and LedgerApi rather than accessing their repositories.

The walkthrough order is settlement, remittance, FX, then security details. All modules compile together: remittance depends on the FX contract even though FX is explained third. Security is installed before any interactive endpoint testing.

## 1. Settlement service

### 1.1 Define what settlement means for prefunding

The provider cash asset is funded before payout. On success, payout reduces that asset. Do not also create a debt to pay the same provider later. Partner FX obligations and float replenishment are different movements and need their own contracts before they are added.

This first service owns:

- Cash and reserved NGN float, with `available = cash - reserved`.
- Idempotent simulated top-ups.
- Reservations tied to payout attempts.
- Capture on confirmed success and release on confirmed failure.
- Immutable closing-balance reconciliation records that either match or record a break.

This is not a full statement-line importer, bank-transfer executor or cross-partner netting engine. Those require provider, statement and cutoff decisions above.

### 1.2 Add the schema

Apply `src/main/resources/db/migration/V7__identity_settlement_fx_remittance.sql`. Relevant tables are `settlement_float`, `settlement_topup`, `settlement_reservation`, and `settlement_reconciliation`.

The database enforces nonnegative cash/reservations and reservations no greater than cash. A single float row is locked with `FOR UPDATE` before top-up or reservation mutation. That deliberately favors clarity over throughput in a one-provider demo. Later, key float rows by provider/account/currency instead of `id=1`.

### 1.3 Expose a small module API

`settlement/api/SettlementApi.java` exposes `reserve`, `capture` and `release`. Monetary changes require an operation ID, so replay is explicit. Capture/release reject a conflicting prior terminal state.

Reservation is cash availability control. It does not itself recognize a new provider cash movement. Remittance posts its own payable/clearing entries in the same transaction that reserves or captures float.

### 1.4 Add treasury endpoints

| Method | Path | Permission | Purpose |
|---|---|---|---|
| GET | `/api/v1/settlements/float` | TREASURY | Read cash, reserved and available |
| POST | `/api/v1/local/settlements/topups` | TREASURY, local/test profile | Simulate verified treasury funding |
| POST | `/api/v1/settlements/reconciliations` | TREASURY | Compare a reported closing balance with the local book snapshot |

A top-up posts debit provider NGN cash / credit treasury funding. It does not claim a real bank deposit occurred. Reconciliation verifies the float projection against the ledger first; a mismatch is an error rather than silently adjusting books. A statement mismatch creates BREAK and does not change balances.

### 1.5 Test the settlement invariants

Check unauthorized roles, same-reference replay, changed-amount conflict, insufficient float, and duplicate capture. A full reconciliation adapter must additionally match statement references, gross/fee/net amounts, value dates and statement coverage; a closing snapshot alone cannot prove individual payouts matched.

## 2. Remittance service

### 2.1 Create and own the operation

`POST /api/v1/remittances` derives the owner from the access token's linked customer. The request contains no sender customer ID. It takes `Idempotency-Key` and a USD amount plus an explicit NGN beneficiary.

Creation locks the customer's identity mapping row, checks for an existing operation and compares all relevant request fields. The same logical request returns its original ID. A changed payload returns 409. Different customers have independent key namespaces.

This demo serializes remittance creation/conversion per customer. It does not establish a universal limit lock across the older local transfer flow.

### 2.2 Model the states

| State | Meaning | Allowed next action |
|---|---|---|
| AWAITING_FUNDING | Operation exists; no USD liability booked | Treasury local funding confirmation |
| FUNDED | Verified simulated USD receipt booked | Accept a valid owned quote and convert |
| PAYOUT_PENDING | Conversion and NGN reservation committed | Submit/verify stable payout reference |
| PAID_OUT | Confirmed payout, float captured, clearing discharged | Read/replay only |
| PAYOUT_FAILED | Definitive payout failure, reservation released, NGN payable restored | Owner-authorized new payout attempt |

An UNKNOWN provider outcome leaves PAYOUT_PENDING. It does not credit a refund. There is intentionally no public “mark paid” or “confirm my funding” endpoint.

### 2.3 Confirm funding through the local adapter

Only a TREASURY token can call `POST /api/v1/local/remittances/{id}/funding`, and this route exists only in local/test profiles. The funding reference is unique for the local provider, and a remittance can receive only its single expected amount. A replay creates no extra journal.

The receipt posts debit provider USD cash / credit remittance USD liability. A real funding adapter must replace this local assertion with signed and verified provider evidence, checking owner, source amount/currency, provider reference and final funding state. Do not expose the local confirmation operation in production.

### 2.4 Convert and queue atomically

`POST /api/v1/remittances/{id}/convert` locks the customer's authorization row and remittance, checks ownership, KYC and limits, accepts the quote, reserves float, screens the beneficiary through the existing demo compliance API, posts conversion/reservation journals, and changes the remittance state in one transaction.

If float or screening fails, quote acceptance and accounting roll back together. The operation remains FUNDED. If a quote expires, obtain a fresh quote for the same funded amount; do not rewrite an accepted conversion.

The USD journal debits the remittance liability for gross source, credits the USD FX position for net source, and credits USD fee revenue. The NGN journal debits the NGN FX position and credits the remittance NGN payable. A third journal moves NGN payable to payout clearing. Each journal balances independently in one currency.

### 2.5 Submit and recover payouts

`LocalPayoutWorker` has no surrounding database transaction. It reads a pending operation, invokes the fake provider in its own transaction, and then calls the locked financial transition. The fake provider persists results by a stable attempt reference.

If the process dies after the fake provider commits success but before the remittance transition commits, run `/api/v1/local/remittances/recover`. The worker verifies the same reference and applies the result once. Concurrent completion calls lock the remittance and only one performs accounting.

This is an operator-triggered durable queue for the local adapter. There is no scheduled job or real HTTP provider implementation hidden behind it. When adding automatic workers, page/claim work, use leases and backoff, isolate failures per work item, and verify provider idempotency contracts. The current recovery loop stops on the first thrown error; improve that before unattended processing.

### 2.6 Handle failure and retry

SUCCESS: capture reserved float; debit payout clearing / credit provider NGN cash; set PAID_OUT.

FAILED: release reserved float; debit payout clearing / credit remittance NGN payable; set PAYOUT_FAILED. USD is not refunded because it has already been converted.

Owner-authorized `/retry` reserves the same NGN amount under a new attempt and generates a new payout reference only because the prior attempt is definitively FAILED. UNKNOWN operations cannot be retried this way. Late reversal after PAID_OUT is not implemented in the local remittance workflow and must become an explicit provider-backed compensation path before live use.

## 3. FX service

### 3.1 Keep rate direction explicit

A quote contains gross USD cents, USD fee cents, destination NGN kobo, `rate` in **NGN per USD**, expiry, customer ownership and acceptance state. The local rate is configured by `paybridge.fx.demo-ngn-per-usd`.

With both currencies using exponent 2:

```java
long targetKobo = BigDecimal.valueOf(sourceCents - feeCents)
    .multiply(ngnPerUsd)
    .setScale(0, RoundingMode.DOWN)
    .longValueExact();
```

For USD 100, fee USD 1 and the demo rate 1,500, the destination is NGN 148,500, or 14,850,000 kobo. The rate is illustrative, not a live price.

### 3.2 Create and consume quotes

Customer endpoints are `POST /api/v1/fx/quotes` and `GET /api/v1/fx/quotes/{id}`. Acceptance is an internal module API called by the funded remittance transaction; there is no customer endpoint that can consume an unrelated quote without funding.

Acceptance locks the quote and validates owner, gross funding amount, expiry and prior operation ID. One quote cannot be consumed by two operations. A replay from the same operation does not reprice it. An expired unused quote retains OPEN in storage but is rejected at acceptance; expiry is computed from the timestamp, not a background state-flip job.

### 3.3 Add a real rate provider later

Introduce a rate provider API, immutable observed/received timestamps, rate source, maximum age and spread policy. A real firm quote also needs a liquidity/hedging/exposure policy. This code does not claim to hedge conversion exposure or settle an FX counterparty. Configure fee and rounding behavior explicitly before supporting destination-amount quotes or currencies with different exponents.

### 3.4 Test FX as a financial boundary

Test ownership, exact expiry, fee-consuming source, rounding boundaries, arithmetic range errors, amount mismatch, simultaneous acceptance and transaction rollback on insufficient float. The HTTP suite exercises ownership, expiry, deterministic calculation and rollback. Additional precision and PostgreSQL concurrency tests are listed in the test report rather than claimed as completed.

## 4. Authentication and authorization

### 4.1 Put credential management in Keycloak

Keycloak issues access tokens. PayBridge is a Spring OAuth2 resource server. It validates JWT signatures with the issuer's JWK set, issuer, expiry/not-before and audience `paybridge-api`. It does not store passwords, expose a custom login endpoint or sign its own production access tokens.

Swagger uses Authorization Code + PKCE with a public client. The realm import disables implicit and password grants for that client and restricts the redirect/origin to localhost. No client secret belongs in Swagger UI.

### 4.2 Map identity by issuer and subject

`customer_identity` binds `(issuer, subject)` to the customer UUID with uniqueness on both identity and customer. Onboarding requires the CUSTOMER role and the verified token email to match the submitted email. It does not automatically link existing customers by matching email.

For an existing customer, have an authorized operator verify the external identity and make the mapping deliberately. Do not allow a user to submit an arbitrary customer ID as proof of ownership.

### 4.3 Distinguish roles and ownership

| Principal | Allowed |
|---|---|
| CUSTOMER | Onboard self; read own customer/account/transfer; change own next of kin; own remittances and quotes |
| TREASURY | Local funding/top-up/outcome/recovery controls and settlement reconciliation |
| COMPLIANCE | KYC verification endpoint |
| SUPPORT | Role recognized, but no blanket customer-data bypass is granted |

Existing transfer requests still contain senderCustomerId for compatibility, but it must match the linked identity before initiation. Transfer reads require sender ownership. Account and customer routes enforce ownership. KYC verification requires COMPLIANCE, so a customer cannot self-verify.

The legacy transfer idempotency path additionally checks sender, recipient, amount and narration before returning an existing transfer. Its key namespace remains globally unique in V4; migrating that schema to a scoped namespace and fixing its concurrency/recovery behavior remain separate changes.

### 4.4 Keep security failures meaningful

Missing/invalid/expired token: 401 from the security filter chain. Wrong role or customer ownership: 403. Missing linked customer/profile: business error. Reused reference with changed request: 409. Invalid fields: 400. Business state, quote or liquidity failure: 422. The global handler now preserves 403 and ResponseStatusException rather than converting them into generic 500 errors.

The API is stateless bearer-only, so CSRF is disabled for this setup. If you introduce cookie-based browser sessions later, revisit CSRF. Swagger and API are same-origin; do not enable wildcard CORS with credentials. Signed Paystack webhooks retain their signature-authenticated route and do not require a customer JWT.

### 4.5 Production work still required

Use HTTPS, a stable issuer URL, managed database/secrets, Keycloak MFA for privileged users, audit trails for KYC/treasury decisions and an approved existing-customer linking workflow. Short-lived JWTs remain valid until expiry unless you add an online revocation/introspection strategy. Configure refresh rotation and session/logout behavior in Keycloak; PayBridge never accepts an ID token as a substitute for a correctly scoped/audienced API access token.

The new money services are profile-restricted to local/test because their rails are simulated. Authentication still applies to the older application in other profiles. This change does not certify the rest of the legacy transfer path for production.

## 5. Run locally and test with Swagger

### 5.1 Prerequisites

Use JDK 21, Docker Compose and an Internet connection for first-time dependencies/images. On Windows, run `mvnw.cmd`; on Bash, run `bash mvnw`. Check `java -version` first. Keep Keycloak/PostgreSQL bound to localhost for this walkthrough.

### 5.2 Start infrastructure

Set `KEYCLOAK_ADMIN_PASSWORD` to a local admin password in your shell, then:

```bash
docker compose up -d
```

PowerShell sets environment variables as `$env:KEYCLOAK_ADMIN_PASSWORD = 'your-local-admin-password'`. Bash uses `export KEYCLOAK_ADMIN_PASSWORD='your-local-admin-password'`. Do not commit the value. The compose database password and application-local default match; if you override DB_PASSWORD, export it to both Compose and the app.

The realm import lives in `infra/keycloak/paybridge-realm.json`. The sample pins Keycloak 26.8.1 and runs start-dev for localhost only. It persists Keycloak data in its own volume. Import occurs on creation; changing the JSON does not overwrite an existing realm automatically. Apply later changes in the admin console or use an explicit migration process rather than deleting data casually.

### 5.3 Create the four test users

At `http://localhost:18080/admin`, log in to the **master** admin console using the bootstrap admin. Switch to the **paybridge** realm.

Create users:

| Username | Email | Realm role |
|---|---|---|
| alice | alice@example.com | CUSTOMER |
| bob | bob@example.com | CUSTOMER |
| treasury | treasury@example.com | TREASURY |
| reviewer | reviewer@example.com | COMPLIANCE |

Set local passwords in Credentials and mark Email verified in each user profile for this controlled demo. Assign roles through Role mapping. In a real registration flow, email verification must occur through the identity provider rather than an administrator pretending it happened.

Confirm the imported client `paybridge-swagger` is public, Authorization Code enabled, PKCE S256 required, password/implicit grants disabled, redirect `http://localhost:8080/swagger-ui/oauth2-redirect.html`, origin `http://localhost:8080`. Its audience mapper includes `paybridge-api` in access tokens. The profile/email/roles scopes are assigned.

### 5.4 Build and start PayBridge

```bash
bash mvnw test
bash mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Windows:

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

The local profile applies V1–V7 and skips development seed data. Use a new local test database or deliberately link existing customers; the identity mapping does not silently claim seeded profiles. Existing databases using a different password need their correct DB_PASSWORD. Existing Flyway checksum errors should be investigated against migration history, not repaired blindly.

### 5.5 Open Swagger and obtain access tokens

Open `http://localhost:8080/swagger-ui.html`, choose the **all** group if necessary and click **Authorize**. Select openid/profile/email scopes, use `paybridge-swagger` with no client secret and log in as Alice. The browser performs the PKCE exchange; Swagger adds the bearer access token.

To switch users, clear Swagger authorization and sign out of the Keycloak session as well. Swagger's Logout button alone may only clear the stored token while the identity provider session remains. Use separate browser profiles/private windows for Alice, treasury and reviewer if easier. Never paste real tokens into public logs or the docs.

The automated suite verifies the OpenAPI document contains OAuth and new endpoint paths. A human browser PKCE round trip is a separate verification step; consult TEST-RESULTS for whether it was executed here.

### 5.6 Onboard Alice and Bob

As Alice, `POST /api/v1/customers`:

```json
{"firstName":"Alice","lastName":"Example","email":"alice@example.com","phoneNumber":"08031230001"}
```

Save returned `id` as ALICE_ID. `GET /api/v1/customers/me` should return the same ID. Account creation now commits in the same onboarding transaction.

As Bob, repeat with email bob@example.com, firstName Bob and phone 08031230002. Save BOB_ID. As Alice, attempting `GET /api/v1/customers/{BOB_ID}` or Bob's balance must return 403.

### 5.7 Verify KYC as reviewer

As Alice, `POST /api/v1/customers/{ALICE_ID}/kyc/verify` must return 403.

As reviewer, the same request should return 200 and VERIFIED. The reviewer does not need a customer profile. Repeat for Bob if testing foreign quote ownership.

### 5.8 Settlement first: fund and reconcile float

As treasury:

```http
POST /api/v1/local/settlements/topups
Content-Type: application/json

{"reference":"float-001","amountMinor":100000000}
```

That is NGN 1,000,000. Expect cashMinor 100000000, reservedMinor 0 and availableMinor 100000000. Repeat unchanged: no second top-up. Change amount using the same reference: 409.

`GET /api/v1/settlements/float` reads the snapshot. Then:

```http
POST /api/v1/settlements/reconciliations

{"statementId":"statement-001","closingBalanceMinor":100000000}
```

Expect MATCHED. Submit a new statement ID with 99999999 and expect BREAK with differenceMinor -1. Books stay unchanged. A customer token calling these controls must return 403.

### 5.9 Remittance second: create and confirm local USD funding

As Alice, set header `Idempotency-Key: remit-001`:

```http
POST /api/v1/remittances

{"sourceUsdMinor":10000,"beneficiaryName":"Ada Obi","bankCode":"058","accountNumber":"0123456789"}
```

Save the returned REMITTANCE_ID. Expect AWAITING_FUNDING. Replay unchanged: same ID. Change amount with that key: 409.

As treasury:

```http
POST /api/v1/local/remittances/{REMITTANCE_ID}/funding

{"fundingReference":"usd-receipt-001","sourceUsdMinor":10000}
```

Expect FUNDED. Replay unchanged: one receipt/journal. A different amount fails with FUNDING_MISMATCH; a customer attempting this control gets 403.

### 5.10 FX third: quote and accept through conversion

As Alice:

```http
POST /api/v1/fx/quotes

{"sourceUsdMinor":10000}
```

Expect sourceFeeMinor 100, destinationMinor 14850000 and the configured rate. Save QUOTE_ID. Within its 60-second validity:

```http
POST /api/v1/remittances/{REMITTANCE_ID}/convert

{"quoteId":"QUOTE_ID"}
```

Replace QUOTE_ID with the actual UUID. Expect PAYOUT_PENDING. Float remains cash 100000000 but reserved becomes 14850000 and available becomes 85150000. The quote is consumed in the same transaction.

Wait beyond expiry before accepting a different unused quote to test QUOTE_EXPIRED. Obtain a fresh quote for the funded operation. A Bob-owned quote must be refused for Alice. Insufficient float must leave the quote unconsumed and the remittance FUNDED.

### 5.11 Complete or simulate uncertainty

For immediate simulated success, as treasury:

```http
POST /api/v1/local/remittances/{REMITTANCE_ID}/process
```

Expect PAID_OUT. Float is cash 85150000, reserved 0. Repeat: no extra capture or journal. A subsequent reported balance of 85150000 under a new statement ID should match.

For another funded/converted remittance, **before processing** set the local provider outcome:

```http
POST /api/v1/local/remittances/{id}/outcome

{"outcome":"UNKNOWN"}
```

Processing stays PAYOUT_PENDING with reservation retained. Change the simulated UNKNOWN to SUCCESS, then run `/api/v1/local/remittances/recover`. It completes using the same reference. Terminal fake provider outcomes cannot be changed arbitrarily.

For definitive failure use FAILED before processing. Expect PAYOUT_FAILED, released float and an NGN payable. As owner call `/api/v1/remittances/{id}/retry`; the new attempt reserves again. The fake provider defaults to SUCCESS for its new reference. Process that attempt and verify PAID_OUT. No second FX conversion occurs.

### 5.12 Inspect expected errors

| Scenario | HTTP | Error/result |
|---|---|---|
| No/expired/malformed token | 401 | Security rejection |
| Wrong audience or issuer | 401 | Token validation failure |
| Alice reads Bob's resource | 403 | Access denied |
| CUSTOMER calls treasury/KYC control | 403 | Role denied |
| No Idempotency-Key / malformed beneficiary | 400 | Request/field validation |
| Same idempotency key, changed payload | 409 | Conflict |
| Unknown owned operation/quote ID | 404 | Resource not found |
| Source funding amount differs | 422 | FUNDING_MISMATCH |
| Unfunded conversion | 422 | REMITTANCE_STATE |
| Expired quote | 422 | QUOTE_EXPIRED |
| Quote amount differs from funding | 422 | QUOTE_AMOUNT_MISMATCH |
| NGN float insufficient | 422 | INSUFFICIENT_FLOAT |
| Configured screening blocks beneficiary/value | 422 | COMPLIANCE_REJECTED |
| Failed payout | 200 | PAYOUT_FAILED, NGN retained; this is an operation outcome |
| Unknown provider outcome | 200 | PAYOUT_PENDING, still reserved |
| Closing statement mismatch | 200 | BREAK, no forced book adjustment |

Do not treat this expected-error matrix as proof that every row was tested. Executed cases and unexecuted cases are separated in TEST-RESULTS.

### 5.13 Run the HTTP suite against actual PostgreSQL

The default test profile uses a portable H2 schema and a real embedded HTTP server. To verify production Flyway migrations and PostgreSQL locks, use a **fresh dedicated test database**, never your application database:

```bash
docker compose -f compose.test.yml up -d
export PAYBRIDGE_TEST_DB_URL=jdbc:postgresql://localhost:15433/paybridge_http_test
bash mvnw -Dtest=WorkflowHttpTest test
```

On PowerShell set `$env:PAYBRIDGE_TEST_DB_URL = 'jdbc:postgresql://localhost:15433/paybridge_http_test'` before running the wrapper. Defaults for this isolated test container are paybridge / paybridge-test-only; environment variables PAYBRIDGE_TEST_DB_USER and PAYBRIDGE_TEST_DB_PASSWORD can override them.

When this URL is present, the HTTP test switches to the PostgreSQL driver, enables Flyway V1–V7 and disables the H2 schema initializer. The suite creates fixtures and expects an empty database. Use an isolated test container/database for each run. Do not point this variable at a shared or production database. This option is provided for reproducibility; TEST-RESULTS states whether it was executed here.

## 6. Further changes I recommend

1. Replace local funding, rate and payout adapters with verified provider contracts. Add signed inbox records, payload verification, provider-aware error classification and a reconciliation worker before real funds.
2. Introduce a verified beneficiary registry with ownership, account-name resolution, screening version and explicit destination consent. Remittance currently validates shape, not bank account existence.
3. Replace exact-name demo sanctions checks with an appropriate screening workflow. Preserve declined/review decisions without weakening the financial transaction. The current remittance screen rolls back when conversion fails, so it does not persist a durable rejected review case.
4. Unify limit reservations across legacy transfers and remittances. Current legacy daily limits still count allowed screenings and are not protected by the new remittance authorization lock. Do not claim global concurrent limit enforcement.
5. Migrate legacy transfer idempotency to owner-scoped keys, serialize its webhook transitions and add crash recovery. These known issues from the earlier review are not all repaired by adding the new services.
6. Add general currency-aware wallets/holds only if customers need reusable USD balances. Current remittances use segregated per-operation liabilities, so they cannot spend the same funding through another wallet.
7. Add full statement ingestion, statement-line reconciliation, fee booking, FX counterparty obligations and treasury replenishment execution. A snapshot comparison does not substitute for them.
8. Add operator decision audit logs, maker/checker approvals if required, rate/fee policy versions, metrics, work pagination/retries/dead letters, failure isolation, notification outbox and a defined reversal/refund workflow.
9. Keep JDK 21 and the actual PostgreSQL database in CI. Verify dependencies as a supported BOM set. This implementation preserves the supplied POM version overrides; their compatibility/security must not be inferred from comments alone.

## 7. Technical references

- Spring Security JWT resource server: https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html
- Spring Boot OAuth2 configuration, including audiences: https://docs.spring.io/spring-boot/reference/security/oauth2.html
- Keycloak OIDC: https://www.keycloak.org/securing-apps/oidc-layers
- Keycloak container guidance: https://www.keycloak.org/server/containers
- Keycloak 26.8.1 release: https://github.com/keycloak/keycloak/releases/tag/26.8.1
- Swagger UI OAuth2/PKCE: https://swagger.io/docs/open-source-tools/swagger-ui/usage/oauth2/

The code and accounting choices are this implementation's design, derived from the supplied application and your selected defaults. They are not supplied by those references.
