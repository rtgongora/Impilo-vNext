/**
 * ENGINEERING-AUTHORED pending clinical verification (MoHCC ratification) — Wave SB-4.
 * Anatomy simplified for clickable region selection, not for anatomical teaching.
 *
 * Orthopaedic and limb surgical maps.
 *
 * Fractures, joint procedures and hand or foot injuries are described by bone, segment and
 * side, and every one of those three is lost when the location is typed as prose. Side is the
 * one that matters most: wrong-site surgery is a never event, and the map is often where the
 * side is first committed to the record — so every lateralised region carries an explicit
 * `laterality`, and there is no unsided fallback region to fall into by accident.
 *
 * The hand and foot maps deliberately do NOT carry per-region laterality: they draw a single
 * generic hand or foot, and the instrument asks which side. Stamping "left" onto a template
 * used for both hands would be a lie in half of all uses.
 */

import type { BodyRegionDef, RegionMapDef } from "../core/types";
import { code, pending } from "./surgical-maps-shared";

/**
 * Upper limb skeleton, both sides.
 *
 * The humerus is split proximal / shaft / distal because that is how the fracture, the
 * fixation and the nerve at risk all differ — a proximal humeral fracture and a distal one
 * share a bone and nothing else.
 */
export const UPPER_LIMB_SKELETON_MAP: RegionMapDef = {
  id: "upper-limb-skeleton",
  title: "Upper limb skeleton",
  viewBox: "0 0 260 400",
  silhouettePaths: [
    "M130 6 a18 18 0 0 1 18 18 a18 18 0 0 1 -36 0 a18 18 0 0 1 18 -18 Z",
    "M100 56 h60 v170 h-60 Z",
    "M26 56 h56 v340 h-56 Z",
    "M178 56 h56 v340 h-56 Z",
  ],
  regions: [
    { id: "clavicle-right", label: "Right clavicle", group: "shoulder girdle", svgPath: "M46 44 h64 v12 h-64 Z", laterality: "right", locationCode: code("51299004", "Clavicle") },
    { id: "clavicle-left", label: "Left clavicle", group: "shoulder girdle", svgPath: "M150 44 h64 v12 h-64 Z", laterality: "left", locationCode: code("51299004", "Clavicle") },
    { id: "scapula-right", label: "Right scapula", group: "shoulder girdle", svgPath: "M30 58 h48 v42 h-48 Z", laterality: "right", locationCode: code("79601000", "Scapula") },
    { id: "scapula-left", label: "Left scapula", group: "shoulder girdle", svgPath: "M182 58 h48 v42 h-48 Z", laterality: "left", locationCode: code("79601000", "Scapula") },
    { id: "humerus-proximal-right", label: "Right proximal humerus", group: "arm", svgPath: "M36 100 h32 v30 h-32 Z", laterality: "right", locationCode: pending("proximal-humerus", "Proximal humerus structure") },
    { id: "humerus-proximal-left", label: "Left proximal humerus", group: "arm", svgPath: "M192 100 h32 v30 h-32 Z", laterality: "left", locationCode: pending("proximal-humerus", "Proximal humerus structure") },
    { id: "humerus-shaft-right", label: "Right humeral shaft", group: "arm", svgPath: "M40 132 h24 v72 h-24 Z", laterality: "right", locationCode: code("85050009", "Humerus") },
    { id: "humerus-shaft-left", label: "Left humeral shaft", group: "arm", svgPath: "M196 132 h24 v72 h-24 Z", laterality: "left", locationCode: code("85050009", "Humerus") },
    { id: "humerus-distal-right", label: "Right distal humerus", group: "arm", svgPath: "M36 206 h32 v26 h-32 Z", laterality: "right", locationCode: pending("distal-humerus", "Distal humerus structure") },
    { id: "humerus-distal-left", label: "Left distal humerus", group: "arm", svgPath: "M192 206 h32 v26 h-32 Z", laterality: "left", locationCode: pending("distal-humerus", "Distal humerus structure") },
    { id: "radius-right", label: "Right radius", group: "forearm", svgPath: "M34 234 h16 v74 h-16 Z", laterality: "right", locationCode: code("62413002", "Radius") },
    { id: "radius-left", label: "Left radius", group: "forearm", svgPath: "M210 234 h16 v74 h-16 Z", laterality: "left", locationCode: code("62413002", "Radius") },
    { id: "ulna-right", label: "Right ulna", group: "forearm", svgPath: "M54 234 h16 v74 h-16 Z", laterality: "right", locationCode: code("23416004", "Ulna") },
    { id: "ulna-left", label: "Left ulna", group: "forearm", svgPath: "M190 234 h16 v74 h-16 Z", laterality: "left", locationCode: code("23416004", "Ulna") },
    { id: "carpus-right", label: "Right carpus", group: "hand", svgPath: "M32 312 h40 v20 h-40 Z", laterality: "right", locationCode: pending("carpal-bones", "Carpal bone structure") },
    { id: "carpus-left", label: "Left carpus", group: "hand", svgPath: "M188 312 h40 v20 h-40 Z", laterality: "left", locationCode: pending("carpal-bones", "Carpal bone structure") },
    { id: "metacarpals-right", label: "Right metacarpals", group: "hand", svgPath: "M32 334 h40 v32 h-40 Z", laterality: "right", locationCode: pending("metacarpal-bones", "Metacarpal bone structure") },
    { id: "metacarpals-left", label: "Left metacarpals", group: "hand", svgPath: "M188 334 h40 v32 h-40 Z", laterality: "left", locationCode: pending("metacarpal-bones", "Metacarpal bone structure") },
    { id: "hand-phalanges-right", label: "Right phalanges", group: "hand", svgPath: "M30 368 h44 v26 h-44 Z", laterality: "right", locationCode: pending("hand-phalanges", "Phalanx of hand structure") },
    { id: "hand-phalanges-left", label: "Left phalanges", group: "hand", svgPath: "M186 368 h44 v26 h-44 Z", laterality: "left", locationCode: pending("hand-phalanges", "Phalanx of hand structure") },
  ],
};

