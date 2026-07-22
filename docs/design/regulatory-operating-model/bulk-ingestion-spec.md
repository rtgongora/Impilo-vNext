# ROM Bulk Onboarding & Ingestion Spec

How each of the nine regulatory organisations imports the data it already holds — current staff,
currently registered/licensed people with their conditions of practice, licensed facilities, and
brand assets — into the ROM substrate **without an import ever conferring authority**. Every ingest
follows the same four-beat rail already proven by the TUSO facility importer:

> **STAGE → MATCH → human REVIEW → APPLY.** vNext never blindly trusts an upload, and an imported
> record **grants no authority, access, employment, scope, or standing until a human approves** — the
> same law as the HPA practitioner-in-charge import
> ([`varapi.hpa_practitioner_candidate`](../../../services/varapi-service/src/main/resources/db/migration/V027__hpa_practitioner_candidate.sql),
> `approval_state=PENDING`, `authority_grant=NONE`).

Ownership per [`ownership-rulings.md`](ownership-rulings.md); entities per
[`generic-model-spec.md`](generic-model-spec.md); waves per [`build-waves.md`](build-waves.md);
migration ledger in [`README.md`](README.md).

---

## 1. The reference rail (what we mirror, verbatim)

The gold-standard is the TUSO facility importer. It is not reinvented — it is the template.

| Concern | Reference implementation | Path |
|---|---|---|
| Run/batch header (dry-run, counts, quality report, initiated_by, status) | `tuso.facility_import_run` | [`V011__facility_master_staging.sql`](../../../services/tuso-service/src/main/resources/db/migration/V011__facility_master_staging.sql) |
| Per-row staging (raw_values JSONB, canonical fields, outcome, dup metadata, matched/result refs) | `tuso.facility_import_row` | [`V014__facility_import_row.sql`](../../../services/tuso-service/src/main/resources/db/migration/V014__facility_import_row.sql) |
| Review overlay (decision_status, reviewed_by/at, review_reason, conflict_reason, append-only `review_history` JSONB) | `V015` ALTER on `facility_import_row` | [`V015__facility_import_row_review.sql`](../../../services/tuso-service/src/main/resources/db/migration/V015__facility_import_row_review.sql) |
| Match + outcome vocabulary, dry-run-first, idempotent re-run, `codeConflict`, per-row review mutations, `approveRow`/`approveAll`/`applyApproved` | `FacilityMasterImportService` | [`FacilityMasterImportService.java`](../../../services/tuso-service/src/main/java/zw/gov/mohcc/impilo/tuso/core/FacilityMasterImportService.java) |
| Match strategy (correlation key, code, name-in-batch dedupe) | `FacilityMatchService` | [`FacilityMatchService.java`](../../../services/tuso-service/src/main/java/zw/gov/mohcc/impilo/tuso/core/FacilityMatchService.java) |
| Endpoint surface (`/master-pack`, `/dry-run`, `/runs`, `/runs/{id}/rows`, `/review`, `/duplicates`, per-row `supply-code`/`match-existing`/`resolve-distinct`/`canonical-values`/`approve`, `/approve-all`, `/apply-approved`) | `FacilityMasterImportController` @ `/v1/internal/facilities/import` | [`FacilityMasterImportController.java`](../../../services/tuso-service/src/main/java/zw/gov/mohcc/impilo/tuso/api/controller/FacilityMasterImportController.java) |
| Richer enrichment variant (immutable candidate staging + separate match-decision table + field-level provenance + fingerprinted batch + rollback-by-batch + 12-outcome vocabulary) | `HpaEnrichmentImportService` + `hpa_import_batch`/`hpa_candidate_staging`/`hpa_match_decision` | [`V036__hpa_facility_enrichment.sql`](../../../services/tuso-service/src/main/resources/db/migration/V036__hpa_facility_enrichment.sql) |
| **Grants-no-authority law** (candidate resolves a profile, never authenticates/authorises; materialises only on human approval) | `HpaPractitionerImportService` + `hpa_practitioner_candidate` | [`HpaPractitionerImportService.java`](../../../services/varapi-service/src/main/java/zw/gov/mohcc/impilo/varapi/core/HpaPractitionerImportService.java) |
| Admin review console | `admin/facility-imports/[runId]/review` + `useFacilityImports` | [`review/page.tsx`](../../../ui/one-ui-shell/src/app/admin/facility-imports/%5BrunId%5D/review/page.tsx) |

