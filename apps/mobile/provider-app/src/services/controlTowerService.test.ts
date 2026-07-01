import { describe, expect, it, vi, beforeEach } from "vitest";
import { apiClient } from "@impilo/mobile-api-client";
import {
  acknowledgeAlert,
  fetchControlTowerAggregate,
  fetchFacilityAlerts,
} from "./controlTowerService";

vi.mock("@impilo/mobile-api-client", () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

describe("controlTowerService", () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.post).mockReset();
  });

  it("fetchControlTowerAggregate unwraps the aggregate and hits the live route", async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: {
        data: {
          facilityCount: 2,
          openAlertCount: 3,
          visibility: "ROW_DETAIL",
          facilities: [{ facilityId: 1, name: "Harare Central", openAlerts: 3, bedOccupancyPercent: 82 }],
        },
      },
      status: 200,
      correlationId: "c1",
      headers: {},
    });

    const agg = await fetchControlTowerAggregate();
    expect(agg.openAlertCount).toBe(3);
    expect(agg.facilities[0].name).toBe("Harare Central");
    expect(apiClient.get).toHaveBeenCalledWith("/internal/v1/facility-mode/control-tower/aggregate");
  });

  it("fetchFacilityAlerts unwraps the array and passes facility + status", async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: { data: [{ id: "a1", facilityId: 1, severity: "HIGH", title: "Bed capacity", status: "OPEN" }] },
      status: 200,
      correlationId: "c1",
      headers: {},
    });

    const alerts = await fetchFacilityAlerts(1);
    expect(alerts).toHaveLength(1);
    expect(alerts[0].id).toBe("a1");
    expect(apiClient.get).toHaveBeenCalledWith(
      "/internal/v1/facility-mode/1/control-tower/alerts?status=OPEN",
    );
  });

  it("acknowledgeAlert posts to the acknowledge route and returns the updated alert", async () => {
    vi.mocked(apiClient.post).mockResolvedValue({
      data: { data: { id: "a1", status: "ACKNOWLEDGED", acknowledgedBy: "prov-1" } },
      status: 200,
      correlationId: "c1",
      headers: {},
    });

    const updated = await acknowledgeAlert("a1");
    expect(updated.status).toBe("ACKNOWLEDGED");
    expect(apiClient.post).toHaveBeenCalledWith(
      "/internal/v1/facility-mode/control-tower/alerts/a1/acknowledge",
    );
  });
});
