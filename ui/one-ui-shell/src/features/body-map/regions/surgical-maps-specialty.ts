/**
 * ENGINEERING-AUTHORED pending clinical verification (MoHCC ratification) — Wave SB-4.
 * Anatomy simplified for clickable region selection, not for anatomical teaching.
 *
 * Specialty surgical maps: breast, urological tract, male and female reproductive tracts,
 * cardiothoracic and lung.
 *
 * Terminology binding follows the same two-kind rule set out in surgical-maps-head-neck.ts:
 * `code(...)` is a verified SNOMED CT body-structure code that is true of the region (often at
 * a coarser level than the label, with `laterality` carrying the side, exactly as the existing
 * respiratory map does); `unmapped(...)` marks a structure for which no SNOMED code was
 * verified, using a local system URI so nothing downstream can mistake it for a coded location.
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
 * Breast quadrants with the axillary tail and node levels.
 *
 * Node levels I–III are on the same map as the quadrants because the operation and the
 * staging are decided together; sending the surgeon to a second instrument to say "level II"
 * is how the level stops being recorded at all.
 */
export const BREAST_QUADRANTS_MAP: RegionMapDef = {
  id: "breast-quadrants",
  title: "Breast quadrants and axillary levels",
  viewBox: "0 0 240 200",
  silhouettePaths: [
    "M70 48 a52 50 0 0 1 0 100 a52 50 0 0 1 0 -100 Z",
    "M170 48 a52 50 0 0 1 0 100 a52 50 0 0 1 0 -100 Z",
    "M6 8 h108 v26 h-108 Z",
    "M126 8 h108 v26 h-108 Z",
  ],
  regions: [
    { id: "breast-uoq-right", label: "Right breast, upper outer quadrant", group: "right breast", svgPath: "M24 54 h44 v44 h-44 Z", laterality: "right", locationCode: code("76752008", "Breast structure") },
    { id: "breast-uiq-right", label: "Right breast, upper inner quadrant", group: "right breast", svgPath: "M72 54 h44 v44 h-44 Z", laterality: "right", locationCode: code("76752008", "Breast structure") },
    { id: "breast-loq-right", label: "Right breast, lower outer quadrant", group: "right breast", svgPath: "M24 102 h44 v44 h-44 Z", laterality: "right", locationCode: code("76752008", "Breast structure") },
    { id: "breast-liq-right", label: "Right breast, lower inner quadrant", group: "right breast", svgPath: "M72 102 h44 v44 h-44 Z", laterality: "right", locationCode: code("76752008", "Breast structure") },
    { id: "breast-central-right", label: "Right breast, central zone", group: "right breast", svgPath: "M56 86 h28 v28 h-28 Z", laterality: "right", locationCode: code("76752008", "Breast structure") },
    { id: "breast-nipple-right", label: "Right nipple and areola", group: "right breast", svgPath: "M64 94 h12 v12 h-12 Z", laterality: "right", locationCode: unmapped("nipple-areola-complex", "Nipple-areola complex") },
    { id: "breast-axillary-tail-right", label: "Right axillary tail", group: "right breast", svgPath: "M18 34 h30 v18 h-30 Z", laterality: "right", locationCode: code("76752008", "Breast structure") },
    { id: "axillary-level-i-right", label: "Right axilla, level I", group: "right axilla", svgPath: "M8 12 h26 v18 h-26 Z", laterality: "right", locationCode: unmapped("axillary-node-level-i", "Axillary lymph node level I") },
    { id: "axillary-level-ii-right", label: "Right axilla, level II", group: "right axilla", svgPath: "M36 12 h26 v18 h-26 Z", laterality: "right", locationCode: unmapped("axillary-node-level-ii", "Axillary lymph node level II") },
    { id: "axillary-level-iii-right", label: "Right axilla, level III", group: "right axilla", svgPath: "M64 12 h26 v18 h-26 Z", laterality: "right", locationCode: unmapped("axillary-node-level-iii", "Axillary lymph node level III") },
    { id: "breast-uoq-left", label: "Left breast, upper outer quadrant", group: "left breast", svgPath: "M172 54 h44 v44 h-44 Z", laterality: "left", locationCode: code("76752008", "Breast structure") },
    { id: "breast-uiq-left", label: "Left breast, upper inner quadrant", group: "left breast", svgPath: "M124 54 h44 v44 h-44 Z", laterality: "left", locationCode: code("76752008", "Breast structure") },
    { id: "breast-loq-left", label: "Left breast, lower outer quadrant", group: "left breast", svgPath: "M172 102 h44 v44 h-44 Z", laterality: "left", locationCode: code("76752008", "Breast structure") },
    { id: "breast-liq-left", label: "Left breast, lower inner quadrant", group: "left breast", svgPath: "M124 102 h44 v44 h-44 Z", laterality: "left", locationCode: code("76752008", "Breast structure") },
    { id: "breast-central-left", label: "Left breast, central zone", group: "left breast", svgPath: "M156 86 h28 v28 h-28 Z", laterality: "left", locationCode: code("76752008", "Breast structure") },
    { id: "breast-nipple-left", label: "Left nipple and areola", group: "left breast", svgPath: "M164 94 h12 v12 h-12 Z", laterality: "left", locationCode: unmapped("nipple-areola-complex", "Nipple-areola complex") },
    { id: "breast-axillary-tail-left", label: "Left axillary tail", group: "left breast", svgPath: "M192 34 h30 v18 h-30 Z", laterality: "left", locationCode: code("76752008", "Breast structure") },
    { id: "axillary-level-i-left", label: "Left axilla, level I", group: "left axilla", svgPath: "M206 12 h26 v18 h-26 Z", laterality: "left", locationCode: unmapped("axillary-node-level-i", "Axillary lymph node level I") },
    { id: "axillary-level-ii-left", label: "Left axilla, level II", group: "left axilla", svgPath: "M178 12 h26 v18 h-26 Z", laterality: "left", locationCode: unmapped("axillary-node-level-ii", "Axillary lymph node level II") },
    { id: "axillary-level-iii-left", label: "Left axilla, level III", group: "left axilla", svgPath: "M150 12 h26 v18 h-26 Z", laterality: "left", locationCode: unmapped("axillary-node-level-iii", "Axillary lymph node level III") },
  ],
};

