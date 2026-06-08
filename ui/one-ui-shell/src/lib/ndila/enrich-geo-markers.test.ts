import { describe, expect, it, vi } from "vitest";
import { enrichOpsMapMarkers } from "./enrich-geo-markers";

vi.mock("@/lib/ndila/ndila-client", () => ({
  ndilaGeocode: vi.fn(async () => ({
    success: true,
    results: [{ latitude: -17.83, longitude: 31.05, name: "Harare" }],
  })),
}));

describe("enrichOpsMapMarkers", () => {
  it("keeps exact coordinates and geocodes district-only rows", async () => {
    const markers = await enrichOpsMapMarkers([
      {
        id: "f1",
        label: "Central Clinic",
        latitude: -18.0,
        longitude: 31.5,
        district: "Harare",
        province: "Harare",
      },
      {
        id: "f2",
        label: "Rural Clinic",
        district: "Goromonzi",
        province: "Mashonaland East",
      },
    ]);

    expect(markers).toHaveLength(2);
    expect(markers[0].coordinateSource).toBe("RECORD");
    expect(markers[1].coordinateSource).toBe("GEOCODED");
    expect(markers[1].latitude).toBeCloseTo(-17.83);
  });
});
