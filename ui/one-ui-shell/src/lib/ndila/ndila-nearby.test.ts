import { describe, expect, it } from "vitest";
import { normalizeNearbyResponse } from "./ndila-client";

describe("normalizeNearbyResponse", () => {
  it("unwraps BFF envelope and maps matches to items", () => {
    const result = normalizeNearbyResponse({
      data: {
        matches: [
          {
            id: "loc-1",
            name: "Parirenyatwa",
            locationType: "HEALTH_FACILITY",
            latitude: -17.78,
            longitude: 31.05,
            distanceMeters: 1200,
          },
        ],
      },
      meta: { request_id: "r1", correlation_id: "c1" },
    });
    expect(result.items).toHaveLength(1);
    expect(result.items[0]?.name).toBe("Parirenyatwa");
    expect(result.items[0]?.locationType).toBe("HEALTH_FACILITY");
    expect(result.total).toBe(1);
  });

  it("accepts legacy items array", () => {
    const result = normalizeNearbyResponse({
      items: [{ id: "x", name: "Clinic", locationType: "PHARMACY", latitude: 1, longitude: 2 }],
    });
    expect(result.items).toHaveLength(1);
  });

  it("returns empty list when payload is empty", () => {
    expect(normalizeNearbyResponse({ data: {} }).items).toEqual([]);
  });
});