Two shapes exist in the reference: the **lean shape** (`facility_import_row` — raw + canonical +
review overlay on one row; `V027 hpa_practitioner_candidate`) and the **rich shape**
(`V036` — immutable staging separate from a match-decision table, field-level `facility_field_assertion`
provenance, fingerprinted batch, rollback-by-batch). ROM adopts the **lean shape** for staff and
assets (simple tabular, single owner target) and the **rich shape** where a target already carries
field-level provenance and progressive trust (people/registers), so re-runs never overwrite a
verified value with an imported blank.

---

## 2. Reuse decision — per-service mirror, not a central import service

**Recommendation: each system-of-record owns its own ingest staging tables and its own import
service, mirroring the TUSO rail. There is no central "import-service".**

Rationale, grounded in repo law (CLAUDE.md "do not create duplicate system-of-record functionality",
"extend don't rebuild", "before introducing a new service prove no existing service owns the
capability"):

- The APPLY step writes **authoritative** rows (appointments, register entries, licences, CPD,
  restrictions, facilities). Only the owning SoR may do that — org-registry owns appointments,
  varapi owns registers/people/licences/CPD/conditions, tuso owns facilities, document-service owns
  binary assets. A central importer writing across all four would duplicate SoR write authority and
  break the ownership rulings.
- The match strategy is entity-specific (staff by national-id/email; people by council reg-number;
  facility by code) and needs the SoR's own repositories and uniqueness constraints (cf.
  `codeConflict` reaching `facilityRepository.findByTenantIdAndFacilityCode`). It cannot be resolved
  outside the owning service without leaking that service's data model.
- TUSO's facility rail **already exists** — ROM reuses it as-is, adding nothing. A central service
  would strand or fork it.

What **is** shared is the *contract and shape*, not a runtime:

- **Shared vocabulary (docs, this spec):** the run/row column shape, the outcome vocabulary (§4), the
  review lifecycle (§5), the idempotency + provenance rules (§7), and the ROM-INGEST invariant (§10).
  Each service's migration reproduces the same columns and index set; each import service reproduces
  the same STAGE→MATCH→REVIEW→APPLY methods and the same review-mutation endpoints.
- **Shared UI:** the `admin/facility-imports/[runId]/review` console generalises to
  `admin/regulatory-imports/[target]/[runId]/review` driven by one `useRegulatoryImports` hook,
  because every target exposes the same run/rows/review/approve/apply endpoints.
- **Optional thin helper (nice-to-have, not required):** a header→canonical column-mapping helper and
  a CSV/XLSX byte→`List<Map<String,String>>` parser (§6) can live in `shared-*` since they carry no
  SoR data. If extracted, model it on the existing `TariffUploadFileParser`; if not, each service
  copies that ~120-line parser. Do **not** put any staging table or APPLY logic in shared code.

Per-service ingest surface, all under each service's `/v1/internal/…/import` namespace:

| Target entity | SoR | Ingest service (new, mirrors reference) | Staging tables |
|---|---|---|---|
| Staff → regulatory appointments | organization-registry | `RegulatoryAppointmentImportService` | `org_registry_appointment_import_run` / `_row` |
| Registered/licensed people + registers + licences + CPD + conditions of practice | varapi | `RegisterImportService` (rich shape) | `varapi.register_import_batch` / `register_candidate_staging` / `register_match_decision` |
| Licensed facilities | tuso | **existing** `FacilityMasterImportService` | **existing** `facility_import_run` / `_row` (reused unchanged) |
| Logos / certificates / seals (binary assets) | document-service (bytes) + owning SoR (the object ref) | existing `ObjectController` multipart; ref stored on the org row | none (uses existing `objects` catalogue) |

---

## 3. Common lifecycle (all targets)

