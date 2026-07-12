#!/usr/bin/env node
/**
 * HPA requirement → marketplace sourcing-category draft generator.
 *
 * Reads the 38 manual-derived inspection modules and drafts a mapping of
 * purchasable requirements to sourcing categories. The output is a DRAFT for
 * human curation — nothing here is authoritative until curated, approved and
 * seeded via the msika V007 migration.
 *
 * Modes:
 *   node generate-sourcing-map-draft.mjs draft  > draft.csv
 *   node generate-sourcing-map-draft.mjs sql <curated.csv>   # emits V007 seed SQL
 */
import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";

const MODULE_DIR = new URL(
  "../../services/tuso-service/src/main/resources/regulatory/inspection-modules/",
  import.meta.url
).pathname;

const PURCHASABLE_TYPES = new Set(["EQUIPMENT", "MEDICINE", "SAFETY"]);
// DOCUMENT items are purchasable only when they are physical artefacts.
const PURCHASABLE_DOCUMENT_TERMS =
  /register|signage|sign\b|chart|poster|label|book\b|stationery|forms?\b/i;
const DANGEROUS_TERMS = /dangerous drug|dda\b|controlled|schedule|narcotic|pethidine|morphine/i;

function loadModules() {
  return readdirSync(MODULE_DIR)
    .filter((f) => f.endsWith(".json"))
    .map((f) => JSON.parse(readFileSync(join(MODULE_DIR, f), "utf8")));
}

function proposeCategory(item, moduleCode) {
  if (item.requirementType === "MEDICINE") {
    return DANGEROUS_TERMS.test(item.requirement) ? "CONTROLLED_MEDICINES" : "MEDICINES_GENERAL";
  }
  if (item.group) return item.group; // curators merge/rename group-derived buckets
  const text = item.requirement.toLowerCase();
  const buckets = [
    [/(fridge|refrigerat|cold chain|freezer)/, "REFRIGERATION_COLD_CHAIN"],
    [/(autoclave|steriliz|sterilis|disinfect)/, "STERILISATION_DISINFECTION"],
    [/(fire extinguisher|fire blanket|smoke)/, "FIRE_SAFETY"],
    [/(glove|ppe|apron|mask|protective|gown)/, "PPE_PROTECTIVE_WEAR"],
    [/(sharps|waste|bin|disposal)/, "WASTE_MANAGEMENT"],
    [/(couch|bed\b|chair|desk|table|cabinet|cupboard|trolley|stool|shelf|locker|screen)/, "CLINICAL_FURNITURE"],
    [/(x-ray|imaging|ultrasound|mri|ct\b|radiograph|film|cassette|lead)/, "IMAGING_EQUIPMENT"],
    [/(microscope|centrifuge|analyser|analyzer|reagent|slide|specimen)/, "LABORATORY_EQUIPMENT"],
    [/(oxygen|suction|resuscit|ambu|defibril|laryngoscope|airway)/, "EMERGENCY_RESUSCITATION"],
    [/(sphygmomanometer|stethoscope|thermometer|otoscope|ophthalmoscope|weighing|scale|diagnostic set)/, "DIAGNOSTIC_INSTRUMENTS"],
    [/(basin|sink|water|tap|geyser)/, "PLUMBING_SANITARY"],
    [/(linen|towel|blanket|sheet|curtain)/, "LINEN_TEXTILES"],
    [/(computer|printer|telephone|intercom)/, "ICT_COMMUNICATIONS"],
    [/(generator|power|electric|lighting|light\b|air condition)/, "POWER_LIGHTING_HVAC"],
    [/(dental)/, "DENTAL_EQUIPMENT"],
    [/(register|signage|chart|poster|label|book|forms?)/, "REGISTERS_SIGNAGE_STATIONERY"],
  ];
  for (const [re, cat] of buckets) if (re.test(text)) return cat;
  return "GENERAL_MEDICAL_EQUIPMENT";
}

function draftRows() {
  const rows = [];
  for (const mod of loadModules()) {
    for (const item of mod.items) {
      const type = item.requirementType;
      const purchasable =
        PURCHASABLE_TYPES.has(type) ||
        (type === "DOCUMENT" && PURCHASABLE_DOCUMENT_TERMS.test(item.requirement));
      if (!purchasable) continue;
      if (item.group && item.groupRole === "PARENT") continue; // components are the purchasable rows
      const restricted =
        type === "MEDICINE" && DANGEROUS_TERMS.test(item.requirement);
      rows.push({
        code: item.code,
        module: mod.moduleCode,
        type,
        category: proposeCategory(item, mod.moduleCode),
        restricted,
        requirement: item.requirement.replace(/\s+/g, " ").slice(0, 160),
      });
    }
  }
  return rows;
}

function csvEscape(v) {
  const s = String(v ?? "");
  return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
}

const mode = process.argv[2] ?? "draft";

if (mode === "draft") {
  const rows = draftRows();
  console.log("requirement_code,module,requirement_type,proposed_category,restricted,requirement_text");
  for (const r of rows) {
    console.log(
      [r.code, r.module, r.type, r.category, r.restricted, r.requirement].map(csvEscape).join(",")
    );
  }
  console.error(`# ${rows.length} purchasable rows drafted`);
} else if (mode === "sql") {
  // Emit V007 seed SQL from a curated CSV:
  // requirement_code,module,category_code,restricted,notes  (header row required)
  const csv = readFileSync(process.argv[3], "utf8").trim().split("\n").slice(1);
  let seq = 0;
  const pad = (n) => String(n).padStart(4, "0");
  for (const line of csv) {
    const [code, module, category] = line.split(",");
    if (!code || !category || category === "EXCLUDED") continue;
    seq++;
    // Deterministic literal 26-char ids (ULID-shaped) — replay-safe in migrations.
    const mapId = `SRCMAP0000000000000000${pad(seq)}`.slice(0, 26);
    console.log(
      `    ('${mapId}', '${code}', '${category}', 'APPROVED', 'HPA-2017-V1 module ${module}; curated 2026-07-12', 'PROGRAMME_OWNER', 1),`
    );
  }
} else {
  console.error("unknown mode");
  process.exit(1);
}
