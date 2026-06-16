# Preview Role Seeding & Bootstrap Plan

> **Status:** DRAFT for Product Owner review. Documents only — no seed/SQL/Keycloak/cluster
> changes are authorized by this document. Implementation is held until approval.
>
> Companion to
> [`docs/architecture/SESSION_EXPERIENCE_AUTHORITY_AND_REMEDIATION.md`](../architecture/SESSION_EXPERIENCE_AUTHORITY_AND_REMEDIATION.md)
> (Deliverable 3). Audit evidence (2026-06-16, `impilo-full-preview`, HEAD `c19fefd1`) is the
> basis for the "current state" below.

The goal: **one representative login per canonical role**, each producing the correct shell
purely from the seeded identity → governance chain.

## 0. Product Owner directive (2026-06-16) — read first

- **`superadmin` IS the Product Owner.** There is one identity, not a "superadmin" plus a
  separate "PO." The Keycloak `superadmin` account (`b0000000-…-010`,
  `superadmin@impilo.gov.zw`) is the PO's login.
- **Do not bend the product to accommodate the PO.** The `previewProductOwnerAccess`
  email/actor allowlist is **removed**, not retained — it is exactly the kind of product
  modification the PO has ruled out.
- **Instead, seed enough.** The PO/`superadmin` account is given enough **real WGV authority**
  that the normal Session Experience Contract resolution grants the access. The PO reaches
  everything through the product's own governance model, the same path every other user uses.

---

## 1. Current state (audit-verified)

| Layer | Current | Evidence |
|---|---|---|
| Keycloak (preview realm) | **1 human user** (`superadmin`) + service accounts | `realm-impilo-preview.json` |
| WGV organisations | **2** (`MOHCC-NATIONAL` SOVEREIGN_PUBLIC_OWNER, `MOHCC-HCH` PUBLIC_FACILITY) | live `wgv_organisation` count = 2 |
| WGV role definitions | **2** (`CLINICAL_DOCTOR`, `PLATFORM_ADMIN`) | live `wgv_role_definition` count = 2 |
| WGV assignments | **4** (superadmin org+facility, Dr Mapfumo, Nurse Musekwa) | live `wgv_assignment` count = 4 |
| WGV organisation memberships | **0** | live `wgv_organisation_membership` count = 0 |
| Role templates in `contracts/trust/seeds` | **726**, **not materialized** | not present in WGV |
| Governance authority path | **PO email/actor allowlist only** | non-allowlisted admin → citizen-only (see §2) |

### 1.1 The real root cause — tenant scoping (corrected 2026-06-16 after live tracing)

Earlier analysis suspected an ID mismatch. **Live tracing disproved that** — the seed IDs are
already aligned and the chain resolves correctly when the tenant is right:

- VARAPI `GET /v1/internal/providers/by-health-id/b0000000-…-010` (with tenant
  `00000000-0000-4000-8000-000000000001`) returns the provider with
  `providerPublicId = PROV-ZW-ADMIN-001`, status `ACTIVE`.
- WGV `assignments/search?subjectType=PROVIDER&subjectId=PROV-ZW-ADMIN-001&status=ACTIVE`
  returns the two seeded ACTIVE assignments.
- **Proof with a non-allowlisted, seeded clinician** (`c0000000-…-001`, no PO override):
  - tenant `00000000-0000-4000-8000-000000000001` → `dual_citizen_provider`, work + professional
    tabs visible, `providerWorkerId=PROV-ZW-00001`, management workspaces
    `[public_facility_staff_management, facility_work_assignment]`, `previewProductOwnerAccess: null`.
  - tenant `default` → `citizen`, work hidden.

**Conclusion:** all sovereign data is seeded under tenant
`00000000-0000-4000-8000-000000000001`. When the client/BFF sends a non-canonical
`X-Tenant-ID` (e.g. `default`), every tenant-scoped lookup (VARAPI, WGV) misses and the user
collapses to **citizen**. This — not an ID mismatch — is why the PO appeared citizen-only.
The PO email allowlist override was a band-aid hiding this single tenant defect.

> The shell `api-client` already defaults `X-Tenant-ID` to the canonical UUID
> (`api-client.ts:209–215`) and `exp:tenant_id` is never written by the app, so a clean
> browser session sends the correct tenant. The override is therefore **redundant** now that
> the admin VARAPI/WGV seed exists (added in `19934609`). Removing it is safe; the PO resolves
> via the same real path the clinician already uses. The seed alignment work below is for
> **multi-context breadth**, not to fix the citizen issue.

