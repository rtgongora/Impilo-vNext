/**
 * ENGINEERING-AUTHORED pending clinical verification (MoHCC ratification) — Wave SB-4.
 * Anatomy simplified for clickable region selection, not for anatomical teaching.
 *
 * Burn surface-area maps, pressure-injury risk sites and surgical drain sites.
 *
 * Terminology binding follows the two-kind rule set out in surgical-maps-head-neck.ts:
 * `code(...)` is a verified SNOMED CT body-structure code true of the region (often a coarser
 * parent, with `laterality` carrying the side); `unmapped(...)` marks a structure with no
 * verified SNOMED binding, using a local system URI so a placeholder can never be mistaken for
 * a coded location.
 *
 * TBSA PERCENTAGES: `BodyRegionDef` has no data field, and adding one to a shared type from a
 * content wave would be the wrong seam. The percentages therefore live beside each map as a
 * typed `Record<string, number>` keyed by region id, not smuggled into a display string where
 * a calculation would have to parse them back out.
 */

import { SNOMED_BODY_STRUCTURE, type RegionMapDef } from "../core/types";
import { UNMAPPED_BODY_STRUCTURE } from "./surgical-maps-head-neck";

const code = (id: string, display: string) => ({
  system: SNOMED_BODY_STRUCTURE,
  code: id,
  display,
});

const unmapped = (localCode: string, display: string) => ({
  system: UNMAPPED_BODY_STRUCTURE,
  code: localCode,
  display,
});

/**
 * Adult burn assessment, rule of nines — front figure on the left, back figure on the right.
 *
 * Both figures are drawn in the same orientation rather than mirroring the back view, which is
 * the convention CHILD_BODY_BACK already established: a clinician switching between the two
 * halves of one map should not have laterality flip under the cursor.
 *
 * The rule of nines is a rapid estimate. It is the right instrument for triage and initial
 * fluids; it is not a substitute for a Lund-Browder chart where a chart is available, and the
 * figure it produces should be recorded as an estimate.
 */
export const BURNS_ADULT_MAP: RegionMapDef = {
  id: "burns-total-body-adult",
  title: "Burns — adult rule of nines",
  viewBox: "0 0 400 400",
  silhouettePaths: [
    "M100 12 a26 26 0 0 1 26 26 a26 26 0 0 1 -52 0 a26 26 0 0 1 26 -26 Z",
    "M86 64 h28 v14 h-28 Z",
    "M62 78 h76 v112 h-76 Z",
    "M70 190 h60 v30 h-60 Z",
    "M32 82 h30 v104 h-30 Z",
    "M138 82 h30 v104 h-30 Z",
    "M70 220 h30 v164 h-30 Z",
    "M100 220 h30 v164 h-30 Z",
    "M300 12 a26 26 0 0 1 26 26 a26 26 0 0 1 -52 0 a26 26 0 0 1 26 -26 Z",
    "M286 64 h28 v14 h-28 Z",
    "M262 78 h76 v112 h-76 Z",
    "M270 190 h60 v30 h-60 Z",
    "M232 82 h30 v104 h-30 Z",
    "M338 82 h30 v104 h-30 Z",
    "M270 220 h30 v164 h-30 Z",
    "M300 220 h30 v164 h-30 Z",
  ],
  regions: [
    { id: "burn-head-front", label: "Head and neck, front (4.5%)", group: "front", svgPath: "M100 12 a26 26 0 0 1 26 26 a26 26 0 0 1 -52 0 a26 26 0 0 1 26 -26 Z", laterality: "midline", locationCode: code("69536005", "Head") },
    { id: "burn-chest-front", label: "Chest (9%)", group: "front", svgPath: "M62 78 h76 v56 h-76 Z", laterality: "midline", locationCode: code("78904004", "Chest") },
    { id: "burn-abdomen-front", label: "Abdomen (9%)", group: "front", svgPath: "M62 134 h76 v56 h-76 Z", laterality: "midline", locationCode: code("113345001", "Abdomen") },
    { id: "burn-arm-right-front", label: "Right arm, front (4.5%)", group: "front", svgPath: "M32 82 h30 v104 h-30 Z", laterality: "right", locationCode: code("40983000", "Upper limb") },
    { id: "burn-arm-left-front", label: "Left arm, front (4.5%)", group: "front", svgPath: "M138 82 h30 v104 h-30 Z", laterality: "left", locationCode: code("40983000", "Upper limb") },
    { id: "burn-perineum", label: "Perineum (1%)", group: "front", svgPath: "M84 190 h32 v28 h-32 Z", laterality: "midline", locationCode: code("26107004", "Perineum") },
    { id: "burn-leg-right-front", label: "Right leg, front (9%)", group: "front", svgPath: "M70 220 h30 v164 h-30 Z", laterality: "right", locationCode: code("61685007", "Lower limb") },
    { id: "burn-leg-left-front", label: "Left leg, front (9%)", group: "front", svgPath: "M100 220 h30 v164 h-30 Z", laterality: "left", locationCode: code("61685007", "Lower limb") },
    { id: "burn-head-back", label: "Head and neck, back (4.5%)", group: "back", svgPath: "M300 12 a26 26 0 0 1 26 26 a26 26 0 0 1 -52 0 a26 26 0 0 1 26 -26 Z", laterality: "midline", locationCode: code("69536005", "Head") },
    { id: "burn-upper-back", label: "Upper back (9%)", group: "back", svgPath: "M262 78 h76 v56 h-76 Z", laterality: "midline", locationCode: unmapped("back-of-trunk", "Back of trunk") },
    { id: "burn-lower-back", label: "Lower back and buttocks (9%)", group: "back", svgPath: "M262 134 h76 v56 h-76 Z", laterality: "midline", locationCode: unmapped("back-of-trunk", "Back of trunk") },
    { id: "burn-arm-right-back", label: "Right arm, back (4.5%)", group: "back", svgPath: "M232 82 h30 v104 h-30 Z", laterality: "right", locationCode: code("40983000", "Upper limb") },
    { id: "burn-arm-left-back", label: "Left arm, back (4.5%)", group: "back", svgPath: "M338 82 h30 v104 h-30 Z", laterality: "left", locationCode: code("40983000", "Upper limb") },
    { id: "burn-leg-right-back", label: "Right leg, back (9%)", group: "back", svgPath: "M270 220 h30 v164 h-30 Z", laterality: "right", locationCode: code("61685007", "Lower limb") },
    { id: "burn-leg-left-back", label: "Left leg, back (9%)", group: "back", svgPath: "M300 220 h30 v164 h-30 Z", laterality: "left", locationCode: code("61685007", "Lower limb") },
  ],
};

