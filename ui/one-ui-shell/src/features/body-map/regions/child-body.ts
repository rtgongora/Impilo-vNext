import { SNOMED_BODY_STRUCTURE, type RegionMapDef } from "../core/types";

/**
 * General child body map, front and back.
 *
 * The shapes are schematic rather than anatomically illustrative, and that is deliberate:
 * a clinician marking a rash needs unambiguous, finger-sized regions on a small screen, not
 * a detailed drawing whose boundaries are hard to hit and whose realism implies a precision
 * the record does not have. Every region is one a clinician would actually record against.
 */

const code = (id: string, display: string) => ({
  system: SNOMED_BODY_STRUCTURE,
  code: id,
  display,
});

export const CHILD_BODY_FRONT: RegionMapDef = {
  id: "child-body-front",
  title: "Front",
  viewBox: "0 0 200 400",
  silhouettePaths: [
    // Outline: head, neck, torso, arms, legs — drawn beneath the regions so the map reads
    // as a body rather than a scatter of shapes.
    "M100 12 a26 26 0 0 1 26 26 a26 26 0 0 1 -52 0 a26 26 0 0 1 26 -26 Z",
    "M86 64 h28 v14 h-28 Z",
    "M62 78 h76 v112 h-76 Z",
    "M70 190 h60 v54 h-60 Z",
    "M62 82 l-30 12 v92 h20 v-84 l10 -6 Z",
    "M138 82 l30 12 v92 h-20 v-84 l-10 -6 Z",
    "M74 244 h22 v140 h-22 Z",
    "M104 244 h22 v140 h-22 Z",
  ],
  regions: [
    { id: "head-face", label: "Face and scalp", group: "head", svgPath: "M100 12 a26 26 0 0 1 26 26 a26 26 0 0 1 -52 0 a26 26 0 0 1 26 -26 Z", laterality: "midline", locationCode: code("69536005", "Head") },
    { id: "neck-front", label: "Neck", group: "head", svgPath: "M86 64 h28 v14 h-28 Z", laterality: "midline", locationCode: code("45048000", "Neck") },
    { id: "chest-left", label: "Left chest", group: "torso", svgPath: "M100 78 h38 v56 h-38 Z", laterality: "left", locationCode: code("78904004", "Chest") },
    { id: "chest-right", label: "Right chest", group: "torso", svgPath: "M62 78 h38 v56 h-38 Z", laterality: "right", locationCode: code("78904004", "Chest") },
    { id: "abdomen", label: "Abdomen", group: "torso", svgPath: "M62 134 h76 v56 h-76 Z", laterality: "midline", locationCode: code("113345001", "Abdomen") },
    { id: "groin", label: "Groin and genitalia", group: "torso", svgPath: "M70 190 h60 v54 h-60 Z", laterality: "midline", locationCode: code("26107004", "Perineum") },
    { id: "arm-left", label: "Left arm", group: "upper limb", svgPath: "M138 82 l30 12 v92 h-20 v-84 l-10 -6 Z", laterality: "left", locationCode: code("40983000", "Upper limb") },
    { id: "arm-right", label: "Right arm", group: "upper limb", svgPath: "M62 82 l-30 12 v92 h20 v-84 l10 -6 Z", laterality: "right", locationCode: code("40983000", "Upper limb") },
    { id: "hand-left", label: "Left hand", group: "upper limb", svgPath: "M148 186 h20 v22 h-20 Z", laterality: "left", locationCode: code("85562004", "Hand") },
    { id: "hand-right", label: "Right hand", group: "upper limb", svgPath: "M32 186 h20 v22 h-20 Z", laterality: "right", locationCode: code("85562004", "Hand") },
    { id: "leg-left", label: "Left leg", group: "lower limb", svgPath: "M104 244 h22 v112 h-22 Z", laterality: "left", locationCode: code("61685007", "Lower limb") },
    { id: "leg-right", label: "Right leg", group: "lower limb", svgPath: "M74 244 h22 v112 h-22 Z", laterality: "right", locationCode: code("61685007", "Lower limb") },
    { id: "foot-left", label: "Left foot", group: "lower limb", svgPath: "M104 356 h22 v28 h-22 Z", laterality: "left", locationCode: code("56459004", "Foot") },
    { id: "foot-right", label: "Right foot", group: "lower limb", svgPath: "M74 356 h22 v28 h-22 Z", laterality: "right", locationCode: code("56459004", "Foot") },
  ],
};

export const CHILD_BODY_BACK: RegionMapDef = {
  id: "child-body-back",
  title: "Back",
  viewBox: "0 0 200 400",
  silhouettePaths: CHILD_BODY_FRONT.silhouettePaths,
  regions: [
    { id: "scalp-back", label: "Back of head", group: "head", svgPath: "M100 12 a26 26 0 0 1 26 26 a26 26 0 0 1 -52 0 a26 26 0 0 1 26 -26 Z", laterality: "midline", locationCode: code("69536005", "Head") },
    { id: "neck-back", label: "Back of neck", group: "head", svgPath: "M86 64 h28 v14 h-28 Z", laterality: "midline", locationCode: code("45048000", "Neck") },
    { id: "upper-back-left", label: "Left upper back", group: "torso", svgPath: "M100 78 h38 v56 h-38 Z", laterality: "left", locationCode: code("123037004", "Back") },
    { id: "upper-back-right", label: "Right upper back", group: "torso", svgPath: "M62 78 h38 v56 h-38 Z", laterality: "right", locationCode: code("123037004", "Back") },
    { id: "lower-back", label: "Lower back", group: "torso", svgPath: "M62 134 h76 v56 h-76 Z", laterality: "midline", locationCode: code("123037004", "Back") },
    // Sacrum is called out separately: it is where pressure injury and spinal dysraphism
    // are looked for, and both are missed when it is folded into "lower back".
    { id: "sacrum", label: "Sacrum and buttocks", group: "torso", svgPath: "M70 190 h60 v54 h-60 Z", laterality: "midline", locationCode: code("54735007", "Sacrococcygeal region") },
    { id: "arm-back-left", label: "Left arm (back)", group: "upper limb", svgPath: "M138 82 l30 12 v92 h-20 v-84 l-10 -6 Z", laterality: "left", locationCode: code("40983000", "Upper limb") },
    { id: "arm-back-right", label: "Right arm (back)", group: "upper limb", svgPath: "M62 82 l-30 12 v92 h20 v-84 l10 -6 Z", laterality: "right", locationCode: code("40983000", "Upper limb") },
    { id: "leg-back-left", label: "Left leg (back)", group: "lower limb", svgPath: "M104 244 h22 v112 h-22 Z", laterality: "left", locationCode: code("61685007", "Lower limb") },
    { id: "leg-back-right", label: "Right leg (back)", group: "lower limb", svgPath: "M74 244 h22 v112 h-22 Z", laterality: "right", locationCode: code("61685007", "Lower limb") },
  ],
};