/**
 * Lower limb skeleton, both sides.
 *
 * The femoral neck is its own region rather than part of the femur: a neck of femur fracture
 * in an older person is a distinct clinical pathway with its own time-to-theatre target, and
 * folding it into "femur" makes that pathway uncountable.
 */
export const LOWER_LIMB_SKELETON_MAP: RegionMapDef = {
  id: "lower-limb-skeleton",
  title: "Lower limb skeleton",
  viewBox: "0 0 260 400",
  silhouettePaths: [
    "M56 20 h148 v54 h-148 Z",
    "M60 74 h52 v322 h-52 Z",
    "M148 74 h52 v322 h-52 Z",
  ],
  regions: [
    { id: "hemipelvis-right", label: "Right hemipelvis", group: "pelvis", svgPath: "M62 24 h56 v46 h-56 Z", laterality: "right", locationCode: pending("hemipelvis", "Hemipelvis structure") },
    { id: "hemipelvis-left", label: "Left hemipelvis", group: "pelvis", svgPath: "M142 24 h56 v46 h-56 Z", laterality: "left", locationCode: pending("hemipelvis", "Hemipelvis structure") },
    { id: "femoral-neck-right", label: "Right femoral neck", group: "thigh", svgPath: "M70 72 h34 v26 h-34 Z", laterality: "right", locationCode: pending("femoral-neck", "Neck of femur structure") },
    { id: "femoral-neck-left", label: "Left femoral neck", group: "thigh", svgPath: "M156 72 h34 v26 h-34 Z", laterality: "left", locationCode: pending("femoral-neck", "Neck of femur structure") },
    { id: "femoral-shaft-right", label: "Right femoral shaft", group: "thigh", svgPath: "M76 100 h24 v112 h-24 Z", laterality: "right", locationCode: code("71341001", "Femur") },
    { id: "femoral-shaft-left", label: "Left femoral shaft", group: "thigh", svgPath: "M160 100 h24 v112 h-24 Z", laterality: "left", locationCode: code("71341001", "Femur") },
    { id: "femur-distal-right", label: "Right distal femur", group: "thigh", svgPath: "M70 214 h34 v26 h-34 Z", laterality: "right", locationCode: pending("distal-femur", "Distal femur structure") },
    { id: "femur-distal-left", label: "Left distal femur", group: "thigh", svgPath: "M156 214 h34 v26 h-34 Z", laterality: "left", locationCode: pending("distal-femur", "Distal femur structure") },
    { id: "patella-right", label: "Right patella", group: "knee", svgPath: "M78 242 h20 v18 h-20 Z", laterality: "right", locationCode: pending("patella", "Patella structure") },
    { id: "patella-left", label: "Left patella", group: "knee", svgPath: "M162 242 h20 v18 h-20 Z", laterality: "left", locationCode: pending("patella", "Patella structure") },
    { id: "tibia-right", label: "Right tibia", group: "leg", svgPath: "M78 262 h24 v88 h-24 Z", laterality: "right", locationCode: code("12611008", "Tibia") },
    { id: "tibia-left", label: "Left tibia", group: "leg", svgPath: "M158 262 h24 v88 h-24 Z", laterality: "left", locationCode: code("12611008", "Tibia") },
    { id: "fibula-right", label: "Right fibula", group: "leg", svgPath: "M62 262 h12 v88 h-12 Z", laterality: "right", locationCode: code("87342007", "Fibula") },
    { id: "fibula-left", label: "Left fibula", group: "leg", svgPath: "M186 262 h12 v88 h-12 Z", laterality: "left", locationCode: code("87342007", "Fibula") },
    { id: "ankle-mortise-right", label: "Right ankle", group: "foot and ankle", svgPath: "M68 352 h34 v16 h-34 Z", laterality: "right", locationCode: pending("ankle-region", "Ankle region structure") },
    { id: "ankle-mortise-left", label: "Left ankle", group: "foot and ankle", svgPath: "M158 352 h34 v16 h-34 Z", laterality: "left", locationCode: pending("ankle-region", "Ankle region structure") },
    { id: "tarsus-right", label: "Right tarsus", group: "foot and ankle", svgPath: "M62 370 h40 v14 h-40 Z", laterality: "right", locationCode: pending("tarsal-bones", "Tarsal bone structure") },
    { id: "tarsus-left", label: "Left tarsus", group: "foot and ankle", svgPath: "M158 370 h40 v14 h-40 Z", laterality: "left", locationCode: pending("tarsal-bones", "Tarsal bone structure") },
    { id: "metatarsals-right", label: "Right metatarsals", group: "foot and ankle", svgPath: "M56 386 h46 v12 h-46 Z", laterality: "right", locationCode: pending("metatarsal-bones", "Metatarsal bone structure") },
    { id: "metatarsals-left", label: "Left metatarsals", group: "foot and ankle", svgPath: "M158 386 h46 v12 h-46 Z", laterality: "left", locationCode: pending("metatarsal-bones", "Metatarsal bone structure") },
  ],
};

