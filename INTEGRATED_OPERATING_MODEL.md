## Integrated operating model (vNext)

This document captures the “legitimacy fabric” and cross-service contracts that keep sovereign services coherent while preserving bounded contexts.

### Core ownership (truth + write authority)

- **Tuso (Facility Registry + Facility Regulatory Ops)**: canonical truth for facility identity (numeric `facilityId`), operational/regulatory status, certificates, inspections, and the facility-scoped view of *mirrored* PIC assignments.
- **Indawo (Public Health Site Registry + Regulatory Ops)**: canonical truth for non-facility sites of public health concern (`siteId` UUID), site regulatory lifecycle, inspections, compliance actions, documents.
- **Varapi (Provider Registry + Standing/Licensure)**: canonical truth for provider identity/standing/licensure; source-of-truth for PIC assignment lifecycle events.
- **Vito (Client Identity + Registry)**: canonical truth for client identity proofing state (`healthId` UUID), verification status, and assurance level.
- **Msika Core (Catalog + Offering policy)**: canonical truth for catalog items and restriction policy (e.g. `controlled_item`, `facility_only`).
- **Msika Flow (Order orchestration)**: canonical truth for order lifecycle, fulfillment routing, custody/handoffs, and payments/settlement orchestration.
- **MusheX (Wallet/Payments)**: canonical truth for payment intents and payment state transitions.
- **Tshepo (ext_authz)**: canonical truth for authorization decisions (roles, scopes, ABAC policy).
- **Workforce Governance**: canonical truth for **organisations**, **organisational units**, **facility/site ↔ organisation links**, **jurisdictions & jurisdiction links**, **role definitions**, **assignments** (with history), and **facility scope evaluation** consumed by Tshepo when `x-tuso-facility-id` is present; Varapi may read assignment summaries; Experience BFF exposes `/internal/v1/workforce-governance/*` for UI.
- **Experience BFF**: orchestration façade; does not own truth, but may cache/mirror to support UX.

### Cross-service reference conventions

- Prefer explicit string references when crossing service boundaries to avoid ID-type mismatch:
  - `facilityRef`: `tuso:{facilityId}`
  - `providerRef`: `varapi:{providerPublicId}`
  - `siteRef`: `indawo:{siteId}`
  - `clientRef`: `vito:{healthId}`

### Canonical “summary” read contracts (gating-safe)

These endpoints exist to support fast legitimacy checks without forcing full-profile coupling.

- **Tuso**: `GET /v1/internal/facilities/{id}/status-summary`
- **Indawo**: `GET /internal/v1/sites/{site_id}/status-summary`
- **Varapi**: `GET /v1/internal/providers/{providerPublicId}/standing-summary`
- **Vito**: `GET /v1/client-registry/clients/{healthId}/identity-summary`
- **Msika Flow**: `GET /v1/orders/{id}/status-summary`

### PIC legitimacy flow (Varapi → Tuso mirror)

- **Varapi publishes** PIC lifecycle events on canonical topic `impilo.varapi.pic_assignment`.
- **Tuso consumes** the topic and idempotently mirrors state into `tuso.practitioner_in_charge_assignment` using `external_assignment_id`.
- **Experience surfaces** mirrored PIC state via facility regulatory profile plus status summary.

### Regulated commerce gating (Msika Flow)

When validating/placing orders, Msika Flow enforces restriction policy by consulting:

- **Catalog restriction flags** from Msika Core (`prescription_required`, `controlled_item`, `facility_only`, `provider_only_order`)
- **Facility legitimacy** from Tuso status summary when item requires facility context
- **Provider standing/licensure** from Varapi standing summary when provider authorization is required
- **Client identity assurance** from Vito identity summary for regulated/controlled patient orders

### Event topics: canonical + aliasing

- Canonical v1.1 topic format: `impilo.{service}.{domain}`
- Legacy topics remain in-flight; consumers should listen to both canonical + legacy until deprecation.

### Assumptions / current scope

- Where full v1.1 envelope emit is not yet universal, topic aliasing is used as a safe bridge.
- Experience BFF forwards upstream JSON for orchestration endpoints; typed contracts are added only where stability is required (summaries and gating).

