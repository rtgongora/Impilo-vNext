# Organization Registry Adoption — Wave-1 Dual-SoR-with-Mirror Strategy

**Status**: Wave-1 (IATG). **Service**: `organization-registry-service` (registry plane, port **8153**).

## Problem

Impilo has two organization-shaped truths today:

1. `workforce-governance-service` owns `wgv_organisation` — organisations that anchor HSC
   employment, jurisdictions, facility-regulator relationships, and governance assignments.
2. There is no canonical registry for **new** organizations entering the platform through
   partner/NGO/private onboarding, nor for **Channel-C delegated onboarding claims**
   (an organization's authorized representative asserting that a person holds a role in
   that organization).

Creating a second write-path into `wgv_organisation` would violate the single-SoR rule and
couple partner onboarding to workforce-governance internals. Rebuilding governance links on
day one would be a high-risk big-bang cutover.

## Wave-1 decision: dual SoR with a one-way mirror

| Concern | System of record (Wave-1) |
|---|---|
| **NEW organizations** (partner/NGO/private/insurer/etc. onboarding) | `organization-registry-service` (`source = NATIVE`) |
| **Authorized representatives** of organizations | `organization-registry-service` |
| **Channel-C delegated onboarding claims** | `organization-registry-service` |
| **Existing governance organisations** and everything hanging off them (HSC employment, jurisdictions, governance assignments) | `workforce-governance-service` (`wgv_organisation`) — unchanged |
| Facility registry | `tuso-service` — unchanged (org-registry holds affiliation links only) |
| Provider professional registry | `varapi-service` — unchanged (affiliation links only) |

### The mirror

- Endpoint: `POST /v1/internal/org-registry/mirror/wgv` on organization-registry-service,
  accepting `wgv_organisation`-shaped payloads
  (`{id, code, legalName, organisationType, status, parentOrganisationId}`).
- Mirror rows are stored with `source = WGV_MIRROR` and `source_ref = wgv_organisation.id`
  (**back-pointer** to the origin record). Native rows use `source = NATIVE`.
- **Idempotent on `source_ref`**: posting the same wgv organisation twice upserts the single
  existing mirror row (enforced by a partial unique index
  `uq_org_registry_org_wgv_source_ref ON (source_ref) WHERE source = 'WGV_MIRROR'`).
- **One-way**: data flows wgv → org-registry only, via this endpoint. The org-registry never
  writes to workforce-governance code or schema, and edits to mirror rows in org-registry are
  not supported write-paths — the next mirror push overwrites them.
- Raw wgv attributes (`wgv_org_type_raw`, `wgv_status_raw`, `wgv_parent_source_ref`,
  `mirrored_at`) are carried alongside the mapped canonical fields so nothing is lost in the
  `organisationType → OrgType` mapping (unknown types map to `OTHER`).
- Mirror rows carry `verification_status = VERIFIED`: wgv organisations are already
  governance-managed, so the mirror carries that trust rather than re-running Channel-A/B
  verification.

### Why this shape

- Consumers (experience shell, BFF, downstream registries) get **one read surface** for
  "all organizations" immediately, without touching governance truth.
- New onboarding (Channel-C included) lands in its final home on day one — no future
  migration for NATIVE rows.
- The `source_ref` back-pointer makes the eventual cutover a metadata flip, not a data
  migration.

## Channel-C delegated onboarding claims

Claims live in `org_registry_claim_submission`:
`SUBMITTED → UNDER_REVIEW → ACCEPTED | REJECTED` (SUBMITTED may be rejected directly;
ACCEPTED/REJECTED are terminal). Claims may only be submitted by a **VERIFIED** authorized
representative of the organization (`trust_basis = ORG_DELEGATED`), and an accepted claim
records an `adjudication_ref` pointing at the downstream adjudication artefact. Organization
verification itself is a national-admin action that records the verifier and requires at
least one VERIFIED authorized representative.

## Wave-2 cutover criteria

`wgv_organisation` is retired as an organization SoR (becoming a consumer of org-registry)
only when **all** of the following hold:

1. **Mirror completeness**: every active `wgv_organisation` row has a `WGV_MIRROR` row in
   org-registry, reconciled (code/legalName/status) with zero drift over an agreed soak
   window.
2. **Reference migration**: workforce-governance internal FKs to `wgv_organisation` are
   re-pointed (or dual-keyed) to org-registry organization ids, using the `source_ref`
   back-pointer as the join key.
3. **Write-path freeze**: all creators of `wgv_organisation` rows (bootstrap imports,
   onboarding flows) write to org-registry instead, and the mirror endpoint is disabled.
4. **Event parity**: downstream consumers of governance organisation events consume
   `impilo.org-registry.*` events instead.
5. **Sign-off**: registry-plane ownership review confirms `docs/registry/services-registry.yaml`
   and `docs/registry/system-of-record-map.md` are updated to make org-registry the single
   organization SoR.

Until then, both records coexist deliberately, with `source`/`source_ref` making provenance
unambiguous.

## Wiring (Wave-1)

- Service: `services/organization-registry-service` — port 8153, DB `impilo_org_registry`,
  schema `org_registry`, outbox → Kafka topic `impilo.org-registry.events` (mark-published +
  log in no-Kafka contexts).
- BFF: `OrganizationRegistryServiceClient` + `OrganizationRegistryController`
  (`/internal/v1/organizations`, fail-closed 502 — governance actions are never stub-served).
- Registries: `docs/registry/services-registry.yaml`, `docs/registry/system-of-record-map.md`,
  `docs/runbooks/port-allocation.md` (8153).