/**
 * Hand, detailed.
 *
 * Hand injuries are recorded by digit and by phalanx because that is what decides the
 * operation, the splint and the rehabilitation — and because "cut on the hand" is a record
 * that helps nobody at the follow-up clinic. Nail beds are separate targets: a nail bed
 * injury implies a tuft fracture until proven otherwise.
 *
 * One generic hand; the instrument asks which side. Regions carry no laterality by design.
 */
export const HAND_DETAILED_MAP: RegionMapDef = {
  id: "hand-detailed",
  title: "Hand (detailed)",
  viewBox: "0 0 200 300",
  silhouettePaths: [
    "M50 150 h100 v128 h-100 Z",
    "M60 44 h96 v108 h-96 Z",
    "M22 144 h30 v80 h-30 Z",
  ],
  regions: [
    { id: "thumb-nail", label: "Thumb nail bed", group: "thumb", svgPath: "M26 148 h22 v12 h-22 Z", locationCode: pending("thumb-nail-bed", "Nail bed of thumb structure") },
    { id: "thumb-distal-phalanx", label: "Thumb distal phalanx", group: "thumb", svgPath: "M26 160 h22 v26 h-22 Z", locationCode: pending("thumb-distal-phalanx", "Distal phalanx of thumb structure") },
    { id: "thumb-proximal-phalanx", label: "Thumb proximal phalanx", group: "thumb", svgPath: "M26 188 h22 v32 h-22 Z", locationCode: pending("thumb-proximal-phalanx", "Proximal phalanx of thumb structure") },
    { id: "index-nail", label: "Index nail bed", group: "index finger", svgPath: "M62 56 h20 v12 h-20 Z", locationCode: pending("index-nail-bed", "Nail bed of index finger structure") },
    { id: "index-distal-phalanx", label: "Index distal phalanx", group: "index finger", svgPath: "M62 68 h20 v26 h-20 Z", locationCode: pending("index-distal-phalanx", "Distal phalanx of index finger structure") },
    { id: "index-middle-phalanx", label: "Index middle phalanx", group: "index finger", svgPath: "M62 96 h20 v26 h-20 Z", locationCode: pending("index-middle-phalanx", "Middle phalanx of index finger structure") },
    { id: "index-proximal-phalanx", label: "Index proximal phalanx", group: "index finger", svgPath: "M62 124 h20 v26 h-20 Z", locationCode: pending("index-proximal-phalanx", "Proximal phalanx of index finger structure") },
    { id: "middle-nail", label: "Middle nail bed", group: "middle finger", svgPath: "M86 44 h20 v12 h-20 Z", locationCode: pending("middle-nail-bed", "Nail bed of middle finger structure") },
    { id: "middle-distal-phalanx", label: "Middle finger distal phalanx", group: "middle finger", svgPath: "M86 56 h20 v28 h-20 Z", locationCode: pending("middle-distal-phalanx", "Distal phalanx of middle finger structure") },
    { id: "middle-middle-phalanx", label: "Middle finger middle phalanx", group: "middle finger", svgPath: "M86 86 h20 v28 h-20 Z", locationCode: pending("middle-middle-phalanx", "Middle phalanx of middle finger structure") },
    { id: "middle-proximal-phalanx", label: "Middle finger proximal phalanx", group: "middle finger", svgPath: "M86 116 h20 v34 h-20 Z", locationCode: pending("middle-proximal-phalanx", "Proximal phalanx of middle finger structure") },
    { id: "ring-nail", label: "Ring nail bed", group: "ring finger", svgPath: "M110 52 h20 v12 h-20 Z", locationCode: pending("ring-nail-bed", "Nail bed of ring finger structure") },
    { id: "ring-distal-phalanx", label: "Ring distal phalanx", group: "ring finger", svgPath: "M110 64 h20 v26 h-20 Z", locationCode: pending("ring-distal-phalanx", "Distal phalanx of ring finger structure") },
    { id: "ring-middle-phalanx", label: "Ring middle phalanx", group: "ring finger", svgPath: "M110 92 h20 v26 h-20 Z", locationCode: pending("ring-middle-phalanx", "Middle phalanx of ring finger structure") },
    { id: "ring-proximal-phalanx", label: "Ring proximal phalanx", group: "ring finger", svgPath: "M110 120 h20 v30 h-20 Z", locationCode: pending("ring-proximal-phalanx", "Proximal phalanx of ring finger structure") },
    { id: "little-nail", label: "Little finger nail bed", group: "little finger", svgPath: "M134 76 h18 v11 h-18 Z", locationCode: pending("little-nail-bed", "Nail bed of little finger structure") },
    { id: "little-distal-phalanx", label: "Little finger distal phalanx", group: "little finger", svgPath: "M134 87 h18 v22 h-18 Z", locationCode: pending("little-distal-phalanx", "Distal phalanx of little finger structure") },
    { id: "little-middle-phalanx", label: "Little finger middle phalanx", group: "little finger", svgPath: "M134 111 h18 v20 h-18 Z", locationCode: pending("little-middle-phalanx", "Middle phalanx of little finger structure") },
    { id: "little-proximal-phalanx", label: "Little finger proximal phalanx", group: "little finger", svgPath: "M134 133 h18 v17 h-18 Z", locationCode: pending("little-proximal-phalanx", "Proximal phalanx of little finger structure") },
    { id: "palm-metacarpophalangeal", label: "Metacarpophalangeal zone", group: "palm", svgPath: "M56 152 h94 v28 h-94 Z", locationCode: pending("palm-mcp-zone", "Metacarpophalangeal region of hand") },
    { id: "palm-thenar", label: "Thenar eminence", group: "palm", svgPath: "M50 182 h38 v66 h-38 Z", locationCode: pending("thenar-eminence", "Thenar eminence structure") },
    { id: "palm-central", label: "Central palm", group: "palm", svgPath: "M90 182 h26 v66 h-26 Z", locationCode: code("85562004", "Hand") },
    { id: "palm-hypothenar", label: "Hypothenar eminence", group: "palm", svgPath: "M118 182 h32 v66 h-32 Z", locationCode: pending("hypothenar-eminence", "Hypothenar eminence structure") },
    { id: "palm-wrist-crease", label: "Wrist crease and carpal tunnel", group: "palm", svgPath: "M56 250 h88 v26 h-88 Z", locationCode: pending("carpal-tunnel", "Carpal tunnel structure") },
  ],
};

