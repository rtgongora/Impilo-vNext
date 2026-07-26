# HPA Authoritative Data Remediation (HAR) — Completion Report

**Date:** 2026-07-26
**Branch:** `claude/staging-ux-orchestration-remediation-Yypyl`
**Deployed at:** `830ef68b2` (tuso, varapi, ndila, experience-bff, one-ui-shell)
**Estate:** `impilo-full-preview`

---

## 1. The problem, as stated

> *"Catch up with current changes and tell me why I still can't see all the data we imported from HPA."*
> *"If we can be authoritative about the 1700+ then we surely can be more authoritative about a
> list from HPA, they are the legal authority."*

The HPA import had run. 7,285 facilities existed in `tuso.facility`, 5,509 of them from HPA. And
none of it was reachable.

## 2. What was actually wrong

Verified against the live preview database **before** any change:

| Symptom | Measured |
|---|---|
| Facilities in the register | 7,285 (5,509 HPA-listed) |
| `facility_source_legitimacy` rows | **0** |
| `provider_council_registration_records` | **0** |
| Preloaded practitioners | 4,241, every public lookup returning `NOT_FOUND` |
| PIC nominations | 6,180, all parked at `ELIGIBILITY_CHECKED` |
| Open data-gap tasks | 24,616, reachable only one facility at a time |
| Workspaces | 0 |

**The `INACTIVE` status was never the defect.** `HpaEnrichmentImportService` refuses to infer an
operating status from a regulatory listing — "regulator-listed ≠ operational" — and that is correct
and was not reverted. The defects were:

1. **The statutory registrar's verdict was recorded nowhere the system reads.**
   `FacilityMasterImportService` stamps the Ministry's verdict into `facility_source_legitimacy`;
   the HPA importer stamped nothing. So `getComposite` reported *"No source legitimacy recorded…
   platform access is not granted by default"* for facilities the legal registrar had affirmatively
   listed. HPA's register was the one list in the system carrying no legitimacy record at all.

2. **Public practitioner verification resolves only through
   `provider_council_registration_records`, which had zero rows.** All 4,241 imported practitioners
   returned `NOT_FOUND` on every public lookup. Their registration numbers sat unused in
   `provider.practice_number`.

3. **Reachability, not status.** `status` is barely a filter — every real caller passes null, and
   the 5,509 were already returned by plain name search. What made them unreachable was zero
   capabilities (invisible to service-filtered search), zero coordinates (never on a map), no
   lifecycle filter option in the registry browser, and no pagination past the first 30 of 7,285.

## 3. What was built

Six waves. Each landed as an atomic commit with tests.

| Wave | Commit | Substance |
|---|---|---|
| 0 — Safety hardening | `dee0cf2b9` | Closed two live holes **before** touching data: the regulated-order gate keyed on `status` rather than an operational verdict, and `findWithoutActiveWorkspace` had no status filter, so the bulk operationalization tool would have swept all 5,509. Also deleted the `CLINIC → MATERNITY` capability inference. |
| 1 — The legal verdict | `1e7840e32` | HPA legitimacy stamping (`HPA_LEGAL` + `PLATFORM_OPERATIONAL`) via an audited, idempotent, batched endpoint — not a SQL migration, which would bypass the outbox and the audit trail. |
| 2 — Practitioner rescue | `e1666977a` | Register records at `LISTED_PENDING_COUNCIL_VERIFICATION` — a status deliberately outside every verified-registry vocabulary, so making an entry *findable* cannot make it *verified*. |
| 3 — Claim + PIC unblock | inside `51a7d9e95` | Claim-by-registration-number on the existing access-request rail; PIC re-assessment consumer; registry-wide PIC queue. |
| 4 — Ndila vocabulary | `7725f9f18` | One facility vocabulary + geocode-proposal queue. |
| 5 — Find-care reachability | `9a16a16b9` | `REGISTRY_LISTING` second lane + bounded capability derivation. |
| 6 — The human surface | `830ef68b2` | Registry browser filter + pagination, cross-facility data-gap worklist, public contacts. |

### The line that was never crossed

Nothing in HAR asserts operational readiness. Specifically:

- **`facility.status` was never flipped to `ACTIVE`.** Composite `platform_access_allowed` stays
  `false` for HPA facilities — correct, not a compromise: the rule is a veto lattice, and *every
  Ministry facility sits in the same position* with a `PLATFORM_OPERATIONAL/PENDING_VERIFICATION`
  denier. HPA facilities became peers of the Ministry's 1,774 with a different allower. That was the
  PO's argument, implemented.
- **No PIC assignment was created.** Parking at `ELIGIBILITY_CHECKED` is the machine working;
  creating assignments would fabricate authority.
- **OPD, IPD, MATERNITY and EMERGENCY are never derived**, even though HPA's own register contains
  "Maternity Homes" (40) and "Emergency Rooms" (69). Enforced three ways: the mapping tables, a
  runtime assertion on every write, and a test asserting no mapping can emit a forbidden token.
- **No pin publishes without human confirmation.**

## 4. Live before/after

Measured on the preview estate, same queries either side of the deploy.

| | before | after |
|---|---|---|
| `facility_source_legitimacy` | 0 | **11,018** (5,509 `HPA_LEGAL` + 5,509 `PLATFORM_OPERATIONAL`) |
| `provider_council_registration_records` | 0 | **4,241**, all `LISTED_PENDING_COUNCIL_VERIFICATION` |
| `ndila_locations` vocabulary | split across two spellings | uniform `TUSO|HEALTH_FACILITY`, 8,009 rows |
| …of which carry coordinates under the canonical type | **0 findable** | **1,687** |

**Invariants held:** HPA-listed-and-`ACTIVE` = 0 · workspaces = 0 · PIC assignments = 0 ·
providers not `PENDING_VERIFICATION` = 0.

**Citizen-visible proof.** A practitioner who returned `NOT_FOUND` all week now returns:

```
registerStatus:  LISTED_PENDING_COUNCIL_VERIFICATION
councilVerified: false
registerSource:  "HPA institution return, 17 July 2026 — not yet verified
                  against the practitioner's professional council register"
```

**Find-care proof.** A Harare pharmacy search returns six `SERVICE_MATCH` results, then
`REGISTRY_LISTING` rows tagged `IMPORTED_PENDING_CONFIGURATION` under *"registered with the Health
Professions Authority, but the services they offer have not been confirmed yet. Call before
travelling."*

## 5. Corrections to the approved plan

Three, all load-bearing. Recorded because the plan is wrong on disk otherwise.

**5.1 The ndila naming split runs the other way.** The plan said to change the HPA importer *to*
`'tuso-service'`/`'FACILITY'`. Verified in code: both runtime writers already used
`'TUSO'`/`'HEALTH_FACILITY'`; only the V003 seed migration disagreed. Following the plan would have
spread the defect to the correct half.

The live consequence was also the reverse of the plan's: `NdilaSpatialSearchService` normalises a
caller's `FACILITY`/`CLINIC`/`HOSPITAL` **towards** `HEALTH_FACILITY` before querying, so the
V003-seeded rows — **the ones that actually have coordinates** — could never match a proximity
search. Every "what's near me" for a facility silently missed all 1,687.

**5.2 The "817 orphans" attribution does not hold.** The plan attributed them to `findByOwner` being
split. `ndilaListLocationsByOwner` is defined in the shell client and never called, and the BFF does
not touch ndila at all. Whatever the 817 are, that lookup is not the mechanism.

**5.3 No varapi V039 was needed.** `provider_access_request` already carries
`request_type=COUNCIL_NUMBER`, masking, adjudication statuses and decision fields. A second claim
table would have duplicated the system of record.

## 6. Source-data findings

Parsed from `hpaco_facilities_legacy_dump_2025-02-06.sql` directly (5,423 facility rows), not from
the derived CSVs:

| Column | Non-NULL |
|---|---|
| `physical_address` | **2,227** |
| `postal_address` | 2,229 |
| `telephone` | 1,839 |
| `mobile_number` | 1,545 |
| `latitude` / `longitude` | **4** |

