/**
 * ENGINEERING-AUTHORED pending clinical verification (MoHCC ratification) — Wave SB-4.
 * Anatomy simplified for clickable region selection, not for anatomical teaching.
 *
 * Abdominal and gastrointestinal surgical maps.
 *
 * These are operative maps, not examination maps. The abdominal examination map already in
 * this module answers "where does it hurt"; these answer "where was it cut", "which segment
 * was resected", "where will the stoma sit" — questions whose answers are recorded once and
 * then read by everyone who touches the patient afterwards, from the ward round to the
 * histopathology request to the stoma nurse.
 *
 * Region geometry is schematic. A surgeon marking a Kocher incision needs an unambiguous,
 * finger-sized target on a phone in a theatre corridor, not a drawing whose realism implies
 * a precision the record does not carry.
 */

import type { RegionMapDef } from "../core/types";
import { code, pending } from "./surgical-maps-shared";

/**
 * Abdominal incisions and laparoscopic port sites.
 *
 * An incision is not cosmetic history: it predicts adhesions, it dictates where the next
 * operation can safely enter, and an unrecorded port site is the commonest place an
 * incisional hernia is later found and not explained. Ports are a separate group from
 * incisions because they are chosen and closed differently.
 */
export const ABDOMINAL_INCISIONS_MAP: RegionMapDef = {
  id: "abdominal-incisions",
  title: "Abdominal incisions and port sites",
  viewBox: "0 0 200 300",
  silhouettePaths: [
    // Trunk, costal margin and pelvic brim — scenery, so the strips read as incisions on an
    // abdomen rather than as a set of floating bars.
    "M50 20 h100 v240 h-100 Z",
    "M50 58 L100 92 L150 58 L150 68 L100 102 L50 68 Z",
    "M55 228 L100 254 L145 228 L145 238 L100 264 L55 238 Z",
  ],
  regions: [
    { id: "midline-upper", label: "Upper midline", group: "incision", svgPath: "M94 58 h12 v86 h-12 Z", laterality: "midline", locationCode: pending("incision-upper-midline", "Upper midline abdominal incision site") },
    { id: "midline-lower", label: "Lower midline", group: "incision", svgPath: "M94 158 h12 v70 h-12 Z", laterality: "midline", locationCode: pending("incision-lower-midline", "Lower midline abdominal incision site") },
    { id: "paramedian-right", label: "Right paramedian", group: "incision", svgPath: "M72 70 h10 v110 h-10 Z", laterality: "right", locationCode: pending("incision-paramedian", "Paramedian abdominal incision site") },
    { id: "paramedian-left", label: "Left paramedian", group: "incision", svgPath: "M118 70 h10 v110 h-10 Z", laterality: "left", locationCode: pending("incision-paramedian", "Paramedian abdominal incision site") },
    { id: "kocher-right", label: "Right subcostal (Kocher)", group: "incision", svgPath: "M62 76 L100 98 L100 108 L62 86 Z", laterality: "right", locationCode: pending("incision-kocher-subcostal", "Right subcostal (Kocher) incision site") },
    { id: "kocher-left", label: "Left subcostal", group: "incision", svgPath: "M138 76 L100 98 L100 108 L138 86 Z", laterality: "left", locationCode: pending("incision-subcostal", "Subcostal incision site") },
    { id: "transverse-abdominal", label: "Transverse", group: "incision", svgPath: "M60 118 h80 v10 h-80 Z", laterality: "midline", locationCode: pending("incision-transverse", "Transverse abdominal incision site") },
    { id: "mcburney-gridiron", label: "Gridiron (McBurney)", group: "incision", svgPath: "M64 196 L86 178 L94 186 L72 204 Z", laterality: "right", locationCode: pending("incision-gridiron-mcburney", "Gridiron (McBurney) incision site") },
    { id: "lanz", label: "Lanz", group: "incision", svgPath: "M66 208 h28 v9 h-28 Z", laterality: "right", locationCode: pending("incision-lanz", "Lanz incision site") },
    { id: "pfannenstiel", label: "Pfannenstiel", group: "incision", svgPath: "M72 238 h56 v10 h-56 Z", laterality: "midline", locationCode: pending("incision-pfannenstiel", "Pfannenstiel incision site") },
    { id: "port-umbilical", label: "Umbilical port", group: "laparoscopic port", svgPath: "M92 146 h16 v16 h-16 Z", laterality: "midline", locationCode: code("29707007", "Umbilicus") },
    { id: "port-epigastric", label: "Epigastric port", group: "laparoscopic port", svgPath: "M94 34 h12 v14 h-12 Z", laterality: "midline", locationCode: code("27947004", "Epigastric region") },
    { id: "port-right-flank", label: "Right flank port", group: "laparoscopic port", svgPath: "M56 150 h12 v12 h-12 Z", laterality: "right", locationCode: pending("port-flank", "Flank laparoscopic port site") },
    { id: "port-left-flank", label: "Left flank port", group: "laparoscopic port", svgPath: "M132 150 h12 v12 h-12 Z", laterality: "left", locationCode: pending("port-flank", "Flank laparoscopic port site") },
    { id: "port-right-iliac", label: "Right iliac fossa port", group: "laparoscopic port", svgPath: "M68 214 h12 v12 h-12 Z", laterality: "right", locationCode: pending("port-iliac-fossa", "Iliac fossa laparoscopic port site") },
    { id: "port-left-iliac", label: "Left iliac fossa port", group: "laparoscopic port", svgPath: "M120 214 h12 v12 h-12 Z", laterality: "left", locationCode: pending("port-iliac-fossa", "Iliac fossa laparoscopic port site") },
    { id: "port-suprapubic", label: "Suprapubic port", group: "laparoscopic port", svgPath: "M94 220 h12 v12 h-12 Z", laterality: "midline", locationCode: code("68505005", "Hypogastric region") },
  ],
};