/**
 * Urological tract from kidney to urethral meatus.
 *
 * Ureters are divided into thirds rather than drawn continuously: a stone is reported and
 * operated on by third, and "mid-ureteric" is the answer a theatre list needs.
 */
export const UROLOGICAL_TRACT_MAP: RegionMapDef = {
  id: "urological-tract",
  title: "Urological tract",
  viewBox: "0 0 200 360",
  silhouettePaths: [
    "M34 30 h40 v78 h-40 Z",
    "M126 30 h40 v78 h-40 Z",
    "M66 108 L86 230",
    "M134 108 L114 230",
    "M66 230 h68 v56 h-68 Z",
    "M74 288 h52 v32 h-52 Z",
    "M94 320 h12 v38 h-12 Z",
  ],
  regions: [
    { id: "kidney-upper-pole-right", label: "Right kidney, upper pole", group: "right kidney", svgPath: "M34 30 h40 v26 h-40 Z", laterality: "right", locationCode: code("64033007", "Kidney") },
    { id: "kidney-interpolar-right", label: "Right kidney, interpolar region", group: "right kidney", svgPath: "M34 56 h40 v26 h-40 Z", laterality: "right", locationCode: code("64033007", "Kidney") },
    { id: "kidney-lower-pole-right", label: "Right kidney, lower pole", group: "right kidney", svgPath: "M34 82 h40 v26 h-40 Z", laterality: "right", locationCode: code("64033007", "Kidney") },
    { id: "renal-pelvis-right", label: "Right renal pelvis", group: "right kidney", svgPath: "M68 62 h16 v20 h-16 Z", laterality: "right", locationCode: unmapped("renal-pelvis", "Renal pelvis") },
    { id: "kidney-upper-pole-left", label: "Left kidney, upper pole", group: "left kidney", svgPath: "M126 30 h40 v26 h-40 Z", laterality: "left", locationCode: code("64033007", "Kidney") },
    { id: "kidney-interpolar-left", label: "Left kidney, interpolar region", group: "left kidney", svgPath: "M126 56 h40 v26 h-40 Z", laterality: "left", locationCode: code("64033007", "Kidney") },
    { id: "kidney-lower-pole-left", label: "Left kidney, lower pole", group: "left kidney", svgPath: "M126 82 h40 v26 h-40 Z", laterality: "left", locationCode: code("64033007", "Kidney") },
    { id: "renal-pelvis-left", label: "Left renal pelvis", group: "left kidney", svgPath: "M116 62 h16 v20 h-16 Z", laterality: "left", locationCode: unmapped("renal-pelvis", "Renal pelvis") },
    { id: "ureter-upper-right", label: "Right upper ureter", group: "right ureter", svgPath: "M60 110 h16 v40 h-16 Z", laterality: "right", locationCode: code("87953007", "Ureter") },
    { id: "ureter-mid-right", label: "Right mid ureter", group: "right ureter", svgPath: "M66 150 h16 v40 h-16 Z", laterality: "right", locationCode: code("87953007", "Ureter") },
    { id: "ureter-lower-right", label: "Right lower ureter", group: "right ureter", svgPath: "M72 190 h16 v40 h-16 Z", laterality: "right", locationCode: code("87953007", "Ureter") },
    { id: "ureter-upper-left", label: "Left upper ureter", group: "left ureter", svgPath: "M124 110 h16 v40 h-16 Z", laterality: "left", locationCode: code("87953007", "Ureter") },
    { id: "ureter-mid-left", label: "Left mid ureter", group: "left ureter", svgPath: "M118 150 h16 v40 h-16 Z", laterality: "left", locationCode: code("87953007", "Ureter") },
    { id: "ureter-lower-left", label: "Left lower ureter", group: "left ureter", svgPath: "M112 190 h16 v40 h-16 Z", laterality: "left", locationCode: code("87953007", "Ureter") },
    { id: "bladder-dome", label: "Bladder dome", group: "bladder", svgPath: "M66 230 h68 v16 h-68 Z", laterality: "midline", locationCode: code("89837001", "Urinary bladder") },
    { id: "bladder-wall-right", label: "Right bladder wall", group: "bladder", svgPath: "M66 246 h22 v28 h-22 Z", laterality: "right", locationCode: code("89837001", "Urinary bladder") },
    { id: "bladder-wall-left", label: "Left bladder wall", group: "bladder", svgPath: "M112 246 h22 v28 h-22 Z", laterality: "left", locationCode: code("89837001", "Urinary bladder") },
    { id: "bladder-posterior-wall", label: "Posterior bladder wall", group: "bladder", svgPath: "M88 246 h24 v16 h-24 Z", laterality: "midline", locationCode: code("89837001", "Urinary bladder") },
    { id: "bladder-trigone", label: "Bladder trigone", group: "bladder", svgPath: "M88 262 h24 v14 h-24 Z", laterality: "midline", locationCode: code("89837001", "Urinary bladder") },
    { id: "bladder-neck", label: "Bladder neck", group: "bladder", svgPath: "M92 276 h16 v10 h-16 Z", laterality: "midline", locationCode: code("89837001", "Urinary bladder") },
    { id: "prostate-transition-zone", label: "Prostate, transition zone", group: "prostate", svgPath: "M74 288 h52 v10 h-52 Z", laterality: "midline", locationCode: code("41216001", "Prostate") },
    { id: "prostate-central-zone", label: "Prostate, central zone", group: "prostate", svgPath: "M74 298 h52 v10 h-52 Z", laterality: "midline", locationCode: code("41216001", "Prostate") },
    { id: "prostate-peripheral-zone", label: "Prostate, peripheral zone", group: "prostate", svgPath: "M74 308 h52 v12 h-52 Z", laterality: "midline", locationCode: code("41216001", "Prostate") },
    { id: "urethra-prostatic", label: "Prostatic urethra", group: "urethra", svgPath: "M94 320 h12 v12 h-12 Z", laterality: "midline", locationCode: code("13648007", "Urethra") },
    { id: "urethra-membranous", label: "Membranous urethra", group: "urethra", svgPath: "M94 332 h12 v10 h-12 Z", laterality: "midline", locationCode: code("13648007", "Urethra") },
    { id: "urethra-bulbar", label: "Bulbar urethra", group: "urethra", svgPath: "M94 342 h12 v10 h-12 Z", laterality: "midline", locationCode: code("13648007", "Urethra") },
    { id: "urethra-penile", label: "Penile urethra", group: "urethra", svgPath: "M94 352 h12 v6 h-12 Z", laterality: "midline", locationCode: code("13648007", "Urethra") },
  ],
};

