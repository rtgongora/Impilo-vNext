/**
 * Assurance service tests (G-CZO-05) — mobile reads the citizen's current assurance level.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

const mockApiClient = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
}));

vi.mock("@impilo/mobile-api-client", () => ({
  apiClient: mockApiClient,
}));

import { fetchAssuranceStatus } from "./assuranceService";

describe("assuranceService", () => {
  beforeEach(() => vi.clearAllMocks());

  it("reads the current level from the identity-assurance BFF endpoint", async () => {
    mockApiClient.get.mockResolvedValue({ data: { data: { currentLevel: "LOA3" } } });

    const status = await fetchAssuranceStatus();

    expect(mockApiClient.get).toHaveBeenCalledWith("/internal/v1/identity/assurance/status");
    expect(status.currentLevel).toBe("LOA3");
  });

  it("defaults to LOA1 (account-only) when the level is absent", async () => {
    mockApiClient.get.mockResolvedValue({ data: { data: {} } });
    expect((await fetchAssuranceStatus()).currentLevel).toBe("LOA1");
  });
});