/**
 * Inguinal, femoral and abdominal-wall hernia sites.
 *
 * Inguinal and femoral are drawn as separate targets because the distinction changes the
 * operation and the urgency: a femoral hernia strangulates. Laterality is mandatory here —
 * wrong-side hernia surgery is a never event, and the map is one of the places the side is
 * first written down.
 */
export const INGUINAL_GROIN_MAP: RegionMapDef = {
  id: "inguinal-groin",
  title: "Groin and abdominal wall hernia sites",
  viewBox: "0 0 200 200",
  silhouettePaths: [
    "M30 10 h140 v170 h-140 Z",
    "M40 108 L100 140 L160 108 L160 118 L100 150 L40 118 Z",
    "M96 10 h8 v130 h-8 Z",
  ],
  regions: [
    { id: "inguinal-canal-right", label: "Right inguinal canal", group: "groin", svgPath: "M52 116 L86 134 L82 146 L48 128 Z", laterality: "right", locationCode: pending("inguinal-canal", "Inguinal canal structure") },
    { id: "inguinal-canal-left", label: "Left inguinal canal", group: "groin", svgPath: "M148 116 L114 134 L118 146 L152 128 Z", laterality: "left", locationCode: pending("inguinal-canal", "Inguinal canal structure") },
    { id: "femoral-canal-right", label: "Right femoral canal", group: "groin", svgPath: "M74 150 h16 v16 h-16 Z", laterality: "right", locationCode: pending("femoral-canal", "Femoral canal structure") },
    { id: "femoral-canal-left", label: "Left femoral canal", group: "groin", svgPath: "M110 150 h16 v16 h-16 Z", laterality: "left", locationCode: pending("femoral-canal", "Femoral canal structure") },
    { id: "scrotal", label: "Scrotum", group: "groin", svgPath: "M86 168 h28 v22 h-28 Z", laterality: "midline", locationCode: code("20233005", "Scrotum") },
    { id: "umbilical-hernia-site", label: "Umbilical", group: "abdominal wall", svgPath: "M92 40 h16 v16 h-16 Z", laterality: "midline", locationCode: code("29707007", "Umbilicus") },
    { id: "epigastric-hernia-site", label: "Epigastric", group: "abdominal wall", svgPath: "M92 14 h16 v16 h-16 Z", laterality: "midline", locationCode: code("27947004", "Epigastric region") },
    { id: "spigelian-right", label: "Right Spigelian", group: "abdominal wall", svgPath: "M58 78 h14 v26 h-14 Z", laterality: "right", locationCode: pending("spigelian-site", "Spigelian hernia site") },
    { id: "spigelian-left", label: "Left Spigelian", group: "abdominal wall", svgPath: "M128 78 h14 v26 h-14 Z", laterality: "left", locationCode: pending("spigelian-site", "Spigelian hernia site") },
  ],
};