/** Male reproductive tract, including the inguinal route a cord or hernia operation takes. */
export const MALE_REPRODUCTIVE_MAP: RegionMapDef = {
  id: "male-reproductive",
  title: "Male reproductive tract",
  viewBox: "0 0 200 220",
  silhouettePaths: [
    "M84 4 h32 v86 h-32 Z",
    "M24 116 h152 v96 h-152 Z",
    "M92 90 h16 v26 h-16 Z",
  ],
  regions: [
    { id: "urethral-meatus", label: "Urethral meatus", group: "penis", svgPath: "M94 4 h12 v8 h-12 Z", laterality: "midline", locationCode: code("13648007", "Urethra") },
    { id: "glans-penis", label: "Glans penis", group: "penis", svgPath: "M84 12 h32 v14 h-32 Z", laterality: "midline", locationCode: code("18911002", "Penis") },
    { id: "prepuce", label: "Prepuce (foreskin)", group: "penis", svgPath: "M82 26 h36 v14 h-36 Z", laterality: "midline", locationCode: unmapped("prepuce", "Prepuce of penis") },
    { id: "penile-shaft", label: "Penile shaft", group: "penis", svgPath: "M86 40 h28 v50 h-28 Z", laterality: "midline", locationCode: code("18911002", "Penis") },
    { id: "inguinal-ring-right", label: "Right external inguinal ring", group: "inguinal", svgPath: "M50 62 h26 v22 h-26 Z", laterality: "right", locationCode: unmapped("external-inguinal-ring", "External inguinal ring") },
    { id: "inguinal-ring-left", label: "Left external inguinal ring", group: "inguinal", svgPath: "M124 62 h26 v22 h-26 Z", laterality: "left", locationCode: unmapped("external-inguinal-ring", "External inguinal ring") },
    { id: "spermatic-cord-right", label: "Right spermatic cord", group: "cord structures", svgPath: "M56 92 h16 v24 h-16 Z", laterality: "right", locationCode: unmapped("spermatic-cord", "Spermatic cord") },
    { id: "spermatic-cord-left", label: "Left spermatic cord", group: "cord structures", svgPath: "M128 92 h16 v24 h-16 Z", laterality: "left", locationCode: unmapped("spermatic-cord", "Spermatic cord") },
    { id: "testis-right", label: "Right testis", group: "scrotal contents", svgPath: "M40 130 h44 v56 h-44 Z", laterality: "right", locationCode: code("40689003", "Testis") },
    { id: "testis-left", label: "Left testis", group: "scrotal contents", svgPath: "M116 130 h44 v56 h-44 Z", laterality: "left", locationCode: code("40689003", "Testis") },
    { id: "epididymis-right", label: "Right epididymis", group: "scrotal contents", svgPath: "M26 128 h12 v58 h-12 Z", laterality: "right", locationCode: unmapped("epididymis", "Epididymis") },
    { id: "epididymis-left", label: "Left epididymis", group: "scrotal contents", svgPath: "M162 128 h12 v58 h-12 Z", laterality: "left", locationCode: unmapped("epididymis", "Epididymis") },
    { id: "scrotal-raphe", label: "Scrotal skin and raphe", group: "scrotum", svgPath: "M92 128 h16 v80 h-16 Z", laterality: "midline", locationCode: unmapped("scrotum", "Scrotum") },
  ],
};

