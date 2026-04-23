# Provider–council regulatory lifecycle (Varapi)

This document describes the **council-regulated provider** slice added to Varapi, MusheX, the Experience BFF, and the Experience UI. It is a **foundation** for multi-council variation: shared tables and workflow hooks, with council-specific rules intended to layer via configuration and Tshepo policy.

## Data model (Varapi / Postgres)

Flyway: `V007__provider_council_regulation.sql` (after `V006__provider_biometric_governance.sql`).

- **`provider_applications`**: `council_id`, `review_state` — links lifecycle applications to a council and captures lightweight review state.
- **`provider_council_profiles`**: operational provider↔council profile (category, class, standing flags, metadata JSON).
- **`provider_payment_obligations`**: fee/penalty/restoration obligations with **`mushex_intent_id`** and idempotency key — **MusheX remains payment truth**.
- **`fundo_learning_links`**: Impilo Fundo (Moodle) user anchor per provider.
- **`provider_council_reviews`**: append-only council staff review rows.
- **`fundo_cpd_candidates`**: Moodle/Fundo completion → **candidate** CPD row; `verification_state` governs acceptance.

## Workflows (Varapi)

- **Intake**: `SUBMITTED` → `UNDER_ADMIN_REVIEW` via `advanceToUnderAdminReview`.
- **Awaiting fee**: eligible states → `AWAITING_PAYMENT` via `advanceToAwaitingPayment` (`fee_state=UNPAID`).
- **After MusheX settlement**: `AWAITING_PAYMENT` → `READY_FOR_REVIEW` with `fee_state=PAID` via `advanceAfterFeePayment` (also invoked when obligations sync to **PAID**).

Workflow transitions were extended in `ProviderApplicationService.validateTransition` to include **`AWAITING_PAYMENT`**.

## MusheX integration

- New `SourceType.PROVIDER_COUNCIL_FEE` in **mushex-service**.
- Varapi **`MusheXClient`** POSTs `/mushex/v1/payment-intents` with metadata containing **`provider_id`** (public provider id string) for payee verification, plus `obligation_id` / `application_id`.
- Enable with `varapi.mushex.enabled=true` and `varapi.mushex.base-url`.
- **`ProviderPaymentObligationService`**: create obligation, attach/create intent, **`syncFromMusheX`** maps intent status to obligation and may advance the linked application.

## Impilo Fundo (Moodle) integration

- Webhook: **`POST /v1/webhooks/fundo/cpd-completion`** with header **`X-Fundo-Signature`** = hex(HMAC-SHA256(secret, raw body)).
- Configure `varapi.fundo.webhook-shared-secret` (`FUNDO_WEBHOOK_SECRET`). Disable via `varapi.fundo.webhook-enabled=false`.
- Payload fields: `tenantId`, `providerPublicId`, optional `councilId`, `courseId`, optional `courseName`, `completedAt` (ISO-8601), optional `creditsSuggested`, `externalRef` (unique per provider).
- Rows land in **`fundo_cpd_candidates`** with `verification_state=PENDING`. Council (or policy) accepts via internal API → **`CpdService.recordEvent`** on the latest **`IN_PROGRESS`** cycle.
- Optional dev auto-accept: `varapi.fundo.auto-accept-cpd-from-fundo=true` (not for production councils).

## Tshepo / policy gate

- **`CouncilRegulatoryPolicyClient`** (optional): when `varapi.council-regulatory.policy-enabled=true`, POSTs to `${TSHEPO_POLICY_BASE_URL}/v1/council-regulatory/evaluate` with workflow action and resource ids. On failure or missing endpoint, the client **allows** the action but logs a warning (bootstrap-friendly; tighten for production).

## HTTP surfaces

### Varapi (internal)

Base path: `/v1/internal/provider-council`

- Profiles, obligations (+ MusheX intent + sync), council queues, reviews, Fundo links, Fundo CPD candidate accept/reject, application fee workflow helpers.

### Experience BFF

Under `/internal/v1/registry/provider-council/...` — see `RegistryController`.

### Experience UI

- `/registry/provider-council/self-service?providerId=<internal id>`
- `/registry/provider-council/council-workspace?councilId=<id>&workflowStates=...`

## Manual testing (dev)

1. Apply migrations; seed a council and provider in Varapi.
2. Create application with `councilId`; submit; move to `UNDER_ADMIN_REVIEW` then `AWAITING_PAYMENT`.
3. Create obligation + `POST .../mushex-intent` (MusheX reachable, credentials pass).
4. Simulate payment in MusheX; `POST .../sync-payment` on obligation — expect obligation **SETTLED** and application **READY_FOR_REVIEW** when linked.
5. POST Fundo webhook with valid HMAC — expect candidate row; accept via internal API and verify CPD event.

## Known gaps / next wave

- Full **registration / licence / CPD profile** entities from the strategic spec (beyond obligations, profiles, candidates).
- **Kafka / outbox** consumers to react to MusheX payment events instead of pull sync.
- Harden **Tshepo** endpoint contract and deny-by-default policy behaviour.
- **Renewal** automation tying licence expiry → obligation templates per council config.
- Deeper **Experience** UX (forms, not JSON debug views).
