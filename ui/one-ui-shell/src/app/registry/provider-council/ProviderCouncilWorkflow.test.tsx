import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";
import CouncilWorkspacePage from "@/app/registry/provider-council/council-workspace/page";
import {
  councilQueueReviewBucket,
  type CouncilApplicationRow,
} from "@/hooks/queries/useProviderCouncil";

const mutateAdvance = vi.fn();
const mutateReview = vi.fn();
const refetch = vi.fn();

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/learning/CouncilLearningEvidencePanel", () => ({
  CouncilLearningEvidencePanel: () => null,
}));

vi.mock("next/navigation", () => ({
  useSearchParams: () =>
    new URLSearchParams("councilId=1&workflowStates=SUBMITTED,READY_FOR_REVIEW"),
}));

vi.mock("@/hooks/queries/useProviderCouncil", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/hooks/queries/useProviderCouncil")>();
  return {
    ...actual,
    useProviderCouncilQueue: () => ({
      data: [
        {
          id: 101,
          applicationType: "REGISTRATION",
          workflowState: "SUBMITTED",
          reviewState: null,
          feeState: "PENDING",
        },
        {
          id: 102,
          applicationType: "RENEWAL",
          workflowState: "READY_FOR_REVIEW",
          reviewState: "APPROVED",
          feeState: "PAID",
        },
        {
          id: 103,
          applicationType: "RESTORATION",
          workflowState: "READY_FOR_REVIEW",
          reviewState: "REJECTED",
          feeState: "PAID",
        },
        {
          id: 104,
          applicationType: "REGISTRATION",
          workflowState: "UNDER_ADMIN_REVIEW",
          reviewState: "NEEDS_INFO",
          feeState: "PENDING",
        },
      ] satisfies CouncilApplicationRow[],
      isLoading: false,
      refetch,
    }),
    useAdvanceCouncilApplication: () => ({ mutate: mutateAdvance, isPending: false }),
    useRecordCouncilReview: () => ({ mutate: mutateReview, isPending: false }),
  };
});

describe("councilQueueReviewBucket", () => {
  it("maps review states into queue tabs", () => {
    expect(councilQueueReviewBucket({ reviewState: null })).toBe("pending");
    expect(councilQueueReviewBucket({ reviewState: "APPROVED" })).toBe("approved");
    expect(councilQueueReviewBucket({ reviewState: "REJECTED" })).toBe("rejected");
    expect(councilQueueReviewBucket({ reviewState: "NEEDS_INFO" })).toBe("needs-info");
  });
});

describe("ProviderCouncilWorkflow", () => {
  it("renders actionable queue tabs with counts", () => {
    render(<CouncilWorkspacePage />);
    expect(screen.getByTestId("council-workspace-queue")).toBeInTheDocument();
    expect(screen.getByTestId("council-queue-tab-pending")).toHaveTextContent("Pending (1)");
    expect(screen.getByTestId("council-queue-tab-approved")).toHaveTextContent("Approved (1)");
    expect(screen.getByTestId("council-queue-tab-rejected")).toHaveTextContent("Rejected (1)");
    expect(screen.getByTestId("council-queue-tab-needs-info")).toHaveTextContent("Needs info (1)");
  });

  it("wires workflow advance and review mutations", async () => {
    const user = userEvent.setup();
    render(<CouncilWorkspacePage />);

    await user.click(screen.getByTestId("council-advance-admin-101"));
    expect(mutateAdvance).toHaveBeenCalledWith(
      { applicationId: 101, action: "under-admin-review" },
      expect.objectContaining({ onSuccess: expect.any(Function) }),
    );

    await user.click(screen.getByTestId("council-queue-tab-approved"));
    expect(screen.getByTestId("council-application-102")).toBeInTheDocument();

    await user.click(screen.getByTestId("council-queue-tab-needs-info"));
    await user.click(screen.getByTestId("council-review-approve-104"));
    expect(mutateReview).toHaveBeenCalledWith(
      { applicationId: 104, councilId: 1, decision: "APPROVED" },
      expect.objectContaining({ onSuccess: expect.any(Function) }),
    );
  });
});