```
upload (CSV/XLSX/multipart)
  → column-map (headers → canonical fields, saved template)   §6
  → STAGE      persist run + immutable rows, raw_values verbatim, source_record_key + fingerprint  §7
  → MATCH      evaluate each row → outcome + confidence + match evidence   §4
  → DRY-RUN    same evaluation, writes nothing authoritative (default)   §7
  → REVIEW     human works buckets: enrich / possible-dup / conflict / quarantine   §5
  → APPROVE    per-row or approve-all, with the SoR's uniqueness guards
  → APPLY      write authoritative rows in a NON-AUTHORITY-BEARING state   §8
  → (later)    a registrar/verifier promotes to authority-bearing — a SEPARATE human act
```

The line between APPLY and authority is the whole point: **APPLY makes the record exist; a second,
separately-audited human act makes it confer authority.** APPLY lands appointments
`PENDING_VERIFICATION`, register entries `PROVISIONAL`, conditions of practice `source=IMPORT` /
`verification_state=UNVERIFIED`. None of these are honoured by authz, workspace activation, or public
"good standing" until promoted.

---

## 4. Outcome vocabulary (canonical, all targets)

A superset covering every target; each importer emits the subset that applies. Mirrors the
facility-row outcomes (`IMPORTED`, `EXCLUDED_*`, `REQUIRES_REVIEW`, `FAILED`) and the richer
`hpa_match_decision.decision_outcome` set, normalised to the PO's names.

| Outcome | Meaning | APPLY behaviour |
|---|---|---|
| `NEW` | No existing record matched; a genuinely new subject. | Create in non-authority state. |
| `MATCH_EXISTING_ENRICH` | Confidently matched an existing record; import fills gaps only. | Enrich matched record; never overwrite a verified field (field-level provenance in the rich shape). |
| `POSSIBLE_DUPLICATE_REVIEW` | One or more candidate matches, not confident, OR a duplicate within the batch. | **Blocked** — routed to review; never auto-merged. |
| `CONFLICT` | Matched but a material identity/uniqueness conflict (e.g. reg-number belongs to a different person; appointment role collides; facility code assigned elsewhere). | **Blocked** — must be resolved before approve. |
| `QUARANTINED_BAD_DATA` | Row fails structural validation (missing required key, unparseable, malformed reg-number). | **Never applied, never crashes the run** — quarantined with reason; run continues. |

Confidence (`HIGH`/`MEDIUM`/`LOW`) and `match_evidence` (JSONB: which signals fired) accompany every
non-quarantined row, exactly as `hpa_match_decision`.

---

## 5. Review lifecycle (per row, all targets)

Reuses the `facility_import_row` review overlay verbatim: an immutable `outcome` plus a mutable
`decision_status`, with append-only `review_history` JSONB and `reviewer`/`reason` on every mutation
(cf. `recordReview`). Raw source values are **never** mutated; reviewers only correct canonical
fields.

```
PENDING_REVIEW ──enrich/correct──► CORRECTED_READY_FOR_IMPORT ──approve──► APPROVED_FOR_IMPORT ──apply──► IMPORTED
      │                                                                          ▲
      ├─ match to existing ─► MATCHED_EXISTING ───────────────────────────────────┘
      ├─ resolve as distinct (reason required) ─► RESOLVED_AS_DISTINCT ───────────┘
      ├─ conflict unresolved ─► RESOLUTION_CONFLICT  (approve blocked)
      ├─ reject (terminal) ─► REJECTED
      └─ skip  (terminal) ─► SKIPPED
```

Guards carried over from the reference: `IMPORTED` rows are terminal (further change must go through
the normal domain-update workflow, not the import); `REJECTED`/`SKIPPED` cannot be approved; approve
re-runs the SoR uniqueness check and refuses on conflict; `approve-all` applies exactly the per-row
guards and reports ineligible rows rather than force-approving.

Review endpoints per target mirror the facility set 1:1:
`/v1/internal/{target}/import/runs/{runId}/rows` (filter by outcome/decision_status),
`…/review` (buckets + preview), `…/duplicates`, and per-row `…/rows/{rowId}/{correct|match-existing|resolve-distinct|reject|skip|approve}`,
plus `…/approve-all` and `…/apply-approved`.

---

## 6. Upload formats & column mapping

