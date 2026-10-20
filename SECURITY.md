# Security policy

VOID AI is an experimental phase-0 set of contracts. It is not a production assistant, server or security boundary. No version is approved for production use.

Do not open public issues containing credentials, personal data or exploit details. Use GitHub private vulnerability reporting if enabled; otherwise contact the maintainer through a verified private channel. Never include real secrets in reports or logs.

## Mandatory implementation requirements

- Derive identity and permissions from validated server-side authentication. ToolCall caller/permissions and Task userId are untrusted payload data, not authorization grants.
- Validate token signature, allowed algorithms, issuer, audience and expiry. Enforce ownership/tenant checks for every task, plan and audit lookup.
- Deny tool execution by default. Check permissions before any side effect, and require explicit approval bound to the exact tool, normalized inputs, authenticated user and expiry for sensitive actions. Prevent replay.
- Classify every tool explicitly. Current LOW/idempotent defaults must not be interpreted as evidence of safety.
- Enforce payload size/depth limits, per-tool input schemas, bounded retries/timeouts, budgets and rate limits.
- Isolate tools; restrict network, filesystem and process access. Treat model output and external content as untrusted.
- Redact credentials and personal data before audit persistence. Enforce append-only access, ownership checks and retention.

These requirements are NOT implemented security controls. See docs/SECURITY_REVIEW.md for current limitations.
