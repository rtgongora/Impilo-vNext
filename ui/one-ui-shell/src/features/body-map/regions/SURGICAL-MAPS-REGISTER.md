# Surgical body-map coverage register — Wave SB-4

**ENGINEERING-AUTHORED pending clinical verification (MoHCC ratification).**
Anatomy simplified for clickable region selection, not for anatomical teaching.

This register exists so the coverage of these maps cannot be overstated. Nineteen surgical
region maps were authored by an engineer working from general anatomical knowledge. **None of
them has been reviewed by a surgeon, and none of the terminology bindings has been verified
against a SNOMED CT release.** They are usable as data-entry instruments and are not usable as
a clinical reference until that review happens.

## The two kinds of terminology binding

Every region carries a `locationCode` in the SNOMED CT system, and there are two kinds:

| Kind | Written as | Meaning |
|---|---|---|
| **Coded** | a numeric SNOMED CT identifier | The author could name the concept identifier with confidence. **Still unverified** — see below. |
| **Pending** | `PENDING-SNOMED:<slug>` | The concept exists in SNOMED CT but the author could not source the identifier. The value is deliberately invalid so it fails terminology validation instead of silently reporting the wrong anatomy. |

A wrong code is worse than a missing one: a missing code announces itself, a wrong code reports
the wrong anatomy forever and no test can catch it. That is why unknown bindings are written as
loud placeholders rather than as plausible guesses.

`isPendingTerminologyBinding()` and `terminologyCoverage()` in `surgical-maps-shared.ts` make
this machine-checkable, so a guard can refuse an unverified map anywhere a real coded location
is mandatory (an operation note, a histopathology request, a national return).

**The "coded" column is not a verified column.** The 59 numeric identifiers below were written
from memory and each one needs the same terminology review as the placeholders. Coded means
"the author believed they knew it", not "someone checked".

## Register

| Map | File | Regions | Coded | Pending | SNOMED coverage | Verification status |
|---|---|---|---|---|---|---|
| `abdominal-incisions` | surgical-maps-abdominal.ts | 17 | 3 | 14 | partial | ENGINEERING_AUTHORED |
| `inguinal-groin` | surgical-maps-abdominal.ts | 9 | 3 | 6 | partial | ENGINEERING_AUTHORED |
| `biliary-tree` | surgical-maps-abdominal.ts | 7 | 2 | 5 | partial | ENGINEERING_AUTHORED |
| `colorectal-segments` | surgical-maps-abdominal.ts | 9 | 2 | 7 | partial | ENGINEERING_AUTHORED |
| `upper-gi` | surgical-maps-abdominal.ts | 12 | 5 | 7 | partial | ENGINEERING_AUTHORED |
| `liver-segments` | surgical-maps-abdominal.ts | 9 | 1 | 8 | partial | ENGINEERING_AUTHORED |
| `pancreas` | surgical-maps-abdominal.ts | 6 | 1 | 5 | partial | ENGINEERING_AUTHORED |
| `stoma-sites` | surgical-maps-abdominal.ts | 7 | 0 | 7 | none | ENGINEERING_AUTHORED |
| `upper-limb-skeleton` | surgical-maps-limbs.ts | 20 | 10 | 10 | partial | ENGINEERING_AUTHORED |
| `lower-limb-skeleton` | surgical-maps-limbs.ts | 20 | 6 | 14 | partial | ENGINEERING_AUTHORED |
| `hand-detailed` | surgical-maps-limbs.ts | 24 | 1 | 23 | partial | ENGINEERING_AUTHORED |
| `foot-detailed` | surgical-maps-limbs.ts | 15 | 1 | 14 | partial | ENGINEERING_AUTHORED |
| `major-joints` | surgical-maps-limbs.ts | 12 | 2 | 10 | partial | ENGINEERING_AUTHORED |
| `spine-levels` | surgical-maps-limbs.ts | 30 | 4 | 26 | partial | ENGINEERING_AUTHORED |
| `arterial-tree` | surgical-maps-vascular.ts | 28 | 8 | 20 | partial | ENGINEERING_AUTHORED |
| `venous-lower-limb` | surgical-maps-vascular.ts | 20 | 0 | 20 | none | ENGINEERING_AUTHORED |
| `amputation-levels` | surgical-maps-vascular.ts | 28 | 0 | 28 | none | ENGINEERING_AUTHORED |
| `vascular-access-sites` | surgical-maps-vascular.ts | 18 | 8 | 10 | partial | ENGINEERING_AUTHORED |
| `chest-wall-thoracic` | surgical-maps-vascular.ts | 16 | 2 | 14 | partial | ENGINEERING_AUTHORED |
| **Total** | 3 files | **307** | **59** | **248** | **19% coded** | — |

No map reaches full coverage. Three maps have no coded region at all, and that is expected
rather than a defect: stoma siting zones, varicose-vein perforator sites and amputation levels
are procedure-site concepts whose SNOMED representation is a design decision for the
terminology reviewer, not something an engineer should have guessed.

## What the clinical review has to decide

1. **Terminology, twice over** — verify the 59 numeric bindings and source the 248 placeholders.
   Several of the placeholders may be better represented as a procedure-site qualifier than as a
   body-structure concept (`amputation-levels`, `stoma-sites`, `abdominal-incisions` port sites).
2. **Whether the region set is the right one.** Regions were chosen for what a surgeon records,
   but the choices are arguable: `liver-segments` flattens a three-dimensional Couinaud scheme
   into one plane and must not be used for resection planning; `upper-gi` splits the oesophagus
   into thirds but does not split the stomach by greater/lesser curve; `spine-levels` offers
   every individual level and three regional targets for findings that were never determined to
   a level.
3. **Laterality.** Paired structures are lateralised as separate `-left` / `-right` regions with
   an explicit `laterality`, following the existing clinical maps. `hand-detailed` and
   `foot-detailed` deliberately carry **no** per-region laterality — they draw one generic hand
   or foot and the instrument must ask which side. An instrument that mounts either of those two
   maps without `asksLaterality: true` is a wrong-site hazard.
4. **Where an unverified map may be used.** Marking a finding is safe; emitting one of these
   locations into an operation note, a specimen request or a national return is not, while the
   bindings remain unverified.

## Provenance

Authored in Wave SB-4 as new files only. Instrument wiring (`instruments/index.ts`) is not part
of this register — the maps are inert data until an instrument mounts them.
