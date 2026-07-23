/**
 * Wave OF-C — telemonitoring hook contract: BFF proxy paths, raw-DTO payload
 * typing (no {success,data} unwrap), §14.6 four-field closure payload, and the
 * CHW task lane filtering to MONITORING_* task types.
 */

import type { ReactNode } from "react";
import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get, post },
}));

import {
  useAlertEpisodes,
  useMyMonitoringPlans,
  useMyMonitoringTasks,
  usePlanReadings,
  useRecordLadderStep,
  useResolveEpisode,
  useResolvedDeviceAssignment,
} from "../useTelemonitoring";

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return React.createElement(QueryClientProvider, { client }, children);
}

beforeEach(() => {
  get.mockReset();
  post.mockReset();
});

describe("useAlertEpisodes", () => {
  it("fetches the episode list from the BFF proxy with a status filter", async () => {
    get.mockResolvedValue([{ episodeId: "E-1", severity: "RED", status: "OPEN" }]);

    const { result } = renderHook(() => useAlertEpisodes({ status: "OPEN" }), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(get).toHaveBeenCalledWith("/internal/v1/telemonitoring/alert-episodes?status=OPEN");
    expect(result.current.data?.[0]?.episodeId).toBe("E-1");
  });

  it("fetches without filters when none are given", async () => {
    get.mockResolvedValue([]);

    const { result } = renderHook(() => useAlertEpisodes(), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(get).toHaveBeenCalledWith("/internal/v1/telemonitoring/alert-episodes");
  });
});

describe("useResolveEpisode (§14.6 accountable closure)", () => {
  it("posts all four closure fields together", async () => {
    post.mockResolvedValue({ episodeId: "E-1", status: "RESOLVED" });

    const { result } = renderHook(() => useResolveEpisode(), { wrapper });
    result.current.mutate({
      episodeId: "E-1",
      resolvedBy: "S. Ncube, monitoring nurse",
      assessment: "BP settled after repeat",
      action: "Guided repeat, confirmed normal",
      outcome: "RESOLVED_NO_HARM",
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(post).toHaveBeenCalledWith("/internal/v1/telemonitoring/alert-episodes/E-1/resolve", {
      resolvedBy: "S. Ncube, monitoring nurse",
      assessment: "BP settled after repeat",
      action: "Guided repeat, confirmed normal",
      outcome: "RESOLVED_NO_HARM",
    });
  });

  it("surfaces the coded upstream refusal (no masking)", async () => {
    post.mockRejectedValue({
      status: 409,
      error: { code: "TM_ALERT_INVALID_TRANSITION", message: "RESOLVED not reachable from OPEN" },
    });

    const { result } = renderHook(() => useResolveEpisode(), { wrapper });
    result.current.mutate({
      episodeId: "E-1",
      resolvedBy: "x",
      assessment: "x",
      action: "x",
      outcome: "x",
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect((result.current.error as { error?: { code?: string } })?.error?.code).toBe(
      "TM_ALERT_INVALID_TRANSITION",
    );
  });
});

describe("useRecordLadderStep (§14.7)", () => {
  it("posts the rung and note", async () => {
    post.mockResolvedValue({ episodeId: "E-1", currentRung: 4, status: "ESCALATED" });

    const { result } = renderHook(() => useRecordLadderStep(), { wrapper });
    result.current.mutate({ episodeId: "E-1", rung: 4, note: "CHW visit requested" });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(post).toHaveBeenCalledWith("/internal/v1/telemonitoring/alert-episodes/E-1/ladder-steps", {
      rung: 4,
      note: "CHW visit requested",
    });
  });
});

describe("useMyMonitoringPlans (OF-B27 self lane)", () => {
  it("reads the actor-resolved my-plans lane (no CPID in the client)", async () => {
    get.mockResolvedValue([{ id: "P-1", programmeCode: "HTN", status: "ACTIVE" }]);

    const { result } = renderHook(() => useMyMonitoringPlans(), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(get).toHaveBeenCalledWith("/internal/v1/telemonitoring/my/plans");
  });
});

describe("usePlanReadings", () => {
  it("fetches paged newest-first readings and does not fire without a plan", async () => {
    get.mockResolvedValue({ readings: [], page: 0, totalElements: 0, totalPages: 0 });

    const { result } = renderHook(() => usePlanReadings("P-1", 0, 25), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(get).toHaveBeenCalledWith("/internal/v1/telemonitoring/readings/by-plan/P-1?page=0&size=25");

    get.mockClear();
    renderHook(() => usePlanReadings(undefined), { wrapper });
    expect(get).not.toHaveBeenCalled();
  });
});

describe("useResolvedDeviceAssignment (honest posture)", () => {
  it("reads the assignment-gate resolve lane", async () => {
    get.mockResolvedValue({ assignmentId: "A-1", deviceRef: "DEV-1", calibrationPosture: "DEGRADED" });

    const { result } = renderHook(() => useResolvedDeviceAssignment("DEV-1"), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(get).toHaveBeenCalledWith("/internal/v1/telemonitoring/device-assignments/by-device/DEV-1");
    expect(result.current.data?.calibrationPosture).toBe("DEGRADED");
  });
});

describe("useMyMonitoringTasks (OF-B23 — PCT generic-task lane)", () => {
  it("filters the existing tasks/mine lane to MONITORING_* task types", async () => {
    get.mockResolvedValue({
      data: [
        { id: "T-1", taskType: "MONITORING_ALERT_FOLLOWUP", status: "OPEN" },
        { id: "T-2", taskType: "REFERRAL_REVIEW", status: "OPEN" },
        { id: "T-3", taskType: "MONITORING_ONBOARDING_VISIT", status: "OPEN" },
      ],
    });

    const { result } = renderHook(() => useMyMonitoringTasks(), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(get).toHaveBeenCalledWith("/internal/v1/mobile/provider/tasks/mine?assigned_to=me");
    expect(result.current.data?.map((t) => t.id)).toEqual(["T-1", "T-3"]);
  });

  it("tolerates a paged content shape", async () => {
    get.mockResolvedValue({ data: { content: [{ id: "T-9", taskType: "MONITORING_ONBOARDING_VISIT" }] } });

    const { result } = renderHook(() => useMyMonitoringTasks(), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.[0]?.id).toBe("T-9");
  });
});