/**
 * Foot, detailed — dorsal skeleton on the left panel, plantar surface on the right.
 *
 * Two panels rather than one, because the diabetic foot is the reason this map exists and its
 * two questions occupy the same outline: which bone is involved (dorsal, for osteomyelitis and
 * ray amputation) and where is the ulcer (plantar, where pressure actually falls). Overlapping
 * hit areas on a single foot would make one of those two questions unanswerable on a phone.
 *
 * One generic foot; the instrument asks which side.
 */
export const FOOT_DETAILED_MAP: RegionMapDef = {
  id: "foot-detailed",
  title: "Foot (dorsal and plantar)",
  viewBox: "0 0 320 300",
  silhouettePaths: [
    "M34 60 h100 v226 h-100 Z",
    "M204 60 h100 v226 h-100 Z",
  ],
  regions: [
    { id: "hindfoot", label: "Hindfoot", group: "dorsal", svgPath: "M56 232 h64 v50 h-64 Z", locationCode: pending("hindfoot", "Hindfoot structure") },
    { id: "midfoot", label: "Midfoot", group: "dorsal", svgPath: "M50 168 h74 v58 h-74 Z", locationCode: pending("midfoot", "Midfoot structure") },
    { id: "forefoot", label: "Forefoot", group: "dorsal", svgPath: "M44 112 h84 v50 h-84 Z", locationCode: pending("forefoot", "Forefoot structure") },
    { id: "hallux", label: "Hallux", group: "dorsal", svgPath: "M104 66 h26 v40 h-26 Z", locationCode: pending("hallux", "Great toe structure") },
    { id: "toe-second", label: "Second toe", group: "dorsal", svgPath: "M86 70 h16 v36 h-16 Z", locationCode: pending("second-toe", "Second toe structure") },
    { id: "toe-third", label: "Third toe", group: "dorsal", svgPath: "M68 74 h16 v32 h-16 Z", locationCode: pending("third-toe", "Third toe structure") },
    { id: "toe-fourth", label: "Fourth toe", group: "dorsal", svgPath: "M52 80 h14 v26 h-14 Z", locationCode: pending("fourth-toe", "Fourth toe structure") },
    { id: "toe-fifth", label: "Fifth toe", group: "dorsal", svgPath: "M38 88 h12 v18 h-12 Z", locationCode: pending("fifth-toe", "Fifth toe structure") },
    { id: "plantar-heel", label: "Plantar heel", group: "plantar", svgPath: "M226 232 h64 v50 h-64 Z", locationCode: pending("plantar-heel", "Plantar heel structure") },
    { id: "plantar-medial-arch", label: "Medial arch", group: "plantar", svgPath: "M220 168 h40 v58 h-40 Z", locationCode: pending("plantar-medial-arch", "Medial plantar arch structure") },
    { id: "plantar-lateral-arch", label: "Lateral arch", group: "plantar", svgPath: "M262 168 h32 v58 h-32 Z", locationCode: pending("plantar-lateral-arch", "Lateral plantar arch structure") },
    { id: "plantar-metatarsal-heads", label: "Metatarsal heads (2nd–5th)", group: "plantar", svgPath: "M214 112 h58 v50 h-58 Z", locationCode: pending("plantar-metatarsal-heads", "Plantar metatarsal head region") },
    { id: "plantar-first-metatarsal-head", label: "First metatarsal head", group: "plantar", svgPath: "M276 112 h22 v50 h-22 Z", locationCode: pending("first-metatarsal-head", "First metatarsal head structure") },
    { id: "plantar-toe-pads", label: "Toe pads", group: "plantar", svgPath: "M212 70 h86 v34 h-86 Z", locationCode: pending("plantar-toe-pads", "Plantar surface of toe structure") },
    { id: "foot-whole", label: "Foot (site not specified)", group: "whole", svgPath: "M40 286 h258 v10 h-258 Z", locationCode: code("56459004", "Foot") },
  ],
};

