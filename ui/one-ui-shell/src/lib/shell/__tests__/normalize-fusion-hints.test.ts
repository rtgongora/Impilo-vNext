import { describe, expect, it } from "vitest";
import { normalizeFusionHints } from "../normalize-fusion-hints";

describe("normalizeFusionHints", () => {
  it("returns empty for non-object or missing hints", () => {
    expect(normalizeFusionHints(null)).toEqual([]);
    expect(normalizeFusionHints({ data: {} })).toEqual([]);
  });

  it("maps BFF shell-suggest hints with hit entity fields", () => {
    const raw = {
      data: {
        queryId: "q1",
        hints: [
          {
            source: "platform_index",
            hit: {
              entityType: "Patient",
              entityId: "p-1",
              searchableText: "Jane Doe outpatient",
              contentJson: { title: "Jane Doe" },
            },
          },
        ],
      },
    };
    const out = normalizeFusionHints(raw);
    expect(out).toHaveLength(1);
    expect(out[0].source).toBe("platform_index");
    expect(out[0].title).toContain("Jane");
  });
});
