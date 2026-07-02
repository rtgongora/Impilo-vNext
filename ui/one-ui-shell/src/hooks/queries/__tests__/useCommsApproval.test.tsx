// @vitest-environment jsdom
import { describe, expect, it, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";

const getMock = vi.fn();
const postMock = vi.fn();
vi.mock("@/lib/api-client", () => ({
  apiClient: {
    get: (...a: unknown[]) => getMock(...a),
    post: (...a: unknown[]) => postMock(...a),
  },
}));

import {
  useCommsApprovalQueue,
  useApproveTemplate,
  useRejectTemplate,
  useApproveCampaign,
  useRejectCampaign,
  useCancelCampaign,
  useTemplateDryRun,
} from "../useCommsApproval";

function wrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
  Wrapper.displayName = "TestQueryClientWrapper";
  return Wrapper;
}

describe("useCommsApproval hooks", () => {
  beforeEach(() => {
    getMock.mockReset();
    postMock.mockReset();
  });

  it("queue hits the approval-queue route", async () => {
    getMock.mockResolvedValue({ data: { templates: [], campaigns: [], counts: { templates: 0, campaigns: 0, total: 0 }, source_health: {} } });
    const { result } = renderHook(() => useCommsApprovalQueue(), { wrapper: wrapper() });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(getMock).toHaveBeenCalledWith("/internal/v1/comms/approval-queue");
  });

  it("approve template posts to the approve route", async () => {
    postMock.mockResolvedValue({ data: {} });
    const { result } = renderHook(() => useApproveTemplate(), { wrapper: wrapper() });
    await result.current.mutateAsync("tmpl-1");
    expect(postMock).toHaveBeenCalledWith("/internal/v1/comms/approval-queue/templates/tmpl-1/approve");
  });

  it("reject template posts reason to reject-with-reason", async () => {
    postMock.mockResolvedValue({ data: {} });
    const { result } = renderHook(() => useRejectTemplate(), { wrapper: wrapper() });
    await result.current.mutateAsync({ templateId: "tmpl-2", reason: "off policy" });
    expect(postMock).toHaveBeenCalledWith(
      "/internal/v1/comms/approval-queue/templates/tmpl-2/reject-with-reason",
      { decision_reason: "off policy" },
    );
  });

  it("approve campaign posts to the campaign approve route", async () => {
    postMock.mockResolvedValue({ data: {} });
    const { result } = renderHook(() => useApproveCampaign(), { wrapper: wrapper() });
    await result.current.mutateAsync(42);
    expect(postMock).toHaveBeenCalledWith("/internal/v1/comms/approval-queue/campaigns/42/approve");
  });

  it("reject campaign posts reason to reject-with-reason", async () => {
    postMock.mockResolvedValue({ data: {} });
    const { result } = renderHook(() => useRejectCampaign(), { wrapper: wrapper() });
    await result.current.mutateAsync({ campaignId: 7, reason: "duplicate" });
    expect(postMock).toHaveBeenCalledWith(
      "/internal/v1/comms/approval-queue/campaigns/7/reject-with-reason",
      { decision_reason: "duplicate" },
    );
  });

  it("cancel campaign posts to the cancel route", async () => {
    postMock.mockResolvedValue({ data: {} });
    const { result } = renderHook(() => useCancelCampaign(), { wrapper: wrapper() });
    await result.current.mutateAsync(9);
    expect(postMock).toHaveBeenCalledWith("/internal/v1/comms/approval-queue/campaigns/9/cancel");
  });

  it("template dry-run hits the dry-run route", async () => {
    getMock.mockResolvedValue({ data: { dry_run_ok: true, issues: [], estimated_reachable_audience: 100, blocked_by_policy: 0 } });
    const { result } = renderHook(() => useTemplateDryRun(), { wrapper: wrapper() });
    await result.current.mutateAsync("tmpl-3");
    expect(getMock).toHaveBeenCalledWith("/internal/v1/comms/approval-queue/templates/tmpl-3/dry-run");
  });
});
