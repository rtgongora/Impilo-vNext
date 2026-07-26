import { SNOMED_BODY_STRUCTURE, type RegionMapDef } from "../core/types";

const code = (id: string, display: string) => ({
  system: SNOMED_BODY_STRUCTURE,
  code: id,
  display,
});

/**
 * Newborn examination map.
 *
 * A newborn examination is a fixed sequence with a fixed set of places to look, and the
 * things being looked for are specific to the first days of life: a fontanelle, the cord
 * stump, the red reflex, femoral pulses, hips, and the sacral dimple that can be the only
 * visible sign of spinal dysraphism. A general body map cannot express any of those, which
 * is why this is a separate instrument rather than a preset over the child map.
 */
export const NEWBORN_EXAM_MAP: RegionMapDef = {
  id: "newborn-exam",
  title: "Newborn examination",
  viewBox: "0 0 200 340",
  silhouettePaths: [
    "M100 10 a30 30 0 0 1 30 30 a30 30 0 0 1 -60 0 a30 30 0 0 1 30 -30 Z",
    "M84 68 h32 v12 h-32 Z",
    "M60 80 h80 v100 h-80 Z",
    "M68 180 h64 v44 h-64 Z",
    "M60 84 l-26 10 v78 h18 v-70 l8 -6 Z",
    "M140 84 l26 10 v78 h-18 v-70 l-8 -6 Z",
    "M74 224 h20 v104 h-20 Z",
    "M106 224 h20 v104 h-20 Z",
  ],
  regions: [
    { id: "fontanelle", label: "Fontanelle and sutures", group: "head", svgPath: "M86 14 h28 v22 h-28 Z", laterality: "midline", locationCode: code("79361005", "Fontanel of skull") },
    { id: "head-shape", label: "Head shape, moulding, caput", group: "head", svgPath: "M70 36 h60 v34 h-60 Z", laterality: "midline", locationCode: code("69536005", "Head") },
    { id: "eyes", label: "Eyes and red reflex", group: "head", svgPath: "M74 40 h52 v12 h-52 Z", laterality: "midline", locationCode: code("81745001", "Eye") },
    { id: "mouth-palate", label: "Mouth and palate", group: "head", svgPath: "M86 54 h28 v10 h-28 Z", laterality: "midline", locationCode: code("21082005", "Palate") },
    { id: "ears", label: "Ears and position", group: "head", svgPath: "M64 38 h10 v20 h-10 Z", laterality: "left", locationCode: code("117590005", "Ear") },
    { id: "chest-newborn", label: "Chest, breathing, heart sounds", group: "torso", svgPath: "M60 80 h80 v52 h-80 Z", laterality: "midline", locationCode: code("78904004", "Chest") },
    { id: "umbilicus", label: "Umbilicus and cord stump", group: "torso", svgPath: "M88 136 h24 v24 h-24 Z", laterality: "midline", locationCode: code("29707007", "Umbilicus") },
    { id: "abdomen-newborn", label: "Abdomen", group: "torso", svgPath: "M60 132 h80 v48 h-80 Z", laterality: "midline", locationCode: code("113345001", "Abdomen") },
    { id: "femoral-pulses", label: "Femoral pulses", group: "torso", svgPath: "M68 180 h64 v20 h-64 Z", laterality: "midline", locationCode: code("7657000", "Femoral artery") },
    { id: "genitalia-anus", label: "Genitalia and patent anus", group: "torso", svgPath: "M78 200 h44 v24 h-44 Z", laterality: "midline", locationCode: code("26107004", "Perineum") },
    { id: "hips", label: "Hips (Ortolani and Barlow)", group: "lower limb", svgPath: "M68 216 h64 v18 h-64 Z", laterality: "midline", locationCode: code("29836001", "Hip region") },
    { id: "spine-sacrum", label: "Spine and sacral dimple", group: "torso", svgPath: "M92 80 h16 v100 h-16 Z", laterality: "midline", locationCode: code("421060004", "Vertebral column") },
    { id: "limbs-newborn", label: "Limbs, digits and tone", group: "limbs", svgPath: "M34 94 h18 v78 h-18 Z", laterality: "right", locationCode: code("66019005", "Limb") },
    { id: "skin-newborn", label: "Skin, birthmarks and jaundice", group: "skin", svgPath: "M106 224 h20 v104 h-20 Z", laterality: "left", locationCode: code("39937001", "Skin") },
  ],
};

/**
 * Respiratory examination zones.
 *
 * Auscultation and percussion findings are zone-specific — "crackles at the right base" is
 * a different clinical picture from crackles throughout — and recording them as prose loses
 * the comparison between one examination and the next.
 */
