import { CHILD_BODY_BACK, CHILD_BODY_FRONT } from "../regions/child-body";
import {
  ABDOMEN_NINE_REGION_MAP,
  ABDOMEN_QUADRANT_MAP,
  NEWBORN_EXAM_MAP,
  RESPIRATORY_ZONES_MAP,
} from "../regions/clinical-maps";
import { SURGICAL_SITE_MARKING_INSTRUMENTS } from "./surgical-instruments";
import type { BodyMapInstrument, RegionMapDef } from "../core/types";

/**
 * The examination instruments available to a form.
 *
 * An instrument pairs a map with the structured questions worth asking for that kind of
 * finding: a rash needs morphology, an abdomen needs none, a wound needs photo consent.
 * Asking every question on every map is what makes structured examination slower than
 * prose, which is how structured examination stops being used.
 */

export const GENERAL_BODY_INSTRUMENT: BodyMapInstrument = {
  id: "general-body",
  title: "General examination",
  description: "Rashes, wounds, swelling, bruising, tenderness and other findings by site.",
  maps: [CHILD_BODY_FRONT, CHILD_BODY_BACK],
  morphologyOptions: [
    { code: "rash", display: "Rash" },
    { code: "wound", display: "Wound" },
    { code: "swelling", display: "Swelling" },
    { code: "bruise", display: "Bruise" },
    { code: "tenderness", display: "Tender" },
    { code: "scar", display: "Scar" },
  ],
  asksSeverity: true,
  asksLaterality: true,
  // Bruising and wounds in a child can be a safeguarding matter, and a photograph is
  // sometimes part of that record — so consent has to be recordable at the point of finding.
  asksPhotoConsent: true,
};

export const NEWBORN_EXAM_INSTRUMENT: BodyMapInstrument = {
  id: "newborn-exam",
  title: "Newborn examination",
  description: "The routine newborn check: fontanelle, red reflex, cord, femoral pulses, hips, spine.",
  maps: [NEWBORN_EXAM_MAP],
  morphologyOptions: [
    { code: "normal", display: "Normal" },
    { code: "abnormal", display: "Abnormal" },
    { code: "not_examined", display: "Not examined" },
  ],
  asksSeverity: false,
  asksLaterality: true,
  asksPhotoConsent: false,
};

export const RESPIRATORY_INSTRUMENT: BodyMapInstrument = {
  id: "respiratory",
  title: "Respiratory examination",
  description: "Auscultation and percussion findings by zone.",
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

export const ABDOMEN_INSTRUMENT: BodyMapInstrument = {
  id: "abdomen",
  title: "Abdominal examination",
  description: "Tenderness, guarding, masses and organomegaly by region.",
  maps: [ABDOMEN_NINE_REGION_MAP, ABDOMEN_QUADRANT_MAP],
  morphologyOptions: [
    { code: "tender", display: "Tender" },
    { code: "guarding", display: "Guarding" },
    { code: "rebound", display: "Rebound" },
    { code: "mass", display: "Mass" },
    { code: "organomegaly", display: "Organomegaly" },
    { code: "scar", display: "Scar or stoma" },
  ],
  asksSeverity: true,
  asksLaterality: false,
  asksPhotoConsent: false,
};

export const BODY_MAP_INSTRUMENTS: Record<string, BodyMapInstrument> = {
  [GENERAL_BODY_INSTRUMENT.id]: GENERAL_BODY_INSTRUMENT,
  [NEWBORN_EXAM_INSTRUMENT.id]: NEWBORN_EXAM_INSTRUMENT,
  [RESPIRATORY_INSTRUMENT.id]: RESPIRATORY_INSTRUMENT,
  [ABDOMEN_INSTRUMENT.id]: ABDOMEN_INSTRUMENT,
  // SB-4 (surgical pack §7 / pipeline §12 — built once, one feature): surgical site-marking
  // instruments over the 37 new region maps, grouped by anatomical area in ./surgical-instruments.
  ...SURGICAL_SITE_MARKING_INSTRUMENTS,
};

export function instrumentById(id: string | undefined): BodyMapInstrument | undefined {
  return id ? BODY_MAP_INSTRUMENTS[id] : undefined;
}

export function allRegions(instrument: BodyMapInstrument): RegionMapDef["regions"] {
  return instrument.maps.flatMap((map) => map.regions);
}
