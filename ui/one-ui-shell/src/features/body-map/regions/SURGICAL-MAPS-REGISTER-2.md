# Surgical anatomical region maps — register 2 (head/neck, specialty, wounds & burns)

Wave SB-4. Covers the three map files authored in this lane:

- `surgical-maps-head-neck.ts`
- `surgical-maps-specialty.ts`
- `surgical-maps-wounds-burns.ts`

The abdominal, limb and vascular maps are registered separately in `SURGICAL-MAPS-REGISTER.md`.

**Every map below is ENGINEERING_AUTHORED and pending clinical verification (MoHCC
ratification).** Anatomy is simplified for clickable region selection, not for anatomical
teaching. No map here has been signed off by a clinician.

## How to read the SNOMED coverage column

Two binding kinds are used, and the difference is the point of this column:

- **verified** — a SNOMED CT body-structure code that is *true* of the region, though often at
  a coarser level than the region label (both thyroid lobes bind to "Thyroid structure";
  breast quadrants bind to "Breast structure"), with `laterality` carrying the side. This is
  the idiom already in `clinical-maps.ts`, where both upper lung zones share one lobe code.
- **unmapped** — no SNOMED code was verified for that structure, so it binds to
  `urn:impilo:body-structure-unmapped` with the precise anatomical display. A placeholder is
  used deliberately in preference to a plausible-looking SNOMED code, because a wrong code
  would be silently trusted downstream. These are the rows a terminologist must close.

`full` = every region verified. `partial` = mixed; the unmapped count is stated so the
ratification backlog is a number, not an impression.

## Maps

| Map (const) | id | Regions | SNOMED coverage | Status |
|---|---|---:|---|---|
| `SCALP_SKULL_MAP` | `scalp-skull` | 11 | partial — 7 verified / 4 unmapped (cranial fossae) | ENGINEERING_AUTHORED |
| `FACE_MAXILLOFACIAL_MAP` | `face-maxillofacial` | 14 | partial — 3 verified / 11 unmapped (facial skeleton) | ENGINEERING_AUTHORED |
| `NECK_TRIANGLES_MAP` | `neck-triangles` | 11 | partial — 6 verified / 5 unmapped (cricoid, notch, zones I–III) | ENGINEERING_AUTHORED |
| `THYROID_PARATHYROID_MAP` | `thyroid-parathyroid` | 8 | partial — 4 verified / 4 unmapped (parathyroid positions) | ENGINEERING_AUTHORED |
| `EAR_MAP` | `ear` | 18 | partial — 6 verified / 12 unmapped (canal, mastoid, TM quadrants) | ENGINEERING_AUTHORED |
| `EYE_ORBIT_MAP` | `eye-orbit` | 24 | partial — 2 verified / 22 unmapped (lids, canthi, cornea, sclera, lacrimal) | ENGINEERING_AUTHORED |
| `ORAL_DENTAL_MAP` | `oral-dental` | 40 | partial — 6 verified / 34 unmapped (32 of them FDI teeth, carried as FDI numbers) | ENGINEERING_AUTHORED |
| `CRANIAL_NEURO_MAP` | `cranial-neuro` | 17 | partial — 8 verified / 9 unmapped (fossa + entry sites, which are procedure sites, not body structures) | ENGINEERING_AUTHORED |
| `BREAST_QUADRANTS_MAP` | `breast-quadrants` | 20 | partial — 12 verified (parent-level "Breast structure") / 8 unmapped (nipple-areola, axillary levels I–III) | ENGINEERING_AUTHORED |
| `UROLOGICAL_TRACT_MAP` | `urological-tract` | 27 | partial — 25 verified / 2 unmapped (renal pelvis L/R) | ENGINEERING_AUTHORED |
| `MALE_REPRODUCTIVE_MAP` | `male-reproductive` | 13 | partial — 5 verified / 8 unmapped (prepuce, inguinal rings, cords, epididymes, scrotum) | ENGINEERING_AUTHORED |
| `FEMALE_REPRODUCTIVE_MAP` | `female-reproductive` | 14 | **full** — 14 verified (vulval regions at parent level) | ENGINEERING_AUTHORED |
| `CARDIOTHORACIC_MAP` | `cardiothoracic` | 20 | partial — 12 verified (chambers/valves at "Heart", aorta) / 8 unmapped (coronary territories, pulmonary trunk, cavae, pulmonary veins) | ENGINEERING_AUTHORED |
| `LUNG_LOBES_MAP` | `lung-lobes` | 16 | partial — 7 verified (lobe codes + laterality) / 9 unmapped (lingula, pleura, recesses, mediastinal compartments) | ENGINEERING_AUTHORED |
| `BURNS_ADULT_MAP` | `burns-total-body-adult` | 15 | partial — 13 verified / 2 unmapped (back of trunk) | ENGINEERING_AUTHORED |
| `BURNS_PAEDIATRIC_MAP` | `burns-total-body-paediatric` | 15 | partial — 13 verified / 2 unmapped (back of trunk) | ENGINEERING_AUTHORED |
| `PRESSURE_INJURY_SITES_MAP` | `pressure-injury-sites` | 14 | partial — 2 verified (occiput, sacrum) / 12 unmapped (bony prominences) | ENGINEERING_AUTHORED |
| `WOUND_DRAIN_SITES_MAP` | `wound-drain-sites` | 13 | partial — 12 verified (abdomen parent) / 1 unmapped (nasogastric route) | ENGINEERING_AUTHORED |
| **Total** | | **310** | 143 verified / 167 unmapped | |

