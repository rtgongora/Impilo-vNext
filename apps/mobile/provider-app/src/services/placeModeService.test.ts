import { describe, expect, it, vi, beforeEach } from "vitest";
import { apiClient } from "@impilo/mobile-api-client";
import {
  createOutbreak,
  deployFieldTeam,
  fetchOutbreaks,
  fetchPlaceModeSummary,
  fetchSurveillanceAlerts,
} from "./placeModeService";

vi.mock("@impilo/mobile-api-client", () => ({
  apiClient: { get: vi.fn(), post: vi.fn() },
}));

describe("placeModeService", () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.post).mockReset();
  });

  it("fetchPlaceModeSummary unwraps the summary", async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: { data: { openAlerts: 4, activeOutbreaks: 1, teams: 3 } },
      status: 200,
      correlationId: "c1",
      headers: {},
    });
    const s = await fetchPlaceModeSummary();
    expect(s).toEqual({ openAlerts: 4, activeOutbreaks: 1, teams: 3 });
    expect(apiClient.get).toHaveBeenCalledWith("/internal/v1/place-mode/summary");
  });

  it("fetchSurveillanceAlerts passes status filter", async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: { data: [{ alertId: "al1", severity: "HIGH", status: "OPEN" }] },
      status: 200,
      correlationId: "c1",
      headers: {},
    });
    const alerts = await fetchSurveillanceAlerts("OPEN");
    expect(alerts[0].alertId).toBe("al1");
    expect(apiClient.get).toHaveBeenCalledWith("/internal/v1/place-mode/alerts?status=OPEN");
  });

  it("fetchOutbreaks unwraps the array", async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: { data: [{ outbreakId: "o1", name: "Cholera cluster", conditionCode: "A00" }] },
      status: 200,
      correlationId: "c1",
      headers: {},
    });
    const outbreaks = await fetchOutbreaks();
    expect(outbreaks[0].name).toBe("Cholera cluster");
    expect(apiClient.get).toHaveBeenCalledWith("/internal/v1/place-mode/outbreaks");
  });

  it("createOutbreak posts the payload and returns the created outbreak", async () => {
    vi.mocked(apiClient.post).mockResolvedValue({
      data: { data: { outbreakId: "o2", name: "Measles", conditionCode: "B05" } },
      status: 201,
      correlationId: "c1",
      headers: {},
    });
    const created = await createOutbreak({ conditionCode: "B05", name: "Measles" });
    expect(created.outbreakId).toBe("o2");
    expect(apiClient.post).toHaveBeenCalledWith("/internal/v1/place-mode/outbreaks", {
      conditionCode: "B05",
      name: "Measles",
    });
  });

  it("deployFieldTeam posts to the team deploy route", async () => {
    vi.mocked(apiClient.post).mockResolvedValue({
      data: undefined,
      status: 202,
      correlationId: "c1",
      headers: {},
    });
    await deployFieldTeam({ teamId: "t1", outbreakId: "o1", objective: "Trace contacts" });
    expect(apiClient.post).toHaveBeenCalledWith("/internal/v1/place-mode/field-teams/t1/deploy", {
      outbreakId: "o1",
      siteId: undefined,
      objective: "Trace contacts",
    });
  });
});
