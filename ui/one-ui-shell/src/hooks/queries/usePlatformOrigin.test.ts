import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/lib/api-client", () => ({
  apiClient: {
    get: vi.fn(() => Promise.resolve({ data: [] })),
    post: vi.fn(() => Promise.resolve({ data: {} })),
    delete: vi.fn(() => Promise.resolve({ data: {} })),
  },
}));

import { apiClient } from "@/lib/api-client";
import {
  approveAction,
  createCountryOperation,
  executeAction,
  fetchActionApprovals,
  fetchAppointments,
  fetchCountryOperation,
  fetchCountryOperations,
  fetchPendingActions,
  initiateAppointment,
  revokeAppointment,
  PLATFORM_ORIGIN_ADMIN_ROLE,
} from "./usePlatformOrigin";

const get = apiClient.get as unknown as ReturnType<typeof vi.fn>;
const post = apiClient.post as unknown as ReturnType<typeof vi.fn>;
const del = apiClient.delete as unknown as ReturnType<typeof vi.fn>;

describe("usePlatformOrigin network helpers", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("lists country operations", () => {
    void fetchCountryOperations();
    expect(get).toHaveBeenCalledWith("/internal/v1/platform-origin/country-operations");
  });

  it("gets a country operation detail", () => {
    void fetchCountryOperation("op-1");
    expect(get).toHaveBeenCalledWith("/internal/v1/platform-origin/country-operations/op-1");
  });

  it("lists appointments for a country operation", () => {
    void fetchAppointments("op-1");
    expect(get).toHaveBeenCalledWith(
      "/internal/v1/platform-origin/country-operations/op-1/appointments",
    );
  });

  it("lists the pending approvals inbox with default PENDING status", () => {
    void fetchPendingActions();
    expect(get).toHaveBeenCalledWith("/internal/v1/platform-origin/actions?status=PENDING");
  });

  it("lists the who-approved projection for an action", () => {
    void fetchActionApprovals("ar-1");
    expect(get).toHaveBeenCalledWith("/internal/v1/platform-origin/actions/ar-1/approvals");
  });

  it("creates a country operation with the platform-action body", () => {
    void createCountryOperation({
      tenantId: "t-1",
      isoCountryCode: "ZW",
      displayName: "Zimbabwe",
    });
    expect(post).toHaveBeenCalledWith("/internal/v1/platform-origin/country-operations", {
      tenantId: "t-1",
      isoCountryCode: "ZW",
      displayName: "Zimbabwe",
    });
  });

  it("posts the two-person approve body with role and decision defaults", () => {
    void approveAction({ accessRequestId: "ar-9", approverUserId: "approver-2", note: "ok" });
    expect(post).toHaveBeenCalledWith("/internal/v1/platform-origin/actions/ar-9/approve", {
      approverUserId: "approver-2",
      approverRole: PLATFORM_ORIGIN_ADMIN_ROLE,
      decision: "APPROVE",
      note: "ok",
    });
  });

  it("executes an approved action", () => {
    void executeAction("ar-9");
    expect(post).toHaveBeenCalledWith("/internal/v1/platform-origin/actions/ar-9/execute");
  });

  it("initiates a national appointment on the nested path", () => {
    void initiateAppointment({ countryOperationId: "op-1", subjectUserId: "user-7" });
    expect(post).toHaveBeenCalledWith(
      "/internal/v1/platform-origin/country-operations/op-1/appointments",
      { subjectUserId: "user-7" },
    );
  });

  it("revokes an appointment via DELETE with a reason", () => {
    void revokeAppointment({ countryOperationId: "op-1", appointmentId: "appt-3", reason: "leak" });
    expect(del).toHaveBeenCalledWith(
      "/internal/v1/platform-origin/country-operations/op-1/appointments/appt-3",
      { reason: "leak" },
    );
  });
});