/**
 * Biliary tree.
 *
 * Bile duct injury is the injury that defines laparoscopic cholecystectomy, and it is
 * described by level — cystic duct stump versus common hepatic duct versus a sectoral duct.
 * Prose loses the level; this map keeps it.
 */
export const BILIARY_TREE_MAP: RegionMapDef = {
  id: "biliary-tree",
  title: "Biliary tree",
  viewBox: "0 0 200 220",
  silhouettePaths: [
    "M20 16 h160 v70 h-160 Z",
    "M92 86 h16 v80 h-16 Z",
    "M60 166 h80 v46 h-80 Z",
  ],
  regions: [
    { id: "right-hepatic-duct", label: "Right hepatic duct", group: "duct", svgPath: "M56 60 L94 92 L86 100 L48 68 Z", laterality: "right", locationCode: pending("right-hepatic-duct", "Right hepatic duct structure") },
    { id: "left-hepatic-duct", label: "Left hepatic duct", group: "duct", svgPath: "M144 60 L106 92 L114 100 L152 68 Z", laterality: "left", locationCode: pending("left-hepatic-duct", "Left hepatic duct structure") },
    { id: "common-hepatic-duct", label: "Common hepatic duct", group: "duct", svgPath: "M92 100 h16 v22 h-16 Z", laterality: "midline", locationCode: pending("common-hepatic-duct", "Common hepatic duct structure") },
    { id: "cystic-duct", label: "Cystic duct", group: "duct", svgPath: "M60 106 h32 v10 h-32 Z", laterality: "right", locationCode: pending("cystic-duct", "Cystic duct structure") },
    { id: "gallbladder", label: "Gallbladder", group: "organ", svgPath: "M24 96 h34 v44 h-34 Z", laterality: "right", locationCode: code("28231008", "Gallbladder structure") },
    { id: "common-bile-duct", label: "Common bile duct", group: "duct", svgPath: "M92 124 h16 v40 h-16 Z", laterality: "midline", locationCode: code("79741001", "Common bile duct structure") },
    { id: "ampulla", label: "Ampulla and sphincter", group: "duct", svgPath: "M88 168 h22 v16 h-22 Z", laterality: "midline", locationCode: pending("hepatopancreatic-ampulla", "Hepatopancreatic ampulla structure") },
  ],
};

/**
 * Colorectal segments.
 *
 * Resection, tumour site, perforation and anastomosis are all described by segment, and the
 * flexures are called out separately because they are where mobilisation and blood supply
 * change — "splenic flexure lesion" is a different operation from "descending colon lesion".
 */
export const COLORECTAL_SEGMENTS_MAP: RegionMapDef = {
  id: "colorectal-segments",
  title: "Colorectal segments",
  viewBox: "0 0 200 260",
  silhouettePaths: [
    "M20 20 h160 v220 h-160 Z",
    "M40 56 h120 v10 h-120 Z",
  ],
  regions: [
    { id: "caecum", label: "Caecum", group: "right colon", svgPath: "M40 170 h34 v32 h-34 Z", laterality: "right", locationCode: pending("caecum", "Caecum structure") },
    { id: "ascending-colon", label: "Ascending colon", group: "right colon", svgPath: "M44 92 h26 v76 h-26 Z", laterality: "right", locationCode: pending("ascending-colon", "Ascending colon structure") },
    { id: "hepatic-flexure", label: "Hepatic flexure", group: "right colon", svgPath: "M44 64 h30 v26 h-30 Z", laterality: "right", locationCode: pending("hepatic-flexure", "Hepatic flexure of colon structure") },
    { id: "transverse-colon", label: "Transverse colon", group: "transverse", svgPath: "M76 64 h48 v26 h-48 Z", laterality: "midline", locationCode: pending("transverse-colon", "Transverse colon structure") },
    { id: "splenic-flexure", label: "Splenic flexure", group: "left colon", svgPath: "M126 64 h30 v26 h-30 Z", laterality: "left", locationCode: pending("splenic-flexure", "Splenic flexure of colon structure") },
    { id: "descending-colon", label: "Descending colon", group: "left colon", svgPath: "M130 92 h26 v76 h-26 Z", laterality: "left", locationCode: pending("descending-colon", "Descending colon structure") },
    { id: "sigmoid-colon", label: "Sigmoid colon", group: "left colon", svgPath: "M96 172 h60 v28 h-60 Z", laterality: "left", locationCode: pending("sigmoid-colon", "Sigmoid colon structure") },
    { id: "rectum", label: "Rectum", group: "anorectum", svgPath: "M88 204 h24 v28 h-24 Z", laterality: "midline", locationCode: code("34402009", "Rectum structure") },
    { id: "anus", label: "Anal canal", group: "anorectum", svgPath: "M92 234 h16 v16 h-16 Z", laterality: "midline", locationCode: code("53505006", "Anal structure") },
  ],
};

