import { describe, expect, it } from "vitest";
import {
  extractPublicHealthList,
  parseRegistryCompliancePayload,
  parseRegistryEnforcementPayload,
  parseRegistryInspectionPayload,
  parseWeeklyIdsrPayload,
} from "./usePublicHealth";

describe("extractPublicHealthList", () => {
  it("returns arrays as-is", () => {
    expect(extractPublicHealthList([1, 2], ["items"])).toEqual([1, 2]);
  });

  it("unwraps surveillance-style items", () => {
    expect(extractPublicHealthList({ items: [{ id: 1 }], total_elements: 1 }, ["items"])).toEqual([{ id: 1 }]);
  });

  it("unwraps counter payloads", () => {
    expect(
      extractPublicHealthList({ counters: [{ syndrome_code: "FEVER" }], total: 1 }, ["counters"]),
    ).toEqual([{ syndrome_code: "FEVER" }]);
  });

  it("unwraps alert payloads", () => {
    expect(extractPublicHealthList({ alerts: [{ id: 9 }], total: 1 }, ["alerts"])).toEqual([{ id: 9 }]);
  });

  it("falls back to nested data array", () => {
    expect(extractPublicHealthList({ data: [{ a: 1 }] }, ["items"])).toEqual([{ a: 1 }]);
  });
});

describe("parseWeeklyIdsrPayload", () => {
  it("normalizes rows payload from weekly idsr endpoint", () => {
    const rows = parseWeeklyIdsrPayload({
      rows: [{ syndromeCode: "MEASLES", district: "Mutare", weekStart: "2026-05-10", total: 4 }],
    });
    expect(rows).toHaveLength(1);
    expect(rows[0]?.label).toBe("MEASLES");
    expect(rows[0]?.value).toBe("4");
    expect(rows[0]?.detail).toContain("Mutare");
  });
});

describe("site registry payload parsers", () => {
  it("parses inspection register rows", () => {
    const rows = parseRegistryInspectionPayload({
      items: [{ inspection_id: "insp-1", site_name: "Mbare Market", status: "COMPLETED", score_percent: 88 }],
    });
    expect(rows[0]?.inspectionId).toBe("insp-1");
    expect(rows[0]?.siteName).toBe("Mbare Market");
    expect(rows[0]?.scorePercent).toBe(88);
  });

  it("parses compliance rows", () => {
    const rows = parseRegistryCompliancePayload({
      items: [{ action_id: "act-1", action_type: "RECTIFY_FINDING", status: "OPEN" }],
    });
    expect(rows[0]?.actionId).toBe("act-1");
    expect(rows[0]?.status).toBe("OPEN");
  });

  it("parses enforcement rows", () => {
    const rows = parseRegistryEnforcementPayload({
      items: [{ case_id: "case-1", trigger_type: "INSPECTION_FAILURE", status: "OPEN" }],
    });
    expect(rows[0]?.caseId).toBe("case-1");
    expect(rows[0]?.triggerType).toBe("INSPECTION_FAILURE");
  });
});
