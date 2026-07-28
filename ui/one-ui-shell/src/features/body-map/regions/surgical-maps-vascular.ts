/**
 * ENGINEERING-AUTHORED pending clinical verification (MoHCC ratification) — Wave SB-4.
 * Anatomy simplified for clickable region selection, not for anatomical teaching.
 *
 * Vascular, amputation, access and thoracic surgical maps.
 *
 * These are the maps where the level is the decision. An amputation level, a vein mapped
 * before stripping, an access site chosen for a fistula, a chest drain placed in the triangle
 * of safety — in each of them the anatomy is the plan, and a plan recorded as prose is a plan
 * that has to be re-made from scratch by whoever is on next.
 */

import type { RegionMapDef } from "../core/types";
import { code, pending } from "./surgical-maps-shared";

/**
 * Arterial tree.
 *
 * Drawn by named segment because that is the unit of every arterial decision: the level of an
 * occlusion, the site of an anastomosis, the vessel a graft runs from and to. Paired vessels
 * are lateralised; the aorta is not paired and its segments are midline.
 */
export const ARTERIAL_TREE_MAP: RegionMapDef = {
  id: "arterial-tree",
  title: "Arterial tree",
  viewBox: "0 0 240 430",
  silhouettePaths: [
    "M120 8 a20 20 0 0 1 20 20 a20 20 0 0 1 -40 0 a20 20 0 0 1 20 -20 Z",
    "M92 60 h56 v160 h-56 Z",
    "M50 66 h40 v120 h-40 Z",
    "M150 66 h40 v120 h-40 Z",
    "M84 220 h32 v200 h-32 Z",
    "M124 220 h32 v200 h-32 Z",
  ],
  regions: [
    { id: "carotid-right", label: "Right carotid artery", group: "head and neck", svgPath: "M104 30 h12 v34 h-12 Z", laterality: "right", locationCode: code("69105007", "Carotid artery structure") },
    { id: "carotid-left", label: "Left carotid artery", group: "head and neck", svgPath: "M124 30 h12 v34 h-12 Z", laterality: "left", locationCode: code("69105007", "Carotid artery structure") },
    { id: "aortic-arch", label: "Aortic arch", group: "aorta", svgPath: "M104 70 h32 v14 h-32 Z", laterality: "midline", locationCode: pending("aortic-arch", "Aortic arch structure") },
    { id: "aorta-ascending", label: "Ascending aorta", group: "aorta", svgPath: "M112 86 h16 v20 h-16 Z", laterality: "midline", locationCode: pending("ascending-aorta", "Ascending aorta structure") },
    { id: "aorta-descending-thoracic", label: "Descending thoracic aorta", group: "aorta", svgPath: "M112 108 h16 v52 h-16 Z", laterality: "midline", locationCode: pending("descending-thoracic-aorta", "Descending thoracic aorta structure") },
    { id: "aorta-abdominal", label: "Abdominal aorta", group: "aorta", svgPath: "M112 162 h16 v56 h-16 Z", laterality: "midline", locationCode: pending("abdominal-aorta", "Abdominal aorta structure") },
    { id: "subclavian-right", label: "Right subclavian artery", group: "upper limb", svgPath: "M78 66 h26 v12 h-26 Z", laterality: "right", locationCode: pending("subclavian-artery", "Subclavian artery structure") },
    { id: "subclavian-left", label: "Left subclavian artery", group: "upper limb", svgPath: "M136 66 h26 v12 h-26 Z", laterality: "left", locationCode: pending("subclavian-artery", "Subclavian artery structure") },
    { id: "axillary-right", label: "Right axillary artery", group: "upper limb", svgPath: "M64 80 h20 v14 h-20 Z", laterality: "right", locationCode: pending("axillary-artery", "Axillary artery structure") },
    { id: "axillary-left", label: "Left axillary artery", group: "upper limb", svgPath: "M156 80 h20 v14 h-20 Z", laterality: "left", locationCode: pending("axillary-artery", "Axillary artery structure") },
    { id: "brachial-right", label: "Right brachial artery", group: "upper limb", svgPath: "M58 96 h16 v66 h-16 Z", laterality: "right", locationCode: code("17137000", "Brachial artery structure") },
    { id: "brachial-left", label: "Left brachial artery", group: "upper limb", svgPath: "M166 96 h16 v66 h-16 Z", laterality: "left", locationCode: code("17137000", "Brachial artery structure") },
    { id: "common-iliac-right", label: "Right common iliac artery", group: "pelvis", svgPath: "M98 220 h14 v24 h-14 Z", laterality: "right", locationCode: pending("common-iliac-artery", "Common iliac artery structure") },
    { id: "common-iliac-left", label: "Left common iliac artery", group: "pelvis", svgPath: "M128 220 h14 v24 h-14 Z", laterality: "left", locationCode: pending("common-iliac-artery", "Common iliac artery structure") },
    { id: "internal-iliac-right", label: "Right internal iliac artery", group: "pelvis", svgPath: "M82 246 h14 v18 h-14 Z", laterality: "right", locationCode: pending("internal-iliac-artery", "Internal iliac artery structure") },
    { id: "internal-iliac-left", label: "Left internal iliac artery", group: "pelvis", svgPath: "M144 246 h14 v18 h-14 Z", laterality: "left", locationCode: pending("internal-iliac-artery", "Internal iliac artery structure") },
    { id: "external-iliac-right", label: "Right external iliac artery", group: "pelvis", svgPath: "M100 246 h14 v22 h-14 Z", laterality: "right", locationCode: pending("external-iliac-artery", "External iliac artery structure") },
    { id: "external-iliac-left", label: "Left external iliac artery", group: "pelvis", svgPath: "M126 246 h14 v22 h-14 Z", laterality: "left", locationCode: pending("external-iliac-artery", "External iliac artery structure") },
    { id: "common-femoral-right", label: "Right common femoral artery", group: "lower limb", svgPath: "M96 272 h16 v26 h-16 Z", laterality: "right", locationCode: code("7657000", "Femoral artery") },
    { id: "common-femoral-left", label: "Left common femoral artery", group: "lower limb", svgPath: "M128 272 h16 v26 h-16 Z", laterality: "left", locationCode: code("7657000", "Femoral artery") },
    { id: "superficial-femoral-right", label: "Right superficial femoral artery", group: "lower limb", svgPath: "M94 300 h14 v52 h-14 Z", laterality: "right", locationCode: pending("superficial-femoral-artery", "Superficial femoral artery structure") },
    { id: "superficial-femoral-left", label: "Left superficial femoral artery", group: "lower limb", svgPath: "M132 300 h14 v52 h-14 Z", laterality: "left", locationCode: pending("superficial-femoral-artery", "Superficial femoral artery structure") },
    { id: "popliteal-right", label: "Right popliteal artery", group: "lower limb", svgPath: "M92 354 h14 v26 h-14 Z", laterality: "right", locationCode: code("43899006", "Popliteal artery structure") },
    { id: "popliteal-left", label: "Left popliteal artery", group: "lower limb", svgPath: "M134 354 h14 v26 h-14 Z", laterality: "left", locationCode: code("43899006", "Popliteal artery structure") },
    { id: "anterior-tibial-right", label: "Right anterior tibial artery", group: "lower limb", svgPath: "M84 382 h10 v34 h-10 Z", laterality: "right", locationCode: pending("anterior-tibial-artery", "Anterior tibial artery structure") },
    { id: "anterior-tibial-left", label: "Left anterior tibial artery", group: "lower limb", svgPath: "M146 382 h10 v34 h-10 Z", laterality: "left", locationCode: pending("anterior-tibial-artery", "Anterior tibial artery structure") },
    { id: "posterior-tibial-right", label: "Right posterior tibial artery", group: "lower limb", svgPath: "M96 382 h10 v34 h-10 Z", laterality: "right", locationCode: pending("posterior-tibial-artery", "Posterior tibial artery structure") },
    { id: "posterior-tibial-left", label: "Left posterior tibial artery", group: "lower limb", svgPath: "M134 382 h10 v34 h-10 Z", laterality: "left", locationCode: pending("posterior-tibial-artery", "Posterior tibial artery structure") },
  ],
};

