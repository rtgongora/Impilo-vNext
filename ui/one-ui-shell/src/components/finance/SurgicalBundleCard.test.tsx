import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { SurgicalBundleCard } from "./SurgicalBundleCard";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock("@/lib/api-client", () => ({ apiClient: { get } }));

function wrap(node: React.ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}>{node}</QueryClientProvider>);
}

const ENC = "enc-1";
const EPISODE = "ep-9";

function billingResponse() {
  return { data: { encounter_id: ENC, billing_state: "DRAFT", bills: [{ billId: "bill-1" }] } };
}

function billDetailWith(lines: unknown[]) {
  return { data: { attributes: { lineItems: lines } } };
}

describe("SurgicalBundleCard", () => {
  beforeEach(() => vi.clearAllMocks());

  it("renders grouped theatre bundle lines with a subtotal", async () => {
    get.mockImplementation((url: string) => {
      if (url.includes("/billing") && url.includes("encounters")) return Promise.resolve(billingResponse());
      if (url.includes("/finance/billing/")) {
        return Promise.resolve(
          billDetailWith([
            { lineId: "l1", msikaCode: "THEATRE-TIME", description: "Theatre time", qty: 90, unitPrice: 2, amount: 180, sourceEvent: "theatre.case.completed", sourceRef: EPISODE },
            { lineId: "l2", msikaCode: "ANAESTHESIA-GA", description: "GA", qty: 1, unitPrice: 50, amount: 50, sourceEvent: "theatre.case.completed", sourceRef: EPISODE },
            { lineId: "l3", msikaCode: "CONSULT", description: "Unrelated", qty: 1, unitPrice: 5, amount: 5, sourceEvent: "pct.encounter.charged", sourceRef: "other" },
          ]),
        );
      }
      return Promise.resolve(null);
    });

    wrap(<SurgicalBundleCard encounterId={ENC} />);

    expect(await screen.findByTestId("surgical-bundle-card")).toBeInTheDocument();
    expect(await screen.findByTestId(`surgical-bundle-case-${EPISODE}`)).toHaveTextContent("230.00");
    expect(screen.getByTestId("surgical-bundle-line-THEATRE-TIME")).toBeInTheDocument();
    expect(screen.getByTestId("surgical-bundle-line-ANAESTHESIA-GA")).toBeInTheDocument();
    // The unrelated non-bundle charge is excluded.
    expect(screen.queryByTestId("surgical-bundle-line-CONSULT")).not.toBeInTheDocument();
  });

  it("renders nothing on a non-surgical encounter (no bundle lines)", async () => {
    get.mockImplementation((url: string) => {
      if (url.includes("/billing") && url.includes("encounters")) return Promise.resolve(billingResponse());
      if (url.includes("/finance/billing/")) {
        return Promise.resolve(
          billDetailWith([
            { lineId: "l1", msikaCode: "CONSULT", description: "Consult", amount: 10, sourceEvent: "pct.encounter.charged", sourceRef: "x" },
          ]),
        );
      }
      return Promise.resolve(null);
    });

    const { container } = wrap(<SurgicalBundleCard encounterId={ENC} />);
    await waitFor(() => expect(screen.queryByTestId("surgical-bundle-card")).not.toBeInTheDocument());
    expect(container).toBeEmptyDOMElement();
  });
});