/**
 * Major joints, both sides.
 *
 * One map for the joint-level questions — arthroplasty, arthroscopy, aspiration, dislocation,
 * septic arthritis — where the answer is a joint and a side and nothing finer.
 */
export const MAJOR_JOINTS_MAP: RegionMapDef = {
  id: "major-joints",
  title: "Major joints",
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
    { id: "shoulder-right", label: "Right shoulder", group: "upper limb", svgPath: "M76 74 h26 v26 h-26 Z", laterality: "right", locationCode: pending("shoulder-joint", "Shoulder joint structure") },
    { id: "shoulder-left", label: "Left shoulder", group: "upper limb", svgPath: "M138 74 h26 v26 h-26 Z", laterality: "left", locationCode: pending("shoulder-joint", "Shoulder joint structure") },
    { id: "elbow-right", label: "Right elbow", group: "upper limb", svgPath: "M60 158 h24 v24 h-24 Z", laterality: "right", locationCode: pending("elbow-joint", "Elbow joint structure") },
    { id: "elbow-left", label: "Left elbow", group: "upper limb", svgPath: "M156 158 h24 v24 h-24 Z", laterality: "left", locationCode: pending("elbow-joint", "Elbow joint structure") },
    { id: "wrist-right", label: "Right wrist", group: "upper limb", svgPath: "M48 240 h22 v22 h-22 Z", laterality: "right", locationCode: pending("wrist-joint", "Wrist joint structure") },
    { id: "wrist-left", label: "Left wrist", group: "upper limb", svgPath: "M170 240 h22 v22 h-22 Z", laterality: "left", locationCode: pending("wrist-joint", "Wrist joint structure") },
    { id: "hip-right", label: "Right hip", group: "lower limb", svgPath: "M92 206 h26 v26 h-26 Z", laterality: "right", locationCode: code("29836001", "Hip region structure") },
    { id: "hip-left", label: "Left hip", group: "lower limb", svgPath: "M122 206 h26 v26 h-26 Z", laterality: "left", locationCode: code("29836001", "Hip region structure") },
    { id: "knee-right", label: "Right knee", group: "lower limb", svgPath: "M90 286 h26 v26 h-26 Z", laterality: "right", locationCode: pending("knee-joint", "Knee joint structure") },
    { id: "knee-left", label: "Left knee", group: "lower limb", svgPath: "M124 286 h26 v26 h-26 Z", laterality: "left", locationCode: pending("knee-joint", "Knee joint structure") },
    { id: "ankle-joint-right", label: "Right ankle", group: "lower limb", svgPath: "M90 356 h24 v24 h-24 Z", laterality: "right", locationCode: pending("ankle-joint", "Ankle joint structure") },
    { id: "ankle-joint-left", label: "Left ankle", group: "lower limb", svgPath: "M126 356 h24 v24 h-24 Z", laterality: "left", locationCode: pending("ankle-joint", "Ankle joint structure") },
  ],
};

