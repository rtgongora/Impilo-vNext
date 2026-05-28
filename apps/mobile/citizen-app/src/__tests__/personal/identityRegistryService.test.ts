import { beforeEach, describe, expect, it, vi } from "vitest";

const mockApiClient = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
}));

vi.mock("@impilo/mobile-api-client", () => ({
  apiClient: mockApiClient,
}));

import {
  resolvePatientIdentity,
  searchIdentities,
  startPatientRecovery,
  verifyPatientRecovery,
} from "../../services/identityRegistryService";

describe("citizen identityRegistryService", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("searches and unwraps identity rows", async () => {
    mockApiClient.get.mockResolvedValueOnce({ data: { data: [{ healthId: "PHID-1" }] } });

    const rows = await searchIdentities("PHID");

    expect(mockApiClient.get).toHaveBeenCalledWith("/internal/v1/identity/search?query=PHID");
    expect(rows[0].healthId).toBe("PHID-1");
  });

  it("runs resolve and recovery commands", async () => {
    mockApiClient.post.mockResolvedValue({ data: { data: { accepted: true } } });

    await resolvePatientIdentity("PHID-1");
    await startPatientRecovery({ method: "phone_otp" });
    await verifyPatientRecovery({ recoveryId: "rec-1", otp: "000000" });

    expect(mockApiClient.post).toHaveBeenCalledWith("/internal/v1/identity/patient/resolve", { healthId: "PHID-1" });
    expect(mockApiClient.post).toHaveBeenCalledWith("/internal/v1/identity/patient/recovery/start", { method: "phone_otp" });
    expect(mockApiClient.post).toHaveBeenCalledWith("/internal/v1/identity/patient/recovery/verify", {
      recoveryId: "rec-1",
      otp: "000000",
    });
  });
});
