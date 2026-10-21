# Contract hardening scope

This change establishes a small, testable serialization boundary for the Kotlin core. `VoidObjectMapper` serializes public models with snake_case properties, lowercase enums, ISO-8601 Java time values and omits nullable optional fields. It rejects unknown fields while deserializing contracts.

The JSON schemas are Draft-07 documents with stable `https://void.invalid/schemas/*.json` identifiers. `plan.json` retains the local `step.json` reference; CI resolves it only through an in-memory registry, so schema verification does not make network requests. `Task.created_at` and `ToolDescriptor.name`/`version` are now required. `Step` rejects negative retries and non-positive timeouts in both schema and Kotlin initialization; no upper limits were added because this phase has no runner policy from which to justify them.

## Deliberately out of scope

This does not implement a runner, authorization, approvals, Android persistence, or Android UI. Future Android work must enforce authenticated identity and server-side authorization rather than trust request `user_id`, `caller` or `permissions`; gate tools before execution; bound payloads/retries/timeouts at the runner policy layer; and redact secrets/PII before audit persistence. See `docs/SECURITY_REVIEW.md` for the existing design-risk inventory.

## Policy gate v1

`PolicyGate` is an evaluation boundary, not a runner. `DenyByDefaultPolicyGate` returns a denial for every request and appends a `policy_denied` audit record with the requested action, decision reasons and policy version. It intentionally does not include request arguments or destination in audit data, and it has no dependency on `ToolRunner`. A future allow path requires authenticated identity, a bound approval, action arguments, destination, expiry and dedicated tests before any tool can be invoked.
