/**
 * OF-B7 — binding-copy rendering tests for the comparison surface pieces:
 * ranked-because labels, "estimate — never final" wording, honest stock
 * grades, honest completeness and factual TTL on the OfferCard.
 */

import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { FinancialsBlock, OfferCard, StockGradeBadge } from "./OfferComparisonSurface";
import type { OfferView } from "@/lib/marketplace/offer-comparison";

vi.mock("@/hooks/queries/useMarketplaceOffers", () => ({
  useMarketplaceRequest: vi.fn(),
  useMarketplaceOffers: vi.fn(),
  useSelectOffer: vi.fn(),
  useSelectSplit: vi.fn(),
  useElectPathway: vi.fn(),
  useCancelMarketplaceRequest: vi.fn(),
}));

vi.mock("@/hooks/queries/useMusheWallet", () => ({
  useWalletPay: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
}));

const NOW = new Date("2026-07-23T12:00:00Z");

function offer(partial: Partial<OfferView>): OfferView {
  return {
    offerId: "OF-1",
    requestId: "MR-1",
    vendorId: "V-9",
    status: "ACTIVE",
    statusReason: null,
    priceTotal: 42.5,
    currency: "USD",
    fulfillmentWindowHours: 12,
    ttlExpiresAt: "2026-07-23T12:45:00Z",
    lines: [
      {
        offerLineId: "ol-1",
        requestLineRef: "L1",
        itemCode: "PARA-500",
        quantity: 2,
        unitPrice: 1.25,
        stockGrade: "VERIFIED",
        substitutionProposed: false,
      },
      {
        offerLineId: "ol-2",
        requestLineRef: "L2",
        itemCode: "AMOX-250",
        quantity: 1,
        unitPrice: 40,
        stockGrade: "REPORTED",
        substitutionProposed: true,
      },
    ],
    rankedBecause: ["SUITABLE", "CHEAPEST"],
    financials: {
      status: "VERIFIED",
      currency: "USD",
      grossCharge: 42.5,
      coveredAmount: 30,
      patientLiability: 12.5,
      estimateNeverFinal: true,
    },
    homeCollection: false,
    collectionWindowStart: null,
    collectionWindowEnd: null,
    uncoveredLineRefs: ["L3"],
    ...partial,
  };
}

const noop = () => {};

function renderCard(view: OfferView, extra?: Partial<Parameters<typeof OfferCard>[0]>) {
  return render(
    <OfferCard
      offer={view}
      requestLines={[{ lineRef: "L1" }, { lineRef: "L2" }, { lineRef: "L3" }]}
      now={NOW}
      selectable
      splitMode={false}
      splitChecked={false}
      splitBlocked={false}
      onToggleSplit={noop}
      onChoose={noop}
      {...extra}
    />,
  );
}

describe("OfferCard — §8.6/§11.5 binding copy", () => {
  it("shows human ranked-because labels from the closed taxonomy", () => {
    renderCard(offer({}));
    const labels = screen.getByTestId("ranked-because");
    expect(labels).toHaveTextContent("Meets all requirements");
    expect(labels).toHaveTextContent("Lowest total price");
  });

  it("renders the estimate-never-final wording with the liability block", () => {
    renderCard(offer({}));
    expect(screen.getByTestId("due-now")).toHaveTextContent("You pay (estimate): USD 12.50");
    expect(screen.getByTestId("estimate-never-final")).toHaveTextContent(/Estimate — never final/);
  });

  it("labels stock grades honestly (REPORTED dominates the offer badge)", () => {
    renderCard(offer({}));
    const badges = screen.getAllByTestId("stock-grade-badge");
    // offer-level badge (worst grade) + one per line
    expect(badges.length).toBe(3);
    expect(badges[0]).toHaveTextContent("Stock reported");
  });

  it("states honest completeness — covers 2 of 3 items", () => {
    renderCard(offer({}));
    expect(screen.getByTestId("completeness")).toHaveTextContent("Covers 2 of 3 items");
  });

  it("flags substitutions per line, never silently", () => {
    renderCard(offer({}));
    expect(screen.getByTestId("substitution-indicator")).toBeInTheDocument();
    expect(screen.getByText(/substitute proposed/)).toBeInTheDocument();
  });

  it("shows the factual TTL remaining", () => {
    renderCard(offer({}));
    expect(screen.getByTestId("ttl")).toHaveTextContent("45 min left");
  });

  it("an expired offer is honest and unselectable", () => {
    renderCard(offer({ ttlExpiresAt: "2026-07-23T11:00:00Z" }));
    expect(screen.getByTestId("ttl")).toHaveTextContent("Offer expired");
    expect(screen.getByTestId("choose-offer")).toBeDisabled();
    expect(screen.getByTestId("choose-offer")).toHaveTextContent("No longer available");
  });

  it("selection is gated until the pathway is elected", () => {
    renderCard(offer({}), { selectable: false });
    expect(screen.getByTestId("choose-offer")).toBeDisabled();
  });
});

describe("FinancialsBlock honesty states", () => {
  it("is honest when no financial block exists (no invented numbers)", () => {
    render(<FinancialsBlock offer={offer({ financials: null })} />);
    expect(screen.getByTestId("financials-none")).toHaveTextContent("No cost estimate is available");
  });

  it("is honest when coverage is unverified", () => {
    render(
      <FinancialsBlock
        offer={offer({ financials: { status: "UNVERIFIED", patientLiability: null, estimateNeverFinal: true } })}
      />,
    );
    expect(screen.getByTestId("financials-block")).toHaveTextContent("Coverage could not be verified");
  });
});

describe("StockGradeBadge", () => {
  it("renders unknown grades raw (as received)", () => {
    render(<StockGradeBadge grade="MYSTERY" />);
    expect(screen.getByTestId("stock-grade-badge")).toHaveTextContent("MYSTERY");
  });
});
