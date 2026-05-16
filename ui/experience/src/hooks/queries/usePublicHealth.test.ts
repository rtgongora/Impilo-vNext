import { describe, expect, it } from "vitest";
import { extractPublicHealthList, parseCountersPayload, parseWeeklyIdsrPayload } from "./usePublicHealth";

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

describe("parseCountersPayload", () => {
  it("normalizes weekly idsr counters from bff payload", () => {
    const rows = parseCountersPayload({
      counters: [{ facility_id: "fac-1", syndrome_code: "AWD", count_date: "2026-05-10", event_count: 7 }],
    });
    expect(rows).toHaveLength(1);
    expect(rows[0]?.label).toBe("AWD");
    expect(rows[0]?.value).toBe("7");
    expect(rows[0]?.detail).toContain("2026-05-10");
  });
});

describe("parseWeeklyIdsrPayload", () => {
  it("normalizes rows payload from weekly idsr endpoint", () => {
    const rows = parseWeeklyIdsrPayload({
      rows: [{ syndromeCode: "AWD", district: "Harare", weekStart: "2026-05-10", total: 11 }],
    });
    expect(rows).toHaveLength(1);
    expect(rows[0]?.label).toBe("AWD");
    expect(rows[0]?.value).toBe("11");
    expect(rows[0]?.detail).toContain("Harare");
  });
});
