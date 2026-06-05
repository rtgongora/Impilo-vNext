import { describe, expect, it } from "vitest";

function asList(raw: unknown): Record<string, unknown>[] {
  if (Array.isArray(raw)) {
    return raw.filter((x): x is Record<string, unknown> => typeof x === "object" && x !== null);
  }
  if (raw && typeof raw === "object") {
    const record = raw as Record<string, unknown>;
    if (Array.isArray(record.items)) return record.items as Record<string, unknown>[];
    if (Array.isArray(record.content)) return record.content as Record<string, unknown>[];
    if (Array.isArray(record.data)) return record.data as Record<string, unknown>[];
  }
  return [];
}

describe("citizen health summary results extraction", () => {
  it("reads results from summary.results", () => {
    const summary = {
      results: [{ id: "r1", display: "HbA1c", value: "6.2", unit: "%" }],
    };
    expect(asList(summary.results)).toHaveLength(1);
  });

  it("reads labs and observations when passed as list payloads", () => {
    expect(asList([{ id: "l1" }])).toHaveLength(1);
    expect(asList({ data: [{ id: "o1" }] })).toHaveLength(1);
  });
});
