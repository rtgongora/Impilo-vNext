import { describe, expect, it } from "vitest";
import { extractPublicHealthList, parseWeeklyIdsrPayload } from "./usePublicHealth";

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
