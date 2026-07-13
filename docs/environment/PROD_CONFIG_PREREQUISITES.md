# Production Config Prerequisites (R10 / G32 + G35)

**Purpose:** the environment variables / secrets / seed-data that must be set at deploy time for
functionality that is **code-complete but config-gated** — features that run in a safe, honest
degraded mode locally (record-only, `SKIPPED_*`, `501`, stub) until real credentials/config exist.
This is the config half of the pre-deploy gate; pair it with the
[`no-stale-fullboot-preview-deploy-checklist`](../../docs/runbooks/) recipe.

None of these are code bugs — each default is an intentional fail-safe. Setting them is an operator
action (secrets/agreements/seed data), which is why the R2–R10 remediation run could not do them
in-sandbox.

## 1. Fundo (learning-service) notification delivery — G32

Default is a **STUB** that records delivery intent without contacting any channel (status `RECORDED`,
never `SENT`). To deliver via the platform comms hub set, on learning-service:

| Env var | Prod value | Default |
|---|---|---|
| `LEARNING_NOTIFICATION_PROVIDER` | `NOTIFICATION_SERVICE` | `STUB` |
| `LEARNING_NOTIFICATION_BASE_URL` | `http://notification-service:8200` | *(empty)* |
| `LEARNING_NOTIFICATION_DISPATCH_ENABLED` | `true` (default) | `true` |

Without these Fundo learning/CPD notifications are recorded but never sent.

## 2. Impilo Live / teleconsult media (rtc-gateway-service) — G35

Real LiveKit media + recording egress are gated off by default (dev has no LiveKit):

| Env var | Prod value | Default |
|---|---|---|
| `LIVEKIT_ENABLED` | `true` | `false` |
| `LIVEKIT_URL` / `LIVEKIT_CLIENT_URL` | the self-hosted LiveKit URLs | *(empty)* |
| `LIVEKIT_API_KEY` / `LIVEKIT_API_SECRET` | real LiveKit credentials (secret) | *(empty)* |
| `RTC_EGRESS_ENABLED` | `true` (to record sessions) | `false` |
| `RTC_RECORDING_S3_*` | recording object-store creds (secret) | *(empty)* |

Without LiveKit creds, teleconsult/live sessions fall back to the honest "media unavailable" path.

## 3. Khuluma external delivery — G31 (R4) + G35

Khuluma delegates external channels (SMS/WhatsApp/EMAIL/USSD) to notification-service (R4/G31).
Two config prerequisites:
- notification-service must have **provider credentials** for each live channel (SMS gateway, WhatsApp
  BSP, SMTP, USSD aggregator).
- the **canonical khuluma delivery templateKeys** must be registered/seeded in notification-service
  (it is template-driven). Until seeded, a template-less external dispatch records
  `SKIPPED_NO_TEMPLATE` (honest — no fabricated send). IN_APP delivery is unaffected.

## 4. Money rails external adapters — (money-stack, prior wave)

Vendor payouts / collection cash-in / Paynow aggregator are code-ready but need per-provider
**credentials + agreements** (one adapter each). Until then they stay in the tech-ready/sandbox path.
See the money-stack state note.

## 5. Mobile production signing / delivery — G24 (R9)

- Android: real **upload keystore** + Play **service-account JSON** (replace the `submit.production`
  placeholder `serviceAccountKeyPath` in both apps' `eas.json`).
- iOS: real `ascAppId` / `appleTeamId` (replace the `IMPILO_TEAM` placeholder).
- Release builds are otherwise debug-keystore-signed (not distributable).

## 6. Estate seed / data prerequisites (not env vars)

- **AHFOZ tariff schedule** — preview seeds AHFOZ-indicative placeholders (costa V020); the real
  schedule is a governed tariff import.
- **Coverage subsidy model reconciliation** (G3) and **indawo geo-table retirement** (G4) are
  architecture decisions logged in the R2–R10 execution report's *Deferred for operator* section.

---

*Generated during the R2–R10 functional-depth remediation run. The authoritative running log of every
config/architecture item deferred to the operator is
[`docs/audits/r2-r10-autonomous-execution-report.md`](../audits/r2-r10-autonomous-execution-report.md)
§ Deferred for operator.*
