import { SNOMED_BODY_STRUCTURE, type RegionMapDef } from "../core/types";
import { pending } from "./surgical-maps-shared";

/**
 * Adult-medicine examination maps for the four V113 graphics that have no existing body-map.
 *
 * Chest, abdomen, joints, foot, arterial pulses, pressure sites and general skin already exist
 * elsewhere in this feature; these four fill the remaining §7 diagrams without inventing a
 * parallel examination shape.
 *
 * ENGINEERING-AUTHORED pending clinical verification — same discipline as the surgical maps:
 * usable hit areas, loudly pending SNOMED where the exact code is not yet sourced.
 */

const code = (id: string, display: string) => ({
  system: SNOMED_BODY_STRUCTURE,
  code: id,
  display,
});

/**
 * Cardiac auscultation areas.
 *
 * Aortic, pulmonary, tricuspid, mitral and Erb's point are the places a clinician actually puts
 * the stethoscope; recording "murmur" without a site loses the comparison that decides urgency.
 */
export const CARDIAC_AUSCULTATION_MAP: RegionMapDef = {
  id: "cardiac-auscultation",
  title: "Cardiac auscultation",
  viewBox: "0 0 200 220",
  silhouettePaths: [
    "M40 20 h120 v180 h-120 Z",
    "M96 20 h8 v180 h-8 Z",
  ],
  regions: [
    {
      id: "aortic",
      label: "Aortic area (2RICS)",
      group: "valves",
      svgPath: "M108 36 h40 v36 h-40 Z",
      laterality: "right",
      locationCode: pending("aortic-auscultation-area", "Aortic auscultation area"),
    },
    {
      id: "pulmonary",
      label: "Pulmonary area (2LICS)",
      group: "valves",
      svgPath: "M52 36 h40 v36 h-40 Z",
      laterality: "left",
      locationCode: pending("pulmonary-auscultation-area", "Pulmonary auscultation area"),
    },
    {
      id: "erbs",
      label: "Erb's point (3LICS)",
      group: "valves",
      svgPath: "M52 78 h40 v32 h-40 Z",
      laterality: "left",
      locationCode: pending("erbs-point", "Erb's point"),
    },
    {
      id: "tricuspid",
      label: "Tricuspid area (LLSB)",
      group: "valves",
      svgPath: "M72 116 h40 v36 h-40 Z",
      laterality: "midline",
      locationCode: pending("tricuspid-auscultation-area", "Tricuspid auscultation area"),
    },
    {
      id: "mitral",
      label: "Mitral area (apex)",
      group: "valves",
      svgPath: "M44 156 h48 v36 h-48 Z",
      laterality: "left",
      locationCode: code("13383001", "Structure of apex of heart"),
    },
  ],
};

/**
 * Neurological deficit sites at the limb/face grain a bedside exam uses.
 *
 * Finer than a whole-body map, coarser than a dermatome chart — the places stroke and neuropathy
 * screens actually mark.
 */
export const NEUROLOGICAL_DEFICITS_MAP: RegionMapDef = {
  id: "neurological-deficits",
  title: "Neurological deficits",
  viewBox: "0 0 240 400",
  silhouettePaths: [
    "M120 8 a22 22 0 0 1 22 22 a22 22 0 0 1 -44 0 a22 22 0 0 1 22 -22 Z",
    "M92 70 h56 v140 h-56 Z",
    "M62 74 h30 v186 h-30 Z",
    "M148 74 h30 v186 h-30 Z",
    "M92 210 h26 v182 h-26 Z",
    "M122 210 h26 v182 h-26 Z",
  ],
  regions: [
    {
      id: "face-right",
      label: "Right face",
      group: "face",
      svgPath: "M98 20 h22 v28 h-22 Z",
      laterality: "right",
      locationCode: code("89545001", "Face structure"),
    },
    {
      id: "face-left",
      label: "Left face",
      group: "face",
      svgPath: "M120 20 h22 v28 h-22 Z",
      laterality: "left",
      locationCode: code("89545001", "Face structure"),
    },
    {
      id: "arm-right",
      label: "Right arm",
      group: "upper limb",
      svgPath: "M62 74 h30 v186 h-30 Z",
      laterality: "right",
      locationCode: code("53120007", "Upper limb structure"),
    },
    {
      id: "arm-left",
      label: "Left arm",
      group: "upper limb",
      svgPath: "M148 74 h30 v186 h-30 Z",
      laterality: "left",
      locationCode: code("53120007", "Upper limb structure"),
    },
    {
      id: "leg-right",
      label: "Right leg",
      group: "lower limb",
      svgPath: "M92 210 h26 v182 h-26 Z",
      laterality: "right",
      locationCode: code("61685007", "Lower limb structure"),
    },
    {
      id: "leg-left",
      label: "Left leg",
      group: "lower limb",
      svgPath: "M122 210 h26 v182 h-26 Z",
      laterality: "left",
      locationCode: code("61685007", "Lower limb structure"),
    },
  ],
};

/**
 * Key dermatomes for sensory-level documentation.
 *
 * A full dermatome atlas is teaching material; these are the levels a clinician commonly pins
 * when documenting a sensory level or radiculopathy at the bedside.
 */
