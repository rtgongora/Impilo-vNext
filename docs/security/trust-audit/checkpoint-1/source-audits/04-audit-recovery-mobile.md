# Source audit — Audit chain, recovery, break-glass, mobile auth

Branch `claude/tshepo-trust-cp1-truth-audit`, commit `f190318e1`.
Source: [Audit audit-chain, recovery, mobile auth](8c0a0528-ee54-4878-abc7-4d39425ebc7f).
Classification against **intended production design**.

## Headline

Hash-chained audit ledger is ENFORCED with a legitimate legacy-timestamp recovery. Keycloak event ingestion is ACTIVE (flag-gated; preview claims 352 events intact).

**Confirmed programme defect — recovery codes grant full AAL2:** `auth-recovery-authn-code-form` is a co-equal ALTERNATIVE in the AAL2 methods subflow, so password + recovery code yields `acr=urn:impilo:aal2`. The PDP grants ordinary AAL2 authority (including break-glass step-up) with no default AMR exclusion. This directly violates Checkpoint 4 of the combined programme ("recovery authentication yields a restricted recovery state").

Two-person lost-device recovery is ENFORCED and does **not** mint a session. Envoy fail-closed config is correct in source (but the filter is not on the live path — see other audits). Mobile PKCE + SecureStore is ENFORCED; residual password-grant paths remain on citizen sign-up and web ROPC code/realm flags.

## Classification

| Capability | Classification |
|---|---|
| Hash-chained ledger append/verify | **ENFORCED** |
| Legacy-timestamp precision recovery | **ENFORCED** |
| Kafka audit ingest (manual-ack) | **ENFORCED** |
| Keycloak event ingestion | **ACTIVE_NOT_ENFORCED** (flag-gated; preview evidence claims on) |
| PDP decision/audit pair | **ENFORCED** |
| BFF audit ingest | **PARTIAL** (best-effort, swallowed) |
| PCT audit consumer | **DISCONNECTED** |
| correlation_id propagation | **PARTIAL** |
| decision_id / W3C trace_id | **ABSENT** |
| Two-person lost-device recovery | **ENFORCED** |
| Recovery-code = full AAL2 authority | **BYPASSABLE** |
| Break-glass PDP guard | **ENFORCED** (reachable via recovery AAL2) |
| Break-glass in FHIR gateway | **ABSENT** |
| Envoy ext_authz fail-closed (source) | **ENFORCED** (config); live path **DISCONNECTED** |
| Offline provider credential fail-closed | **ENFORCED** |
| Work-context degraded fail-open | **PARTIAL** (SHADOW) |
| Mobile PKCE + nonce/state replay | **ENFORCED** |
| Mobile SecureStore | **ENFORCED** |
| Mobile residual password grant (sign-up) | **PARTIAL** |
| Web ROPC + realm `directAccessGrantsEnabled` | **PARTIAL** |

## Programme mapping

Checkpoint 4 ("Correct the recovery-code policy") is **not done** — this audit is the evidence that it must be corrected before workforce MFA enforcement. Two-person lost-device recovery is already aligned with the programme and should be preserved.

## Runtime confirmations (2026-08-01)

| Question | Answer |
|---|---|
| Keycloak events enabled on audit pod? | **Yes** — `IMPILO_KEYCLOAK_EVENTS_ENABLED=true`, client `impilo-event-reader` (MFA cohort capture) |
| Work-context mode | **SHADOW** |
| Live Envoy fail-closed | N/A — **ext_authz filter not present** in deployed ConfigMap |
| Recovery-code AMR / accepted_amr in DB | Still open (needs token decode + policy_rules dump) |
| Live `directAccessGrantsEnabled` on experience-ui | Still open (needs Keycloak admin API) |
