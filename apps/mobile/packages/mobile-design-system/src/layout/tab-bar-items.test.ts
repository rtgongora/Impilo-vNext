import { describe, expect, it } from "vitest";
import { partitionTabItems } from "./tab-bar-items";

const items = Array.from({ length: 8 }, (_, index) => ({ id: `tab-${index}`, label: `Tab ${index}` }));

describe("partitionTabItems", () => {
  it("keeps at most four destinations plus More in a five-item bar", () => {
    const result = partitionTabItems(items, 5);
    expect(result.primaryTabs.map((item) => item.id)).toEqual(["tab-0", "tab-1", "tab-2", "tab-3"]);
    expect(result.overflowTabs).toHaveLength(4);
  });

  it("does not create an overflow when all destinations fit", () => {
    const result = partitionTabItems(items.slice(0, 4), 5);
    expect(result.primaryTabs).toHaveLength(4);
    expect(result.overflowTabs).toEqual([]);
  });
});
