import {
  SURGICAL_ABDOMINAL_MAPS,
} from "../regions/surgical-maps-abdominal";
import {
  SURGICAL_LIMB_MAPS,
} from "../regions/surgical-maps-limbs";
import {
  SURGICAL_VASCULAR_MAPS,
} from "../regions/surgical-maps-vascular";
import {
  SURGICAL_HEAD_NECK_MAPS,
} from "../regions/surgical-maps-head-neck";
import {
  SURGICAL_SPECIALTY_MAPS,
} from "../regions/surgical-maps-specialty";
import {
  SURGICAL_WOUND_BURN_MAPS,
} from "../regions/surgical-maps-wounds-burns";
import type { BodyMapInstrument } from "../core/types";

/**
 * Surgical site-marking instruments (SB-4, surgical domain pack §7/§12 — the pipeline's own
 * §12 maps and the surgical pack's §7 maps overlap heavily and are built once as one feature,
 * per the pipeline audit's own consequence #5).
 *
 * <p>Unlike {@code GENERAL_BODY_INSTRUMENT} and its siblings (clinical EXAM findings — a rash,
 * reduced air entry), these instruments answer a narrower, safety-critical question: WHERE is
 * the planned operative site, confirmed before the WHO Time Out, on the map appropriate to the
 * specialty involved. The morphology options below are deliberately about SITE MARKING, not
 * diagnostic findings — a rash instrument asks "what is this", a site-marking instrument asks
 * "is this the right place and the right side".</p>
 *
 * <p>All 37 maps are ENGINEERING-AUTHORED pending clinical verification (see each region file's
 * own header and {@code SURGICAL-MAPS-REGISTER.md}/{@code SURGICAL-MAPS-REGISTER-2.md}) — real,
 * usable region geometry, not yet MoHCC-ratified for every SNOMED binding.</p>
 */

const SITE_MARKING_MORPHOLOGY = [
  { code: "planned_site", display: "Planned operative site" },
  { code: "confirmed_site", display: "Confirmed at Time Out" },
  { code: "previous_scar", display: "Previous scar/incision" },
  { code: "avoid_site", display: "Avoid — infected/compromised skin" },
  { code: "marked_not_confirmed", display: "Marked, not yet confirmed" },
];

export const SURGICAL_ABDOMINAL_SITE_INSTRUMENT: BodyMapInstrument = {
  id: "surgical-abdominal-site-marking",
  title: "Abdominal/pelvic surgical site marking",
  description: "Incision, stoma and organ-specific site marking for abdominal and pelvic surgery.",
  maps: SURGICAL_ABDOMINAL_MAPS,
  morphologyOptions: SITE_MARKING_MORPHOLOGY,
  asksSeverity: false,
  asksLaterality: true,
  asksPhotoConsent: true,
};

export const SURGICAL_LIMB_SITE_INSTRUMENT: BodyMapInstrument = {
  id: "surgical-limb-site-marking",
  title: "Limb surgical site marking",
  description: "Bone, joint and soft-tissue site marking for orthopaedic and limb surgery — asks laterality on every map (hand/foot maps carry no per-region side, so the instrument-level laterality answer is the one that governs).",
  maps: SURGICAL_LIMB_MAPS,
  morphologyOptions: SITE_MARKING_MORPHOLOGY,
  asksSeverity: false,
  asksLaterality: true,
  asksPhotoConsent: true,
};

export const SURGICAL_VASCULAR_SITE_INSTRUMENT: BodyMapInstrument = {
  id: "surgical-vascular-site-marking",
  title: "Vascular access and amputation-level site marking",
  description: "Arterial/venous access, amputation level and vascular-access-site marking.",
  maps: SURGICAL_VASCULAR_MAPS,
  morphologyOptions: SITE_MARKING_MORPHOLOGY,
  asksSeverity: false,
  asksLaterality: true,
  asksPhotoConsent: true,
};

export const SURGICAL_HEAD_NECK_SITE_INSTRUMENT: BodyMapInstrument = {
  id: "surgical-head-neck-site-marking",
  title: "Head and neck surgical site marking",
  description: "Cranial, maxillofacial, ENT, ophthalmic and neck site marking.",
  maps: SURGICAL_HEAD_NECK_MAPS,
  morphologyOptions: SITE_MARKING_MORPHOLOGY,
  asksSeverity: false,
  asksLaterality: true,
  asksPhotoConsent: true,
};

export const SURGICAL_SPECIALTY_SITE_INSTRUMENT: BodyMapInstrument = {
  id: "surgical-specialty-site-marking",
  title: "Specialty surgical site marking",
  description: "Breast, urological, reproductive and cardiothoracic site marking.",
  maps: SURGICAL_SPECIALTY_MAPS,
  morphologyOptions: SITE_MARKING_MORPHOLOGY,
  asksSeverity: false,
  asksLaterality: true,
  asksPhotoConsent: true,
};

export const SURGICAL_WOUND_BURN_INSTRUMENT: BodyMapInstrument = {
  id: "surgical-wound-burn-assessment",
  title: "Burns, pressure injury and drain-site assessment",
  description: "Rule-of-nines burns mapping (adult/paediatric), pressure-injury risk sites and common drain sites.",
  maps: SURGICAL_WOUND_BURN_MAPS,
  morphologyOptions: [
    ...SITE_MARKING_MORPHOLOGY,
    { code: "burn_zone", display: "Burn — this zone affected" },
    { code: "pressure_risk", display: "Pressure-injury risk site" },
  ],
  asksSeverity: true,
  asksLaterality: true,
  asksPhotoConsent: true,
};

export const SURGICAL_SITE_MARKING_INSTRUMENTS: Record<string, BodyMapInstrument> = {
  [SURGICAL_ABDOMINAL_SITE_INSTRUMENT.id]: SURGICAL_ABDOMINAL_SITE_INSTRUMENT,
  [SURGICAL_LIMB_SITE_INSTRUMENT.id]: SURGICAL_LIMB_SITE_INSTRUMENT,
  [SURGICAL_VASCULAR_SITE_INSTRUMENT.id]: SURGICAL_VASCULAR_SITE_INSTRUMENT,
  [SURGICAL_HEAD_NECK_SITE_INSTRUMENT.id]: SURGICAL_HEAD_NECK_SITE_INSTRUMENT,
  [SURGICAL_SPECIALTY_SITE_INSTRUMENT.id]: SURGICAL_SPECIALTY_SITE_INSTRUMENT,
  [SURGICAL_WOUND_BURN_INSTRUMENT.id]: SURGICAL_WOUND_BURN_INSTRUMENT,
};
