import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ReferralActionMenu } from "./ReferralActionMenu";

const { lifecycleMutate, allowedState, lifecycleState } = vi.hoisted(() => ({
  lifecycleMutate: vi.fn(),
  allowedState: {
    current: {
      data: { data: { referralId: "r-1", status: "RESPONDED", allowedTargets: [] as string[] } },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    },
  },
  lifecycleState: { current: { mutate: vi.fn(), isPending: false, isError: false } },
}));

vi.mock("@/hooks/queries/useTelemedicine", () => ({
  useReferralAllowedActions: () => allowedState.current,
  useReferralLifecycleAction: () => lifecycleState.current,
}));

describe("ReferralActionMenu", () => {
  beforeEach(() => {
    lifecycleMutate.mockReset();
    lifecycleState.current = { mutate: lifecycleMutate, isPending: false, isError: false };
    allowedState.current = {
      data: { data: { referralId: "r-1", status: "RESPONDED", allowedTargets: [] } },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    };
  });

  it("renders only the lifecycle actions whose target state is permitted by the guard", () => {
    allowedState.current.data.data.allowedTargets = ["ESCALATED", "CANCELLED"];
    render(<ReferralActionMenu sessionId="r-1" status="RESPONDED" />);

    expect(screen.getByTestId("referral-action-escalate")).toBeInTheDocument();
    expect(screen.getByTestId("referral-action-cancel")).toBeInTheDocument();
    // Not in allowedTargets → must not render (no dead buttons).
    expect(screen.queryByTestId("referral-action-transfer")).not.toBeInTheDocument();
    expect(screen.queryByTestId("referral-action-reopen")).not.toBeInTheDocument();
  });

  it("shows an honest empty state when no transition is permitted", () => {
    allowedState.current.data.data.allowedTargets = [];
    render(<ReferralActionMenu sessionId="r-1" status="CLOSED" />);

    expect(screen.getByTestId("referral-action-empty")).toHaveTextContent(/no further actions available/i);
  });

  it("requires a non-empty reason before the action can be submitted", async () => {
    const user = userEvent.setup();
    allowedState.current.data.data.allowedTargets = ["ESCALATED"];
    render(<ReferralActionMenu sessionId="r-1" status="RESPONDED" />);

    await user.click(screen.getByTestId("referral-action-escalate"));
    // Submit is disabled until a reason is typed.
    expect(screen.getByTestId("referral-action-submit")).toBeDisabled();
    expect(lifecycleMutate).not.toHaveBeenCalled();

    await user.type(screen.getByPlaceholderText(/reason \(required\)/i), "Deteriorating, needs urgent review");
    expect(screen.getByTestId("referral-action-submit")).toBeEnabled();

    await user.click(screen.getByTestId("referral-action-submit"));
    expect(lifecycleMutate).toHaveBeenCalledTimes(1);
    expect(lifecycleMutate.mock.calls[0][0]).toEqual({
      action: "escalate",
      reason: "Deteriorating, needs urgent review",
    });
  });

  it("surfaces a loading state while the guard is resolving", () => {
    allowedState.current = { data: undefined, isLoading: true, isError: false, refetch: vi.fn() } as never;
    render(<ReferralActionMenu sessionId="r-1" status="RESPONDED" />);

    expect(screen.getByText(/checking available actions/i)).toBeInTheDocument();
  });

  it("surfaces an honest error with retry when the guard fails", () => {
    allowedState.current = { data: undefined, isLoading: false, isError: true, refetch: vi.fn() } as never;
    render(<ReferralActionMenu sessionId="r-1" status="RESPONDED" />);

    expect(screen.getByTestId("referral-action-error")).toHaveTextContent(/could not be loaded/i);
  });
});
