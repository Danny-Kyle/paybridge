# Integration validation — 30 October 2026

The Downloads implementation was integrated onto repository main `306e7f0`, preserving its newer notification code and tests. The supplied package had no Git metadata. Existing repository history is preserved. The eight new feature commits use user-requested author/committer dates spanning October 15–18 and 28–30; those dates are organizational metadata, not evidence that testing happened earlier.

## Executed on this computer

| Run | Environment | Result |
|---|---|---|
| Full suite, default configuration | JDK 25.0.4, release 21, H2 | 43 passed, 0 failures/errors/skipped |
| Full suite, real database | JDK 25.0.4, release 21, PostgreSQL 18.6 | 43 passed, 0 failures/errors/skipped |
| Real Flyway migrations | Fresh, isolated PostgreSQL database on localhost:25433 | V1–V7 applied successfully |
| Existing migration comparison | Compared V1–V6 with main `306e7f0` | Unchanged |
| Module boundaries | Spring Modulith / ArchUnit suite | Passed |

Commands used Maven 3.9.16: `mvn test -DargLine=-Xmx512m`. PostgreSQL run additionally set `PAYBRIDGE_TEST_DB_URL`, `PAYBRIDGE_TEST_DB_USER`, and `PAYBRIDGE_TEST_DB_PASSWORD` against a separate disposable database. Existing user databases were not used. The temporary server was stopped after validation.

The HTTP suite runs 13 tests against the actual Spring Boot server with signed JWT fixtures. Coverage includes ownership/roles, invalid tokens, KYC, top-up and request idempotency, reconciliation, verified simulated funding, quote expiry/ownership, deterministic FX, rollback on insufficient float, unknown/failure/retry paths, concurrent create/completion, recovery, OpenAPI, and journal balancing by currency. The other 30 tests cover money, customers, journals, routing, signature verification, transfer snapshot assembly, and modularity.

Fresh suite summaries are in [surefire](surefire/). The [66-request transcript](endpoint-transcript.jsonl) is from the final successful PostgreSQL run. It contains synthetic demonstration data and no access tokens. Raw build logs are in ignored `target/integration-validation.log` (H2) and `target/postgres-verified-build.log` (PostgreSQL).

## Issue found and corrected

The first real PostgreSQL run applied the migrations, but the quote-expiry test passed a `java.time.Instant` directly to JDBC. PostgreSQL could not infer its SQL type. The fixture now binds `java.sql.Timestamp.from(...)`, matching the application's JDBC bindings. The full suite passed against a fresh PostgreSQL database after this change.

Compilation still reports deprecated APIs in existing controller/test code. The local run also emitted a Mockito dynamic-agent warning; it did not fail the tests.

## Not verified and remaining limitations

- Docker/Keycloak startup, realm import, issuer JWK discovery/rotation, and an actual browser Swagger/PKCE round trip were not executed. OpenAPI JSON and signed JWT validation were exercised by the suite. The README provides manual steps.
- PostgreSQL verification used installed PostgreSQL 18.6; the supplied Compose example pins PostgreSQL 17. That exact container image was not run.
- Real USD funding, market FX pricing/hedging, live bank payouts, real sanctions screening, and provider statement-line matching are outside the local adapter implementation.
- Legacy account deposit/external-account provisioning has no public API. Positive manual legacy transfer exercises require prepared funded fixtures. Existing notification delivery is log-based and has no public listing API.
- Worker process-kill tests, multi-instance leases/backoff, all precision/property boundaries and race combinations, universal limits across legacy transfers/remittances, late reversals, and operator maker/checker/audit workflows remain future work.
- Recovery remains operator-triggered, unpaginated, and stops on its first error. A definitively failed converted payout retains an NGN payable rather than automatically refunding USD.

The supplied earlier report is preserved as [TEST-RESULTS-2026-10-16.md](TEST-RESULTS-2026-10-16.md). Its unverified PostgreSQL statements describe that earlier environment, not this integration run.
