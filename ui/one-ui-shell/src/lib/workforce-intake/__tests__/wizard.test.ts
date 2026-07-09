import { describe, expect, it } from "vitest";
import {
  CANONICAL_COLUMNS,
  REQUIRED_COLUMNS,
  WIZARD_STEPS,
  canExecute,
  countCsvDataRows,
  isRowSettled,
  missingRequiredColumns,
  parseCsvHeaders,
  stepForStage,
  stepIndex,
  suggestMapping,
} from "../wizard";

describe("workforce-intake wizard state", () => {
  it("declares the six journey steps in order", () => {
    expect(WIZARD_STEPS).toEqual([
      "upload",
      "mapping",
      "match",
      "approve",
      "execute",
      "complete",
    ]);
    expect(stepIndex("upload")).toBe(0);
    expect(stepIndex("complete")).toBe(5);
  });

  it("derives the wizard step from the server-side batch stage", () => {
    expect(stepForStage("UPLOADED")).toBe("mapping");
    expect(stepForStage("MAPPED")).toBe("mapping");
    expect(stepForStage("VALIDATED")).toBe("match");
    expect(stepForStage("MATCHED")).toBe("approve");
    expect(stepForStage("APPROVED")).toBe("execute");
    expect(stepForStage("EXECUTING")).toBe("execute");
    expect(stepForStage("PARTIAL")).toBe("execute");
    expect(stepForStage("COMPLETED")).toBe("complete");
    expect(stepForStage(undefined)).toBe("upload");
    expect(stepForStage("")).toBe("upload");
  });

  it("parses CSV headers lower-cased and counts data rows", () => {
    const csv = "nationalId,givenName,familyName,profession\r\n63-1,Rudo,Moyo,NURSE\n63-2,Tawanda,Ncube,DOCTOR\n\n";
    expect(parseCsvHeaders(csv)).toEqual(["nationalid", "givenname", "familyname", "profession"]);
    expect(countCsvDataRows(csv)).toBe(2);
    expect(countCsvDataRows("")).toBe(0);
  });

  it("suggests identity mappings for canonical headers and aliases for HR-export spellings", () => {
    const mapping = suggestMapping([
      "nationalid",
      "first_name",
      "surname",
      "post_title",
      "duty_station",
      "official_email",
      "unknown_column",
    ]);
    expect(mapping).toEqual({
      nationalid: "nationalid",
      first_name: "givenname",
      surname: "familyname",
      post_title: "profession",
      duty_station: "facilitycode",
      official_email: "email",
    });
    expect(mapping).not.toHaveProperty("unknown_column");
  });

  it("every suggested mapping target is a canonical column", () => {
    const mapping = suggestMapping(["id_number", "last_name", "mobile", "health_id", "reg_number"]);
    for (const target of Object.values(mapping)) {
      expect(CANONICAL_COLUMNS).toContain(target);
    }
  });

  it("reports required canonical columns not yet covered by the mapping", () => {
    expect(missingRequiredColumns({})).toEqual([...REQUIRED_COLUMNS]);
    expect(
      missingRequiredColumns({
        id_number: "nationalid",
        first_name: "givenname",
        surname: "familyname",
      }),
    ).toEqual(["profession"]);
    expect(
      missingRequiredColumns({
        nationalid: "nationalid",
        givenname: "givenname",
        familyname: "familyname",
        profession: "profession",
      }),
    ).toEqual([]);
  });

  it("only APPROVED and PARTIAL batches can execute", () => {
    expect(canExecute("APPROVED")).toBe(true);
    expect(canExecute("PARTIAL")).toBe(true);
    expect(canExecute("MATCHED")).toBe(false);
    expect(canExecute("EXECUTING")).toBe(false);
    expect(canExecute(undefined)).toBe(false);
  });

  it("treats DONE, blocked-contact and skips as settled; failures as unsettled", () => {
    expect(isRowSettled("DONE")).toBe(true);
    expect(isRowSettled("BLOCKED_MISSING_CONTACT")).toBe(true);
    expect(isRowSettled("SKIPPED_DUPLICATE_IN_BATCH")).toBe(true);
    expect(isRowSettled("SKIPPED_INVALID_ROW")).toBe(true);
    expect(isRowSettled("FAILED_VASHANDI")).toBe(false);
    expect(isRowSettled("BLOCKED_AMBIGUOUS_MATCH")).toBe(false);
    expect(isRowSettled(undefined)).toBe(false);
    expect(isRowSettled("")).toBe(false);
  });
});
