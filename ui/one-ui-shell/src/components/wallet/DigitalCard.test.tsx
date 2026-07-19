import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { DigitalCard } from "./DigitalCard";

// ── Mocks ───────────────────────────────────────────────────────────────────

interface PayArg {
  vitoCardNumber: string;
  payload: string;
  signature: string;
}
const payMutate = vi.fn((_body: PayArg) => Promise.resolve({ data: { id: "txn-1" } }));
const payBioMutate = vi.fn((_body: PayArg) => Promise.resolve({ data: { id: "txn-2" } }));
const setPhrMutate = vi.fn();

const card = {
  cardId: "c1",
  vitoCardNumber: "VITO-1",
  phrEnabled: false,
  maskedCardNumber: "**** 1234",
  lastOfflineCounter: 5,
};

vi.mock("@/hooks/queries/useMusheWallet", () => ({
  useMySmartCards: () => ({ data: { cards: [card] }, isLoading: false, isError: false }),
  useSetPhrCarry: () => ({ mutate: setPhrMutate, isPending: false, isError: false }),
  useCardPay: () => ({ mutateAsync: payMutate, isPending: false }),
  useCardPayBiometric: () => ({ mutateAsync: payBioMutate, isPending: false }),
}));

vi.mock("@/hooks/queries/useCitizenHealthSummary", () => ({
  useCitizenHealthSummary: () => ({
    isLoading: false,
    isError: false,
    allergies: [{ display: "Penicillin" }],
    conditions: [{ display: "Hypertension" }],
    medications: [{ display: "Amlodipine 5mg" }],
  }),
}));

vi.mock("@/lib/citizenPortalClient", () => ({
  citizenPortalApi: {
    getHealthIdQr: vi.fn(() => Promise.resolve({ qr: "tok-xyz" })),
  },
}));

vi.mock("@/lib/digitalCardKey", () => ({
  buildCanonicalPayload: (i: Record<string, unknown>) => JSON.stringify(i),
  nextOfflineCounter: vi.fn(() => Promise.resolve(1_700_000_000_000)),
  signCardPayload: vi.fn(() => Promise.resolve("SIG-BASE64")),
}));

// qrcode.react → echo the encoded value for assertions.
vi.mock("qrcode.react", () => ({
  QRCodeSVG: ({ value }: { value: string }) => <div data-testid="qr" data-value={value} />,
}));

function renderCard() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <DigitalCard enabled />
    </QueryClientProvider>,
  );
}

describe("DigitalCard — three functions", () => {
  beforeEach(() => {
    payMutate.mockClear();
    payBioMutate.mockClear();
    setPhrMutate.mockClear();
  });

  it("Fn1 identity: renders the identity QR and an emergency QR from the signed token", async () => {
    renderCard();
    await waitFor(() => {
      const qrs = screen.getAllByTestId("qr");
      expect(qrs).toHaveLength(2);
      expect(qrs[0]).toHaveAttribute("data-value", "tok-xyz");
    });
    expect(screen.getByTestId("emergency-qr")).toBeInTheDocument();
  });

  it("Fn3 pay: signs the canonical payload and posts {vitoCardNumber,payload,signature}", async () => {
    const keyLib = await import("@/lib/digitalCardKey");
    renderCard();

    fireEvent.click(screen.getByTestId("digital-card-tab-pay"));
    const amount = screen.getByLabelText("Payment amount");
    fireEvent.change(amount, { target: { value: "7.25" } });
    fireEvent.click(screen.getByTestId("pay-button"));

    await waitFor(() => expect(payMutate).toHaveBeenCalledTimes(1));
    const body = payMutate.mock.calls[0][0];
    expect(body.vitoCardNumber).toBe("VITO-1");
    expect(body.signature).toBe("SIG-BASE64");
    expect(body.payload).toContain('"amount":"7.25"');
    expect(body.payload).toContain('"authMethod":"PIN"');
    // The signer was handed the exact bytes that were posted.
    expect(keyLib.signCardPayload).toHaveBeenCalledWith(body.payload);

    await waitFor(() =>
      expect(screen.getByTestId("pay-result")).toHaveTextContent(/Paid USD 7\.25/),
    );
  });

  it("Fn3 biometric pay: asserts authMethod=BIOMETRIC and posts to the biometric mutation", async () => {
    renderCard();
    fireEvent.click(screen.getByTestId("digital-card-tab-pay"));
    fireEvent.click(screen.getByTestId("pay-biometric-button"));

    await waitFor(() => expect(payBioMutate).toHaveBeenCalledTimes(1));
    const body = payBioMutate.mock.calls[0][0];
    expect(body.payload).toContain('"authMethod":"BIOMETRIC"');
    expect(payMutate).not.toHaveBeenCalled();
  });

  it("Fn2 health: renders allergies, conditions, and medications read-only", () => {
    renderCard();
    fireEvent.click(screen.getByTestId("digital-card-tab-health"));
    expect(screen.getByText("Penicillin")).toBeInTheDocument();
    expect(screen.getByText("Hypertension")).toBeInTheDocument();
    expect(screen.getByText("Amlodipine 5mg")).toBeInTheDocument();
    expect(screen.getByLabelText(/Carry my health record/i)).toBeInTheDocument();
  });
});