/**
 * Upper gastrointestinal tract.
 *
 * Oesophageal thirds and the gastro-oesophageal junction are drawn apart because the surgical
 * approach changes at each boundary, and the gastric parts because a bleeding ulcer at the
 * antrum and one at the fundus are different problems with different operations.
 */
export const UPPER_GI_MAP: RegionMapDef = {
  id: "upper-gi",
  title: "Upper gastrointestinal tract",
  viewBox: "0 0 200 280",
  silhouettePaths: [
    "M84 10 h24 v100 h-24 Z",
    "M62 110 h96 v90 h-96 Z",
    "M40 196 h100 v70 h-100 Z",
  ],
  regions: [
    { id: "oesophagus-upper-third", label: "Oesophagus — upper third", group: "oesophagus", svgPath: "M86 12 h20 v30 h-20 Z", laterality: "midline", locationCode: code("32849002", "Esophageal structure") },
    { id: "oesophagus-middle-third", label: "Oesophagus — middle third", group: "oesophagus", svgPath: "M86 44 h20 v30 h-20 Z", laterality: "midline", locationCode: code("32849002", "Esophageal structure") },
    { id: "oesophagus-lower-third", label: "Oesophagus — lower third", group: "oesophagus", svgPath: "M86 76 h20 v30 h-20 Z", laterality: "midline", locationCode: code("32849002", "Esophageal structure") },
    { id: "gastro-oesophageal-junction", label: "Gastro-oesophageal junction", group: "oesophagus", svgPath: "M82 108 h24 v14 h-24 Z", laterality: "midline", locationCode: pending("gastro-oesophageal-junction", "Gastroesophageal junction structure") },
    { id: "gastric-fundus", label: "Fundus", group: "stomach", svgPath: "M108 112 h46 v34 h-46 Z", laterality: "left", locationCode: pending("gastric-fundus", "Fundus of stomach structure") },
    { id: "gastric-body", label: "Body of stomach", group: "stomach", svgPath: "M74 150 h80 v44 h-80 Z", laterality: "midline", locationCode: code("69695003", "Stomach structure") },
    { id: "gastric-antrum", label: "Antrum", group: "stomach", svgPath: "M76 198 h54 v28 h-54 Z", laterality: "midline", locationCode: pending("gastric-antrum", "Pyloric antrum structure") },
    { id: "pylorus", label: "Pylorus", group: "stomach", svgPath: "M62 198 h12 v28 h-12 Z", laterality: "midline", locationCode: pending("pylorus", "Pylorus structure") },
    { id: "duodenum-d1", label: "Duodenum D1 (cap)", group: "duodenum", svgPath: "M42 198 h18 v28 h-18 Z", laterality: "right", locationCode: code("38848004", "Duodenal structure") },
    { id: "duodenum-d2", label: "Duodenum D2 (descending)", group: "duodenum", svgPath: "M42 228 h18 v34 h-18 Z", laterality: "right", locationCode: pending("duodenum-second-part", "Second part of duodenum structure") },
    { id: "duodenum-d3", label: "Duodenum D3 (transverse)", group: "duodenum", svgPath: "M62 246 h32 v16 h-32 Z", laterality: "midline", locationCode: pending("duodenum-third-part", "Third part of duodenum structure") },
    { id: "duodenum-d4", label: "Duodenum D4 (ascending)", group: "duodenum", svgPath: "M96 230 h16 v32 h-16 Z", laterality: "left", locationCode: pending("duodenum-fourth-part", "Fourth part of duodenum structure") },
  ],
};