/** Female reproductive tract and vulva. */
export const FEMALE_REPRODUCTIVE_MAP: RegionMapDef = {
  id: "female-reproductive",
  title: "Female reproductive tract",
  viewBox: "0 0 220 280",
  silhouettePaths: [
    "M84 60 h52 v100 h-52 Z",
    "M22 56 h176 v46 h-176 Z",
    "M94 160 h32 v54 h-32 Z",
    "M74 214 h72 v60 h-72 Z",
  ],
  regions: [
    { id: "uterine-fundus", label: "Uterine fundus", group: "uterus", svgPath: "M84 60 h52 v28 h-52 Z", laterality: "midline", locationCode: code("35039007", "Uterus") },
    { id: "uterine-body", label: "Uterine body", group: "uterus", svgPath: "M84 88 h52 v46 h-52 Z", laterality: "midline", locationCode: code("35039007", "Uterus") },
    { id: "uterine-cervix", label: "Cervix", group: "uterus", svgPath: "M96 134 h28 v26 h-28 Z", laterality: "midline", locationCode: code("71252005", "Cervix uteri") },
    { id: "fallopian-tube-right", label: "Right fallopian tube", group: "adnexa", svgPath: "M34 56 h48 v16 h-48 Z", laterality: "right", locationCode: code("31435000", "Fallopian tube") },
    { id: "fallopian-tube-left", label: "Left fallopian tube", group: "adnexa", svgPath: "M138 56 h48 v16 h-48 Z", laterality: "left", locationCode: code("31435000", "Fallopian tube") },
    { id: "ovary-right", label: "Right ovary", group: "adnexa", svgPath: "M22 74 h32 v26 h-32 Z", laterality: "right", locationCode: code("15497006", "Ovary") },
    { id: "ovary-left", label: "Left ovary", group: "adnexa", svgPath: "M166 74 h32 v26 h-32 Z", laterality: "left", locationCode: code("15497006", "Ovary") },
    { id: "vagina", label: "Vagina", group: "lower tract", svgPath: "M94 160 h32 v54 h-32 Z", laterality: "midline", locationCode: code("76784001", "Vagina") },
    { id: "clitoris", label: "Clitoris", group: "vulva", svgPath: "M102 210 h16 v10 h-16 Z", laterality: "midline", locationCode: code("45292006", "Vulva") },
    { id: "labia-majora-right", label: "Right labium majus", group: "vulva", svgPath: "M74 216 h24 v40 h-24 Z", laterality: "right", locationCode: code("45292006", "Vulva") },
    { id: "labia-majora-left", label: "Left labium majus", group: "vulva", svgPath: "M122 216 h24 v40 h-24 Z", laterality: "left", locationCode: code("45292006", "Vulva") },
    { id: "labia-minora", label: "Labia minora", group: "vulva", svgPath: "M98 220 h24 v32 h-24 Z", laterality: "midline", locationCode: code("45292006", "Vulva") },
    { id: "introitus", label: "Introitus", group: "vulva", svgPath: "M100 232 h20 v18 h-20 Z", laterality: "midline", locationCode: code("45292006", "Vulva") },
    { id: "perineum-female", label: "Perineum", group: "vulva", svgPath: "M90 258 h40 v16 h-40 Z", laterality: "midline", locationCode: code("26107004", "Perineum") },
  ],
};

