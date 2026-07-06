import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { TwoPersonApprovalPanel } from "./TwoPersonApprovalPanel";

function baseProps() {
  return {
    accessRequestId: "ar-1",
    status: "PENDING",
    initiatorId: "initiator-1",
    approvalsRequired: 2,
    approvalsRecorded: 0,
    approvalsSatisfied: false,
    approvals: [],
    onApprove: vi.fn(),
    onExecute: vi.fn(),
  };
}

describe("TwoPersonApprovalPanel", () => {
  it("renders 1-of-2 progress after one approval and keeps Execute disabled while PENDING", () => {
    render(
      <TwoPersonApprovalPanel
        {...baseProps()}
        approvalsRecorded={1}
        approvals={[{ approverUserId: "approver-1", approverRole: "PLATFORM_ORIGIN_ADMINISTRATOR", decision: "APPROVE" }]}
      />,
    );

    expect(screen.getByText("1-of-2 approvals")).toBeInTheDocument();
    expect(screen.getByText("Awaiting approvals")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Execute platform action/i })).toBeDisabled();
  });

  it("renders 2-of-2 satisfied and enables Execute once APPROVED", () => {
    render(
      <TwoPersonApprovalPanel
        {...baseProps()}
        status="APPROVED"
        approvalsRecorded={2}
        approvalsSatisfied
        approvals={[
          { approverUserId: "approver-1", approverRole: "PLATFORM_ORIGIN_ADMINISTRATOR", decision: "APPROVE" },
          { approverUserId: "approver-2", approverRole: "PLATFORM_ORIGIN_ADMINISTRATOR", decision: "APPROVE" },
        ]}
      />,
    );

    expect(screen.getByText("2-of-2 approvals")).toBeInTheDocument();
    expect(screen.getByText("Satisfied")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Execute platform action/i })).toBeEnabled();
  });

  it("blocks the initiator from approving (two-person rule) in the UX", () => {
    const onApprove = vi.fn();
    render(<TwoPersonApprovalPanel {...baseProps()} onApprove={onApprove} />);

    fireEvent.change(screen.getByLabelText(/Approver user id/i), { target: { value: "initiator-1" } });
    expect(screen.getByText(/initiator of a platform action cannot approve it/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Record approval/i })).toBeDisabled();
  });

  it("submits an approval for a distinct approver", () => {
    const onApprove = vi.fn();
    render(<TwoPersonApprovalPanel {...baseProps()} onApprove={onApprove} />);

    fireEvent.change(screen.getByLabelText(/Approver user id/i), { target: { value: "approver-2" } });
    fireEvent.click(screen.getByRole("button", { name: /Record approval/i }));
    expect(onApprove).toHaveBeenCalledWith({ approverUserId: "approver-2", note: undefined });
  });
});
