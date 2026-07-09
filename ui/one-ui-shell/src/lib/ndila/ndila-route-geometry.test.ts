import { describe, expect, it } from "vitest";
import { routePolylineToCoordinates } from "./ndila-route-geometry";

describe("routePolylineToCoordinates", () => {
  it("parses Ndila mock polyline", () => {
    const points = routePolylineToCoordinates("mock-poly:-17.8292,31.0522->-17.8000,31.1000");
    expect(points).toHaveLength(2);
    expect(points[0]?.latitude).toBeCloseTo(-17.8292);
  });

  it("falls back to origin/destination when polyline missing", () => {
    const points = routePolylineToCoordinates(null, {
      origin: { latitude: 1, longitude: 2 },
      destination: { latitude: 3, longitude: 4 },
    });
    expect(points).toEqual([
      { latitude: 1, longitude: 2 },
      { latitude: 3, longitude: 4 },
    ]);
  });
});
