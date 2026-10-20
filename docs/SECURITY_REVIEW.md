# Security review: 2026-10-20

Scope: manual inspection of current public source/contracts, schemas, OpenAPI and build configuration. No production deployment, penetration test, complete Git-history secret scan or validated dependency scan was performed. GitHub secret-scanning tool could not run because the required repository feature was unavailable. Absence of detected secrets is not certification.

## Fixed

- Removed a malformed duplicate schema filename that prevented normal checkout (commit 90155e33f3b649cac997a47279a6118fde860683).
- Replaced duplicate ToolDescriptor declarations in AuditLog.kt with the intended AuditLog model.
- Added small model tests and a least-privilege CI build on JDK 17 / Gradle 8.14.4. Actual execution results must be checked in Actions.
- Added dependency-update configuration for Gradle and GitHub Actions.

## Open design risks

1. High, conditional on future implementation: caller, userId and permissions in request models are client-controlled. Never trust them for authentication or authorization.
2. High: the tool runner contract does not enforce a pre-execution policy gate or approval token. No sensitive connectors should ship before this exists and is tested.
3. Medium: generic maps and arrays lack resource limits; retries/timeouts need bounds.
4. Medium: tool risk/idempotency defaults are optimistic and must not authorize execution.
5. Medium: arbitrary evidence/outcome objects may leak credentials/PII unless redacted before persistence.
6. Dependency versions require a complete advisory scan and compatible upgrades; no claim of vulnerability-free dependencies is made.
7. Kotlin camelCase models and snake_case schemas require explicit serialization configuration and compatibility tests before API implementation.

## Remaining validation

Run the CI build, extend authorization/schema tests, add full-history secret scanning, verify resolved dependency advisories and pin remaining CI action tags to reviewed commit SHAs. No runtime authorization or sandbox implementation is provided by this patch.