### 1.2 ID scheme drift (must be reconciled)

Three provider-ID conventions coexist: `PROV-ZW-001`, `PROV-ZW-00001`, `PROV-DEMO-*`, plus the
admin's `PROV-ZW-ADMIN-001`. The seed, VARAPI, and WGV must agree on one scheme per persona so
the session-contract lookup resolves.

### 1.3 `allowed_target_types` shape bug

`wgv_role_definition.allowed_target_types` is currently seeded as a **CSV string**
(`'FACILITY'`, `'FACILITY,ORGANISATION'` — `09-seed-workforce-governance.sql:78,103`). The
plan requires **JSON arrays** (`["FACILITY","ORGANISATION"]`). Any consumer parsing this as
JSON will fail or mis-scope. Fix during seed remediation.

---

## 2. Target — one representative user per canonical role

For each persona define the full **login → correct-shell chain**:

```
Keycloak user (health_id / actor_id [+ provider_id])
  → VITO client (PII)
  → VARAPI provider (where provider/clinical)
  → WGV organisation (correct organisation_type)
  → WGV role definition
  → WGV ACTIVE assignment (subject_id MUST equal the contract lookup key)
```

### 2.0 The Product Owner (`superadmin`) chain — first priority

The PO/`superadmin` account must resolve real authority with the override gone:

| Step | Required value |
|---|---|
| Keycloak `superadmin` | `health_id = actor_id = b0000000-0000-4000-8000-000000000010` (unchanged) |
| VITO client | person record for `b0000000-…-010` |
| VARAPI provider | provider whose `impilo_health_id = b0000000-…-010`, **`provider_public_id = PROV-ZW-ADMIN-001`**, status `VERIFIED`/`ACTIVE` |
| WGV org | `MOHCC-NATIONAL` (`SOVEREIGN_PUBLIC_OWNER`) — already seeded |
| WGV role def | `PLATFORM_ADMIN` (`requires_provider_flag=true`) — already seeded |
| WGV assignment | `subject_type=PROVIDER`, **`subject_id=PROV-ZW-ADMIN-001`**, org `MOHCC-NATIONAL`, status `ACTIVE` — already seeded |

**The single fix that lights up the PO via the real path:** make the VARAPI provider's
returned public ID for `b0000000-…-010` **exactly equal** the WGV assignment `subject_id`
(`PROV-ZW-ADMIN-001`), and ensure that provider's status is `VERIFIED`/`ACTIVE` so
`isProfessionalEligible` passes. The BFF then walks
`actor_id → VARAPI provider_public_id → WGV searchAssignments(PROVIDER, PROV-ZW-ADMIN-001, ACTIVE)`
and resolves the sovereign-owner Work context with **no allowlist**.

> **Open product decision (see chat):** a `SOVEREIGN_PUBLIC_OWNER` org currently confers only
> 3 management workspaces by default (`national_organisation_registry`,
> `national_trust_console`, `national_platform_user_administration`). "Enough access for the
> PO" is a real design choice — keep the focused sovereign set, broaden the sovereign-owner
> default to the full national governance set, or give the PO **multiple seeded contexts**
> (sovereign + clinical + payer + marketplace …) so they exercise the whole product via the
> product's own context switcher. This is decided before implementation.

### 2.1 Persona matrix (target)