/** Adult rule-of-nines TBSA contribution per region id. Sums to 100. */
export const BURNS_ADULT_TBSA_PERCENT: Record<string, number> = {
  "burn-head-front": 4.5,
  "burn-chest-front": 9,
  "burn-abdomen-front": 9,
  "burn-arm-right-front": 4.5,
  "burn-arm-left-front": 4.5,
  "burn-perineum": 1,
  "burn-leg-right-front": 9,
  "burn-leg-left-front": 9,
  "burn-head-back": 4.5,
  "burn-upper-back": 9,
  "burn-lower-back": 9,
  "burn-arm-right-back": 4.5,
  "burn-arm-left-back": 4.5,
  "burn-leg-right-back": 9,
  "burn-leg-left-back": 9,
};

/**
 * Paediatric burn assessment.
 *
 * A child is not a small adult: the head carries roughly twice the adult share of surface area
 * and the legs correspondingly less, which is why using the adult chart on an infant
 * under-resuscitates a head burn and over-resuscitates a leg burn. The figure is drawn with the
 * child proportions of child-body.ts, exaggerated at the head so the larger allocation is
 * visible rather than merely asserted in a table.
 *
 * The percentages encoded here are the infant / young-child rule-of-nines variant. Age-banded
 * Lund-Browder proportions are a separate clinical artifact and are NOT represented here; this
 * map must not be presented as a Lund-Browder chart.
 */