/**
 * Spine, level by level.
 *
 * Spinal surgery, injury and block are recorded by level and nothing else will do: "lumbar
 * spine surgery" cannot be checked against the consent form, and a level recorded as prose
 * cannot be compared with the imaging. Every individual level is a target.
 *
 * The individual levels carry pending bindings: SNOMED CT names each vertebra, but this
 * author could not source the 26 identifiers with confidence, and a spine map with plausible
 * wrong codes is worse than one that says so. The three regional bindings below are real.
 */
const vertebra = (
  id: string,
  label: string,
  group: string,
  y: number,
  height: number,
  slug: string,
): BodyRegionDef => ({
  id,
  label,
  group,
  svgPath: `M60 ${y} h40 v${height} h-40 Z`,
  laterality: "midline",
  locationCode: pending(slug, `${label} structure`),
});

const CERVICAL_LEVELS: BodyRegionDef[] = [1, 2, 3, 4, 5, 6, 7].map((n, index) =>
  vertebra(`vertebra-c${n}`, `C${n}`, "cervical", 20 + index * 16, 14, `vertebra-c${n}`),
);

const THORACIC_LEVELS: BodyRegionDef[] = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12].map((n, index) =>
  vertebra(`vertebra-t${n}`, `T${n}`, "thoracic", 138 + index * 18, 16, `vertebra-t${n}`),
);

