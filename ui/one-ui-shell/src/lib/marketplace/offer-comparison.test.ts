/**
 * OF-B7 — §11.5/§8.6 fairness + honesty rules, unit-proven:
 * declared user-changeable sort, ranked order preserved by default,
 * honest completeness, TTL factuality, split overlap prevention,
 * combined split posture, coded outcome copy.
 */

import { describe, expect, it } from "vitest";
import {
  DEFAULT_SORT_MODE,
  RANKED_BECAUSE_COPY,
  SORT_MODES,
  extractPublishedLines,
  isSelectable,
  offerCompleteness,
  outcomeCopy,
  overlapsSelection,
  rankedBecauseLabel,
  sortOffers,
  splitSummary,
  stockGradeCopy,
  ttlState,
  unwrapFlowData,
  worstStockGrade,
  type OfferView,
  type RequestView,
} from "./offer-comparison";

function offer(partial: Partial<OfferView>): OfferView {
  return {
    offerId: "OF-x",
    requestId: "MR-1",
    vendorId: "V-1",
    status: "ACTIVE",
    statusReason: null,
    priceTotal: 10,
    currency: "USD",
    fulfillmentWindowHours: 24,
    ttlExpiresAt: new Date(Date.now() + 3_600_000).toISOString(),
    lines: [],
    rankedBecause: ["SUITABLE"],
    financials: null,
    homeCollection: false,
    collectionWindowStart: null,
    collectionWindowEnd: null,
    uncoveredLineRefs: null,
    ...partial,
  };
}

describe("unwrapFlowData (msika-flow ApiResponse envelope)", () => {
  it("unwraps the data member", () => {
    expect(unwrapFlowData<{ a: number }>({ success: true, data: { a: 1 } })).toEqual({ a: 1 });
  });
  it("returns null for error envelopes and junk (no fabrication)", () => {
    expect(unwrapFlowData({ success: false, error: { code: "X" } })).toBeNull();
    expect(unwrapFlowData(null)).toBeNull();
    expect(unwrapFlowData("nope")).toBeNull();
  });
});

describe("§11.5 declared, user-changeable sort", () => {
  const a = offer({ offerId: "A", priceTotal: 30, financials: { status: "VERIFIED", patientLiability: 5 }, fulfillmentWindowHours: 48 });
  const b = offer({ offerId: "B", priceTotal: 10, financials: { status: "SELF_PAY", patientLiability: 10 }, fulfillmentWindowHours: 2, uncoveredLineRefs: ["L2"] });
  const c = offer({ offerId: "C", priceTotal: 20, financials: null, fulfillmentWindowHours: 24 });

  it("default sort is the marketplace RANKED order, never price-alone", () => {
    expect(DEFAULT_SORT_MODE).toBe("RANKED");
    expect(sortOffers([a, b, c], "RANKED").map((o) => o.offerId)).toEqual(["A", "B", "C"]);
  });

  it("every sort mode declares a human rationale", () => {
    for (const mode of SORT_MODES) {
      expect(mode.rationale.length).toBeGreaterThan(10);
    }
  });

  it("LOWEST_DUE_NOW sorts by estimated patient liability (gross price only as fallback)", () => {
    expect(sortOffers([a, b, c], "LOWEST_DUE_NOW").map((o) => o.offerId)).toEqual(["A", "B", "C"]);
  });

  it("LOWEST_TOTAL_PRICE sorts by gross price", () => {
    expect(sortOffers([a, b, c], "LOWEST_TOTAL_PRICE").map((o) => o.offerId)).toEqual(["B", "C", "A"]);
  });

  it("MOST_COMPLETE puts fully-covering offers first, ties keep rank order", () => {
    expect(sortOffers([b, a, c], "MOST_COMPLETE").map((o) => o.offerId)).toEqual(["A", "C", "B"]);
  });

  it("FASTEST_READY sorts by fulfilment window", () => {
    expect(sortOffers([a, b, c], "FASTEST_READY").map((o) => o.offerId)).toEqual(["B", "C", "A"]);
  });

  it("never drops an offer in any mode (no hiding cheaper/any offers)", () => {
    for (const mode of SORT_MODES) {
      expect(sortOffers([a, b, c], mode.id)).toHaveLength(3);
    }
  });
});

