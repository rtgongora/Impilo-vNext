import { describe, expect, it } from "vitest";
import { mergeLogisticsMarkers, rowToOpsMapMarker, rowsToOpsMapMarkers } from "./logistics-map-adapters";

describe("logistics-map-adapters", () => {
  it("maps geo-tagged nhume delivery rows", () => {
    const marker = rowToOpsMapMarker(
      {
        delivery_id: "d-1",
        reference: "RX-001",
        status: "IN_TRANSIT",
        destination_lat: -17.82,
        destination_lon: 31.05,
      },
      { idPrefix: "nhume", kind: "delivery" },
    );
    expect(marker?.latitude).toBe(-17.82);
    expect(marker?.longitude).toBe(31.05);
    expect(marker?.label).toBe("RX-001");
  });

  it("keeps non-geo markers for list fallback", () => {
    const marker = rowToOpsMapMarker({ asset_id: "a-1", registration: "AMB-12" }, { idPrefix: "dispatch", kind: "fleet" });
    expect(marker?.latitude).toBeUndefined();
    expect(marker?.label).toBe("AMB-12");
  });

  it("deduplicates merged marker groups", () => {
    const merged = mergeLogisticsMarkers(
      [{ id: "nhume:1", label: "A" }],
      [{ id: "nhume:1", label: "A dup" }, { id: "dispatch:2", label: "B", latitude: 1, longitude: 2 }],
    );
    expect(merged).toHaveLength(2);
  });

  it("batch converts dispatch rows", () => {
    const markers = rowsToOpsMapMarkers(
      [{ task_id: "t1", status: "OPEN", lat: -18, lng: 30 }],
      { idPrefix: "dispatch", kind: "task" },
    );
    expect(markers[0]?.id).toBe("dispatch:t1");
  });
});