/**
 * Cardiothoracic map: chambers, valves, coronary territories and great vessels.
 *
 * Coronary territories are the simplified three (LAD, LCx, RCA). Real coronary anatomy is
 * variable and dominance matters; three territories is what an operation note and a handover
 * use, and pretending to more precision than that on a touch target would be false.
 */
export const CARDIOTHORACIC_MAP: RegionMapDef = {
  id: "cardiothoracic",
  title: "Heart, valves and great vessels",
  viewBox: "0 0 220 250",
  silhouettePaths: [
    "M62 60 h106 v140 h-106 Z",
    "M96 12 h56 v18 h-56 Z",
    "M96 194 h36 v24 h-36 Z",
  ],
  regions: [
    { id: "right-atrium", label: "Right atrium", group: "chambers", svgPath: "M62 62 h48 v56 h-48 Z", laterality: "right", locationCode: code("80891009", "Heart") },
    { id: "left-atrium", label: "Left atrium", group: "chambers", svgPath: "M116 62 h48 v40 h-48 Z", laterality: "left", locationCode: code("80891009", "Heart") },
    { id: "right-ventricle", label: "Right ventricle", group: "chambers", svgPath: "M62 122 h52 v72 h-52 Z", laterality: "right", locationCode: code("80891009", "Heart") },
    { id: "left-ventricle", label: "Left ventricle", group: "chambers", svgPath: "M118 106 h50 v88 h-50 Z", laterality: "left", locationCode: code("80891009", "Heart") },
    { id: "cardiac-apex", label: "Cardiac apex", group: "chambers", svgPath: "M96 194 h36 v22 h-36 Z", laterality: "left", locationCode: code("80891009", "Heart") },
    { id: "tricuspid-valve", label: "Tricuspid valve", group: "valves", svgPath: "M86 114 h26 v14 h-26 Z", laterality: "right", locationCode: code("80891009", "Heart") },
    { id: "mitral-valve", label: "Mitral valve", group: "valves", svgPath: "M124 100 h26 v14 h-26 Z", laterality: "left", locationCode: code("80891009", "Heart") },
    { id: "aortic-valve", label: "Aortic valve", group: "valves", svgPath: "M100 86 h24 v14 h-24 Z", laterality: "midline", locationCode: code("80891009", "Heart") },
    { id: "pulmonary-valve", label: "Pulmonary valve", group: "valves", svgPath: "M128 66 h22 v14 h-22 Z", laterality: "midline", locationCode: code("80891009", "Heart") },
    { id: "coronary-territory-lad", label: "LAD territory (anterior)", group: "coronary territories", svgPath: "M112 130 h14 v64 h-14 Z", laterality: "left", locationCode: unmapped("coronary-territory-lad", "Left anterior descending artery territory") },
    { id: "coronary-territory-lcx", label: "LCx territory (lateral)", group: "coronary territories", svgPath: "M154 106 h14 v70 h-14 Z", laterality: "left", locationCode: unmapped("coronary-territory-lcx", "Left circumflex artery territory") },
    { id: "coronary-territory-rca", label: "RCA territory (inferior)", group: "coronary territories", svgPath: "M50 96 h12 v90 h-12 Z", laterality: "right", locationCode: unmapped("coronary-territory-rca", "Right coronary artery territory") },
    { id: "aortic-arch", label: "Aortic arch", group: "great vessels", svgPath: "M96 12 h56 v18 h-56 Z", laterality: "midline", locationCode: code("15825003", "Aorta") },
    { id: "ascending-aorta", label: "Ascending aorta", group: "great vessels", svgPath: "M104 30 h20 v30 h-20 Z", laterality: "midline", locationCode: code("15825003", "Aorta") },
    { id: "descending-aorta", label: "Descending thoracic aorta", group: "great vessels", svgPath: "M146 30 h16 v60 h-16 Z", laterality: "left", locationCode: code("15825003", "Aorta") },
    { id: "pulmonary-trunk", label: "Pulmonary trunk", group: "great vessels", svgPath: "M126 32 h22 v32 h-22 Z", laterality: "midline", locationCode: unmapped("pulmonary-trunk", "Pulmonary trunk") },
    { id: "superior-vena-cava", label: "Superior vena cava", group: "great vessels", svgPath: "M78 26 h20 v34 h-20 Z", laterality: "right", locationCode: unmapped("superior-vena-cava", "Superior vena cava") },
    { id: "inferior-vena-cava", label: "Inferior vena cava", group: "great vessels", svgPath: "M62 196 h20 v40 h-20 Z", laterality: "right", locationCode: unmapped("inferior-vena-cava", "Inferior vena cava") },
    { id: "pulmonary-veins-right", label: "Right pulmonary veins", group: "great vessels", svgPath: "M38 68 h20 v20 h-20 Z", laterality: "right", locationCode: unmapped("pulmonary-vein", "Pulmonary vein") },
    { id: "pulmonary-veins-left", label: "Left pulmonary veins", group: "great vessels", svgPath: "M170 68 h20 v20 h-20 Z", laterality: "left", locationCode: unmapped("pulmonary-vein", "Pulmonary vein") },
  ],
};