export const BURNS_PAEDIATRIC_MAP: RegionMapDef = {
  id: "burns-total-body-paediatric",
  title: "Burns — paediatric proportions",
  viewBox: "0 0 400 400",
  silhouettePaths: [
    "M100 12 a36 36 0 0 1 36 36 a36 36 0 0 1 -72 0 a36 36 0 0 1 36 -36 Z",
    "M88 84 h24 v10 h-24 Z",
    "M64 94 h72 v104 h-72 Z",
    "M84 198 h32 v24 h-32 Z",
    "M36 98 h28 v92 h-28 Z",
    "M136 98 h28 v92 h-28 Z",
    "M70 222 h30 v150 h-30 Z",
    "M100 222 h30 v150 h-30 Z",
    "M300 12 a36 36 0 0 1 36 36 a36 36 0 0 1 -72 0 a36 36 0 0 1 36 -36 Z",
    "M288 84 h24 v10 h-24 Z",
    "M264 94 h72 v104 h-72 Z",
    "M284 198 h32 v24 h-32 Z",
    "M236 98 h28 v92 h-28 Z",
    "M336 98 h28 v92 h-28 Z",
    "M270 222 h30 v150 h-30 Z",
    "M300 222 h30 v150 h-30 Z",
  ],
  regions: [
    { id: "paed-burn-head-front", label: "Head and neck, front (9%)", group: "front", svgPath: "M100 12 a36 36 0 0 1 36 36 a36 36 0 0 1 -72 0 a36 36 0 0 1 36 -36 Z", laterality: "midline", locationCode: code("69536005", "Head") },
    { id: "paed-burn-chest-front", label: "Chest (9%)", group: "front", svgPath: "M64 94 h72 v52 h-72 Z", laterality: "midline", locationCode: code("78904004", "Chest") },
    { id: "paed-burn-abdomen-front", label: "Abdomen (9%)", group: "front", svgPath: "M64 146 h72 v52 h-72 Z", laterality: "midline", locationCode: code("113345001", "Abdomen") },
    { id: "paed-burn-arm-right-front", label: "Right arm, front (4.5%)", group: "front", svgPath: "M36 98 h28 v92 h-28 Z", laterality: "right", locationCode: code("40983000", "Upper limb") },
    { id: "paed-burn-arm-left-front", label: "Left arm, front (4.5%)", group: "front", svgPath: "M136 98 h28 v92 h-28 Z", laterality: "left", locationCode: code("40983000", "Upper limb") },
    { id: "paed-burn-perineum", label: "Perineum (1%)", group: "front", svgPath: "M84 198 h32 v24 h-32 Z", laterality: "midline", locationCode: code("26107004", "Perineum") },
    { id: "paed-burn-leg-right-front", label: "Right leg, front (6.75%)", group: "front", svgPath: "M70 222 h30 v150 h-30 Z", laterality: "right", locationCode: code("61685007", "Lower limb") },
    { id: "paed-burn-leg-left-front", label: "Left leg, front (6.75%)", group: "front", svgPath: "M100 222 h30 v150 h-30 Z", laterality: "left", locationCode: code("61685007", "Lower limb") },
    { id: "paed-burn-head-back", label: "Head and neck, back (9%)", group: "back", svgPath: "M300 12 a36 36 0 0 1 36 36 a36 36 0 0 1 -72 0 a36 36 0 0 1 36 -36 Z", laterality: "midline", locationCode: code("69536005", "Head") },
    { id: "paed-burn-upper-back", label: "Upper back (9%)", group: "back", svgPath: "M264 94 h72 v52 h-72 Z", laterality: "midline", locationCode: unmapped("back-of-trunk", "Back of trunk") },
    { id: "paed-burn-lower-back", label: "Lower back and buttocks (9%)", group: "back", svgPath: "M264 146 h72 v52 h-72 Z", laterality: "midline", locationCode: unmapped("back-of-trunk", "Back of trunk") },
    { id: "paed-burn-arm-right-back", label: "Right arm, back (4.5%)", group: "back", svgPath: "M236 98 h28 v92 h-28 Z", laterality: "right", locationCode: code("40983000", "Upper limb") },
    { id: "paed-burn-arm-left-back", label: "Left arm, back (4.5%)", group: "back", svgPath: "M336 98 h28 v92 h-28 Z", laterality: "left", locationCode: code("40983000", "Upper limb") },
    { id: "paed-burn-leg-right-back", label: "Right leg, back (6.75%)", group: "back", svgPath: "M270 222 h30 v150 h-30 Z", laterality: "right", locationCode: code("61685007", "Lower limb") },
    { id: "paed-burn-leg-left-back", label: "Left leg, back (6.75%)", group: "back", svgPath: "M300 222 h30 v150 h-30 Z", laterality: "left", locationCode: code("61685007", "Lower limb") },
  ],
};

/** Paediatric TBSA contribution per region id (infant rule-of-nines variant). Sums to 100. */
export const BURNS_PAEDIATRIC_TBSA_PERCENT: Record<string, number> = {
  "paed-burn-head-front": 9,
  "paed-burn-chest-front": 9,
  "paed-burn-abdomen-front": 9,
  "paed-burn-arm-right-front": 4.5,
  "paed-burn-arm-left-front": 4.5,
  "paed-burn-perineum": 1,
  "paed-burn-leg-right-front": 6.75,
  "paed-burn-leg-left-front": 6.75,
  "paed-burn-head-back": 9,
  "paed-burn-upper-back": 9,
  "paed-burn-lower-back": 9,
  "paed-burn-arm-right-back": 4.5,
  "paed-burn-arm-left-back": 4.5,
  "paed-burn-leg-right-back": 6.75,
  "paed-burn-leg-left-back": 6.75,
};

