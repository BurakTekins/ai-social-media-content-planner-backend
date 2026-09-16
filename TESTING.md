# Automated testing

## Audit (2026-10-31)

Audited base commit: `780e99105aaa8bd492ac3d2e625ae62dbe90555b`.

Spring Boot 3.5.7 / Java 21, REST controllers, application services, domain entities,
Spring Data JPA/PostgreSQL, Flyway, scheduled generation/publication/recovery jobs,
and external AI/social adapters. Existing `spring-boot-starter-test` already provides
JUnit Jupiter, Mockito, AssertJ, Spring Test, and MockMvc. There were 19 existing test
classes and no checked-in CI workflows. The initial suite could not compile because
AiModelServiceTest and DraftRegenerationServiceTest used obsolete signatures.
After compilation was repaired, GenerationBatchJobTest exposed an offset-sensitive
timestamp comparison; its fixture now uses UTC, matching the domain normalization.

## Added or repaired tests

- `GeneralSettingsServiceTest`: configured defaults, persisted overrides, 20 invalid-limit cases rejected before persistence, valid limits and decimal precision (23 cases).
- `GeneralSettingsControllerTest`: real MVC binding/Bean Validation and exception advice; valid update, missing/malformed bodies, cross-field timing validation, invalid budget (6 cases). These are standalone MockMvc tests, not full application-context tests.
- `SettingsPersistenceTest`: all 13 Flyway migrations run on disposable PostgreSQL 16; full entity schema validation; repeated updates retain one row per key; caller rollback; injected PostgreSQL write failure proves the service rolls back the whole update (3 cases).
- Repaired three existing test files without changing production code.

## Running the suite

Java 21, Docker, and network access for initial dependencies/images are required.
Run `sh ./mvnw --batch-mode --no-transfer-progress test` from this repository.
The final local run used the wrapper (Maven 3.9.16), with `MAVEN_USER_HOME` and
`-Dmaven.repo.local` redirected to writable temporary caches.
Testcontainers `junit-jupiter` and `postgresql` 1.21.3 were added with test scope;
Spring Boot manages their versions. `src/test/resources/docker-java.properties`
sets API 1.44 for Docker 29 compatibility. Tests require Docker and fail rather
than silently skipping integration coverage when it is unavailable.

## Verified result (2026-10-31)

147 tests executed, 147 passed, 0 failures, 0 errors, 0 skipped.
32 cases were added in this change. Results come from actual local execution,
not an inferred GitHub Actions result.

## CI

`.github/workflows/tests.yml` runs on push, pull request, and manual dispatch.
Actions are pinned to verified commit SHAs, permissions are read-only, overlapping
runs are cancelled, and jobs have a 20-minute timeout. Java/frontend workflows
upload test reports even on failure. All four workflows passed actionlint 1.7.7.
GitHub-hosted execution has not been verified. Local test execution was completed
on 2026-10-31; commit metadata dates do not represent additional test runs.

## Remaining gaps and boundaries

- Authentication/authorization is not established by these tests; this pom has no Spring Security dependency. No authentication scheme was introduced.
- Other REST routes, OAuth exchange/refresh, external AI/social provider contracts, storage failures, and concurrent publication still need broader coverage.
- Passing regressions and database tests do not establish full browser/provider or deployed-system correctness.