/**
 * Liver — Couinaud segments, flattened.
 *
 * A true Couinaud map is three-dimensional and cannot be drawn honestly in one plane; this is
 * a projection whose only job is to let a surgeon or radiologist say "segment VII" and have
 * the record mean segment VII. It is not a resection planning tool and must not be used as one.
 */
export const LIVER_SEGMENTS_MAP: RegionMapDef = {
  id: "liver-segments",
  title: "Liver — Couinaud segments",
  viewBox: "0 0 220 160",
  silhouettePaths: [
    "M14 26 h194 v112 h-194 Z",
    "M120 26 h6 v112 h-6 Z",
  ],
  regions: [
    { id: "liver-segment-vii", label: "Segment VII", group: "right posterior", svgPath: "M20 30 h46 v44 h-46 Z", laterality: "right", locationCode: pending("liver-segment-vii", "Couinaud segment VII of liver") },
    { id: "liver-segment-vi", label: "Segment VI", group: "right posterior", svgPath: "M20 78 h46 v52 h-46 Z", laterality: "right", locationCode: pending("liver-segment-vi", "Couinaud segment VI of liver") },
    { id: "liver-segment-viii", label: "Segment VIII", group: "right anterior", svgPath: "M70 30 h46 v44 h-46 Z", laterality: "right", locationCode: pending("liver-segment-viii", "Couinaud segment VIII of liver") },
    { id: "liver-segment-v", label: "Segment V", group: "right anterior", svgPath: "M70 78 h46 v52 h-46 Z", laterality: "right", locationCode: pending("liver-segment-v", "Couinaud segment V of liver") },
    { id: "liver-segment-i", label: "Segment I (caudate)", group: "caudate", svgPath: "M130 82 h26 v34 h-26 Z", laterality: "midline", locationCode: pending("liver-segment-i", "Caudate lobe of liver") },
    { id: "liver-segment-iv", label: "Segment IV", group: "left medial", svgPath: "M130 30 h32 v46 h-32 Z", laterality: "left", locationCode: pending("liver-segment-iv", "Couinaud segment IV of liver") },
    { id: "liver-segment-ii", label: "Segment II", group: "left lateral", svgPath: "M166 30 h40 v38 h-40 Z", laterality: "left", locationCode: pending("liver-segment-ii", "Couinaud segment II of liver") },
    { id: "liver-segment-iii", label: "Segment III", group: "left lateral", svgPath: "M166 72 h40 v48 h-40 Z", laterality: "left", locationCode: pending("liver-segment-iii", "Couinaud segment III of liver") },
    { id: "liver-whole", label: "Liver (segment not specified)", group: "whole organ", svgPath: "M20 134 h186 v20 h-186 Z", laterality: "midline", locationCode: code("10200004", "Liver structure") },
  ],
};

/**
 * Pancreas.
 *
 * Head versus tail decides the operation — a Whipple or a distal pancreatectomy — and the
 * uncinate process is drawn separately because involvement there is what makes a tumour
 * borderline resectable against the mesenteric vessels.
 */