- **Tabular:** CSV and XLSX. **Reuse the existing dependency** — `com.opencsv:opencsv` +
  `org.apache.poi:poi-ozxml` are already on the classpath of many services (msika, forms, zibo,
  costa, credential-verification, notification, …). The canonical parser to copy is
  [`TariffUploadFileParser.java`](../../../services/costing-engine-service/src/main/java/zw/gov/mohcc/impilo/costa/service/tariff/TariffUploadFileParser.java)
  (`parse(fileType, bytes, mapper)` → `List<Map<String,String>>`, header-keyed, trimmed; handles
  CSV via `CSVReader`, XLSX via `XSSFWorkbook`/`DataFormatter`, JSON arrays). **Do not invent a new
  CSV/Excel dependency.** (`experience-bff` also carries a lightweight `CsvLineParser` for
  single-line cases.) org-registry/varapi will need `opencsv`+`poi` added to their poms — the same
  versions already pinned elsewhere.
- **Column mapping:** an explicit step maps uploaded headers → canonical fields, saved as a reusable
  **mapping template** per (org, target) so a council's recurring export is mapped once. Store the
  template as JSONB (`{uploaded_header → canonical_field}`) on the run header (or a small
  `import_mapping_template` table keyed by org+target+name). Unmapped required columns block STAGE;
  extra columns are preserved verbatim in `raw_values`.
- **Per-row validation:** validate the mapped canonical row against a versioned schema via
  **forms-service** — `POST /internal/v1/forms/{id}/validate`
  ([`FormSchemaController`](../../../services/forms-service/src/main/java/zw/gov/mohcc/impilo/forms/api/FormSchemaController.java),
  `SchemaValidationService`). ROM already reserves **forms V003** for regulatory application section
  schemas; add ingest row-shape schemas there (`rom.staff-import.v1`, `rom.register-import.v1`).
  Validation failures → `QUARANTINED_BAD_DATA` with the field errors, never a crashed run.
- **Assets (logos/certificates/seals):** multipart → document-service
  `POST /v1/internal/objects` (`file`, `mimeType`, `metadata`), which computes SHA-256, stores in
  MinIO, dedupes by hash (`GET /by-hash/{sha256}`), and returns an `objectId` (UUID). The **object
  ref is stored on the owning row** (e.g. `org_registry_organization.logo_object_id`), exactly as
  other services reference stored objects — the SoR keeps the ref, document-service keeps the bytes.
  No new asset store.

---

## 7. Idempotency, dry-run, rollback, provenance

Every ingest table carries, on each staged row:

- **`source_record_key`** — deterministic per source row (cf. HPA
  `"HPA_CURRENT_2026-07-17:<institutionId>"`, facility `"MHF-<label>-row<n>"`). Re-import ⇒ same key
  ⇒ same subject, never regenerated.
- **`batch_id` + batch fingerprint** — `UNIQUE (batch_id, source_record_key)` (as `uq_hpc_bundle_key`,
  `uq_hcs_batch_key`) makes re-staging a no-op (`ON CONFLICT DO NOTHING` → `SKIPPED`). Batch header
  stores a `feed_checksum` (sha256) so an identical file re-run is recognised.
- **Dry-run first** — `dry_run` defaults TRUE (as `facility_import_run`); dry-run evaluates and
  persists the run + rows for review but writes **nothing** authoritative. A dedicated
  `…/import/dry-run` endpoint forces it.
- **Rollback-by-batch** — APPLY is per-batch reversible: applied authoritative rows carry the
  `batch_id` that created them; a batch rollback reverts rows still in the non-authority state it
  created and never hard-deletes anything a human later promoted or edited (cf. V036 "no canonical
  hard deletes; rollback is by import batch").
- **Provenance** — every staged and applied row records source system, `source_effective_date`,
  the **uploader's appointment** (initiated_by = actor's regulatory appointment id, not just a user
  id), upload timestamp, and batch id. Applied authoritative rows carry `source=IMPORT` +
  `verification_state=UNVERIFIED` until promoted.
- **Ingest audit trail** — append-only `review_history` JSONB per row (every decision, prev→new
  status, reviewer, reason, timestamp) plus the run header, identical to the facility rail.

---

## 8. Per-target detail

### 8.1 Staff → regulatory appointments (org-registry) — ROM-W1

- **Upload:** each regulator's current staff roster (name, national-id, email, role, jurisdiction,
  valid_from). CSV/XLSX.
