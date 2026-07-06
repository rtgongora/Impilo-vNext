import type { ReactNode } from "react";
import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useTrustProfile, type TrustProfile } from "../useTrustProfile";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get },
}));

// Signed-in person anchor — the Health ID the profile is composed for.
vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: () => ({ user: { id: "hid-77" }, isAuthenticated: true }),
}));

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return React.createElement(QueryClientProvider, { client }, children);
}

// A raw four-block map with ONE block (professional) linked, employment
// UNAVAILABLE — proving independent per-block degradation and no fabrication.
const RAW_PROFILE: TrustProfile = {
  healthId: "hid-77",
  generatedAt: "2026-07-06T00:00:00Z",
  identity: { sourceOfRecord: "identity-assurance-service", status: "OK", assurance: { loa: "LOA2" } },
  professional: {
    sourceOfRecord: "varapi-service (provider registry trust ledger)",
    status: "OK",
    providerId: "1234",
    trustLevel: "EMPLOYMENT_MATCHED",
    registryStatus: "ACTIVE",
  },
  employment: {
    sourceOfRecord: "workforce-governance-service (wgv_hsc_employment)",
    status: "UNAVAILABLE",
  },
  operational: {
    sourceOfRecord: "vashandi-workforce-service (work-context read model)",
    status: "OK",
    activeAssignmentCount: 2,
  },
};

describe("useTrustProfile", () => {
  beforeEach(() => {
    get.mockReset();
  });

  it("GETs the per-Health-ID trust profile from /api/v1/trust/profile/{healthId}", async () => {
    get.mockResolvedValue(RAW_PROFILE);
    const { result } = renderHook(() => useTrustProfile(), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(get).toHaveBeenCalledWith("/api/v1/trust/profile/hid-77");
  });

  it("parses all four blocks as-is (raw map, never `.data`-unwrapped)", async () => {
    get.mockResolvedValue(RAW_PROFILE);
    const { result } = renderHook(() => useTrustProfile(), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    const data = result.current.data!;
    expect(data.healthId).toBe("hid-77");
    expect(data.identity.status).toBe("OK");
    expect(data.professional.status).toBe("OK");
    expect(data.employment.status).toBe("UNAVAILABLE");
    expect(data.operational.status).toBe("OK");
    // Each block cites its own system of record.
    expect(data.professional.sourceOfRecord).toContain("varapi-service");
    expect(data.employment.sourceOfRecord).toContain("workforce-governance");
  });

  it("preserves an UNAVAILABLE block without inventing its fields", async () => {
    get.mockResolvedValue(RAW_PROFILE);
    const { result } = renderHook(() => useTrustProfile(), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    const employment = result.current.data!.employment;
    expect(employment.status).toBe("UNAVAILABLE");
    // No records fabricated for the unreachable source.
    expect(employment).not.toHaveProperty("records");
    expect(employment).not.toHaveProperty("recordCount");
  });
});