export const PANCREAS_MAP: RegionMapDef = {
  id: "pancreas",
  title: "Pancreas",
  viewBox: "0 0 220 140",
  silhouettePaths: [
    "M14 40 h196 v62 h-196 Z",
    "M14 102 h50 v26 h-50 Z",
  ],
  regions: [
    { id: "pancreatic-head", label: "Head", group: "pancreas", svgPath: "M18 52 h44 v52 h-44 Z", laterality: "right", locationCode: pending("pancreatic-head", "Head of pancreas structure") },
    { id: "pancreatic-uncinate", label: "Uncinate process", group: "pancreas", svgPath: "M20 106 h36 v20 h-36 Z", laterality: "right", locationCode: pending("pancreatic-uncinate-process", "Uncinate process of pancreas structure") },
    { id: "pancreatic-neck", label: "Neck", group: "pancreas", svgPath: "M64 52 h26 v46 h-26 Z", laterality: "midline", locationCode: pending("pancreatic-neck", "Neck of pancreas structure") },
    { id: "pancreatic-body", label: "Body", group: "pancreas", svgPath: "M92 46 h60 v44 h-60 Z", laterality: "left", locationCode: pending("pancreatic-body", "Body of pancreas structure") },
    { id: "pancreatic-tail", label: "Tail", group: "pancreas", svgPath: "M154 40 h52 v40 h-52 Z", laterality: "left", locationCode: pending("pancreatic-tail", "Tail of pancreas structure") },
    { id: "pancreas-whole", label: "Pancreas (part not specified)", group: "whole organ", svgPath: "M92 96 h114 v18 h-114 Z", laterality: "midline", locationCode: code("15776009", "Pancreatic structure") },
  ],
};

/**
 * Stoma siting zones.
 *
 * Stoma siting is a pre-operative act with a permanent consequence: a stoma sited in a
 * skin crease, at the belt line or on the costal margin leaks, and a leaking stoma is why
 * people stop leaving the house. Recording where it was sited — and by whom — makes the
 * pre-operative marking auditable rather than a chalk line lost at induction.
 */
export const STOMA_SITES_MAP: RegionMapDef = {
  id: "stoma-sites",
  title: "Stoma siting zones",
  viewBox: "0 0 200 240",
  silhouettePaths: [
    "M30 20 h140 v200 h-140 Z",
    "M40 54 L100 84 L160 54 L160 62 L100 92 L40 62 Z",
    "M96 20 h8 v200 h-8 Z",
    "M92 116 h16 v16 h-16 Z",
  ],
  regions: [
    { id: "stoma-zone-ruq", label: "Right upper quadrant zone", group: "siting zone", svgPath: "M44 62 h48 v46 h-48 Z", laterality: "right", locationCode: pending("stoma-zone-ruq", "Right upper quadrant stoma siting zone") },
    { id: "stoma-zone-luq", label: "Left upper quadrant zone", group: "siting zone", svgPath: "M108 62 h48 v46 h-48 Z", laterality: "left", locationCode: pending("stoma-zone-luq", "Left upper quadrant stoma siting zone") },
    { id: "stoma-zone-rif", label: "Right iliac fossa zone", group: "siting zone", svgPath: "M44 136 h48 v48 h-48 Z", laterality: "right", locationCode: pending("stoma-zone-rif", "Right iliac fossa stoma siting zone") },
    { id: "stoma-zone-lif", label: "Left iliac fossa zone", group: "siting zone", svgPath: "M108 136 h48 v48 h-48 Z", laterality: "left", locationCode: pending("stoma-zone-lif", "Left iliac fossa stoma siting zone") },
    { id: "urostomy-site", label: "Urostomy site", group: "siting zone", svgPath: "M108 188 h44 v26 h-44 Z", laterality: "left", locationCode: pending("urostomy-site", "Urinary stoma siting zone") },
    { id: "stoma-avoid-belt-line", label: "Belt line (avoid)", group: "avoid", svgPath: "M36 112 h128 v10 h-128 Z", laterality: "midline", locationCode: pending("stoma-avoid-belt-line", "Belt line — stoma siting exclusion") },
    { id: "stoma-avoid-costal-margin", label: "Costal margin (avoid)", group: "avoid", svgPath: "M40 40 h120 v12 h-120 Z", laterality: "midline", locationCode: pending("stoma-avoid-costal-margin", "Costal margin — stoma siting exclusion") },
  ],
};

/** Every abdominal surgical map, for registration and coverage reporting. */
export const SURGICAL_ABDOMINAL_MAPS: RegionMapDef[] = [
  ABDOMINAL_INCISIONS_MAP,
  INGUINAL_GROIN_MAP,
  BILIARY_TREE_MAP,
  COLORECTAL_SEGMENTS_MAP,
  UPPER_GI_MAP,
  LIVER_SEGMENTS_MAP,
  PANCREAS_MAP,
  STOMA_SITES_MAP,
];
