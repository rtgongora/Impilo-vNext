import { beforeEach, describe, expect, it, vi } from "vitest";

const mockApiClient = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
}));

vi.mock("@impilo/mobile-api-client", () => ({
  apiClient: mockApiClient,
}));

import {
  createProviderIdentity,
  getProviderIdentity,
  registerPatientIdentity,
  resolvePatientIdentity,
  searchIdentities,
  startPatientRecovery,
  verifyPatientRecovery,
} from "../../services/identityRegistryService";

describe("identityRegistryService", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("searches identities through the canonical BFF route", async () => {
    mockApiClient.get.mockResolvedValueOnce({ data: { data: [{ id: "id-1", displayName: "Jane" }] } });

    const rows = await searchIdentities("Jane");

    expect(mockApiClient.get).toHaveBeenCalledWith("/internal/v1/identity/search?query=Jane");
    expect(rows).toHaveLength(1);
  });

  it("posts patient and provider registry commands", async () => {
    mockApiClient.post.mockResolvedValue({ data: { data: { accepted: true } } });

    await resolvePatientIdentity("PHID-1");
    await registerPatientIdentity({ givenName: "Jane" });
    await startPatientRecovery({ method: "phone_otp" });
    await verifyPatientRecovery({ recoveryId: "rec-1", otp: "000000" });
    await createProviderIdentity({ givenName: "Dr" });

    expect(mockApiClient.post).toHaveBeenCalledWith("/internal/v1/identity/patient/resolve", { healthId: "PHID-1" });
    expect(mockApiClient.post).toHaveBeenCalledWith("/internal/v1/identity/patient/register", { givenName: "Jane" });
    expect(mockApiClient.post).toHaveBeenCalledWith("/internal/v1/identity/patient/recovery/start", { method: "phone_otp" });
    expect(mockApiClient.post).toHaveBeenCalledWith("/internal/v1/identity/patient/recovery/verify", {
      recoveryId: "rec-1",
      otp: "000000",
    });
    expect(mockApiClient.post).toHaveBeenCalledWith("/internal/v1/identity/provider/create", { givenName: "Dr" });
  });

  it("looks up provider identity through VARAPI BFF path", async () => {
    mockApiClient.get.mockResolvedValueOnce({ data: { data: { providerId: "prov-1" } } });

    const provider = await getProviderIdentity("prov-1");

    expect(mockApiClient.get).toHaveBeenCalledWith("/internal/v1/identity/provider/prov-1");
    expect(provider).toEqual({ providerId: "prov-1" });
  });
});
