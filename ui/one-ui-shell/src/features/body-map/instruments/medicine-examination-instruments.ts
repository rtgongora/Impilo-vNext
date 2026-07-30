import { CHILD_BODY_BACK, CHILD_BODY_FRONT } from "../regions/child-body";
import {
  ABDOMEN_NINE_REGION_MAP,
  ABDOMEN_QUADRANT_MAP,
  RESPIRATORY_ZONES_MAP,
} from "../regions/clinical-maps";
import {
  CARDIAC_AUSCULTATION_MAP,
  DERMATOMES_MAP,
  NEUROLOGICAL_DEFICITS_MAP,
  OEDEMA_SITES_MAP,
} from "../regions/medicine-exam-maps";
import { FOOT_DETAILED_MAP, MAJOR_JOINTS_MAP } from "../regions/surgical-maps-limbs";
import { ARTERIAL_TREE_MAP } from "../regions/surgical-maps-vascular";
import { PRESSURE_INJURY_SITES_MAP } from "../regions/surgical-maps-wounds-burns";
import type { BodyMapInstrument } from "../core/types";

/**
 * The eleven V113 examination graphics as body-map instruments.
 *
 * Instrument ids are the V113 graphic codes themselves so ExaminationShell can resolve a diagram
 * from the same closed vocabulary the database CHECK accepts — no parallel naming layer.
 */

export const CHEST_AUSCULTATION_INSTRUMENT: BodyMapInstrument = {
  id: "CHEST_AUSCULTATION",
  title: "Chest auscultation",
  description: "Auscultation and percussion findings by respiratory zone.",
  maps: [RESPIRATORY_ZONES_MAP],
  morphologyOptions: [
    { code: "normal_air_entry", display: "Normal" },
    { code: "reduced_air_entry", display: "Reduced" },
    { code: "crackles", display: "Crackles" },
    { code: "wheeze", display: "Wheeze" },
    { code: "bronchial", display: "Bronchial" },
    { code: "dull", display: "Dull" },
  ],
  asksSeverity: false,
  asksLaterality: false,
  asksPhotoConsent: false,
};

export const CARDIAC_FINDINGS_INSTRUMENT: BodyMapInstrument = {
  id: "CARDIAC_FINDINGS",
  title: "Cardiac findings",
  description: "Murmurs and heart sounds by auscultation area.",
  maps: [CARDIAC_AUSCULTATION_MAP],
  morphologyOptions: [
    { code: "normal", display: "Normal" },
    { code: "murmur", display: "Murmur" },
    { code: "gallop", display: "Gallop" },
    { code: "rub", display: "Rub" },
    { code: "click", display: "Click" },
  ],
  asksSeverity: true,
  asksLaterality: false,
  asksPhotoConsent: false,
};

export const ABDOMINAL_REGIONS_INSTRUMENT: BodyMapInstrument = {
  id: "ABDOMINAL_REGIONS",
  title: "Abdominal regions",
  description: "Tenderness, guarding, masses and organomegaly by region.",
  maps: [ABDOMEN_NINE_REGION_MAP, ABDOMEN_QUADRANT_MAP],
  morphologyOptions: [
    { code: "tender", display: "Tender" },
    { code: "guarding", display: "Guarding" },
    { code: "rebound", display: "Rebound" },
    { code: "mass", display: "Mass" },
    { code: "organomegaly", display: "Organomegaly" },
  ],
  asksSeverity: true,
  asksLaterality: false,
  asksPhotoConsent: false,
};

export const NEUROLOGICAL_DEFICITS_INSTRUMENT: BodyMapInstrument = {
  id: "NEUROLOGICAL_DEFICITS",
  title: "Neurological deficits",
  description: "Motor or sensory deficit by face, arm or leg.",
  maps: [NEUROLOGICAL_DEFICITS_MAP],
  morphologyOptions: [
    { code: "weakness", display: "Weakness" },
    { code: "paralysis", display: "Paralysis" },
    { code: "numbness", display: "Numbness" },
    { code: "ataxia", display: "Ataxia" },
  ],
  asksSeverity: true,
  asksLaterality: false,
  asksPhotoConsent: false,
};

export const DERMATOMES_INSTRUMENT: BodyMapInstrument = {
  id: "DERMATOMES",
  title: "Dermatomes",
  description: "Sensory level or radiculopathy by dermatome.",
  maps: [DERMATOMES_MAP],
  morphologyOptions: [
    { code: "altered", display: "Altered sensation" },
    { code: "absent", display: "Absent sensation" },
    { code: "hyper", display: "Hyperaesthesia" },
    { code: "pain", display: "Pain" },
  ],
  asksSeverity: true,
  asksLaterality: true,
  asksPhotoConsent: false,
};

export const PERIPHERAL_PULSES_INSTRUMENT: BodyMapInstrument = {
  id: "PERIPHERAL_PULSES",
  title: "Peripheral pulses",
  description: "Pulse presence and character along the arterial tree.",
  maps: [ARTERIAL_TREE_MAP],
  morphologyOptions: [
    { code: "present", display: "Present" },
    { code: "reduced", display: "Reduced" },
    { code: "absent", display: "Absent" },
    { code: "bounding", display: "Bounding" },
  ],
  asksSeverity: false,
  asksLaterality: false,
  asksPhotoConsent: false,
};