/**
 * Lower limb venous map, for varicose vein assessment.
 *
 * Duplex findings are junction- and perforator-specific, and the operation follows the map:
 * an incompetent saphenofemoral junction is a different procedure from an isolated
 * below-knee perforator. The gaiter area is a target in its own right because that is where
 * venous ulceration appears and where its healing is tracked.
 */
export const VENOUS_LOWER_LIMB_MAP: RegionMapDef = {
  id: "venous-lower-limb",
  title: "Lower limb venous map",
  viewBox: "0 0 240 400",
  silhouettePaths: [
    "M70 20 h100 v46 h-100 Z",
    "M64 66 h56 v320 h-56 Z",
    "M120 66 h56 v320 h-56 Z",
  ],
  regions: [
    { id: "sfj-right", label: "Right saphenofemoral junction", group: "junction", svgPath: "M92 68 h20 v18 h-20 Z", laterality: "right", locationCode: pending("saphenofemoral-junction", "Saphenofemoral junction structure") },
    { id: "sfj-left", label: "Left saphenofemoral junction", group: "junction", svgPath: "M128 68 h20 v18 h-20 Z", laterality: "left", locationCode: pending("saphenofemoral-junction", "Saphenofemoral junction structure") },
    { id: "gsv-thigh-right", label: "Right GSV — thigh", group: "great saphenous", svgPath: "M96 88 h14 v90 h-14 Z", laterality: "right", locationCode: pending("great-saphenous-vein", "Great saphenous vein structure") },
    { id: "gsv-thigh-left", label: "Left GSV — thigh", group: "great saphenous", svgPath: "M130 88 h14 v90 h-14 Z", laterality: "left", locationCode: pending("great-saphenous-vein", "Great saphenous vein structure") },
    { id: "gsv-knee-right", label: "Right GSV — knee", group: "great saphenous", svgPath: "M96 180 h14 v24 h-14 Z", laterality: "right", locationCode: pending("great-saphenous-vein", "Great saphenous vein structure") },
    { id: "gsv-knee-left", label: "Left GSV — knee", group: "great saphenous", svgPath: "M130 180 h14 v24 h-14 Z", laterality: "left", locationCode: pending("great-saphenous-vein", "Great saphenous vein structure") },
    { id: "gsv-calf-right", label: "Right GSV — calf", group: "great saphenous", svgPath: "M96 206 h14 v96 h-14 Z", laterality: "right", locationCode: pending("great-saphenous-vein", "Great saphenous vein structure") },
    { id: "gsv-calf-left", label: "Left GSV — calf", group: "great saphenous", svgPath: "M130 206 h14 v96 h-14 Z", laterality: "left", locationCode: pending("great-saphenous-vein", "Great saphenous vein structure") },
    { id: "spj-right", label: "Right saphenopopliteal junction", group: "junction", svgPath: "M70 182 h18 v18 h-18 Z", laterality: "right", locationCode: pending("saphenopopliteal-junction", "Saphenopopliteal junction structure") },
    { id: "spj-left", label: "Left saphenopopliteal junction", group: "junction", svgPath: "M152 182 h18 v18 h-18 Z", laterality: "left", locationCode: pending("saphenopopliteal-junction", "Saphenopopliteal junction structure") },
    { id: "ssv-calf-right", label: "Right SSV — calf", group: "small saphenous", svgPath: "M74 202 h12 v100 h-12 Z", laterality: "right", locationCode: pending("small-saphenous-vein", "Small saphenous vein structure") },
    { id: "ssv-calf-left", label: "Left SSV — calf", group: "small saphenous", svgPath: "M154 202 h12 v100 h-12 Z", laterality: "left", locationCode: pending("small-saphenous-vein", "Small saphenous vein structure") },
    { id: "perforator-thigh-right", label: "Right mid-thigh perforator", group: "perforator", svgPath: "M60 120 h12 v12 h-12 Z", laterality: "right", locationCode: pending("thigh-perforating-vein", "Perforating vein of thigh structure") },
    { id: "perforator-thigh-left", label: "Left mid-thigh perforator", group: "perforator", svgPath: "M168 120 h12 v12 h-12 Z", laterality: "left", locationCode: pending("thigh-perforating-vein", "Perforating vein of thigh structure") },
    { id: "perforator-above-knee-right", label: "Right above-knee perforator", group: "perforator", svgPath: "M60 168 h12 v12 h-12 Z", laterality: "right", locationCode: pending("above-knee-perforating-vein", "Above-knee perforating vein structure") },
    { id: "perforator-above-knee-left", label: "Left above-knee perforator", group: "perforator", svgPath: "M168 168 h12 v12 h-12 Z", laterality: "left", locationCode: pending("above-knee-perforating-vein", "Above-knee perforating vein structure") },
    { id: "perforator-below-knee-right", label: "Right below-knee perforator", group: "perforator", svgPath: "M60 236 h12 v12 h-12 Z", laterality: "right", locationCode: pending("below-knee-perforating-vein", "Below-knee perforating vein structure") },
    { id: "perforator-below-knee-left", label: "Left below-knee perforator", group: "perforator", svgPath: "M168 236 h12 v12 h-12 Z", laterality: "left", locationCode: pending("below-knee-perforating-vein", "Below-knee perforating vein structure") },
    { id: "gaiter-right", label: "Right gaiter area", group: "skin", svgPath: "M88 306 h26 v26 h-26 Z", laterality: "right", locationCode: pending("gaiter-area", "Gaiter area of lower leg") },
    { id: "gaiter-left", label: "Left gaiter area", group: "skin", svgPath: "M126 306 h26 v26 h-26 Z", laterality: "left", locationCode: pending("gaiter-area", "Gaiter area of lower leg") },
  ],
};

