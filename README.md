# PayBridge

PayBridge is a Spring Boot modular application for customer accounts, NGN transfers, and USD-to-NGN remittances. This branch adds settlement float, FX quotes, remittances, and Keycloak authentication while retaining the existing transfer and notification modules.

The new money workflows use **simulated funding and payouts**, a configurable demo FX rate, and the `local`/`test` profiles. They do not move real money.

## Features

| Feature | How to check |
|---|---|
| Customer onboarding and identity ownership | Create Alice/Bob, read your profile, try another customer's profile |
| Automatic NGN account creation | Read the account and balance after onboarding |
| Role-controlled KYC | CUSTOMER is refused; COMPLIANCE can verify |
| Next of kin | Set and read your designation |
| Existing NGN transfers and routing | Use transfer endpoints with funded fixtures; see section 10 |
| Settlement float and reconciliation | Top up, reserve through conversion, reconcile cash |
| USD funding, FX, and remittance | Follow sections 6–9 entirely in Swagger |
| Unknown/failed payouts and recovery | Set fake outcomes and retry/recover appropriately |
| Notifications and balanced accounting | Inspect application logs and automated tests; no public listing endpoints |

## 1. Start locally

Requirements: JDK 21 or a compatible newer JDK, Docker Desktop with Compose, Git, and internet for initial dependencies/images. Maven is included through the wrapper. October 30 validation used JDK 25.0.4 with Java release 21.

Open PowerShell in the project root containing `pom.xml`:

```powershell
java -version
docker compose version
$env:KEYCLOAK_ADMIN_PASSWORD = Read-Host 'Choose a local Keycloak admin password' -MaskInput
docker compose up -d
docker compose ps
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

`-MaskInput` requires PowerShell 7.1 or later. On older PowerShell, set `KEYCLOAK_ADMIN_PASSWORD` using your preferred local secret handling before running Compose.

On Bash:

```bash
read -rsp 'Choose a local Keycloak admin password: ' KEYCLOAK_ADMIN_PASSWORD
export KEYCLOAK_ADMIN_PASSWORD
docker compose up -d
bash mvnw test
bash mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Wait for PostgreSQL to become healthy and Keycloak to finish starting. Leave the application terminal running.

