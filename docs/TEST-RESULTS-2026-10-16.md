# Executed test results and remaining verification

Date: 16 October 2026. The supplied archived test reports were not used as proof for this implementation.

## Final result

**BUILD SUCCESS: 43 tests, zero failures, zero errors, zero skipped.** This includes 13 HTTP workflow tests issuing 66 requests to the real Spring Boot server on a random localhost port, plus 30 money/customer/journal/routing/signature/assembler/module tests.

The application compiled 152 main Java source files with release 21. The test environment used JDK 21.0.6, Spring Boot 4.1.0, Tomcat 11.0.26 and H2 in PostgreSQL compatibility mode. Do not interpret the isolated runtime's JDK patch version as a recommendation to install that older patch on your machine.

Default reproduction: `bash mvnw test` with JDK 21. A small heap was used in this workspace via test JVM options. The test-only Mockito extension selects the subclass mock maker because the existing tests only mock interfaces and the environment does not allow the default inline agent attachment.

| Suite | Tests | Failures | Errors |
|---|---:|---:|---:|
| ModularityTests | 2 | 0 | 0 |
| WorkflowHttpTest | 13 | 0 | 0 |
| CustomerTest | 4 | 0 | 0 |
| JournalTest | 4 | 0 | 0 |
| MoneyTest | 5 | 0 | 0 |
| NextOfKinRoutingPolicyTest | 7 | 0 | 0 |
| PaystackSignatureVerifierTest | 3 | 0 | 0 |
| TransferSnapshotAssemblerTest | 5 | 0 | 0 |

## HTTP coverage

The requests use Java HttpClient over loopback to the actual embedded servlet container, not MockMvc or a replacement application. Tokens are RSA-signed test fixtures validated by NimbusJwtDecoder, with issuer, audience and expiry validation. The test-only decoder does not bypass signature checks. Production uses the configured issuer and JWK discovery.

Executed scenarios:

- Missing token, malformed token, expired token and wrong audience return 401.
- Alice/Bob onboarding binds the authenticated identity; account provisioning runs in the onboarding transaction.
- Alice cannot read Bob's customer or balance. A customer cannot self-verify KYC or invoke treasury controls. COMPLIANCE can verify KYC.
- Treasury top-up repeats do not duplicate cash; changed amount conflicts. Matching statements produce MATCHED and a differing closing balance produces BREAK.
- Remittance replay returns the same operation. Changed payload conflicts; a missing idempotency header and malformed beneficiary fail validation.
- A customer cannot assert funding. Amount mismatch fails; duplicate verified simulated funding posts once.
- Conversion before funding fails. Expired quotes fail and foreign-owned quotes are refused.
- USD 100 less USD 1 at the demo 1500 rate yields 14,850,000 kobo.
- Conversion reserves float; replay does not duplicate conversion. An UNKNOWN provider result preserves pending state and reservation.
- Six concurrent completion requests commit one financial capture. Recovery and duplicate processing do not create another payout journal.
- A failed payout preserves NGN entitlement, releases reservation and can be retried with a distinct attempt reference without reconversion.
- Insufficient float rolls back quote acceptance, conversion journals and remittance state together.
- Six concurrent identical create requests return one operation ID.
- OpenAPI contains the OAuth authorization-code scheme and new paths.
- Every journal posted in the workflow balances independently by currency.

| HTTP status | Observed requests | Interpretation |
|---|---:|---|
| 200 | 45 | Successful request, replay or operation outcome |
| 201 | 2 | Customer created |
| 400 | 2 | Expected request validation rejection |
| 401 | 4 | Expected authentication rejection |
| 403 | 7 | Expected role/ownership rejection |
| 409 | 2 | Expected reference/payload conflict |
| 422 | 4 | Expected funding/state/quote/liquidity rejection |

No unexpected 500 response occurred in the final 66-request run. See endpoint-transcript.jsonl for the actual request method/path/status/body records. Bearer tokens and passwords are not recorded.

## Errors found while validating and corrected

1. An OpenAPI test searched for lowercase `paybridge` even though the returned document title is `PayBridge` and the test issuer is issuer.test. Corrected the assertion to test actual document contents; no endpoint was removed or bypassed.
2. The original interface mocks used Mockito's default inline mock maker, which could not attach its agent here. Selected the supported subclass mock maker for the test suite. The five affected assembler tests now execute and pass.
3. During source review, JDBC Instant bindings were changed to explicit java.sql.Timestamp values for PostgreSQL driver compatibility. The final H2 suite includes those changes; actual PostgreSQL binding remains part of the pending PostgreSQL run.

Build warnings remain for deprecated API usage in the supplied TransferController (and one test helper), annotation processor auto-discovery and Springdoc enabled in the test profile. There are no final compilation errors. Production's supplied springdoc disabling configuration is preserved; local/test docs are intentionally enabled.

## Not executed here

- Actual PostgreSQL V1-V7 migration and locking run: Docker/PostgreSQL services were unavailable. H2 uses a dedicated portable schema; its customer email index does not reproduce PostgreSQL's lower(email) expression index. The optional PAYBRIDGE_TEST_DB_URL configuration switches this same HTTP suite to a fresh PostgreSQL test database and real Flyway migrations. It is documented, not claimed as executed.
- Keycloak server startup, realm import, JWK discovery/rotation and browser PKCE login: no Keycloak instance was running here. The real app's OIDC configuration and Swagger flow need the provided local walkthrough.
- Clicking the Swagger browser UI: the OpenAPI JSON endpoint was tested, but no browser login or manual Try it out session was executed.
- Real USD funding, FX quotes, bank account resolution, settlement bank transfers, Paystack remittance payout, sanctions-provider checks or provider statement imports: all new rails are local simulations.
- Full process-kill/restart durability: the suite exercises persisted unknown-result recovery and duplicate processing, but does not kill the JVM between every boundary.
- All boundary/property tests for FX precision, simultaneous quote consumption across distinct operations, all funding-reference races, daily-limit concurrency across legacy transfers and remittances, post-success provider reversals and multi-instance worker leases.

## Remaining product/engineering limits

The settlement implementation is prefunded float control and closing-balance reconciliation; it is not a complete statement-line matching engine or bank-settlement executor. Remittance's local funding assertion and fixed FX rate must be replaced before real money. Its pending rows are durable work, but recovery is operator-triggered, unpaginated and currently stops on the first exception. A failed converted remittance remains an NGN payable; automated USD refunds, cancellation and late reversal are not implemented.

The existing compliance rules are demo controls. Screening rejections during the conversion transaction roll back their screening row, so durable review cases need a separate design. Global daily limits still need shared atomic reservations across the legacy transfer path and the new remittance path.

The new workflow is confined to local/test profiles. Authentication and ownership protections also cover the existing customer/account/transfer endpoints. Legacy transfer webhook concurrency, ambiguous HTTP refund classification and crash recovery from the earlier review still need separate work. The role checks are implemented; a full operator decision audit trail and maker/checker workflow are not.
