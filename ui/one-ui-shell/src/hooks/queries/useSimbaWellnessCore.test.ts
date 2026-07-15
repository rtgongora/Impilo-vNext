import { describe, expect, it } from "vitest";
import { flattenTimeline, type TimelineEvent } from "./useSimbaWellnessCore";

function ev(id: number): TimelineEvent {
  return {
    id,
    eventId: `ev-${id}`,
    eventCategory: "ASSESSMENT",
    title: `Event ${id}`,
    severity: "INFO",
    occurredAt: new Date().toISOString(),
  };
}

describe("flattenTimeline", () => {
  it("returns [] for undefined pages", () => {
    expect(flattenTimeline(undefined)).toEqual([]);
  });

  it("flattens multiple pages preserving order", () => {
    const flat = flattenTimeline([
      { data: [ev(10), ev(9)] },
      { data: [ev(8), ev(7)] },
    ]);
    expect(flat.map((e) => e.id)).toEqual([10, 9, 8, 7]);
  });

  it("tolerates missing data arrays", () => {
    expect(flattenTimeline([{ data: undefined as unknown as TimelineEvent[] }])).toEqual([]);
  });
});
