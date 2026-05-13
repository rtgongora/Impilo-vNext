# `ui/experience` workspace (deprecated)

The **Impilo web experience** — the single actor-facing orchestration layer — is built and
run from **`ui/one-ui-shell`** (port **3000** in Docker and local dev). There is no separate
“shell product” vs “experience product”; this folder is a legacy workspace only.

This package is retained temporarily for:

- Paths and tooling that still resolve from this directory during migration
- Incremental updates until all imports and CI targets point at `one-ui-shell`

All new work belongs in **`ui/one-ui-shell`**. Runtime and compose use the **`one-ui-shell`**
service/image name (not the retired `experience-ui` **service** name). Keycloak may still
use the **`experience-ui` OIDC *client id*** for that same web layer — that is identity
wiring, not a second UX stack.

Retirement of this folder is tracked as **RR-04** in
[`docs/retirement/retirement-readiness-ledger.md`](../../docs/retirement/retirement-readiness-ledger.md);
the telemetry signals required to satisfy the retirement criteria are defined in
[`docs/retirement/telemetry-signals.md`](../../docs/retirement/telemetry-signals.md).
