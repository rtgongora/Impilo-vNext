import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { ProviderLifecyclePanel } from "./ProviderLifecyclePanel";

const { transitions, transitionMutate } = vi.hoisted(() => ({
  transitions: { data: { current: "LICENCED_ACTIVE", allowed: ["SUSPENDED", "RETIRED"] } },
  transitionMutate: vi.fn(),
}));

vi.mock("@/hooks/queries/useProviderLifecycle", async () => {
  const actual = await vi.importActual<typeof import("@/hooks/queries/useProviderLifecycle")>(
    "@/hooks/queries/useProviderLifecycle",
  );
  return {
    ...actual,
    useLifecycleTransitions: () => ({ data: transitions.data, isPending: false }),
    useTransitionLifecycle: () => ({ mutate: transitionMutate, isPending: false, isError: false, error: null }),
  };
});

describe("ProviderLifecyclePanel", () => {
  beforeEach(() => transitionMutate.mockReset());

  it("shows the current state and allowed transitions", () => {
    render(<ProviderLifecyclePanel providerPublicId="PRV-1" />);
    expect(screen.getByText(/Licenced Active/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Suspended" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Retired" })).toBeInTheDocument();
  });

  it("requires a reason before a terminal (Retired) transition", () => {
    render(<ProviderLifecyclePanel providerPublicId="PRV-1" />);
    fireEvent.click(screen.getByRole("button", { name: "Retired" }));
    const confirm = screen.getByRole("button", { name: /Confirm/ });
    expect(confirm).toBeDisabled();
    fireEvent.change(screen.getByPlaceholderText(/Reason \(required\)/i), { target: { value: "Retired per notice" } });
    fireEvent.click(confirm);
    expect(transitionMutate).toHaveBeenCalledWith(
      expect.objectContaining({ targetState: "RETIRED", reason: "Retired per notice" }),
      expect.anything(),
    );
  });

  it("submits a non-terminal transition without a reason", () => {
    render(<ProviderLifecyclePanel providerPublicId="PRV-1" />);
    fireEvent.click(screen.getByRole("button", { name: "Suspended" }));
    fireEvent.click(screen.getByRole("button", { name: /Confirm/ }));
    expect(transitionMutate).toHaveBeenCalledWith(
      expect.objectContaining({ targetState: "SUSPENDED" }),
      expect.anything(),
    );
  });
});
