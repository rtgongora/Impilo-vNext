import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { ProviderLifecyclePanel } from "./ProviderLifecyclePanel";

const { transitions, transitionMutate, renewalMutate } = vi.hoisted(() => ({
  transitions: { data: { current: "LICENCED_ACTIVE", allowed: ["SUSPENDED", "RETIRED"] } },
  transitionMutate: vi.fn(),
  renewalMutate: vi.fn(),
}));

vi.mock("@/hooks/queries/useProviderLifecycle", async () => {
  const actual = await vi.importActual<typeof import("@/hooks/queries/useProviderLifecycle")>(
    "@/hooks/queries/useProviderLifecycle",
  );
  return {
    ...actual,
    useLifecycleTransitions: () => ({ data: transitions.data, isPending: false }),
    useTransitionLifecycle: () => ({ mutate: transitionMutate, isPending: false, isError: false, error: null }),
    useStartLicenceRenewal: () => ({ mutate: renewalMutate, isPending: false }),
    useStartLicenceRestoration: () => ({ mutate: vi.fn(), isPending: false }),
  };
});

describe("ProviderLifecyclePanel", () => {
  beforeEach(() => {
    transitionMutate.mockReset();
    renewalMutate.mockReset();
    transitions.data = { current: "LICENCED_ACTIVE", allowed: ["SUSPENDED", "RETIRED"] };
  });

  it("offers Start renewal when the provider is due for renewal", () => {
    transitions.data = { current: "LICENCE_DUE_FOR_RENEWAL", allowed: [] };
    render(<ProviderLifecyclePanel providerPublicId="PRV-1" />);
    fireEvent.click(screen.getByRole("button", { name: /Start renewal/i }));
    expect(renewalMutate).toHaveBeenCalled();
  });

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