| Persona | actor_type | provider_id | WGV org (type) | role def | Expected shell |
|---|---|---|---|---|---|
| System admin | SYSTEM | — | MOHCC-NATIONAL (SOVEREIGN_PUBLIC_OWNER) | PLATFORM_ADMIN | Work + A&G (national_* workspaces) |
| Facility admin | STAFF | — | MOHCC-HCH (PUBLIC_FACILITY) | FACILITY_ADMIN* | Work + facility user mgmt |
| Doctor | PROVIDER | yes | MOHCC-HCH | CLINICAL_DOCTOR | Personal + Professional + Work (clinical) |
| Nurse | PROVIDER | yes | MOHCC-HCH | CLINICAL_NURSE* | Professional + Work (clinical) |
| Pharmacist | PROVIDER | yes | pharmacy org* | CLINICAL_PHARMACIST* | Work (pharmacy) |
| Lab | PROVIDER | yes | lab org* | LAB_SCIENTIST* | Work (lab) |
| Regulator / council | STAFF | — | council org* (REGULATOR) | COUNCIL_ADMIN* | Work (regulator user mgmt) |
| Payer | STAFF | — | payer org* (PAYER) | PAYER_ADMIN* | Work (payer mgmt) |
| Marketplace vendor | MARKETPLACE_ACTOR | — | vendor org* (MARKETPLACE) | MARKETPLACE_ADMIN* | Work (marketplace) |
| HSC officer | STAFF | — | HSC org* | HSC_OFFICER* | Work (HSC establishment) |
| Blood service (MADI) | STAFF | — | blood org* | BLOOD_SERVICE_ADMIN* | Work (blood service) |
| Public health officer | STAFF | — | public-health org* | PH_OFFICER* | Work (surveillance) |
| Finance | STAFF | — | MOHCC-NATIONAL | FINANCE_ADMIN* | Work (finance) |
| Citizen | CITIZEN | — | — | — | Personal only |
| Caregiver | CITIZEN | — | — | — | Personal + delegated |

`*` = role definition / organisation that **does not yet exist** and must be seeded.

### 2.2 Per-persona acceptance (drives the Deliverable 4 harness)

Each persona's contract must assert exact `identityType`, visible tabs, `defaultRoute`,
`visibleManagementWorkspaces`, and `visibleActions` — **with the PO override OFF**.

---

## 3. Concrete seed work (held until approval)

1. **Extend** `scripts/seed/09-seed-workforce-governance.sql` (or add `10-seed-personas.sql`):
   - Add the missing organisations (regulator/council, payer, marketplace, HSC, blood, lab,
     pharmacy, public-health) with correct `organisation_type`.
   - Add the missing role definitions (one per persona row marked `*`).
   - Add **ACTIVE** assignments whose `subject_id` equals the **contract lookup key** for each
     persona (resolve §1.1 — likely `actor_id`/`health_id`, or add an
     `wgv_organisation_membership` row keyed to it).
   - Fix `allowed_target_types` to **JSON arrays**.
2. **VITO/VARAPI:** add provider Health IDs where dual (person + provider) login is needed;
   reconcile the ID scheme (§1.2) end-to-end.
3. **Keycloak:** expand `realm-impilo-preview.json` (or a post-deploy import) to **~15–25
   single-role users**, one per persona, each with `health_id`/`actor_id` (+`provider_id`
   where clinical) matching the VITO/VARAPI/WGV chain.
4. **Align IDs:** pick one provider-ID convention and apply it across seed + VARAPI + WGV.

---

## 4. Idempotency + correctness fixes (`seed-full-preview-sovereign-data.sh`)

- The skip-queries for **tshepo / zibo / pct / oros** check schema-qualified tables
  (`tshepo.policy_rule`, `zibo.code_system`, `pct.encounter`, `oros.service_request`,
  lines 56–59). Confirm each table actually exists in its DB and is the right idempotency
  signal; the plan notes these "check the wrong tables and re-run every time." Validate and
  correct so re-runs are true no-ops.
- WGV skip-query (line 60) keys on `MOHCC-NATIONAL` presence — keep, and extend the skip
  signal as new personas are added so partial seeds still converge.

---

## 5. Remove the PO override (not "toggle off")

- Per the §0 directive, the override is **deleted**, not toggled. Once the PO/`superadmin`
  account is seeded with real WGV authority (§2.0), remove the allowlist code path and the
  env flags (`IMPILO_PREVIEW_PRODUCT_OWNER_ACCESS`, `IMPILO_PREVIEW_PRODUCT_OWNER_ALLOWED_ACTORS`).
- Acceptance: the PO logs in and resolves full authority from WGV alone, with no allowlist
  present anywhere. This satisfies doctrine gate **K-1** (no PO short-circuit).

---

## 6. Acceptance criteria for this plan

- [x] Current state enumerated with audit evidence (§1).
- [x] ID-alignment root cause identified with concrete key values (§1.1).
- [x] One representative user per canonical role defined with the full chain (§2).
- [x] Exact seed work enumerated: missing orgs/role-defs/assignments, JSON-array fix, Keycloak
      expansion, ID alignment (§3).
- [x] Idempotency/correctness fixes specified (§4).
- [x] PO-override-off testing toggle defined (§5, gate K-1).