- **Staging:** `org_registry_appointment_import_run` + `_row` (lean shape). Canonical fields:
  person national-id, email, full name, `role_code` (validated against the closed
  `org_registry_appointment_role` vocabulary, V006), `org_id`, `jurisdiction_code`, `valid_from`.
- **Match strategy:** resolve the person by **national-id, else verified email**, to an existing
  Health ID / person; resolve `role_code` against the closed vocabulary; `org_id` is the importing
  regulator. A role that isn't in the vocabulary ⇒ `QUARANTINED_BAD_DATA`. Two rows for the same
  (person, org, role) in one batch ⇒ `POSSIBLE_DUPLICATE_REVIEW`. An existing ACTIVE appointment for
  that triple ⇒ `MATCH_EXISTING_ENRICH` (extend/refresh dates) or `CONFLICT` if role details clash.
- **APPLY (grants no authority):** writes `org_registry_regulatory_appointment` with
  **`status = PENDING_VERIFICATION`** (the vocabulary's initial state per generic-model-spec §1). It
  does **not** create a vashandi mirror, an org-session, or any WORK_CONTEXT claim — authz activation
  requires the appointment be moved ACTIVE by a human, which is the existing appointment-verification
  path (context-and-isolation-spec). Import alone ⇒ no login-as-regulator, no workspace.
- **Assets in the same wave:** org **logos** via document-service; `logo_object_id` stored on
  `org_registry_organization`.

### 8.2 Registered/licensed people + conditions of practice (varapi) — ROM-W3

- **Upload:** each council's current register export — reg-number, name, national-id (if held),
  register/roll, category, status, registration date, licence/renewal validity, CPD balance, and
  **conditions of practice** (scope limits, supervision requirements, interdictions).
- **Staging (rich shape, mirrors V036):** `varapi.register_import_batch` (fingerprinted, dry-run,
  counts, quality_report), immutable `varapi.register_candidate_staging` (raw feed line verbatim,
  normalized reg-number/name), and `varapi.register_match_decision` (outcome, confidence,
  match_evidence, competing_candidates, conflicts, reviewer state, append-only review_history).
- **Match strategy:** resolve the person/provider by **council registration number** (normalized,
  case-insensitive — exactly `councilRegRepo.findFirstByTenantIdAndRegistrationNumberIgnoreCase` in
  `HpaPractitionerImportService`) corroborated by **name**; national-id when present raises
  confidence. Reg-number resolving to a *different* existing person ⇒ `CONFLICT`. Two council
  exports asserting the same reg-number to different people ⇒ `POSSIBLE_DUPLICATE_REVIEW`.
- **APPLY (grants no authority):**
  - `register_entries` land **`status = PROVISIONAL`** (the FSM's unverified initial state,
    generic-model-spec §1) — not `REGISTERED`. A registrar promotes PROVISIONAL→REGISTERED as a
    separate, audited act; only then does the person count as "on the register" for public
    good-standing and authz.
  - `licences` / renewal validity land as unverified provisional records; they do not confer a valid
    practising licence until promoted.
  - **Conditions of practice** attach to the entry as `register_entry_restrictions` but marked
    **`source = IMPORT`, `verification_state = UNVERIFIED`** — visible to the registrar for
    confirmation, but not yet enforced as an authoritative restriction.
  - CPD balances land as imported provisional CPD ledger rows (source=IMPORT), not as a satisfied
    renewal gate.
- **Provenance:** field-level assertions (mirror `facility_field_assertion`) so a later verified
  council correction is never overwritten by a re-import blank.

### 8.3 Licensed facilities (tuso) — ROM-W6 — **reuse, build nothing**

ROM reuses the **existing** `FacilityMasterImportService` /
`/v1/internal/facilities/import` rail and the existing `facility_import_run`/`facility_import_row`
tables **unchanged**. A regulator's licensed-facility export is staged, matched (against live
`tuso.facility` first — never a parallel registry), reviewed and applied exactly as the master pack
and HPA enrichment already do. The only ROM addition is the reserved tuso **V039–V040** (practice
establishment case + appeal linkage, already in the ledger) — no new import machinery. Facility
regulatory status set by import lands via the existing progressive-trust/assertion model
(`REGULATOR_LISTED`), promoted to `REGULATOR_VERIFIED` by a human.