/**
 * Amputation levels.
 *
 * The level is the operation, the prosthesis, the rehabilitation and the outcome — a
 * through-knee and an above-knee amputation are not variations of one another. Both sides and
 * both limbs are drawn on one map so that the level and the side are chosen in a single act,
 * because the side is the thing that must never be assumed.
 */
export const AMPUTATION_LEVELS_MAP: RegionMapDef = {
  id: "amputation-levels",
  title: "Amputation levels",
  viewBox: "0 0 260 430",
  silhouettePaths: [
    "M130 8 a18 18 0 0 1 18 18 a18 18 0 0 1 -36 0 a18 18 0 0 1 18 -18 Z",
    "M100 60 h60 v150 h-60 Z",
    "M46 66 h48 v220 h-48 Z",
    "M166 66 h48 v220 h-48 Z",
    "M88 210 h38 v210 h-38 Z",
    "M134 210 h38 v210 h-38 Z",
  ],
  regions: [
    { id: "hip-disarticulation-right", label: "Right hip disarticulation", group: "lower limb level", svgPath: "M88 206 h34 v14 h-34 Z", laterality: "right", locationCode: pending("hip-disarticulation-level", "Hip disarticulation level") },
    { id: "hip-disarticulation-left", label: "Left hip disarticulation", group: "lower limb level", svgPath: "M138 206 h34 v14 h-34 Z", laterality: "left", locationCode: pending("hip-disarticulation-level", "Hip disarticulation level") },
    { id: "above-knee-right", label: "Right above-knee (transfemoral)", group: "lower limb level", svgPath: "M92 258 h26 v14 h-26 Z", laterality: "right", locationCode: pending("transfemoral-level", "Transfemoral amputation level") },
    { id: "above-knee-left", label: "Left above-knee (transfemoral)", group: "lower limb level", svgPath: "M142 258 h26 v14 h-26 Z", laterality: "left", locationCode: pending("transfemoral-level", "Transfemoral amputation level") },
    { id: "through-knee-right", label: "Right through-knee", group: "lower limb level", svgPath: "M92 296 h26 v12 h-26 Z", laterality: "right", locationCode: pending("knee-disarticulation-level", "Knee disarticulation level") },
    { id: "through-knee-left", label: "Left through-knee", group: "lower limb level", svgPath: "M142 296 h26 v12 h-26 Z", laterality: "left", locationCode: pending("knee-disarticulation-level", "Knee disarticulation level") },
    { id: "below-knee-right", label: "Right below-knee (transtibial)", group: "lower limb level", svgPath: "M94 322 h22 v12 h-22 Z", laterality: "right", locationCode: pending("transtibial-level", "Transtibial amputation level") },
    { id: "below-knee-left", label: "Left below-knee (transtibial)", group: "lower limb level", svgPath: "M144 322 h22 v12 h-22 Z", laterality: "left", locationCode: pending("transtibial-level", "Transtibial amputation level") },
    { id: "ankle-syme-right", label: "Right ankle (Syme)", group: "lower limb level", svgPath: "M94 372 h22 v12 h-22 Z", laterality: "right", locationCode: pending("syme-level", "Syme amputation level") },
    { id: "ankle-syme-left", label: "Left ankle (Syme)", group: "lower limb level", svgPath: "M144 372 h22 v12 h-22 Z", laterality: "left", locationCode: pending("syme-level", "Syme amputation level") },
    { id: "transmetatarsal-right", label: "Right transmetatarsal", group: "lower limb level", svgPath: "M92 392 h26 v10 h-26 Z", laterality: "right", locationCode: pending("transmetatarsal-level", "Transmetatarsal amputation level") },
    { id: "transmetatarsal-left", label: "Left transmetatarsal", group: "lower limb level", svgPath: "M142 392 h26 v10 h-26 Z", laterality: "left", locationCode: pending("transmetatarsal-level", "Transmetatarsal amputation level") },
    { id: "toe-ray-right", label: "Right toe or ray", group: "lower limb level", svgPath: "M92 406 h26 v10 h-26 Z", laterality: "right", locationCode: pending("toe-ray-level", "Toe or ray amputation level") },
    { id: "toe-ray-left", label: "Left toe or ray", group: "lower limb level", svgPath: "M142 406 h26 v10 h-26 Z", laterality: "left", locationCode: pending("toe-ray-level", "Toe or ray amputation level") },
    { id: "shoulder-disarticulation-right", label: "Right shoulder disarticulation", group: "upper limb level", svgPath: "M52 74 h34 v14 h-34 Z", laterality: "right", locationCode: pending("shoulder-disarticulation-level", "Shoulder disarticulation level") },
    { id: "shoulder-disarticulation-left", label: "Left shoulder disarticulation", group: "upper limb level", svgPath: "M174 74 h34 v14 h-34 Z", laterality: "left", locationCode: pending("shoulder-disarticulation-level", "Shoulder disarticulation level") },
    { id: "above-elbow-right", label: "Right above-elbow (transhumeral)", group: "upper limb level", svgPath: "M56 128 h26 v12 h-26 Z", laterality: "right", locationCode: pending("transhumeral-level", "Transhumeral amputation level") },
    { id: "above-elbow-left", label: "Left above-elbow (transhumeral)", group: "upper limb level", svgPath: "M178 128 h26 v12 h-26 Z", laterality: "left", locationCode: pending("transhumeral-level", "Transhumeral amputation level") },
    { id: "through-elbow-right", label: "Right through-elbow", group: "upper limb level", svgPath: "M56 158 h26 v12 h-26 Z", laterality: "right", locationCode: pending("elbow-disarticulation-level", "Elbow disarticulation level") },
    { id: "through-elbow-left", label: "Left through-elbow", group: "upper limb level", svgPath: "M178 158 h26 v12 h-26 Z", laterality: "left", locationCode: pending("elbow-disarticulation-level", "Elbow disarticulation level") },
    { id: "below-elbow-right", label: "Right below-elbow (transradial)", group: "upper limb level", svgPath: "M56 182 h26 v12 h-26 Z", laterality: "right", locationCode: pending("transradial-level", "Transradial amputation level") },
    { id: "below-elbow-left", label: "Left below-elbow (transradial)", group: "upper limb level", svgPath: "M178 182 h26 v12 h-26 Z", laterality: "left", locationCode: pending("transradial-level", "Transradial amputation level") },
    { id: "wrist-disarticulation-right", label: "Right wrist disarticulation", group: "upper limb level", svgPath: "M54 240 h28 v12 h-28 Z", laterality: "right", locationCode: pending("wrist-disarticulation-level", "Wrist disarticulation level") },
    { id: "wrist-disarticulation-left", label: "Left wrist disarticulation", group: "upper limb level", svgPath: "M178 240 h28 v12 h-28 Z", laterality: "left", locationCode: pending("wrist-disarticulation-level", "Wrist disarticulation level") },
    { id: "transcarpal-right", label: "Right transcarpal", group: "upper limb level", svgPath: "M54 256 h28 v10 h-28 Z", laterality: "right", locationCode: pending("transcarpal-level", "Transcarpal amputation level") },
    { id: "transcarpal-left", label: "Left transcarpal", group: "upper limb level", svgPath: "M178 256 h28 v10 h-28 Z", laterality: "left", locationCode: pending("transcarpal-level", "Transcarpal amputation level") },
    { id: "finger-ray-right", label: "Right finger or ray", group: "upper limb level", svgPath: "M52 270 h32 v12 h-32 Z", laterality: "right", locationCode: pending("finger-ray-level", "Finger or ray amputation level") },
    { id: "finger-ray-left", label: "Left finger or ray", group: "upper limb level", svgPath: "M176 270 h32 v12 h-32 Z", laterality: "left", locationCode: pending("finger-ray-level", "Finger or ray amputation level") },
  ],
};