| Service | Address/default |
|---|---|
| Swagger | [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) |
| OpenAPI JSON | [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs) |
| Health | [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health) |
| Keycloak administration | [http://localhost:18080/admin](http://localhost:18080/admin) |
| PostgreSQL | `127.0.0.1:15432`, database/user `paybridge` |
| Local DB password | `paybridge-local-only`, unless `DB_PASSWORD` is set |

Compose and the application must use the same `DB_PASSWORD`. Existing volumes retain their original credentials. Changing the environment variable does not change an existing database's password. Use a separate development database when needed; do not delete volumes or edit applied migrations to fix startup errors.

The local profile runs V1–V7 and excludes development seed migrations. Existing customer profiles are not automatically linked by email to new identities. Onboard new demonstration users or use an explicitly verified operator linking process.

Stop the app with Ctrl+C. Run `docker compose stop` with the same environment to stop infrastructure while retaining data.

## 2. Create four Keycloak users

Open Keycloak administration, log in to the **master** realm as `admin` with your chosen password, then select the imported **paybridge** realm.

Create users, set non-temporary local passwords, mark their emails verified for this controlled demo, and assign the indicated **realm role** under Role mapping:

| Username | Email | Role |
|---|---|---|
| alice | alice@example.com | CUSTOMER |
| bob | bob@example.com | CUSTOMER |
| treasury | treasury@example.com | TREASURY |
| reviewer | reviewer@example.com | COMPLIANCE |

Treasury/reviewer do not need PayBridge customer profiles. SUPPORT is recognized but has no general customer-data bypass.

The imported `paybridge-swagger` client must be public, with Standard flow enabled, PKCE S256 required, password/implicit grants disabled, redirect `http://localhost:8080/swagger-ui/oauth2-redirect.html`, and web origin `http://localhost:8080`. Its access tokens include audience `paybridge-api`.

The realm JSON is in `infra/keycloak/paybridge-realm.json`. Import happens when the realm is created; changing the file does not overwrite an existing persisted realm.

## 3. Authorize Swagger

1. Open Swagger and select the **all** API group to see all API routes.
2. Click **Authorize**, select `openid`, `profile`, and `email`, and use client ID `paybridge-swagger`, with no client secret.
3. Log in as the user named in each step below. The browser performs authorization code + PKCE.
4. Expand an operation, click **Try it out**, enter the path values, JSON body, and any `Idempotency-Key`, then **Execute**.
5. Check the response status/body and save IDs for subsequent steps.

To switch users, clear Swagger authorization **and end the Keycloak login session**, then authorize again. Separate browser profiles for Alice, treasury, and reviewer simplify this. Each browser profile needs its own Swagger authorization. Reauthorize when the short-lived access token expires.

Replace every placeholder (`ALICE_ID`, `BOB_ID`, `REMITTANCE_ID`, `QUOTE_ID`, `TRANSFER_ID`) with a real UUID. All `Minor` amounts are integers: USD `10000` cents = USD 100; NGN `100000000` kobo = NGN 1,000,000.

## 4. Onboard, verify KYC, and inspect accounts

As **Alice**, `POST /api/v1/customers`:

```json
{"firstName":"Alice","lastName":"Example","email":"alice@example.com","phoneNumber":"08031230001"}
```

Expect **201**. Save `id` as `ALICE_ID`. The email must match the token's verified email. An NGN account is created in the same transaction.

As **Bob**, repeat with first name `Bob`, email `bob@example.com`, phone `08031230002`. Save `BOB_ID`.

As **Alice**, check:

| Operation | Expected |
|---|---|
| `GET /api/v1/customers/me` | 200, Alice's profile |
| `GET /api/v1/customers/ALICE_ID` | 200 |
| `GET /api/v1/accounts/by-customer/ALICE_ID` | 200, account details |
| `GET /api/v1/accounts/by-customer/ALICE_ID/balance` | 200, initially zero |
| `GET /api/v1/customers/BOB_ID` | 403 |
| `GET /api/v1/accounts/by-customer/BOB_ID/balance` | 403 |
| `POST /api/v1/customers/ALICE_ID/kyc/verify` | 403 |

Switch to **reviewer** and call `POST /api/v1/customers/ALICE_ID/kyc/verify`, with no body. Expect **200**, KYC `VERIFIED`. Repeat for Bob. Verify KYC before FX/remittance exercises.

## 5. Set next of kin

As **Alice**, `PUT /api/v1/customers/ALICE_ID/next-of-kin`:

```json
{"nextOfKinCustomerId":"BOB_ID","relationship":"SIBLING"}
```

Expect **200**. Read with `GET /api/v1/customers/ALICE_ID/next-of-kin`. Supported relationships: `PARENT`, `SPOUSE`, `CHILD`, `SIBLING`, `OTHER`. Another customer's designation is protected by ownership checks.

Next-of-kin routing belongs to the existing NGN transfer flow. Remittances use their explicitly supplied bank beneficiary.

## 6. Top up and reconcile settlement float

As **treasury**, `POST /api/v1/local/settlements/topups`:

```json
{"reference":"float-001","amountMinor":100000000}
```

Expect **200**, cash `100000000`, reserved `0`, available `100000000`. Read with `GET /api/v1/settlements/float`.

Repeat unchanged: no second top-up. Reuse the reference with another amount: **409**. Alice calling treasury controls: **403**.

Call `POST /api/v1/settlements/reconciliations`:

```json
{"statementId":"statement-001","closingBalanceMinor":100000000}
```

Expect **200**, `MATCHED`. With a new statement ID and balance `99999999`, expect `BREAK`, difference `-1`, and unchanged books. After payouts, use the current cash balance and a fresh statement ID for another matching reconciliation.

Settlement float funds remittance payouts. It does not credit a customer's existing NGN account.

## 7. Create and fund a remittance

As **Alice**, `POST /api/v1/remittances`, with header **`Idempotency-Key: remit-001`**:

```json
{"sourceUsdMinor":10000,"beneficiaryName":"Ada Obi","bankCode":"058","accountNumber":"0123456789"}
```

Expect **200**, `AWAITING_FUNDING`. Save `id` as `REMITTANCE_ID`. The sender is derived from the token. Read with `GET /api/v1/remittances/REMITTANCE_ID`.

Replay unchanged with the same key: original ID. Change amount/beneficiary with that key: **409**. Bob reading Alice's remittance: **403**. Account number must contain ten digits.

As **treasury**, `POST /api/v1/local/remittances/REMITTANCE_ID/funding`:

```json
{"fundingReference":"usd-receipt-001","sourceUsdMinor":10000}
```

Expect **200**, `FUNDED`. An identical replay creates no extra receipt/journal. Incorrect amount: **422**, `FUNDING_MISMATCH`. Alice asserting funding: **403**. This is simulated receipt, not an actual bank deposit.

## 8. Quote FX and convert

As **Alice**, `POST /api/v1/fx/quotes`:

```json
{"sourceUsdMinor":10000}
```

Expect **200**. Save `id` as `QUOTE_ID`. Default calculation: USD 100 less USD 1 fee, at NGN 1,500 per USD, gives NGN 148,500 (`sourceFeeMinor: 100`, `destinationMinor: 14850000`). Read with `GET /api/v1/fx/quotes/QUOTE_ID`.

Within **60 seconds**, `POST /api/v1/remittances/REMITTANCE_ID/convert`:

```json
{"quoteId":"QUOTE_ID"}
```

Expect **200**, `PAYOUT_PENDING`, `destinationNgnMinor: 14850000`. Conversion, quote acceptance, journals, and float reservation commit together. Treasury float: cash `100000000`, reserved `14850000`, available `85150000`.

Repeat conversion with the same quote: no duplicate conversion. Expired quote: **422**, `QUOTE_EXPIRED`; request a fresh quote for the funded amount. Foreign quote: **403**. Conversion before funding: **422**. Insufficient float: **422**, `INSUFFICIENT_FLOAT`, with quote acceptance/journals/state rolled back together.

## 9. Payout, unknown results, failure, retry, and recovery

As **treasury**, `POST /api/v1/local/remittances/REMITTANCE_ID/process`, with no body. An unset fake provider result defaults to `SUCCESS`.

Expect **200**, `PAID_OUT`. Repeat: no second payout. Float now has cash/available `85150000`, reserved `0`. Alice can read final status but cannot process payouts.

`POST /api/v1/local/remittances/recover` as treasury checks all pending rows with their stable payout references. Success has no response body. Recovery is operator-triggered; there is no automatic payout scheduler.

For **UNKNOWN**, create/fund/convert a new remittance using new references and a fresh quote. Before processing, as treasury call `POST /api/v1/local/remittances/REMITTANCE_ID/outcome`:

```json
{"outcome":"UNKNOWN"}
```

Process: remains `PAYOUT_PENDING`, float stays reserved. Alice cannot retry it (**422**). Treasury may later set `SUCCESS` and run recovery to finish once.

For **FAILED**, use a separate new converted remittance and set:

```json
{"outcome":"FAILED"}
```

Process: `PAYOUT_FAILED`, reservation released, converted NGN still payable. As Alice, `POST /api/v1/remittances/REMITTANCE_ID/retry`, no body: expect `PAYOUT_PENDING`, attempt `2`, a new payout reference, and the same NGN amount. Treasury can process the new attempt successfully.

Set outcomes **before** processing; an unset attempt succeeds immediately. Terminal outcomes cannot be changed (**409**). Failure does not reverse FX or automatically refund USD.

## 10. Existing NGN transfers and routing

The existing transfer path uses customer account balances, separate from remittance payables and settlement float. Newly onboarded accounts have zero balance. There is currently **no Swagger deposit endpoint or external-account provisioning endpoint**. Positive transfer/routing exercises require operator-prepared funded test fixtures. Settlement top-ups do not fund customer accounts. With fresh users, the request below demonstrates the insufficient-funds rejection; routing logic is also covered by automated tests.

With funded, KYC-verified Alice, as Alice `POST /api/v1/transfers`, header `Idempotency-Key: transfer-001`:

```json
{"senderCustomerId":"ALICE_ID","recipientCustomerId":"BOB_ID","amountMinor":500000,"currency":"NGN","narration":"Swagger transfer check"}
```

Normal internal transfer: **201**, `COMPLETED`, `routingOutcome: DIRECT`. Save `TRANSFER_ID`; read with `GET /api/v1/transfers/TRANSFER_ID` as Alice. Check Alice's decreased balance, switch to Bob, and check his increased balance. A fresh unfunded Alice receives **422** instead.

Identical replays return the original result. Changed sender/recipient/amount/currency/narration conflicts (**409**). Legacy transfer keys are globally unique; choose unique keys across customers. Another customer's sender ID or transfer read returns **403**.

For next-of-kin routing, prepare a recipient with balance at least NGN 10,000,000 and a designated next of kin with an account. Send to that recipient from a different funded customer: expect `REDIRECTED_TO_NEXT_OF_KIN` and the next of kin as destination. Missing routing prerequisites follow the existing policy's rejection rules.

For external fixtures, the default simulated gateway succeeds, except amounts ending in **13 kobo** simulate failure/refund. Responses can be **201** (completed), **202** (pending/processing), or **422** (failed/rejected). This legacy gateway is separate from the new remittance fake adapter.

`POST /api/v1/webhooks/paystack` requires a valid provider signature, not a customer JWT. An ordinary Swagger bearer request is not a valid callback. Signature validation has automated coverage; real provider callbacks need provider setup.

## 11. Notifications, accounting, and negative checks

Existing transfer notifications cover completion/failure and next-of-kin redirection. The email channel logs to the application terminal; it does not send real email. There is no notification listing API and no remittance notification integration in this change.

The ledger records separate balanced journals for funding, FX legs, reservation, release, and payout. There is no public ledger browsing endpoint. Workflow tests inspect persisted journal entries and assert balance independently per currency.

| Scenario | Expected |
|---|---|
| Missing/malformed/expired/wrong-audience token | 401 |
| Wrong role or foreign customer data | 403 |
| Missing idempotency header / malformed beneficiary | 400 |
| Reused reference with changed request | 409 |
| Expired/used quote, invalid state, insufficient float | 422 with business `code` |
| Matching/different closing statement | `MATCHED`/`BREAK`, no book adjustment |
| Unknown payout | Pending, reservation retained |
| Failed payout then retry | New attempt/reference, no reconversion |
| Duplicate success/recovery | One financial capture |
| New money routes outside local/test | Unavailable; existing APIs still require authentication |

## 12. Tests and troubleshooting

Run `.\mvnw.cmd test` (PowerShell) or `bash mvnw test` (Bash). Default tests use H2 and RSA-signed test JWTs. On October 30, **43 tests passed on H2**, then the full **43 tests passed on PostgreSQL 18.6** with real Flyway V1–V7 migrations. The PostgreSQL run found and corrected an `Instant` binding in the expiry test. Keycloak startup/import, real JWK discovery, and browser PKCE login remain manual verification items.

To run the supplied PostgreSQL test setup against a **fresh disposable database**:

```powershell
docker compose -f compose.test.yml up -d
$env:PAYBRIDGE_TEST_DB_URL = 'jdbc:postgresql://127.0.0.1:15433/paybridge_http_test'
$env:PAYBRIDGE_TEST_DB_USER = 'paybridge'
$env:PAYBRIDGE_TEST_DB_PASSWORD = 'paybridge-test-only'
.\mvnw.cmd test
Remove-Item Env:PAYBRIDGE_TEST_DB_URL, Env:PAYBRIDGE_TEST_DB_USER, Env:PAYBRIDGE_TEST_DB_PASSWORD
```

The HTTP suite creates fixed identities/references and needs an empty database each run. Provision another empty test database for reruns. Never target a shared/live customer database. See [validation evidence](docs/TEST-RESULTS.md) for exact limits.

| Symptom | Check |
|---|---|
| Java release/version error | `java -version`, `JAVA_HOME`, IntelliJ/Maven JDK |
| Maven download fails | Internet/proxy settings; initial dependency downloads |
| Port in use | Existing processes on 8080, 18080, 15432, test port 15433 |
| DB connection/password error | PostgreSQL health, `DB_URL`, original volume credentials |
| Flyway checksum error | Applied migration history; do not edit V1–V6 or blindly repair |
| Redirect/login error | Imported realm, exact redirect/origin, PKCE, Keycloak readiness |
| 401 after login | Expiry, `OIDC_ISSUER`, access-token audience `paybridge-api` |
| Previous user keeps logging in | End Keycloak session or use another browser profile |
| `CUSTOMER_NOT_LINKED` | Onboard current identity; seed profiles are not claimed by email |
| `EMAIL_TAKEN` / `CUSTOMER_ALREADY_LINKED` | Reuse profile or choose another verified demo identity |
| New routes missing | Start the `local` profile; choose Swagger's `all` group |
| `QUOTE_EXPIRED` | Obtain and use a fresh quote within its validity |
| `INSUFFICIENT_FLOAT` | Treasury top-up with a new reference |
| Legacy transfer insufficient funds | Funded customer fixture required; float is separate |

## More detail and remaining work

- [Implementation guide](IMPLEMENTATION-GUIDE.md)
- [Source walkthrough](docs/CODE-WALKTHROUGH.md): supplied snapshot; current source is authoritative
- [Test results and limitations](docs/TEST-RESULTS.md)
- [Imported file manifest](docs/CHANGED-FILES.md)
- [Original README](docs/ORIGINAL-README.md)

Before real-money adapters, decide the funding rail, FX source/spread, provider finality/reconciliation, refund policy, operator approval/audit controls, and existing-customer identity linking. Current local limits are USD 10,000 per remittance and USD 20,000 converted per customer per Lagos day; legacy NGN transfer limits are separate. Recovery stops at the first error. Automatic workers/leases, provider-backed reversals, statement-line matching, and shared limits across old/new transfer paths remain future work.