describe("ranked-because closed taxonomy", () => {
  it("covers the §8.6.2 taxonomy", () => {
    for (const code of ["SUITABLE", "CHEAPEST", "NEAREST", "FASTEST", "FULLY_COVERED", "LOWEST_SHORTFALL", "COMPLETE", "PREFERRED", "CONTINUITY"]) {
      expect(RANKED_BECAUSE_COPY[code]).toBeTruthy();
    }
  });
  it("renders unknown labels raw rather than inventing copy", () => {
    expect(rankedBecauseLabel("SOMETHING_NEW")).toBe("SOMETHING_NEW");
  });
});

describe("honest completeness — covers N of M items", () => {
  const requestLines = [{ lineRef: "L1" }, { lineRef: "L2" }, { lineRef: "L3" }];

  it("full coverage", () => {
    const result = offerCompleteness(offer({ uncoveredLineRefs: [] }), requestLines);
    expect(result).toMatchObject({ covered: 3, total: 3, complete: true });
  });

  it("partial coverage names the missing lines", () => {
    const result = offerCompleteness(offer({ uncoveredLineRefs: ["L2", "L3"] }), requestLines);
    expect(result).toMatchObject({ covered: 1, total: 3, complete: false, missingLineRefs: ["L2", "L3"] });
  });
});

describe("TTL factuality", () => {
  it("reports minutes remaining without inventing pressure", () => {
    const now = new Date("2026-07-23T12:00:00Z");
    expect(ttlState("2026-07-23T12:31:00Z", now)).toEqual({ expired: false, minutesLeft: 31 });
  });
  it("expired offers are expired and unselectable", () => {
    const now = new Date("2026-07-23T12:00:00Z");
    expect(ttlState("2026-07-23T11:59:00Z", now)).toEqual({ expired: true, minutesLeft: 0 });
    expect(isSelectable(offer({ ttlExpiresAt: "2026-07-23T11:59:00Z" }), now)).toBe(false);
  });
  it("non-ACTIVE offers are never selectable", () => {
    expect(isSelectable(offer({ status: "WITHDRAWN" }))).toBe(false);
  });
  it("absent TTL is shown as absent, not fabricated", () => {
    expect(ttlState(null)).toEqual({ expired: false, minutesLeft: null });
  });
});

describe("§8.6.4 split composition", () => {
  const line = (ref: string) => ({
    offerLineId: `ol-${ref}`,
    requestLineRef: ref,
    itemCode: `ITEM-${ref}`,
    quantity: 1,
    unitPrice: 5,
    stockGrade: "VERIFIED",
    substitutionProposed: false,
  });
  const requestLines = [{ lineRef: "L1" }, { lineRef: "L2" }, { lineRef: "L3" }];
  const coversL1 = offer({ offerId: "A", lines: [line("L1")], priceTotal: 5, financials: { status: "VERIFIED", patientLiability: 2, currency: "USD" }, currency: "USD" });
  const coversL2L3 = offer({ offerId: "B", lines: [line("L2"), line("L3")], priceTotal: 12, financials: { status: "SELF_PAY", patientLiability: 12, currency: "USD" }, currency: "USD" });
  const alsoCoversL1 = offer({ offerId: "C", lines: [line("L1"), line("L2")], priceTotal: 9 });

  it("prevents overlapping picks (same request line twice)", () => {
    expect(overlapsSelection(alsoCoversL1, [coversL1])).toBe(true);
    expect(overlapsSelection(coversL2L3, [coversL1])).toBe(false);
  });

  it("computes live combined price, due-now and coverage", () => {
    const summary = splitSummary([coversL1, coversL2L3], requestLines);
    expect(summary.combinedPrice).toBe(17);
    expect(summary.combinedDueNow).toBe(14);
    expect(summary.dueNowComplete).toBe(true);
    expect(summary.complete).toBe(true);
    expect(summary.uncoveredLineRefs).toEqual([]);
  });

  it("is honest when a member lacks a financial block", () => {
    const summary = splitSummary([coversL1, alsoCoversL1], requestLines);
    expect(summary.dueNowComplete).toBe(false);
    expect(summary.uncoveredLineRefs).toEqual(["L3"]);
    expect(summary.complete).toBe(false);
  });
});