export const OEDEMA_SITES_INSTRUMENT: BodyMapInstrument = {
  id: "OEDEMA_SITES",
  title: "Oedema sites",
  description: "Oedema by dependent and sacral sites.",
  maps: [OEDEMA_SITES_MAP],
  morphologyOptions: [
    { code: "pitting", display: "Pitting" },
    { code: "non_pitting", display: "Non-pitting" },
  ],
  asksSeverity: true,
  asksLaterality: false,
  asksPhotoConsent: false,
};

export const JOINTS_INSTRUMENT: BodyMapInstrument = {
  id: "JOINTS",
  title: "Joints",
  description: "Joint findings by major joint and side.",
  maps: [MAJOR_JOINTS_MAP],
  morphologyOptions: [
    { code: "tender", display: "Tender" },
    { code: "swollen", display: "Swollen" },
    { code: "reduced_rom", display: "Reduced range" },
    { code: "deformity", display: "Deformity" },
  ],
  asksSeverity: true,
  asksLaterality: false,
  asksPhotoConsent: false,
};

export const SKIN_LESIONS_INSTRUMENT: BodyMapInstrument = {
  id: "SKIN_LESIONS",
  title: "Skin lesions",
  description: "Rashes, wounds and lesions by body site.",
  maps: [CHILD_BODY_FRONT, CHILD_BODY_BACK],
  morphologyOptions: [
    { code: "rash", display: "Rash" },
    { code: "wound", display: "Wound" },
    { code: "ulcer", display: "Ulcer" },
    { code: "bruise", display: "Bruise" },
    { code: "scar", display: "Scar" },
  ],
  asksSeverity: true,
  asksLaterality: true,
  asksPhotoConsent: false,
};

export const DIABETIC_FOOT_INSTRUMENT: BodyMapInstrument = {
  id: "DIABETIC_FOOT",
  title: "Diabetic foot",
  description: "Foot examination findings by dorsal and plantar site.",
  maps: [FOOT_DETAILED_MAP],
  morphologyOptions: [
    { code: "ulcer", display: "Ulcer" },
    { code: "callus", display: "Callus" },
    { code: "infection", display: "Infection" },
    { code: "neuropathy", display: "Neuropathy site" },
    { code: "ischemia", display: "Ischaemic change" },
  ],
  asksSeverity: true,
  asksLaterality: true,
  asksPhotoConsent: false,
};

export const PRESSURE_INJURIES_INSTRUMENT: BodyMapInstrument = {
  id: "PRESSURE_INJURIES",
  title: "Pressure injuries",
  description: "Pressure injury by bony prominence.",
  maps: [PRESSURE_INJURY_SITES_MAP],
  morphologyOptions: [
    { code: "stage_1", display: "Stage 1" },
    { code: "stage_2", display: "Stage 2" },
    { code: "stage_3", display: "Stage 3" },
    { code: "stage_4", display: "Stage 4" },
    { code: "unstageable", display: "Unstageable" },
    { code: "dtpi", display: "Deep tissue" },
  ],
  asksSeverity: false,
  asksLaterality: false,
  asksPhotoConsent: false,
};

/** All eleven V113 graphics, keyed by the closed vocabulary code. */
export const MEDICINE_EXAMINATION_INSTRUMENTS: Record<string, BodyMapInstrument> = {
  [CHEST_AUSCULTATION_INSTRUMENT.id]: CHEST_AUSCULTATION_INSTRUMENT,
  [CARDIAC_FINDINGS_INSTRUMENT.id]: CARDIAC_FINDINGS_INSTRUMENT,
  [ABDOMINAL_REGIONS_INSTRUMENT.id]: ABDOMINAL_REGIONS_INSTRUMENT,
  [NEUROLOGICAL_DEFICITS_INSTRUMENT.id]: NEUROLOGICAL_DEFICITS_INSTRUMENT,
  [DERMATOMES_INSTRUMENT.id]: DERMATOMES_INSTRUMENT,
  [PERIPHERAL_PULSES_INSTRUMENT.id]: PERIPHERAL_PULSES_INSTRUMENT,
  [OEDEMA_SITES_INSTRUMENT.id]: OEDEMA_SITES_INSTRUMENT,
  [JOINTS_INSTRUMENT.id]: JOINTS_INSTRUMENT,
  [SKIN_LESIONS_INSTRUMENT.id]: SKIN_LESIONS_INSTRUMENT,
  [DIABETIC_FOOT_INSTRUMENT.id]: DIABETIC_FOOT_INSTRUMENT,
  [PRESSURE_INJURIES_INSTRUMENT.id]: PRESSURE_INJURIES_INSTRUMENT,
};

/** The eleven codes V113 accepts — used by tests that prove the register is complete. */
export const V113_GRAPHIC_CODES = Object.keys(MEDICINE_EXAMINATION_INSTRUMENTS);