const LUMBAR_LEVELS: BodyRegionDef[] = [1, 2, 3, 4, 5].map((n, index) =>
  vertebra(`vertebra-l${n}`, `L${n}`, "lumbar", 360 + index * 22, 20, `vertebra-l${n}`),
);

export const SPINE_LEVELS_MAP: RegionMapDef = {
  id: "spine-levels",
  title: "Spine levels",
  viewBox: "0 0 160 540",
  silhouettePaths: [
    "M56 14 h48 v510 h-48 Z",
    "M76 14 h8 v510 h-8 Z",
  ],
  regions: [
    ...CERVICAL_LEVELS,
    ...THORACIC_LEVELS,
    ...LUMBAR_LEVELS,
    {
      id: "sacrum",
      label: "Sacrum",
      group: "sacrococcygeal",
      svgPath: "M56 472 h48 v34 h-48 Z",
      laterality: "midline",
      locationCode: pending("sacrum", "Sacrum structure"),
    },
    {
      id: "coccyx",
      label: "Coccyx",
      group: "sacrococcygeal",
      svgPath: "M68 510 h24 v16 h-24 Z",
      laterality: "midline",
      locationCode: pending("coccyx", "Coccyx structure"),
    },
    {
      id: "sacrococcygeal-region",
      label: "Sacrococcygeal region",
      group: "region",
      svgPath: "M108 472 h40 v54 h-40 Z",
      laterality: "midline",
      locationCode: code("54735007", "Sacrococcygeal region"),
    },
    // Regional targets, for the finding that is real at the region but not at a level —
    // "cervical spine cleared", "thoracolumbar tenderness" — so it is not forced onto a
    // level nobody actually determined.
    {
      id: "cervical-spine-region",
      label: "Cervical spine (level not specified)",
      group: "region",
      svgPath: "M108 20 h40 v112 h-40 Z",
      laterality: "midline",
      locationCode: code("122494005", "Cervical spine structure"),
    },
    {
      id: "thoracic-spine-region",
      label: "Thoracic spine (level not specified)",
      group: "region",
      svgPath: "M108 138 h40 v212 h-40 Z",
      laterality: "midline",
      locationCode: code("122495006", "Thoracic spine structure"),
    },
    {
      id: "lumbar-spine-region",
      label: "Lumbar spine (level not specified)",
      group: "region",
      svgPath: "M108 360 h40 v108 h-40 Z",
      laterality: "midline",
      locationCode: code("122496007", "Lumbar spine structure"),
    },
  ],
};

/** Every limb and axial-skeleton surgical map, for registration and coverage reporting. */
export const SURGICAL_LIMB_MAPS: RegionMapDef[] = [
  UPPER_LIMB_SKELETON_MAP,
  LOWER_LIMB_SKELETON_MAP,
  HAND_DETAILED_MAP,
  FOOT_DETAILED_MAP,
  MAJOR_JOINTS_MAP,
  SPINE_LEVELS_MAP,
];
