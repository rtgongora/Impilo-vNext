import { describe, expect, it, vi, beforeEach } from "vitest";
import { apiClient } from "@impilo/mobile-api-client";
import { fetchFacilityRegulators, linkRegulator } from "./regulatorService";

vi.mock("@impilo/mobile-api-client", () => ({
  apiClient: { get: vi.fn(), post: vi.fn() },
}));

describe("regulatorService", () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.post).mockReset();
  });

  it("fetchFacilityRegulators unwraps the array and hits the facility route", async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: { data: [{ id: "r1", councilCode: "MDPCZ", status: "ACTIVE", relationshipType: "PRIMARY" }] },
      status: 200,
      correlationId: "c1",
      headers: {},
    });
    const rels = await fetchFacilityRegulators(42);
    expect(rels[0].councilCode).toBe("MDPCZ");
    expect(apiClient.get).toHaveBeenCalledWith("/internal/v1/facility-mode/42/regulators");
  });

  it("linkRegulator posts the council payload and returns the relationship", async () => {
    vi.mocked(apiClient.post).mockResolvedValue({
      data: { data: { id: "r2", councilCode: "NMCZ", status: "PENDING" } },
      status: 201,
      correlationId: "c1",
      headers: {},
    });
    const created = await linkRegulator("fac-7", {
      councilId: "c-9",
      councilCode: "NMCZ",
      registrationNumber: "REG-123",
    });
    expect(created.id).toBe("r2");
    expect(apiClient.post).toHaveBeenCalledWith("/internal/v1/facility-mode/fac-7/regulators", {
      councilId: "c-9",
      councilCode: "NMCZ",
      registrationNumber: "REG-123",
    });
  });
});
