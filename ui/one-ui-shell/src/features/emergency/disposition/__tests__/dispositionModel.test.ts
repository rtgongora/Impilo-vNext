import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import {
  DISPOSITION_TYPES,
  dispositionGroups,
  dispositionType,
  missingFields,
} from "../dispositionModel";

/**
 * The model file says it mirrors pct's V208 CHECK constraints and that pct wins if they disagree.
 * A comment cannot keep that true. These tests read the migration and fail when the copy drifts, so
 * the claim is checked on every run rather than believed.
 */
const MIGRATION = readFileSync(
  resolve(
    __dirname,
    "../../../../../../..",
    "services/pct-service/src/main/resources/db/migration/V208__emergency_disposition_and_observation.sql",
  ),
  "utf8",
);

/** The code list from one named CHECK constraint, in the migration's own words. */
function codesInConstraint(constraintName: string): string[] {
  const start = MIGRATION.indexOf(`CONSTRAINT ${constraintName}`);
  expect(start, `constraint ${constraintName} is present in V208`).toBeGreaterThan(-1);
  const body = MIGRATION.slice(start, MIGRATION.indexOf("),", start));
  return [...body.matchAll(/'([A-Z_]+)'/g)].map((match) => match[1]);
}

describe("emergency disposition model", () => {
  it("carries exactly the fifteen codes pct will accept", () => {
    const fromMigration = codesInConstraint("chk_ed_disp_type").sort();
    const fromModel = DISPOSITION_TYPES.map((type) => type.code).sort();

    expect(fromMigration).toHaveLength(15);
    expect(fromModel).toEqual(fromMigration);
  });

  it("asks for a destination on exactly the types pct requires one for", () => {
    const fromModel = DISPOSITION_TYPES.filter((type) => type.requires.includes("destination"))
      .map((type) => type.code)
      .sort();

    expect(fromModel).toEqual(codesInConstraint("chk_ed_disp_destination_required").sort());
  });

  it("asks for circumstances on exactly the types pct requires them for", () => {
    const fromModel = DISPOSITION_TYPES.filter((type) =>
      type.requires.includes("causeOrCircumstances"),
    ).map((type) => type.code);

    expect(fromModel).toEqual(codesInConstraint("chk_ed_disp_death_circumstances_required"));
  });

  it("asks when the patient was last seen on exactly the unplanned departures", () => {
    const fromModel = DISPOSITION_TYPES.filter((type) => type.requires.includes("lastSeenAt"))
      .map((type) => type.code)
      .sort();

    expect(fromModel).toEqual(codesInConstraint("chk_ed_disp_last_seen_required").sort());
  });

  it("always asks for a reason, because pct always requires one", () => {
    // chk_ed_disp_mandatory_content is universal, so no disposition can be complete without it.
    expect(MIGRATION).toContain("disposition_reason IS NOT NULL");

    for (const type of DISPOSITION_TYPES) {
      expect(missingFields({ dispositionType: type.code, dispositionReason: "  " })).toContain(
        "dispositionReason",
      );
    }
  });

  it("names every field still missing, not merely the first", () => {
    expect(
      missingFields({ dispositionType: "TRANSFERRED_OUT", dispositionReason: "" }),
    ).toEqual(["dispositionReason", "destination"]);
  });

  it("treats whitespace as absence, the same way pct's btrim does", () => {
    expect(
      missingFields({
        dispositionType: "ADMITTED_ICU",
        dispositionReason: "Needs ventilation",
        destination: "   ",
      }),
    ).toEqual(["destination"]);
  });

  it("reports an unknown code as the one thing wrong, rather than listing its fields", () => {
    // A code pct would reject outright should not produce a field checklist a clinician could fill
    // in and still be refused.
    expect(missingFields({ dispositionType: "SENT_TO_RADIOLOGY", dispositionReason: "x" })).toEqual([
      "dispositionType",
    ]);
    expect(dispositionType("SENT_TO_RADIOLOGY")).toBeUndefined();
  });

  it("groups every type exactly once for the picker", () => {
    const grouped = dispositionGroups().flatMap((group) => group.types.map((type) => type.code));
    expect(grouped.sort()).toEqual(DISPOSITION_TYPES.map((type) => type.code).sort());
  });
});
