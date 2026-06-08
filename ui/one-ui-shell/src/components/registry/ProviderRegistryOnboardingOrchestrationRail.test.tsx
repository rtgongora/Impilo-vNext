import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ProviderRegistryOnboardingOrchestrationRail } from "./ProviderRegistryOnboardingOrchestrationRail";

const mutateDecide = vi.fn();
const mutateStatus = vi.fn();

vi.mock("@/hooks/queries/useRegistry", () => ({
  useProviders: (params?: { status?: string }) => {
    if (params?.status === "PENDING") {
      return {
        data: {
          data: [
            {
              id: "prov-pending-1",
              attributes: { displayName: "Dr Pending Applicant", status: "PENDING" },
            },
          ],
        },
        isLoading: false,
      };
    }
    return { data: { data: [{ id: "p1" }, { id: "p2" }] }, isLoading: false };
  },
  useProviderReconciliationQueue: () => ({
    data: { items: [{ id: 42, sourceRef: "SRC-1" }], total_elements: 1 },
    isLoading: false,
  }),
  useDecideReconciliationCase: () => ({ mutate: mutateDecide, isPending: false }),
  useChangeProviderStatus: () => ({ mutate: mutateStatus, isPending: false }),
}));

describe("ProviderRegistryOnboardingOrchestrationRail", () => {
  it("shows provider and reconciliation orchestration", () => {
    render(<ProviderRegistryOnboardingOrchestrationRail />);
    expect(screen.getByTestId("provider-registry-onboarding-orchestration-rail")).toBeInTheDocument();
    expect(screen.getByTestId("registry-provider-kpi-strip")).toHaveTextContent("2 provider row(s)");
    expect(screen.getByTestId("registry-reconciliation-probe")).toHaveTextContent("1 reconciliation case(s)");
    expect(screen.getByTestId("registry-pending-providers-probe")).toHaveTextContent("1 provider application(s)");
  });

  it("wires apply and verify actions to registry routes and BFF mutations", async () => {
    const user = userEvent.setup();
    render(<ProviderRegistryOnboardingOrchestrationRail />);

    expect(screen.getByTestId("registry-provider-apply-link")).toHaveAttribute("href", "/registry/providers/new");
    expect(screen.getByTestId("registry-provider-verify-queue-link")).toHaveAttribute(
      "href",
      "/registry/providers/verification",
    );

    await user.click(screen.getByTestId("registry-provider-verify-prov-pending-1"));
    expect(mutateStatus).toHaveBeenCalledWith({
      id: "prov-pending-1",
      newStatus: "ACTIVE",
      reason: "Provider verified from registry onboarding rail",
    });

    await user.click(screen.getByRole("button", { name: "Accept" }));
    expect(mutateDecide).toHaveBeenCalledWith({
      caseId: 42,
      action: "ACCEPT",
      reason: "Council import accepted from rail",
    });
  });
});