/**
 * Total burn surface area for a set of marked region ids.
 *
 * Ids are de-duplicated: marking a region twice is a click, not a second burn, and a TBSA that
 * silently doubles is worse than no TBSA at all. Unknown ids contribute nothing and are
 * ignored rather than defaulted, so a typo cannot inflate a fluid prescription.
 */
export function totalBurnSurfaceArea(
  regionIds: string[],
  table: Record<string, number>,
): number {
  const unique = Array.from(new Set(regionIds));
  const total = unique.reduce((sum, id) => sum + (table[id] ?? 0), 0);
  return Math.round(total * 100) / 100;
}

/**
 * Pressure-injury risk sites, posterior figure.
 *
 * Grouped by the position that puts the site under load — supine, lateral, sitting — because
 * that is what a repositioning plan is written against. A heel and an ischial tuberosity are
 * never at risk in the same posture.
 */
export const PRESSURE_INJURY_SITES_MAP: RegionMapDef = {
  id: "pressure-injury-sites",
  title: "Pressure injury risk sites",
  viewBox: "0 0 200 400",
  silhouettePaths: [
    "M100 12 a26 26 0 0 1 26 26 a26 26 0 0 1 -52 0 a26 26 0 0 1 26 -26 Z",
    "M86 64 h28 v14 h-28 Z",
    "M62 78 h76 v112 h-76 Z",
    "M70 190 h60 v54 h-60 Z",
    "M32 82 h30 v104 h-30 Z",
    "M138 82 h30 v104 h-30 Z",
    "M74 244 h22 v140 h-22 Z",
    "M104 244 h22 v140 h-22 Z",
  ],
  regions: [
    { id: "pressure-occiput", label: "Occiput", group: "supine", svgPath: "M84 16 h32 v24 h-32 Z", laterality: "midline", locationCode: code("69536005", "Head") },
    { id: "pressure-scapula-right", label: "Right scapula", group: "supine", svgPath: "M64 84 h32 v30 h-32 Z", laterality: "right", locationCode: unmapped("scapula", "Scapula") },
    { id: "pressure-scapula-left", label: "Left scapula", group: "supine", svgPath: "M104 84 h32 v30 h-32 Z", laterality: "left", locationCode: unmapped("scapula", "Scapula") },
    { id: "pressure-elbow-right", label: "Right elbow", group: "supine", svgPath: "M32 168 h24 v22 h-24 Z", laterality: "right", locationCode: unmapped("olecranon", "Elbow (olecranon)") },
    { id: "pressure-elbow-left", label: "Left elbow", group: "supine", svgPath: "M144 168 h24 v22 h-24 Z", laterality: "left", locationCode: unmapped("olecranon", "Elbow (olecranon)") },
    { id: "pressure-sacrum", label: "Sacrum", group: "supine", svgPath: "M84 194 h32 v28 h-32 Z", laterality: "midline", locationCode: code("54735007", "Sacrococcygeal region") },
    { id: "pressure-heel-right", label: "Right heel", group: "supine", svgPath: "M74 358 h22 v26 h-22 Z", laterality: "right", locationCode: unmapped("heel", "Heel") },
    { id: "pressure-heel-left", label: "Left heel", group: "supine", svgPath: "M104 358 h22 v26 h-22 Z", laterality: "left", locationCode: unmapped("heel", "Heel") },
    { id: "pressure-trochanter-right", label: "Right greater trochanter", group: "lateral", svgPath: "M52 198 h20 v26 h-20 Z", laterality: "right", locationCode: unmapped("greater-trochanter", "Greater trochanter of femur") },
    { id: "pressure-trochanter-left", label: "Left greater trochanter", group: "lateral", svgPath: "M128 198 h20 v26 h-20 Z", laterality: "left", locationCode: unmapped("greater-trochanter", "Greater trochanter of femur") },
    { id: "pressure-malleolus-right", label: "Right lateral malleolus", group: "lateral", svgPath: "M56 332 h18 v22 h-18 Z", laterality: "right", locationCode: unmapped("lateral-malleolus", "Lateral malleolus") },
    { id: "pressure-malleolus-left", label: "Left lateral malleolus", group: "lateral", svgPath: "M126 332 h18 v22 h-18 Z", laterality: "left", locationCode: unmapped("lateral-malleolus", "Lateral malleolus") },
    { id: "pressure-ischial-right", label: "Right ischial tuberosity", group: "sitting", svgPath: "M70 226 h24 v20 h-24 Z", laterality: "right", locationCode: unmapped("ischial-tuberosity", "Ischial tuberosity") },
    { id: "pressure-ischial-left", label: "Left ischial tuberosity", group: "sitting", svgPath: "M106 226 h24 v20 h-24 Z", laterality: "left", locationCode: unmapped("ischial-tuberosity", "Ischial tuberosity") },
  ],
};