### 8.4 Assets (logos/certificates/seals) — ROM-W1 (logos), ROM-W3 (certificates)

Multipart to document-service `POST /v1/internal/objects`; returned `objectId` stored as a ref on the
owning SoR row. Deduped by SHA-256. No staging/match/review rail — assets are not authority-bearing;
they carry no match risk. (A certificate *issued* by a council is generated by the council workflow,
not imported; only pre-existing brand/seal assets are uploaded here.)

---

## 9. Migration-number recommendations

Coordinate with the ledger in [`README.md`](README.md) (verify heads before each wave). ROM already
reserved: varapi **V028–V036**, tuso **V039–V040**, org-registry **V006–V008**, forms **V003**.

**Recommendation — dedicated ingest-staging migrations, appended after the reserved ROM block so
domain and ingest concerns stay separable:**

| Service | New ingest migration | Contents |
|---|---|---|
| organization-registry | **V009** | `org_registry_appointment_import_run` + `_row` (+ review overlay columns, mirroring facility V011+V014+V015 folded into one migration) + optional `import_mapping_template`. `logo_object_id` column on `org_registry_organization` folds into the existing ROM **V007** org-seed migration (no separate number). |
| varapi | **V037** | `register_import_batch` + `register_candidate_staging` + `register_match_decision` (rich shape, mirroring tuso V036). |
| tuso | *(none)* | Reuses existing `facility_import_run`/`_row` (V011/V014/V015). ROM's reserved V039–V040 are domain, not ingest. |
| forms | fold into reserved **V003** | Add `rom.staff-import.v1` and `rom.register-import.v1` row-shape schemas alongside the reserved application-section schemas. |
| document-service | *(none)* | Existing `objects` catalogue + `ObjectController`. |

Note the appointment-import staging (`org-registry V009`) is distinct from the reserved
vashandi **V009** (regulatory-org assignment consumer) — different services, same local number, no
collision. Verify org-registry head is still V008 and varapi head is still V036 at wave start; if the
ROM domain migrations have already advanced the head, take the next free number and keep the mapping
above as the ordering.

---

## 10. Conformance invariant — ROM-INGEST

Add to the `tests/regulatory-contract` pack (alongside ROM-APPT, ROM-ISO, ROM-CTX per build-waves
ROM-W11). Three assertions, each proven against a live import run per target:

1. **No authority from import.** An imported-then-applied record confers **zero** authority until a
   human promotes it. Concretely: an applied appointment is `PENDING_VERIFICATION` and yields no
   login-as-regulator / no WORK_CONTEXT claim / no workspace; an applied register entry is
   `PROVISIONAL` and is absent from public good-standing and from any authz register-membership check;
   an imported condition of practice is `source=IMPORT, UNVERIFIED` and is not enforced. Assert authz
   DENY / absence for each before promotion; assert the promotion is a separate audited event.
2. **Idempotent re-run.** Re-staging the identical batch produces **zero** new authoritative rows and
   zero duplicate staging rows (the `UNIQUE(batch_id, source_record_key)` no-op path); counts return
   `SKIPPED`, not `NEW`.
3. **Bad rows quarantine, never crash.** A batch containing malformed/invalid rows completes; bad
   rows land `QUARANTINED_BAD_DATA` with a reason; all valid rows in the same batch still stage and
   evaluate. The run status is `COMPLETED` (or `COMPLETED_WITH_FAILURES`), never a 5xx/aborted run.

---

## 11. Wave mapping

| Slice | Wave | Target(s) |
|---|---|---|
| Staff → appointments (`PENDING_VERIFICATION`) + org logos | **ROM-W1** | org-registry V009 + document-service (existing) |
| Registers/people/licences/CPD/conditions-of-practice (`PROVISIONAL`/`UNVERIFIED`) + certificates | **ROM-W3** | varapi V037 + forms V003 + document-service |
| Licensed facilities (reuse existing rail) | **ROM-W6** | tuso (existing `/v1/internal/facilities/import`) |
| ROM-INGEST conformance + shared review console generalisation | **ROM-W11** | tests/regulatory-contract + one-ui-shell |
