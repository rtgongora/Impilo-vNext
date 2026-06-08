import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { CouncilObligationPaymentPanel } from "@/components/registry/CouncilObligationPaymentPanel";

const mutateCreate = vi.fn();
const mutateSync = vi.fn();

vi.mock("@/hooks/queries/useProviderCouncil", () => ({
  useCreateCouncilMusheXIntent: () => ({ mutate: mutateCreate, isPending: false }),
  useSyncCouncilObligationPayment: () => ({ mutate: mutateSync, isPending: false }),
}));

describe("ProviderCouncilMusheXIntent", () => {
  it("renders obligation rows with create intent, sync, and wallet actions", async () => {
    const user = userEvent.setup();
    render(
      <CouncilObligationPaymentPanel
        providerId="42"
        obligations={[
          { id: 7, obligationType: "REGISTRATION_FEE", amount: 150, currency: "USD", status: "OPEN" },
          {
            id: 8,
            obligationType: "RENEWAL_FEE",
            amount: 80,
            currency: "USD",
            status: "PENDING_PAYMENT",
            mushexIntentId: "intent-abc",
          },
        ]}
      />,
    );

    expect(screen.getByTestId("council-obligation-payment-panel")).toBeInTheDocument();
    expect(screen.getByTestId("council-obligation-row-7")).toBeInTheDocument();
    expect(screen.getByTestId("council-create-intent-7")).toBeInTheDocument();
    expect(screen.getByTestId("council-sync-payment-8")).toBeInTheDocument();
    expect(screen.getByTestId("council-open-wallet-8")).toHaveAttribute(
      "href",
      "/wallet?intentId=intent-abc",
    );

    await user.click(screen.getByTestId("council-create-intent-7"));
    expect(mutateCreate).toHaveBeenCalledWith(7);

    await user.click(screen.getByTestId("council-sync-payment-8"));
    expect(mutateSync).toHaveBeenCalledWith(8);
  });

  it("shows empty state when no obligations", () => {
    render(<CouncilObligationPaymentPanel providerId="42" obligations={[]} />);
    expect(screen.getByText(/No council fee obligations/i)).toBeInTheDocument();
  });
});
