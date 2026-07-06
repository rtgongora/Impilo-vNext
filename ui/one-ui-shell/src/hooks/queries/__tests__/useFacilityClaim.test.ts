import type { ReactNode } from "react";
import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  facilityClaimErrorMessage,
  isFeaturePending,
  useApproveFacilityAppointment,
  useAppointFacilityClaim,
  useFacilityClaimEligibility,
  usePreviewFacilityClaim,
} from "../useFacilityClaim";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get, post },
}));

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return React.createElement(QueryClientProvider, { client }, children);
}

/**
 * IATG Wave 2, WS-E — endpoint-contract + envelope-unwrap unit tests for the facility-claim hooks.
 * The BFF returns a { data, meta } envelope; every hook unwraps `data`. The subject key is the
 * facility UUID (never a Health ID); the claimant is bound server-side to X-Actor-ID.
 */
describe("useFacilityClaim hooks", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
  });

  it("eligibility: GETs the facility-scoped endpoint and unwraps data", async () => {
    get.mockResolvedValue({
      data: { facilityUuid: "fac-1", allowedOnPlatform: true, claimable: true, alreadyAdministered: false, activeAdministratorCount: 0 },
      meta: {},
    });

    const { result } = renderHook(() => useFacilityClaimEligibility("fac-1"), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(get).toHaveBeenCalledWith("/api/v1/facility-claim/eligibility?facilityUuid=fac-1");
    expect(result.current.data?.claimable).toBe(true);
  });

  it("eligibility: stays disabled without a facility uuid", () => {
    const { result } = renderHook(() => useFacilityClaimEligibility(""), { wrapper });
    expect(result.current.fetchStatus).toBe("idle");
    expect(get).not.toHaveBeenCalled();
  });

  it("preview: POSTs the facility uuid and unwraps the masked summary", async () => {
    post.mockResolvedValue({
      data: { facilityUuid: "fac-1", facilityCode: "ZW-H***", name: "Harare Central", allowedOnPlatform: true, claimable: true },
      meta: {},
    });

    const { result } = renderHook(() => usePreviewFacilityClaim(), { wrapper });
    result.current.mutate("fac-1");

    await waitFor(() => expect(post).toHaveBeenCalled());
    expect(post).toHaveBeenCalledWith("/api/v1/facility-claim/preview", { facilityUuid: "fac-1" });
    await waitFor(() => expect(result.current.data?.facilityCode).toBe("ZW-H***"));
  });

  it("appoint: POSTs facilityUuid + consent + role to /appoint", async () => {
    post.mockResolvedValue({ data: { submitted: true, appointmentId: "app-1", approvalState: "PENDING" }, meta: {} });

    const { result } = renderHook(() => useAppointFacilityClaim(), { wrapper });
    result.current.mutate({ facilityUuid: "fac-1", consent: true, role: "FACILITY_ADMIN" });

    await waitFor(() => expect(post).toHaveBeenCalled());
    expect(post).toHaveBeenCalledWith("/api/v1/facility-claim/appoint", {
      facilityUuid: "fac-1",
      consent: true,
      role: "FACILITY_ADMIN",
    });
    await waitFor(() => expect(result.current.data?.submitted).toBe(true));
  });

  it("approve: POSTs the appointment id to /approve", async () => {
    post.mockResolvedValue({ data: { appointmentId: "app-1", approvalState: "ACTIVE", affiliationRecorded: false }, meta: {} });

    const { result } = renderHook(() => useApproveFacilityAppointment(), { wrapper });
    result.current.mutate({ appointmentId: "app-1" });

    await waitFor(() => expect(post).toHaveBeenCalled());
    expect(post).toHaveBeenCalledWith("/api/v1/facility-claim/approve", { appointmentId: "app-1" });
    await waitFor(() => expect(result.current.data?.approvalState).toBe("ACTIVE"));
  });

  it("isFeaturePending recognises 501 / FEATURE_PENDING (honest coming-soon)", () => {
    expect(isFeaturePending({ status: 501 })).toBe(true);
    expect(isFeaturePending({ error: { code: "FEATURE_PENDING" } })).toBe(true);
    expect(isFeaturePending({ status: 409 })).toBe(false);
  });

  it("facilityClaimErrorMessage surfaces honest downstream copy", () => {
    expect(
      facilityClaimErrorMessage({ error: { message: "You already administer this facility." } }, "fallback"),
    ).toBe("You already administer this facility.");
    expect(facilityClaimErrorMessage(undefined, "fallback")).toBe("fallback");
  });
});