/**
 * Vascular access sites.
 *
 * Access sites are a finite, exhaustible resource. A patient heading for dialysis has a
 * limited number of veins and every cannulation, line and failed fistula spends some of them —
 * which is why the site is worth recording as a coded location rather than as "line in the
 * groin", and why arm vein preservation depends on this being written down at all.
 */
export const VASCULAR_ACCESS_SITES_MAP: RegionMapDef = {
  id: "vascular-access-sites",
  title: "Vascular access sites",
  viewBox: "0 0 240 400",
  silhouettePaths: [
    "M120 8 a20 20 0 0 1 20 20 a20 20 0 0 1 -40 0 a20 20 0 0 1 20 -20 Z",
    "M92 60 h56 v180 h-56 Z",
    "M50 66 h44 v220 h-44 Z",
    "M146 66 h44 v220 h-44 Z",
    "M92 240 h26 v150 h-26 Z",
    "M122 240 h26 v150 h-26 Z",
  ],
  regions: [
    { id: "internal-jugular-right", label: "Right internal jugular vein", group: "central venous", svgPath: "M100 42 h16 v24 h-16 Z", laterality: "right", locationCode: pending("internal-jugular-vein", "Internal jugular vein structure") },
    { id: "internal-jugular-left", label: "Left internal jugular vein", group: "central venous", svgPath: "M124 42 h16 v24 h-16 Z", laterality: "left", locationCode: pending("internal-jugular-vein", "Internal jugular vein structure") },
    { id: "subclavian-vein-right", label: "Right subclavian vein", group: "central venous", svgPath: "M84 74 h26 v12 h-26 Z", laterality: "right", locationCode: pending("subclavian-vein", "Subclavian vein structure") },
    { id: "subclavian-vein-left", label: "Left subclavian vein", group: "central venous", svgPath: "M130 74 h26 v12 h-26 Z", laterality: "left", locationCode: pending("subclavian-vein", "Subclavian vein structure") },
    { id: "brachiobasilic-fistula-right", label: "Right brachiobasilic fistula site", group: "fistula", svgPath: "M62 118 h16 v26 h-16 Z", laterality: "right", locationCode: pending("brachiobasilic-fistula-site", "Brachiobasilic arteriovenous fistula site") },
    { id: "brachiobasilic-fistula-left", label: "Left brachiobasilic fistula site", group: "fistula", svgPath: "M162 118 h16 v26 h-16 Z", laterality: "left", locationCode: pending("brachiobasilic-fistula-site", "Brachiobasilic arteriovenous fistula site") },
    { id: "brachial-artery-access-right", label: "Right brachial artery", group: "arterial", svgPath: "M62 150 h16 v26 h-16 Z", laterality: "right", locationCode: code("17137000", "Brachial artery structure") },
    { id: "brachial-artery-access-left", label: "Left brachial artery", group: "arterial", svgPath: "M162 150 h16 v26 h-16 Z", laterality: "left", locationCode: code("17137000", "Brachial artery structure") },
    { id: "brachiocephalic-fistula-right", label: "Right brachiocephalic fistula site", group: "fistula", svgPath: "M60 180 h20 v18 h-20 Z", laterality: "right", locationCode: pending("brachiocephalic-fistula-site", "Brachiocephalic arteriovenous fistula site") },
    { id: "brachiocephalic-fistula-left", label: "Left brachiocephalic fistula site", group: "fistula", svgPath: "M160 180 h20 v18 h-20 Z", laterality: "left", locationCode: pending("brachiocephalic-fistula-site", "Brachiocephalic arteriovenous fistula site") },
    { id: "radial-artery-access-right", label: "Right radial artery", group: "arterial", svgPath: "M54 232 h14 v26 h-14 Z", laterality: "right", locationCode: code("45631007", "Radial artery structure") },
    { id: "radial-artery-access-left", label: "Left radial artery", group: "arterial", svgPath: "M172 232 h14 v26 h-14 Z", laterality: "left", locationCode: code("45631007", "Radial artery structure") },
    { id: "radiocephalic-fistula-right", label: "Right radiocephalic fistula site", group: "fistula", svgPath: "M52 262 h18 v18 h-18 Z", laterality: "right", locationCode: pending("radiocephalic-fistula-site", "Radiocephalic arteriovenous fistula site") },
    { id: "radiocephalic-fistula-left", label: "Left radiocephalic fistula site", group: "fistula", svgPath: "M170 262 h18 v18 h-18 Z", laterality: "left", locationCode: pending("radiocephalic-fistula-site", "Radiocephalic arteriovenous fistula site") },
    { id: "femoral-vein-access-right", label: "Right femoral vein", group: "central venous", svgPath: "M80 250 h14 v30 h-14 Z", laterality: "right", locationCode: code("83419000", "Femoral vein structure") },
    { id: "femoral-vein-access-left", label: "Left femoral vein", group: "central venous", svgPath: "M146 250 h14 v30 h-14 Z", laterality: "left", locationCode: code("83419000", "Femoral vein structure") },
    { id: "femoral-artery-access-right", label: "Right femoral artery", group: "arterial", svgPath: "M96 250 h16 v30 h-16 Z", laterality: "right", locationCode: code("7657000", "Femoral artery") },
    { id: "femoral-artery-access-left", label: "Left femoral artery", group: "arterial", svgPath: "M128 250 h16 v30 h-16 Z", laterality: "left", locationCode: code("7657000", "Femoral artery") },
  ],
};

