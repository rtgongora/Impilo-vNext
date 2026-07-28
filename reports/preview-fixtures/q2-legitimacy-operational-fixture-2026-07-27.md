# Preview fixture — Q2 platform-operational legitimacy stamp

**This is a PREVIEW TEST FIXTURE, not a regulatory determination.** A human deliberately
stamped the facilities below as platform-operational on the **preview** estate so that
maternity/find-care/EmONC proofs have live positives to resolve against. It says **nothing**
about these facilities' real-world operating status, and it must never be promoted to
production or read as a Ministry/HPA verdict.

- **Authority:** PO ruling on Q2 (2026-07-27), relayed via the coordinator session
  (`local_a1b034bd…`, "stamp a SMALL NAMED SET"). Scope was explicitly a named set, never
  status-wide / estate-wide.
- **Actor of record:** `preview-fixture-q2-legitimacy` (a fixture identity, not a person or a
  registrar).
- **When:** 2026-07-27.
- **Estate:** `impilo-full-preview` only.
- **Mechanism (audited):** `PUT /v1/internal/facilities/{facility_uuid}/source-legitimacy/PLATFORM_OPERATIONAL`
  with `status=GOVERNMENT_OPERATIONAL_EXCEPTION, allowedOnPlatform=true` — the real service path
  (`FacilitySourceLegitimacyService.upsert`), which emits `tuso.facility.source_legitimacy.upserted`
  and records `recorded_by`/`as_of`/`reason` on `tuso.facility_source_legitimacy`. Tenant =
  REGISTRY_PLANE (`00000000-0000-0000-0000-000000000001`), the plane holding facility-master rows.
- **Reason string stamped on every row:** "PREVIEW TEST FIXTURE (PO Q2 ruling 2026-07-27):
  platform-operational stamp for proof enablement only. NOT a regulatory determination; says
  nothing about real-world operating status."
- **Evidence ref:** `PREVIEW_FIXTURE:Q2_LEGITIMACY:2026-07-27`.

## The named set (5 — 2 tertiary + 3 provincial, 5 provinces, all CEMONC-capable)

| Facility | Code | facility_uuid | id | Province | Level |
|---|---|---|---|---|---|
| Parirenyatwa Group of Hospitals | ZW000E0E | 9b222c87-e96a-5f6e-80eb-00b55e40ab2e | 344 | Harare | QUATERNARY |
| Mpilo Central Hospital | ZW090A0D | ccd6a682-3ec9-5953-a788-27d6644df2e3 | 1750 | Bulawayo | QUATERNARY |
| Gweru Provincial Hospital | ZW07040A | 7eae1583-562b-5a97-91ed-65cebdf407dc | 1557 | Midlands | SECONDARY |
| Masvingo Provincial Hospital | ZW08050A | d80e2e3d-642a-5134-a910-35901510e0fd | 1017 | Masvingo | TERTIARY |
| Chinhoyi Provincial Hospital | ZW01440A | c98b1da4-8bee-5d85-b992-c261787011ba | 476 | Mashonaland West | PRIMARY |

## Proof (status-summary.operational)

| Facility | BEFORE | AFTER |
|---|---|---|
| Parirenyatwa / Mpilo / Gweru / Masvingo / Chinhoyi (5 stamped) | false | **true** |
| United Bulawayo Hospital (ZW090C0C, id 1771 — negative control, **unstamped**) | false | **false** |

**The fail-closed default survives.** `operational = statusActive && operatingConfirmed &&
!regulatorListingOnly && legitimacyAllows`. `facility.status` was NOT touched; only the legitimacy
half was supplied for the 5 named facilities. Every unstamped facility — including the negative
control and all ~1,769 other eligible-shape MFL facilities — still returns `operational: false`
with the same verdict text. This is a bounded set of exceptions, not a softening of the rule.

## Reversal

Delete the `PLATFORM_OPERATIONAL` legitimacy row (or upsert it to `allowedOnPlatform=false`) for
each of the 5 `facility_uuid`s above; each reverts to `operational: false` immediately. Nothing
else was changed.

## Note for the broader picture (separate from this fixture)

While selecting the set it surfaced that **all 1,774 Ministry MFL facilities currently have zero
`facility_source_legitimacy` rows** and therefore read `operational: false` estate-wide — the real
referral hospitals included. The 11,018 existing legitimacy rows are all HPA (2/facility). The
correct fix (the MFL importer stamping the Ministry's own verdict, as `FacilityMasterImportService`
already does for the sources it handles, or a proper audited backfill) is a **code/data decision for
the PO**, deliberately NOT done here — this fixture is only the small named set the PO authorised.