/**
 * Surgical drain and enteral-access sites.
 *
 * Drain sites are recorded against the space drained, not the skin puncture, because that is
 * what the output means: "300 ml from the pelvic drain" and "300 ml from the subhepatic drain"
 * are different clinical events even when the tubes exit two centimetres apart. The nasogastric
 * inset at the top is drawn so the enteral route can be selected on the same instrument.
 */
export const WOUND_DRAIN_SITES_MAP: RegionMapDef = {
  id: "wound-drain-sites",
  title: "Drain, wound and enteral access sites",
  viewBox: "0 0 200 280",
  silhouettePaths: [
    "M100 8 a24 20 0 0 1 0 40 a24 20 0 0 1 0 -40 Z",
    "M28 56 h144 v206 h-144 Z",
    "M92 92 h16 v110 h-16 Z",
  ],
  regions: [
    { id: "ng-tube-site", label: "Nasogastric route", group: "enteral access", svgPath: "M84 10 h32 v34 h-32 Z", laterality: "midline", locationCode: unmapped("nasogastric-route", "Nasogastric route") },
    { id: "peg-site", label: "PEG / gastrostomy site", group: "enteral access", svgPath: "M108 96 h40 v28 h-40 Z", laterality: "left", locationCode: code("113345001", "Abdomen") },
    { id: "drain-subphrenic-right", label: "Right subphrenic drain", group: "intra-abdominal drains", svgPath: "M34 62 h58 v28 h-58 Z", laterality: "right", locationCode: code("113345001", "Abdomen") },
    { id: "drain-subphrenic-left", label: "Left subphrenic drain", group: "intra-abdominal drains", svgPath: "M108 62 h58 v28 h-58 Z", laterality: "left", locationCode: code("113345001", "Abdomen") },
    { id: "drain-subhepatic", label: "Subhepatic drain (Morison's pouch)", group: "intra-abdominal drains", svgPath: "M34 92 h58 v30 h-58 Z", laterality: "right", locationCode: code("113345001", "Abdomen") },
    { id: "drain-paracolic-right", label: "Right paracolic gutter drain", group: "intra-abdominal drains", svgPath: "M28 126 h32 v72 h-32 Z", laterality: "right", locationCode: code("113345001", "Abdomen") },
    { id: "drain-paracolic-left", label: "Left paracolic gutter drain", group: "intra-abdominal drains", svgPath: "M140 126 h32 v72 h-32 Z", laterality: "left", locationCode: code("113345001", "Abdomen") },
    { id: "drain-pelvic", label: "Pelvic drain", group: "intra-abdominal drains", svgPath: "M70 202 h60 v40 h-60 Z", laterality: "midline", locationCode: code("113345001", "Abdomen") },
    { id: "drain-exit-flank-right", label: "Right flank drain exit site", group: "exit sites", svgPath: "M8 200 h18 v24 h-18 Z", laterality: "right", locationCode: code("113345001", "Abdomen") },
    { id: "drain-exit-flank-left", label: "Left flank drain exit site", group: "exit sites", svgPath: "M174 200 h18 v24 h-18 Z", laterality: "left", locationCode: code("113345001", "Abdomen") },
    { id: "wound-midline-upper", label: "Upper midline wound edge", group: "wound edges", svgPath: "M92 92 h16 v54 h-16 Z", laterality: "midline", locationCode: code("113345001", "Abdomen") },
    { id: "wound-midline-lower", label: "Lower midline wound edge", group: "wound edges", svgPath: "M92 146 h16 v56 h-16 Z", laterality: "midline", locationCode: code("113345001", "Abdomen") },
    { id: "wound-pfannenstiel", label: "Pfannenstiel wound edge", group: "wound edges", svgPath: "M64 244 h72 v18 h-72 Z", laterality: "midline", locationCode: code("113345001", "Abdomen") },
  ],
};

/** Every burn, pressure-injury and drain-site map in this module, for registration and tests. */
export const SURGICAL_WOUND_BURN_MAPS: RegionMapDef[] = [
  BURNS_ADULT_MAP,
  BURNS_PAEDIATRIC_MAP,
  PRESSURE_INJURY_SITES_MAP,
  WOUND_DRAIN_SITES_MAP,
];
