# Security review: 2026-10-22

Scope: manual inspection of current public source/contracts, schemas, OpenAPI and build configuration. No production deployment, penetration test, complete Git-history secret scan or validated dependency scan was performed. GitHub secret-scanning tool could not run because the required repository feature was unavailable. Absence of detected secrets is not certification.

## Fixed

- Removed a malformed duplicate schema filename that prevented normal checkout (commit 90155e33f3b649cac997a47279a6118fde860683).
- Replaced duplicate ToolDescriptor declarations in AuditLog.kt with the intended AuditLog model.
- Added small model tests and a least-privilege CI build on JDK 17 / Gradle 8.14.4. Actual execution results must be checked in Actions.
- Added dependency-update configuration for Gradle and GitHub Actions.

## Open design risks

1. High, conditional on future implementation: `caller`, `userId` e permissões em requisições são controlados pelo cliente. Nunca usá-los para autenticação ou autorização. `ActionProposalService` exige a abstração `VerifiedPrincipal`, mas a autenticação concreta continua fora do núcleo.
2. High, reduzido apenas para simulações: `GuardedSimulationRunner` reavalia política e exige autorização vinculada antes de `SimulatedTool`. `ActionProposalService` separa proposta, decisão humana e emissão transacional por interface. Ainda não há identidade autenticada, armazenamento de produção, revogação persistida, conectores ou autorização para efeitos externos.
3. Medium, reduzido: entradas e destinos do runtime simulado têm limites de profundidade, tamanho, tipos e números finitos antes de hash, política ou ferramenta. Ainda faltam limites persistentes de tentativas, timeout, orçamento e concorrência.
4. Medium: tool risk/idempotency defaults are optimistic and must not authorize execution.
5. Medium: arbitrary evidence/outcome objects may leak credentials/PII unless redacted before persistence.
6. Dependency versions require a complete advisory scan and compatible upgrades; no claim of vulnerability-free dependencies is made.
7. Kotlin camelCase models and snake_case schemas require explicit serialization configuration and compatibility tests before API implementation.

## Remaining validation

Run the CI build, extend authorization/schema tests, add full-history secret scanning, verify resolved dependency advisories and pin remaining CI action tags to reviewed commit SHAs. No runtime authorization or sandbox implementation is provided by this patch.