Counts above were produced by compiling the three modules and counting `regions[]` and the
`locationCode.system` of each, not by hand.

## Clinical caveats carried in the code

- **Rule of nines is an estimate.** `BURNS_ADULT_MAP` is a triage instrument. Where a
  Lund–Browder chart is available it is the better instrument, and the derived figure should be
  recorded as an estimate.
- **The paediatric map is the infant/young-child rule-of-nines variant**, not a Lund–Browder
  age-banded chart. It must not be presented as one. Head 18%, legs 13.5% each.
- **Parathyroid positions are where a surgeon looks first**, not a claim about where the glands
  are; number and position are variable.
- **Coronary territories are the simplified three** (LAD, LCx, RCA). Dominance and variation are
  not represented.
- **Burr-hole and shunt sites are procedure sites, not body structures**, and are never given a
  body-structure code.
- **Back views are drawn unmirrored**, matching the convention already set by
  `CHILD_BODY_BACK`, so laterality does not flip between the two halves of a burns map.

## TBSA percentages

`BodyRegionDef` has no data field. Adding one to a shared core type from a content wave would
be the wrong seam, so burn percentages live beside their maps as typed lookup tables keyed by
region id:

- `BURNS_ADULT_TBSA_PERCENT` — 15 entries, sums to 100.
- `BURNS_PAEDIATRIC_TBSA_PERCENT` — 15 entries, sums to 100.
- `totalBurnSurfaceArea(regionIds, table)` — de-duplicates ids (a double click is not a second
  burn) and contributes nothing for unknown ids, so a typo cannot inflate a fluid prescription.

Both sums and the de-duplication were executed and checked, not asserted.

## Verification performed

- `tsc --noEmit` over the whole `ui/one-ui-shell` project: **0 errors**.
- Strict standalone compile of the three files: **0 errors**.
- Region ids unique within every map; region counts, SNOMED/unmapped splits and TBSA sums
  computed from the compiled modules.

## Open items for ratification

1. Terminology: bind the 167 unmapped structures, or rule that a coarser parent binding is
   acceptable for them. The dental arch (32 rows) is the largest single block and may be better
   served by a dedicated FDI/ISO 3950 code system than by SNOMED body structures.
2. Clinical review of every map's region set and of the two burn percentage tables.
3. No instrument in `instruments/index.ts` yet references these maps; wiring them to
   instruments (with the morphology options each surgical context needs) is a separate step.