export const DERMATOMES_MAP: RegionMapDef = {
  id: "dermatomes",
  title: "Dermatomes",
  viewBox: "0 0 200 420",
  silhouettePaths: [
    "M100 8 a20 20 0 0 1 20 20 a20 20 0 0 1 -40 0 a20 20 0 0 1 20 -20 Z",
    "M70 56 h60 v340 h-60 Z",
  ],
  regions: [
    {
      id: "c5",
      label: "C5",
      group: "cervical",
      svgPath: "M70 56 h60 v28 h-60 Z",
      laterality: "midline",
      locationCode: pending("dermatome-c5", "C5 dermatome"),
    },
    {
      id: "c6",
      label: "C6",
      group: "cervical",
      svgPath: "M70 84 h60 v28 h-60 Z",
      laterality: "midline",
      locationCode: pending("dermatome-c6", "C6 dermatome"),
    },
    {
      id: "c7",
      label: "C7",
      group: "cervical",
      svgPath: "M70 112 h60 v28 h-60 Z",
      laterality: "midline",
      locationCode: pending("dermatome-c7", "C7 dermatome"),
    },
    {
      id: "c8",
      label: "C8",
      group: "cervical",
      svgPath: "M70 140 h60 v28 h-60 Z",
      laterality: "midline",
      locationCode: pending("dermatome-c8", "C8 dermatome"),
    },
    {
      id: "t4",
      label: "T4 (nipple line)",
      group: "thoracic",
      svgPath: "M70 168 h60 v28 h-60 Z",
      laterality: "midline",
      locationCode: pending("dermatome-t4", "T4 dermatome"),
    },
    {
      id: "t10",
      label: "T10 (umbilicus)",
      group: "thoracic",
      svgPath: "M70 196 h60 v28 h-60 Z",
      laterality: "midline",
      locationCode: pending("dermatome-t10", "T10 dermatome"),
    },
    {
      id: "l1",
      label: "L1",
      group: "lumbar",
      svgPath: "M70 224 h60 v28 h-60 Z",
      laterality: "midline",
      locationCode: pending("dermatome-l1", "L1 dermatome"),
    },
    {
      id: "l3",
      label: "L3",
      group: "lumbar",
      svgPath: "M70 252 h60 v28 h-60 Z",
      laterality: "midline",
      locationCode: pending("dermatome-l3", "L3 dermatome"),
    },
    {
      id: "l4",
      label: "L4",
      group: "lumbar",
      svgPath: "M70 280 h60 v28 h-60 Z",
      laterality: "midline",
      locationCode: pending("dermatome-l4", "L4 dermatome"),
    },
    {
      id: "l5",
      label: "L5",
      group: "lumbar",
      svgPath: "M70 308 h60 v28 h-60 Z",
      laterality: "midline",
      locationCode: pending("dermatome-l5", "L5 dermatome"),
    },
    {
      id: "s1",
      label: "S1",
      group: "sacral",
      svgPath: "M70 336 h60 v28 h-60 Z",
      laterality: "midline",
      locationCode: pending("dermatome-s1", "S1 dermatome"),
    },
  ],
};

/**
 * Oedema sites the examination framework's OEDEMA region is meant to locate.
 */
export const OEDEMA_SITES_MAP: RegionMapDef = {
  id: "oedema-sites",
  title: "Oedema sites",
  viewBox: "0 0 240 400",
  silhouettePaths: [
    "M120 8 a22 22 0 0 1 22 22 a22 22 0 0 1 -44 0 a22 22 0 0 1 22 -22 Z",
    "M92 70 h56 v140 h-56 Z",
    "M62 74 h30 v100 h-30 Z",
    "M148 74 h30 v100 h-30 Z",
    "M92 210 h26 v182 h-26 Z",
    "M122 210 h26 v182 h-26 Z",
  ],
  regions: [
    {
      id: "hand-right",
      label: "Right hand / fingers",
      group: "upper limb",
      svgPath: "M52 160 h28 v28 h-28 Z",
      laterality: "right",
      locationCode: code("85562004", "Hand structure"),
    },
    {
      id: "hand-left",
      label: "Left hand / fingers",
      group: "upper limb",
      svgPath: "M160 160 h28 v28 h-28 Z",
      laterality: "left",
      locationCode: code("85562004", "Hand structure"),
    },
    {
      id: "sacral",
      label: "Sacral oedema",
      group: "trunk",
      svgPath: "M100 200 h40 v28 h-40 Z",
      laterality: "midline",
      locationCode: code("54735007", "Sacrococcygeal region"),
    },
    {
      id: "pretibial-right",
      label: "Right pretibial",
      group: "lower limb",
      svgPath: "M88 280 h30 v50 h-30 Z",
      laterality: "right",
      locationCode: pending("pretibial-region", "Pretibial region"),
    },
    {
      id: "pretibial-left",
      label: "Left pretibial",
      group: "lower limb",
      svgPath: "M122 280 h30 v50 h-30 Z",
      laterality: "left",
      locationCode: pending("pretibial-region", "Pretibial region"),
    },
    {
      id: "ankle-right",
      label: "Right ankle / malleolus",
      group: "lower limb",
      svgPath: "M88 340 h30 v40 h-30 Z",
      laterality: "right",
      locationCode: code("344001", "Ankle region structure"),
    },
    {
      id: "ankle-left",
      label: "Left ankle / malleolus",
      group: "lower limb",
      svgPath: "M122 340 h30 v40 h-30 Z",
      laterality: "left",
      locationCode: code("344001", "Ankle region structure"),
    },
  ],
};
