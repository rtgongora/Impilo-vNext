import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import CommsApprovalsPage from "./page";

const { queueData, approveTemplate, rejectTemplate, approveCampaign, rejectCampaign, cancelCampaign, templateDryRun, campaignDryRun } =
  vi.hoisted(() => ({
    queueData: { current: undefined as unknown },
    approveTemplate: vi.fn(),
    rejectTemplate: vi.fn(),
    approveCampaign: vi.fn(),
    rejectCampaign: vi.fn(),
    cancelCampaign: vi.fn(),
    templateDryRun: vi.fn(),
    campaignDryRun: vi.fn(),
  }));

vi.mock("next/link", () => ({
  default: ({ children }: { children: ReactNode }) => <span>{children}</span>,
}));

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title, subtitle }: { children: ReactNode; title: string; subtitle?: string }) => (
    <div>
      <h1>{title}</h1>
      {subtitle ? <p>{subtitle}</p> : null}
      {children}
    </div>
  ),
}));

vi.mock("@/components/FeatureMaturityBadge", () => ({
  FeatureMaturityBadge: () => <span>badge</span>,
}));

vi.mock("@/hooks/queries/useCommsApproval", () => ({
  useCommsApprovalQueue: () => ({
    data: queueData.current,
    isLoading: queueData.current === undefined,
    isError: false,
    refetch: vi.fn(),
  }),
  useApproveTemplate: () => ({ mutateAsync: approveTemplate, isPending: false }),
  useRejectTemplate: () => ({ mutateAsync: rejectTemplate, isPending: false }),
  useApproveCampaign: () => ({ mutateAsync: approveCampaign, isPending: false }),
  useRejectCampaign: () => ({ mutateAsync: rejectCampaign, isPending: false }),
  useCancelCampaign: () => ({ mutateAsync: cancelCampaign, isPending: false }),
  useTemplateDryRun: () => ({ mutateAsync: templateDryRun, isPending: false }),
  useCampaignDryRun: () => ({ mutateAsync: campaignDryRun, isPending: false }),
}));

describe("CommsApprovalsPage", () => {
  beforeEach(() => {
    approveTemplate.mockReset().mockResolvedValue({ data: {} });
    rejectTemplate.mockReset().mockResolvedValue({ data: {} });
    approveCampaign.mockReset().mockResolvedValue({ data: {} });
    rejectCampaign.mockReset().mockResolvedValue({ data: {} });
    cancelCampaign.mockReset().mockResolvedValue({ data: {} });
    templateDryRun.mockReset().mockResolvedValue({ data: { dry_run_ok: true, issues: [], estimated_reachable_audience: 100, blocked_by_policy: 0 } });
    campaignDryRun.mockReset().mockResolvedValue({ data: { dry_run_ok: true, issues: [], estimated_reachable_audience: 100, blocked_by_policy: 0 } });
    queueData.current = {
      data: {
        templates: [
          { id: "t1", key: "welcome-sms", name: null, type: null, channel: "SMS", status: "PENDING_APPROVAL", updated_at: "2026-07-01T10:00:00Z", age_hours: 30, sla_breach: true, priority: "HIGH" },
        ],
        campaigns: [
          { id: "c1", key: null, name: "Measles drive", type: "BROADCAST", channel: "SMS", status: "PENDING_APPROVAL", updated_at: "2026-07-01T10:00:00Z", age_hours: 4, sla_breach: false, priority: "LOW" },
        ],
        counts: { templates: 1, campaigns: 1, total: 2 },
        source_health: { templates: "UP", campaigns: "UP" },
      },
    };
  });

  it("renders the real queue with counts, items and SLA badge", () => {
    render(<CommsApprovalsPage />);
    expect(screen.getByText("Comms Approval Queue")).toBeInTheDocument();
    expect(screen.getByText(/2 pending/)).toBeInTheDocument();
    expect(screen.getByText("welcome-sms")).toBeInTheDocument();
    expect(screen.getByText("Measles drive")).toBeInTheDocument();
    expect(screen.getByText("SLA breach")).toBeInTheDocument();
  });

  it("approves a template via the real hook", async () => {
    render(<CommsApprovalsPage />);
    const user = userEvent.setup();
    await user.click(screen.getAllByText("Approve")[0]);
    await waitFor(() => expect(approveTemplate).toHaveBeenCalledWith("t1"));
  });

  it("requires a reason before rejecting", async () => {
    render(<CommsApprovalsPage />);
    const user = userEvent.setup();
    await user.click(screen.getAllByText("Reject")[0]);
    const confirm = screen.getByText("Confirm rejection");
    expect(confirm).toBeDisabled();
    await user.type(screen.getByPlaceholderText(/Reason for rejection/), "not compliant");
    expect(confirm).not.toBeDisabled();
    await user.click(confirm);
    await waitFor(() => expect(rejectTemplate).toHaveBeenCalledWith({ templateId: "t1", reason: "not compliant" }));
  });

  it("warns when a source is down", () => {
    queueData.current = {
      data: {
        templates: [],
        campaigns: [],
        counts: { templates: 0, campaigns: 0, total: 0 },
        source_health: { templates: "UP", campaigns: "DOWN" },
      },
    };
    render(<CommsApprovalsPage />);
    expect(screen.getByText(/Source unavailable: campaigns/)).toBeInTheDocument();
  });
});