/**
 * Lung lobes, pleural spaces and mediastinal compartments.
 *
 * Lobe codes are the same generic upper/middle/lower lobe concepts already used by
 * RESPIRATORY_ZONES_MAP, with `laterality` carrying the side — the estate's existing idiom.
 * The lingula has no verified code of its own and is marked as such rather than being folded
 * into the left upper lobe, because a lingula-sparing resection is a different operation.
 */
export const LUNG_LOBES_MAP: RegionMapDef = {
  id: "lung-lobes",
  title: "Lung lobes and mediastinum",
  viewBox: "0 0 220 240",
  silhouettePaths: [
    "M24 20 h72 v206 h-72 Z",
    "M124 20 h72 v206 h-72 Z",
    "M100 14 h18 v198 h-18 Z",
  ],
  regions: [
    { id: "rul", label: "Right upper lobe", group: "right lung", svgPath: "M24 24 h72 v56 h-72 Z", laterality: "right", locationCode: code("44714003", "Upper lobe of lung") },
    { id: "rml", label: "Right middle lobe", group: "right lung", svgPath: "M24 84 h72 v40 h-72 Z", laterality: "right", locationCode: code("72481006", "Middle lobe of right lung") },
    { id: "rll", label: "Right lower lobe", group: "right lung", svgPath: "M24 128 h72 v78 h-72 Z", laterality: "right", locationCode: code("41224006", "Lower lobe of lung") },
    { id: "lul", label: "Left upper lobe", group: "left lung", svgPath: "M124 24 h72 v56 h-72 Z", laterality: "left", locationCode: code("44714003", "Upper lobe of lung") },
    { id: "lingula", label: "Lingula", group: "left lung", svgPath: "M124 84 h72 v34 h-72 Z", laterality: "left", locationCode: unmapped("lingula", "Lingula of left lung") },
    { id: "lll", label: "Left lower lobe", group: "left lung", svgPath: "M124 122 h72 v84 h-72 Z", laterality: "left", locationCode: code("41224006", "Lower lobe of lung") },
    { id: "hilum-right", label: "Right hilum", group: "right lung", svgPath: "M84 96 h14 v24 h-14 Z", laterality: "right", locationCode: code("39607008", "Lung") },
    { id: "hilum-left", label: "Left hilum", group: "left lung", svgPath: "M122 96 h14 v24 h-14 Z", laterality: "left", locationCode: code("39607008", "Lung") },
    { id: "pleural-space-right", label: "Right pleural space", group: "pleura", svgPath: "M12 20 h10 v190 h-10 Z", laterality: "right", locationCode: unmapped("pleural-cavity", "Pleural cavity") },
    { id: "pleural-space-left", label: "Left pleural space", group: "pleura", svgPath: "M198 20 h10 v190 h-10 Z", laterality: "left", locationCode: unmapped("pleural-cavity", "Pleural cavity") },
    { id: "costophrenic-recess-right", label: "Right costophrenic recess", group: "pleura", svgPath: "M24 208 h72 v18 h-72 Z", laterality: "right", locationCode: unmapped("costophrenic-recess", "Costophrenic recess") },
    { id: "costophrenic-recess-left", label: "Left costophrenic recess", group: "pleura", svgPath: "M124 208 h72 v18 h-72 Z", laterality: "left", locationCode: unmapped("costophrenic-recess", "Costophrenic recess") },
    { id: "mediastinum-superior", label: "Superior mediastinum", group: "mediastinum", svgPath: "M98 14 h22 v14 h-22 Z", laterality: "midline", locationCode: unmapped("mediastinum", "Mediastinum") },
    { id: "mediastinum-anterior", label: "Anterior mediastinum", group: "mediastinum", svgPath: "M100 30 h18 v60 h-18 Z", laterality: "midline", locationCode: unmapped("mediastinum", "Mediastinum") },
    { id: "mediastinum-middle", label: "Middle mediastinum", group: "mediastinum", svgPath: "M100 92 h18 v60 h-18 Z", laterality: "midline", locationCode: unmapped("mediastinum", "Mediastinum") },
    { id: "mediastinum-posterior", label: "Posterior mediastinum", group: "mediastinum", svgPath: "M100 154 h18 v56 h-18 Z", laterality: "midline", locationCode: unmapped("mediastinum", "Mediastinum") },
  ],
};

/** Every specialty surgical map in this module, for registration and tests. */
export const SURGICAL_SPECIALTY_MAPS: RegionMapDef[] = [
  BREAST_QUADRANTS_MAP,
  UROLOGICAL_TRACT_MAP,
  MALE_REPRODUCTIVE_MAP,
  FEMALE_REPRODUCTIVE_MAP,
  CARDIOTHORACIC_MAP,
  LUNG_LOBES_MAP,
];