export const RESPIRATORY_ZONES_MAP: RegionMapDef = {
  id: "respiratory-zones",
  title: "Respiratory zones",
  viewBox: "0 0 200 260",
  silhouettePaths: [
    "M40 20 h120 v200 h-120 Z",
    "M96 20 h8 v200 h-8 Z",
  ],
  regions: [
    { id: "upper-right", label: "Right upper zone", group: "anterior", svgPath: "M40 20 h56 v64 h-56 Z", laterality: "right", locationCode: code("44714003", "Upper lobe of lung") },
    { id: "upper-left", label: "Left upper zone", group: "anterior", svgPath: "M104 20 h56 v64 h-56 Z", laterality: "left", locationCode: code("44714003", "Upper lobe of lung") },
    { id: "mid-right", label: "Right mid zone", group: "anterior", svgPath: "M40 88 h56 v64 h-56 Z", laterality: "right", locationCode: code("72481006", "Middle lobe of right lung") },
    { id: "mid-left", label: "Left mid zone", group: "anterior", svgPath: "M104 88 h56 v64 h-56 Z", laterality: "left", locationCode: code("41224006", "Lower lobe of lung") },
    { id: "lower-right", label: "Right lower zone", group: "anterior", svgPath: "M40 156 h56 v64 h-56 Z", laterality: "right", locationCode: code("41224006", "Lower lobe of lung") },
    { id: "lower-left", label: "Left lower zone", group: "anterior", svgPath: "M104 156 h56 v64 h-56 Z", laterality: "left", locationCode: code("41224006", "Lower lobe of lung") },
  ],
};

/**
 * Abdominal examination, nine regions.
 *
 * Nine rather than four quadrants: in a child the difference between epigastric and
 * periumbilical pain, or right iliac fossa versus right lumbar tenderness, is often the
 * difference between reassurance and a surgical referral. Four quadrants cannot express it.
 */
export const ABDOMEN_NINE_REGION_MAP: RegionMapDef = {
  id: "abdomen-nine",
  title: "Abdomen (nine regions)",
  viewBox: "0 0 180 180",
  silhouettePaths: ["M10 10 h160 v160 h-160 Z"],
  regions: [
    { id: "right-hypochondrium", label: "Right hypochondrium", group: "upper", svgPath: "M10 10 h53 v53 h-53 Z", laterality: "right", locationCode: code("48544008", "Right hypochondriac region") },
    { id: "epigastrium", label: "Epigastrium", group: "upper", svgPath: "M63 10 h54 v53 h-54 Z", laterality: "midline", locationCode: code("27947004", "Epigastric region") },
    { id: "left-hypochondrium", label: "Left hypochondrium", group: "upper", svgPath: "M117 10 h53 v53 h-53 Z", laterality: "left", locationCode: code("47110000", "Left hypochondriac region") },
    { id: "right-lumbar", label: "Right lumbar", group: "middle", svgPath: "M10 63 h53 v54 h-53 Z", laterality: "right", locationCode: code("37117007", "Right lumbar region") },
    { id: "umbilical", label: "Umbilical", group: "middle", svgPath: "M63 63 h54 v54 h-54 Z", laterality: "midline", locationCode: code("29707007", "Umbilicus") },
    { id: "left-lumbar", label: "Left lumbar", group: "middle", svgPath: "M117 63 h53 v54 h-53 Z", laterality: "left", locationCode: code("11708003", "Left lumbar region") },
    { id: "right-iliac", label: "Right iliac fossa", group: "lower", svgPath: "M10 117 h53 v53 h-53 Z", laterality: "right", locationCode: code("37117007", "Right iliac region") },
    { id: "suprapubic", label: "Suprapubic", group: "lower", svgPath: "M63 117 h54 v53 h-54 Z", laterality: "midline", locationCode: code("68505005", "Hypogastric region") },
    { id: "left-iliac", label: "Left iliac fossa", group: "lower", svgPath: "M117 117 h53 v53 h-53 Z", laterality: "left", locationCode: code("11708003", "Left iliac region") },
  ],
};

/** Four quadrants, for services and cadres whose protocol uses them. */
export const ABDOMEN_QUADRANT_MAP: RegionMapDef = {
  id: "abdomen-quadrants",
  title: "Abdomen (quadrants)",
  viewBox: "0 0 180 180",
  silhouettePaths: ["M10 10 h160 v160 h-160 Z"],
  regions: [
    { id: "ruq", label: "Right upper quadrant", group: "upper", svgPath: "M10 10 h80 v80 h-80 Z", laterality: "right", locationCode: code("48544008", "Right upper quadrant") },
    { id: "luq", label: "Left upper quadrant", group: "upper", svgPath: "M90 10 h80 v80 h-80 Z", laterality: "left", locationCode: code("47110000", "Left upper quadrant") },
    { id: "rlq", label: "Right lower quadrant", group: "lower", svgPath: "M10 90 h80 v80 h-80 Z", laterality: "right", locationCode: code("37117007", "Right lower quadrant") },
    { id: "llq", label: "Left lower quadrant", group: "lower", svgPath: "M90 90 h80 v80 h-80 Z", laterality: "left", locationCode: code("11708003", "Left lower quadrant") },
  ],
};