describe("coded outcomes → honest actionable states", () => {
  it("OFFER_EXPIRED refreshes offers, never a dead end", () => {
    expect(outcomeCopy("OFFER_EXPIRED").action).toBe("REFRESH_OFFERS");
  });
  it("AWAITING_PAYMENT is a pending complete-payment state, not a failure", () => {
    const copy = outcomeCopy("AWAITING_PAYMENT");
    expect(copy.action).toBe("COMPLETE_PAYMENT");
    expect(copy.tone).toBe("pending");
  });
  it("STOCK_UNAVAILABLE / PA_REQUIRED / FINANCIAL_UNVERIFIED all carry actions", () => {
    for (const code of ["STOCK_UNAVAILABLE", "PA_REQUIRED", "FINANCIAL_UNVERIFIED", "NOT_COVERED", "PAYMENT_TIMEOUT"]) {
      expect(outcomeCopy(code).action).not.toBe("NONE");
    }
  });
  it("unknown codes get an honest generic failure naming the code", () => {
    const copy = outcomeCopy("SOMETHING_ELSE");
    expect(copy.tone).toBe("failure");
    expect(copy.body).toContain("SOMETHING_ELSE");
  });
});

describe("stock-truth grades", () => {
  it("labels VERIFIED and REPORTED honestly", () => {
    expect(stockGradeCopy("VERIFIED").label).toBe("Stock verified");
    expect(stockGradeCopy("REPORTED").detail).toContain("not verified");
  });
  it("worst grade dominates the offer badge", () => {
    const lines = [
      { offerLineId: "1", requestLineRef: "L1", itemCode: "A", quantity: 1, unitPrice: 1, stockGrade: "VERIFIED", substitutionProposed: false },
      { offerLineId: "2", requestLineRef: "L2", itemCode: "B", quantity: 1, unitPrice: 1, stockGrade: "REPORTED", substitutionProposed: false },
    ];
    expect(worstStockGrade(offer({ lines }))).toBe("REPORTED");
    expect(worstStockGrade(offer({ lines: [lines[0]] }))).toBe("VERIFIED");
    expect(worstStockGrade(offer({ lines: [] }))).toBeNull();
  });
});

describe("published-lines snapshot extraction", () => {
  const base: Omit<RequestView, "publishedLines"> = {
    requestId: "MR-1",
    orosOrderId: "ORD-1",
    profile: "PHARMACY",
    publicationMode: "OPEN",
    status: "OFFERS_AVAILABLE",
    controlled: false,
    coarseZone: null,
    urgency: null,
    offerWindowEndsAt: null,
    selectionWindowEndsAt: null,
  };

  it("handles the {lines:[]} wrapper and the bare-array form", () => {
    expect(extractPublishedLines({ ...base, publishedLines: { lines: [{ lineRef: "L1" }] } })).toEqual([{ lineRef: "L1" }]);
    expect(extractPublishedLines({ ...base, publishedLines: [{ lineRef: "L2" }] })).toEqual([{ lineRef: "L2" }]);
    expect(extractPublishedLines({ ...base, publishedLines: null })).toEqual([]);
    expect(extractPublishedLines(null)).toEqual([]);
  });
});