/**
 * Chest wall and thoracic access.
 *
 * The triangle of safety is on this map for one reason: chest drains placed outside it injure
 * the liver, the spleen and the heart, and "drain inserted" without a site is a record that
 * cannot be reviewed after such an injury. Sides are separate targets throughout — a drain on
 * the wrong side of the chest is the classic wrong-site procedure.
 */
export const CHEST_WALL_THORACIC_MAP: RegionMapDef = {
  id: "chest-wall-thoracic",
  title: "Chest wall and thoracic access",
  viewBox: "0 0 220 300",
  silhouettePaths: [
    "M20 30 h180 v220 h-180 Z",
    "M104 30 h12 v220 h-12 Z",
    "M20 62 h180 v6 h-180 Z",
    "M20 96 h180 v6 h-180 Z",
  ],
  regions: [
    { id: "median-sternotomy", label: "Median sternotomy", group: "incision", svgPath: "M102 40 h16 v80 h-16 Z", laterality: "midline", locationCode: pending("median-sternotomy-site", "Median sternotomy incision site") },
    { id: "clamshell-thoracotomy", label: "Clamshell thoracotomy", group: "incision", svgPath: "M40 128 h140 v14 h-140 Z", laterality: "midline", locationCode: pending("clamshell-thoracotomy-site", "Clamshell thoracotomy incision site") },
    { id: "posterolateral-thoracotomy-right", label: "Right posterolateral thoracotomy", group: "incision", svgPath: "M30 70 h46 v16 h-46 Z", laterality: "right", locationCode: pending("posterolateral-thoracotomy-site", "Posterolateral thoracotomy incision site") },
    { id: "posterolateral-thoracotomy-left", label: "Left posterolateral thoracotomy", group: "incision", svgPath: "M144 70 h46 v16 h-46 Z", laterality: "left", locationCode: pending("posterolateral-thoracotomy-site", "Posterolateral thoracotomy incision site") },
    { id: "anterolateral-thoracotomy-right", label: "Right anterolateral thoracotomy", group: "incision", svgPath: "M52 96 h44 v14 h-44 Z", laterality: "right", locationCode: pending("anterolateral-thoracotomy-site", "Anterolateral thoracotomy incision site") },
    { id: "anterolateral-thoracotomy-left", label: "Left anterolateral thoracotomy", group: "incision", svgPath: "M124 96 h44 v14 h-44 Z", laterality: "left", locationCode: pending("anterolateral-thoracotomy-site", "Anterolateral thoracotomy incision site") },
    { id: "triangle-of-safety-right", label: "Right triangle of safety", group: "drain site", svgPath: "M24 150 L58 150 L42 204 Z", laterality: "right", locationCode: pending("triangle-of-safety", "Triangle of safety for chest drain insertion") },
    { id: "triangle-of-safety-left", label: "Left triangle of safety", group: "drain site", svgPath: "M196 150 L162 150 L178 204 Z", laterality: "left", locationCode: pending("triangle-of-safety", "Triangle of safety for chest drain insertion") },
    { id: "anterior-drain-site-right", label: "Right anterior drain site (2nd ICS)", group: "drain site", svgPath: "M62 150 h26 v20 h-26 Z", laterality: "right", locationCode: pending("second-intercostal-space", "Second intercostal space structure") },
    { id: "anterior-drain-site-left", label: "Left anterior drain site (2nd ICS)", group: "drain site", svgPath: "M132 150 h26 v20 h-26 Z", laterality: "left", locationCode: pending("second-intercostal-space", "Second intercostal space structure") },
    { id: "fourth-intercostal-right", label: "Right 4th intercostal space", group: "rib space", svgPath: "M62 176 h26 v14 h-26 Z", laterality: "right", locationCode: pending("fourth-intercostal-space", "Fourth intercostal space structure") },
    { id: "fourth-intercostal-left", label: "Left 4th intercostal space", group: "rib space", svgPath: "M132 176 h26 v14 h-26 Z", laterality: "left", locationCode: pending("fourth-intercostal-space", "Fourth intercostal space structure") },
    { id: "fifth-intercostal-right", label: "Right 5th intercostal space", group: "rib space", svgPath: "M62 194 h26 v14 h-26 Z", laterality: "right", locationCode: pending("fifth-intercostal-space", "Fifth intercostal space structure") },
    { id: "fifth-intercostal-left", label: "Left 5th intercostal space", group: "rib space", svgPath: "M132 194 h26 v14 h-26 Z", laterality: "left", locationCode: pending("fifth-intercostal-space", "Fifth intercostal space structure") },
    { id: "chest-wall-right", label: "Right chest wall (site not specified)", group: "chest wall", svgPath: "M24 216 h72 v26 h-72 Z", laterality: "right", locationCode: code("78904004", "Chest") },
    { id: "chest-wall-left", label: "Left chest wall (site not specified)", group: "chest wall", svgPath: "M124 216 h72 v26 h-72 Z", laterality: "left", locationCode: code("78904004", "Chest") },
  ],
};

/** Every vascular, amputation and thoracic map, for registration and coverage reporting. */
export const SURGICAL_VASCULAR_MAPS: RegionMapDef[] = [
  ARTERIAL_TREE_MAP,
  VENOUS_LOWER_LIMB_MAP,
  AMPUTATION_LEVELS_MAP,
  VASCULAR_ACCESS_SITES_MAP,
  CHEST_WALL_THORACIC_MAP,
];
