# IATG leases — Paediatric & Neonatal Domain Pack

Status: **open to coordinator amendment.** Authored by the Paediatric pack lane per the
one-lease-file-per-pack convention already set by `iatg-trauma-leases.md`,
`iatg-surgery-procedures-leases.md`, `iatg-emergency-leases.md`, `iatg-rmnp-leases.md` and
`iatg-adult-medicine-leases.md`.

Scope of this entry: the **growth standards** slice — adding Fenton 2013 preterm growth as a
first-class standard alongside WHO 2006 in `libs/paediatric-domain`, its persistence in
`pct-service`, its exposure through `experience-bff`, and its surfacing in the web shell and the
mobile provider app.

## 1. Files this lane holds

| Held | Not held |
|---|---|
| `libs/paediatric-domain/**` — the growth engine, growth standards content, corrected/postmenstrual age | Nothing else owns this module today; any lane needing a new indicator should ask rather than fork |
| `services/pct-service` — `pct_growth_measurements` and `GrowthService`/`GrowthController` **only** | Emergency owns `emergency_episode`/`ed_*`; RMNP owns the pregnancy episode and `pct_labour_observations`; Adult Medicine owns `pct_problems` and the medical episode |
| `services/experience-bff` — `GrowthController`, `GrowthStandardsService`, the growth methods on `PctServiceClient` | Every other BFF controller |
| `ui/one-ui-shell/src/features/paediatrics/**`, `src/app/ehr/[patientId]/growth-chart/**`, `src/hooks/queries/useGrowth.ts` | The rest of the shell |
| `apps/mobile/provider-app` — the **neonatal** entry of `SPECIALTY_WORKSPACES` and the Fenton tool tile only | The rest of `SpecialtyWorkspacePanel`; the burns withdrawal machinery is read-and-reuse, not rewrite |

## 2. Migration reservation — band `V400`–`V429`

The band convention is the surgery lane's (`iatg-surgery-procedures-leases.md` §"Numeric distance
fixes what adjacency cannot"): reserve a band far above every incremental head so a concurrent
lane reserving "just above the head" cannot overtake it.

Claimed bands at the time of writing: `V100`–`V129` (Adult Medicine), `V200`–`V229` (Emergency),
`V300`–`V329` (Surgery & Procedures). Verified 2026-07-26 against the working tree: the highest
migration file anywhere in the repository is `pct` `V101`, and no `V2xx`, `V3xx` or `V4xx` exists
on disk yet.

| Band | Owner |
|---|---|
| `V1xx` (V100–V129) | Adult Medicine |
| `V2xx` (V200–V229) | Emergency / Resuscitation / Acute Care |
| `V3xx` (V300–V329) | Surgery & Procedures |
| **`V400`–`V429`** | **Paediatric & Neonatal pack (this lane)** |

| Service | Head at adoption | Reserved for this lane |
|---|---|---|
| `pct-service` | V101 | **V400–V429** — growth standard selection V400 · reserve V401–V429 |
| `clinical-knowledge-platform-service` | V006 | V400–V429 (none needed for this slice) |
| `inpatient-service` | V066 | V400–V429 (none needed for this slice) |

Re-verify heads at commit time; a head is a measurement and it is stale the moment it is written.

## 3. Cross-pack seams frozen by this slice

1. **One growth system of record.** `pct.pct_growth_measurements` is the only store of a child's
   anthropometry and its z-scores. No lane may add a parallel growth table, and no lane may
   recompute a z-score from a stored measurement — CKP's growth interpreter already reads the
   stamped score rather than deriving one, and that boundary holds for Fenton too.

2. **`libs/paediatric-domain` is the only growth arithmetic.** Before this slice
   `experience-bff` carried a second, independent WHO scorer with its own 669 KB copy of the LMS
   table and **no corrected-age handling**, so a preterm infant was scored twice and the two
   answers disagreed. The BFF now delegates to the library. Any service needing a z-score depends
   on the library; nobody reimplements the LMS arithmetic.

3. **Standard selection is the engine's, not the caller's.** A caller supplies date of birth, sex,
   gestational age and the measurement; the engine decides which standard applies and stamps its
   identifier on the result. A caller cannot request a standard, because "score this preterm baby
   against the term chart" is not a request the system should be able to express.

4. **Preterm infants are never scored against WHO term standards.** Where the applicable preterm
   reference has no data, the measurement is stored unscored with a stated reason. This is the
   same stance the pack already takes on WHO weight-for-length and the WHO 5–19 year reference:
   a named absence, never a substitution.

## 4. Content governance — Fenton 2013

- **Ratification status: `ENGINEERING_SEED`, pending MoHCC**, matching every other content pack in
  this programme (IMNCI tables, ZW EPI schedule, growth intelligence, nutrition thresholds).
- **Licence: CC BY-NC-ND 4.0.** The LMS parameters are published by Dr Tanis Fenton
  (University of Calgary) under Creative Commons Attribution-NonCommercial-NoDerivatives 4.0. The
  licence text distributed with the data carries two operative obligations that this
  implementation must keep satisfying:
  1. a chart built from the data must display the label **“Fenton 2013 Preterm Growth Chart”**
     conspicuously, and
  2. the development paper must be cited: *Fenton TR, Kim JH. A systematic review and
     meta-analysis to revise the Fenton growth chart for preterm infants. BMC Pediatr.
     2013;13:59.*

  Both obligations are carried in the content pack's own metadata and surfaced through the API to
  the UI, so a future surface cannot render the chart without the attribution travelling with it.
- **Two questions for MoHCC, recorded rather than assumed:** (a) whether a national public health
  service deployment satisfies the *NonCommercial* term, and (b) whether embedding the published
  weekly LMS values verbatim for lookup is use rather than a *derivative*. This lane has taken the
  conservative reading — values are stored exactly as published, with no smoothing, no
  re-fitting and no interpolation between the published weekly points — but the determination is
  the ministry's to make, not an engineer's.

## 5. Ports

No new service, no new port.
