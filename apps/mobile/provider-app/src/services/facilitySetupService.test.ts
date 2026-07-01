import { describe, expect, it, vi, beforeEach } from "vitest";
import { apiClient } from "@impilo/mobile-api-client";
import {
  advanceSetupStep,
  createFacilityUnit,
  createServicePoint,
  fetchFacilityUnits,
  fetchServicePoints,
  fetchSetupState,
} from "./facilitySetupService";

vi.mock("@impilo/mobile-api-client", () => ({
  apiClient: { get: vi.fn(), post: vi.fn() },
}));

describe("facilitySetupService", () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.post).mockReset();
  });

  it("fetchSetupState unwraps the readiness state", async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: { data: { facilityId: 1, departmentsConfigured: true, readyForGoLive: false, nextStep: "SERVICE_POINTS" } },
      status: 200,
      correlationId: "c1",
      headers: {},
    });
    const s = await fetchSetupState(1);
    expect(s.nextStep).toBe("SERVICE_POINTS");
    expect(apiClient.get).toHaveBeenCalledWith("/internal/v1/facility-mode/1/setup");
  });

  it("fetchFacilityUnits + createFacilityUnit hit the units route", async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: { data: [{ id: 3, name: "OPD", unitType: "OUTPATIENT" }] },
      status: 200,
      correlationId: "c1",
      headers: {},
    });
    const units = await fetchFacilityUnits(1);
    expect(units[0].name).toBe("OPD");
    expect(apiClient.get).toHaveBeenCalledWith("/internal/v1/facility-mode/1/units");

    vi.mocked(apiClient.post).mockResolvedValue({
      data: { data: { id: 4, name: "Maternity", unitType: "WARD" } },
      status: 201,
      correlationId: "c1",
      headers: {},
    });
    const created = await createFacilityUnit(1, { name: "Maternity", unitType: "WARD" });
    expect(created.id).toBe(4);
    expect(apiClient.post).toHaveBeenCalledWith("/internal/v1/facility-mode/1/units", {
      name: "Maternity",
      unitType: "WARD",
    });
  });

  it("fetchServicePoints + createServicePoint hit the service-points route", async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: { data: [{ id: "sp1", name: "Triage Desk", servicePointType: "TRIAGE" }] },
      status: 200,
      correlationId: "c1",
      headers: {},
    });
    const sps = await fetchServicePoints(1);
    expect(sps[0].name).toBe("Triage Desk");
    expect(apiClient.get).toHaveBeenCalledWith("/internal/v1/facility-mode/1/service-points");

    vi.mocked(apiClient.post).mockResolvedValue({
      data: { data: { id: "sp2", name: "Pharmacy Window", servicePointType: "DISPENSING" } },
      status: 201,
      correlationId: "c1",
      headers: {},
    });
    const created = await createServicePoint(1, { name: "Pharmacy Window", servicePointType: "DISPENSING" });
    expect(created.id).toBe("sp2");
    expect(apiClient.post).toHaveBeenCalledWith("/internal/v1/facility-mode/1/service-points", {
      name: "Pharmacy Window",
      servicePointType: "DISPENSING",
    });
  });

  it("advanceSetupStep posts the step and returns the recomputed readiness state", async () => {
    vi.mocked(apiClient.post).mockResolvedValue({
      data: { data: { facilityId: 1, queuesConfigured: true, readyForGoLive: false, nextStep: "WORKFLOWS" } },
      status: 200,
      correlationId: "c1",
      headers: {},
    });
    const state = await advanceSetupStep(1, { step: "QUEUES", complete: true });
    expect(state.queuesConfigured).toBe(true);
    expect(state.nextStep).toBe("WORKFLOWS");
    expect(apiClient.post).toHaveBeenCalledWith("/internal/v1/facility-mode/1/setup/steps", {
      step: "QUEUES",
      complete: true,
    });
  });
});