In the whole 7.3 MB file there are exactly **two** Zimbabwe-plausible latitude-shaped decimals — the
same value twice, one facility. Derived files add `legacy_locality_name` and `province_name` for
5,400 rows.

**Conclusion: HPA's register never recorded coordinates.** The ETL lost nothing; the bundle's counts
equal the source's.

**Correction (2026-07-26, after this report was first written).** An earlier version of this section
said the 2,227 street addresses "were never ingested" because `HpaLocationImportService` reads an
address only when a `legacy_enrichment` block is present. Two of us reached that conclusion
independently from `address_line1 = 0` on all 6,322 live rows, and a PO decision was taken on it.
**It was wrong.** The feed carries 6,327 rows of which 2,294 have a `legacy_enrichment` block, and
2,281 of those carry a `physical_address` — so the gate drops nothing. The original INSERT bound its
parameters positionally and the address landed in **`description`**:

```
INSERT INTO ndila_locations (..., name, description, location_type, ...)
VALUES                      (..., ?,    ?,           'HEALTH_FACILITY', ...)
                                  name  address  ← misfiled
```

Live confirms it: 2,281 HPA rows carry a `description`, every sample a street address
("7 Baines Avenue, Suite 8, Westend Clinic Extension, Harare"). Nothing was lost — it was misfiled.
Recovered as a column move in ndila `V007`, not a re-import. The W4 importer change already writes
`address_line1` correctly, so future imports need no fix.

The real remaining gap is different and smaller: the feed carries a locality for only 2,294 rows,
while the underlying CSV has 5,400. That is a gap in how the feed was joined, not in the importer.

## 7. Known defects in this work

**7.1 `HpaGeocodeProposalService` is inert on the existing queue.** It keys on `district` then
`proposed_locality`. Live measurement: of 6,327 queue rows, **0 have `district`, 0 have
`proposed_locality`**, 6,322 have `province`. Those columns are populated only by the *new* importer
path — future imports. Run today it would produce zero proposals and report success. The correct key
is locality+province, joined back to `ndila_locations` by `owner_entity_id`; there are only **212
distinct pairs**, which makes a human-reviewed gazetteer feasible. Found by the discovery-lane
session's independent check; not yet fixed.

**7.2 A Wave-0 test was left failing.** `FacilityOperationalizationServiceTest` still asserted the
`CLINIC → MATERNITY` inference that Wave 0 deleted. Caught by the full suite two waves later, fixed
in `92aabef49`. Wave 0 was committed without running the full suite.

## 8. Open

- **Coordinates** — PO decision taken 2026-07-26: ingest the 2,227 addresses and geocode at address
  precision, with the 212-pair suburb gazetteer as fallback only, rendered as a distinct lower
  precision tier. Not yet built. Requires a geocoding source; none exists in the estate.
- **Capability derivation has not been run.** `POST /derive-capabilities` is deployed and dry-run
  tested; no capability rows have been written.
- **The claim path and PIC queue are deployed but unexercised** — no practitioner has claimed.
- **Data-gap worklist** is live over 24,616 real tasks; nobody has worked one.

## 9. Verification commands

```bash
# tuso
select count(*) from tuso.facility_source_legitimacy where source='HPA_LEGAL';        -- 5509
select count(*) from tuso.facility
 where regulatory_status='IMPORTED_PENDING_CONFIGURATION' and status='ACTIVE';        -- must be 0
select count(*) from tuso.workspace;                                                  -- must be 0
select count(*) from tuso.practitioner_in_charge_assignment;                          -- must be 0

# varapi
select count(*) from varapi.provider_council_registration_records;                    -- 4241
select count(*) from varapi.provider
 where bootstrap_origin='COUNCIL_IMPORT' and registry_status<>'PENDING_VERIFICATION'; -- must be 0

# ndila
select owner_service, location_type, count(*) from ndila_locations group by 1,2;      -- one row
select count(*) from ndila_locations
 where location_type='HEALTH_FACILITY' and is_active and latitude is not null;        -- 1687
```

**Test totals at `830ef68b2`:** tuso 291 · experience-bff 1193 · varapi 282 · ndila 59 — all green.
