/**
 * OF-B7 — offer-comparison pure logic (§8.6 / §11.5 / §11.6 UX contract).
 *
 * Everything here is presentation-side derivation over the msika-flow
 * comparison payload — no truth is invented (BFF/UI compose; msika-flow ranks).
 * Kept framework-free so the §11.5 fairness rules (declared user-changeable
 * sort, never price-alone default, honest completeness) are unit-testable.
 */

// ── upstream shapes (mirrors msika-flow MarketplaceDtos) ─────────────────────

export interface OfferLineView {
  offerLineId: string;
  requestLineRef: string | null;
  itemCode: string;
  quantity: number;
  unitPrice: number | null;
  /** VERIFIED (DURA on-hand − reserved) | REPORTED (vendor-attested, unverified). */
  stockGrade: string;
  substitutionProposed: boolean;
}

/** OF-B9 §10.4 per-offer financial block (estimateNeverFinal:true inside). */
export interface OfferFinancials {
  status: string; // VERIFIED | SELF_PAY | NOT_COVERED | UNVERIFIED
  fundingMode?: string | null;
  currency?: string | null;
  grossCharge?: number | null;
  coveredAmount?: number | null;
  patientLiability?: number | null;
  calculationId?: string | null;
  paRequired?: boolean;
  paStatus?: string | null;
  detail?: string | null;
  estimateNeverFinal?: boolean;
}

export interface OfferView {
  offerId: string;
  requestId: string;
  vendorId: string;
  status: string;
  statusReason: string | null;
  priceTotal: number | null;
  currency: string | null;
  fulfillmentWindowHours: number | null;
  ttlExpiresAt: string | null;
  lines: OfferLineView[];
  /** §11.5 closed taxonomy labels (SUITABLE · CHEAPEST · NEAREST · COMPLETE · …). */
  rankedBecause: string[] | null;
  financials: OfferFinancials | null;
  homeCollection: boolean;
  collectionWindowStart: string | null;
  collectionWindowEnd: string | null;
  /** §8.6.1 honest partial posture — request lines this offer misses. */
  uncoveredLineRefs: string[] | null;
}

export interface PublishedLine {
  lineRef?: string | null;
  itemCode?: string;
  quantity?: number;
  unit?: string | null;
  controlled?: boolean;
  coldChain?: boolean;
  substitutionAllowed?: boolean;
}

export interface RequestView {
  requestId: string;
  orosOrderId: string;
  profile: string;
  publicationMode: string;
  status: string;
  controlled: boolean;
  coarseZone: string | null;
  urgency: string | null;
  offerWindowEndsAt: string | null;
  selectionWindowEndsAt: string | null;
  publishedLines: { lines?: PublishedLine[] } | PublishedLine[] | null;
}

// ── envelope parsing (msika-flow ApiResponse {success,data,error}) ───────────

export function unwrapFlowData<T>(payload: unknown): T | null {
  if (payload == null || typeof payload !== "object") return null;
  const env = payload as { success?: boolean; data?: T };
  if (env.data !== undefined) return env.data ?? null;
  return null;
}

export function extractPublishedLines(request: RequestView | null): PublishedLine[] {
  const snapshot = request?.publishedLines;
  if (!snapshot) return [];
  if (Array.isArray(snapshot)) return snapshot;
  if (Array.isArray(snapshot.lines)) return snapshot.lines;
  return [];
}

// ── §11.5 ranked-because taxonomy (closed; unknown labels rendered raw) ──────

export const RANKED_BECAUSE_COPY: Record<string, string> = {
  SUITABLE: "Meets all requirements",
  CHEAPEST: "Lowest total price",
  NEAREST: "Closest to you",
  FASTEST: "Fastest to fulfil",
  FULLY_COVERED: "Fully covered by your scheme",
  LOWEST_SHORTFALL: "Lowest amount you pay",
  COMPLETE: "All items, no substitutions",
  PREFERRED: "Matches your stated preference",
  CONTINUITY: "Your usual provider",
};

export function rankedBecauseLabel(code: string): string {
  return RANKED_BECAUSE_COPY[code] ?? code;
}

// ── §8.6.2 declared, user-changeable sort — never price-alone default ────────

export type SortMode = "RANKED" | "LOWEST_DUE_NOW" | "LOWEST_TOTAL_PRICE" | "MOST_COMPLETE" | "FASTEST_READY";

export const SORT_MODES: Array<{ id: SortMode; label: string; rationale: string }> = [
  {
    id: "RANKED",
    label: "Recommended (ranked)",
    rationale:
      "Default order from the marketplace ranking: suitability first, then completeness, then price — every position carries its reason.",
  },
  {
    id: "LOWEST_DUE_NOW",
    label: "Lowest amount you pay",
    rationale: "Sorted by the estimated amount you would pay after coverage (estimates are never final).",
  },
  { id: "LOWEST_TOTAL_PRICE", label: "Lowest total price", rationale: "Sorted by gross offer price before coverage." },
  { id: "MOST_COMPLETE", label: "Most complete", rationale: "Offers covering the most of your order first." },
  { id: "FASTEST_READY", label: "Soonest ready", rationale: "Sorted by the vendor's declared fulfilment window." },
];

export const DEFAULT_SORT_MODE: SortMode = "RANKED";

function dueNow(offer: OfferView): number {
  const liability = offer.financials?.patientLiability;
  if (typeof liability === "number") return liability;
  // No financial block → sort by gross price but never pretend it is a liability.
  return typeof offer.priceTotal === "number" ? offer.priceTotal : Number.POSITIVE_INFINITY;
}

export function uncoveredCount(offer: OfferView): number {
  return offer.uncoveredLineRefs?.length ?? 0;
}

/**
 * Stable re-sort of the upstream RANKED order. "RANKED" preserves the
 * marketplace order exactly (rank snapshot is the audited truth); the other
 * modes are user-elected views over the same complete set — nothing hidden.
 */
export function sortOffers(offers: OfferView[], mode: SortMode): OfferView[] {
  const indexed = offers.map((offer, index) => ({ offer, index }));
  const byRank = (a: { index: number }, b: { index: number }) => a.index - b.index;
  const comparators: Record<SortMode, (a: { offer: OfferView; index: number }, b: { offer: OfferView; index: number }) => number> = {
    RANKED: byRank,
    LOWEST_DUE_NOW: (a, b) => dueNow(a.offer) - dueNow(b.offer) || byRank(a, b),
    LOWEST_TOTAL_PRICE: (a, b) =>
      (a.offer.priceTotal ?? Number.POSITIVE_INFINITY) - (b.offer.priceTotal ?? Number.POSITIVE_INFINITY) || byRank(a, b),
    MOST_COMPLETE: (a, b) => uncoveredCount(a.offer) - uncoveredCount(b.offer) || byRank(a, b),
    FASTEST_READY: (a, b) =>
      (a.offer.fulfillmentWindowHours ?? Number.POSITIVE_INFINITY) - (b.offer.fulfillmentWindowHours ?? Number.POSITIVE_INFINITY) ||
      byRank(a, b),
  };
  return indexed.sort(comparators[mode]).map((entry) => entry.offer);
}

// ── §8.6.1 completeness ("covers N of M items", honest) ──────────────────────

export interface Completeness {
  covered: number;
  total: number;
  complete: boolean;
  missingLineRefs: string[];
}

export function offerCompleteness(offer: OfferView, requestLines: PublishedLine[]): Completeness {
  const total = requestLines.length || offer.lines.length + uncoveredCount(offer);
  const missing = offer.uncoveredLineRefs ?? [];
  const covered = Math.max(total - missing.length, 0);
  return { covered, total, complete: missing.length === 0, missingLineRefs: missing };
}

// ── TTL (factual — the true offer TTL, no invented pressure) ─────────────────

export interface TtlState {
  expired: boolean;
  /** Whole minutes remaining (0 when under a minute but not expired). */
  minutesLeft: number | null;
}

export function ttlState(ttlExpiresAt: string | null, now: Date = new Date()): TtlState {
  if (!ttlExpiresAt) return { expired: false, minutesLeft: null };
  const expiry = new Date(ttlExpiresAt).getTime();
  if (Number.isNaN(expiry)) return { expired: false, minutesLeft: null };
  const deltaMs = expiry - now.getTime();
  if (deltaMs <= 0) return { expired: true, minutesLeft: 0 };
  return { expired: false, minutesLeft: Math.floor(deltaMs / 60_000) };
}

export function isSelectable(offer: OfferView, now: Date = new Date()): boolean {
  return offer.status === "ACTIVE" && !ttlState(offer.ttlExpiresAt, now).expired;
}

// ── §8.6.4 split composition (overlap prevention + live combined posture) ────

function coveredLineRefs(offer: OfferView): string[] {
  return offer.lines.map((line) => line.requestLineRef).filter((ref): ref is string => !!ref);
}

/** True when the candidate covers a request line already covered by the current picks. */
export function overlapsSelection(candidate: OfferView, selected: OfferView[]): boolean {
  const taken = new Set(selected.flatMap(coveredLineRefs));
  return coveredLineRefs(candidate).some((ref) => taken.has(ref));
}

export interface SplitSummary {
  offerCount: number;
  /** Sum of gross prices (only offers with a numeric price contribute). */
  combinedPrice: number;
  /** Sum of estimated patient liabilities where a financial block exists. */
  combinedDueNow: number;
  /** True when every member offer carries a financial block — else the due-now sum is partial. */
  dueNowComplete: boolean;
  currency: string | null;
  coveredLineRefs: string[];
  uncoveredLineRefs: string[];
  complete: boolean;
}

export function splitSummary(selected: OfferView[], requestLines: PublishedLine[]): SplitSummary {
  const covered = new Set(selected.flatMap(coveredLineRefs));
  const allRefs = requestLines.map((line) => line.lineRef).filter((ref): ref is string => !!ref);
  const uncovered = allRefs.filter((ref) => !covered.has(ref));
  const combinedPrice = selected.reduce((sum, offer) => sum + (offer.priceTotal ?? 0), 0);
  const withFinancials = selected.filter((offer) => typeof offer.financials?.patientLiability === "number");
  const combinedDueNow = withFinancials.reduce((sum, offer) => sum + (offer.financials?.patientLiability ?? 0), 0);
  return {
    offerCount: selected.length,
    combinedPrice,
    combinedDueNow,
    dueNowComplete: withFinancials.length === selected.length && selected.length > 0,
    currency: selected.find((offer) => offer.currency)?.currency ?? null,
    coveredLineRefs: [...covered],
    uncoveredLineRefs: uncovered,
    complete: allRefs.length > 0 && uncovered.length === 0,
  };
}

// ── honest outcome copy (coded revalidation failures → actionable states) ────

export type OutcomeAction = "REFRESH_OFFERS" | "COMPLETE_PAYMENT" | "VIEW_STATUS" | "NONE";

export interface OutcomeCopy {
  title: string;
  body: string;
  action: OutcomeAction;
  tone: "success" | "pending" | "failure";
}

export const OUTCOME_COPY: Record<string, OutcomeCopy> = {
  COMMITTED: {
    title: "Offer confirmed",
    body: "Your choice is committed. The provider has been notified and fulfilment is starting.",
    action: "VIEW_STATUS",
    tone: "success",
  },
  AWAITING_PAYMENT: {
    title: "Almost there — payment needed",
    body: "Your selection is held while payment is completed. It stays reserved until the payment window ends; if payment does not arrive in time it is released honestly and you can choose again.",
    action: "COMPLETE_PAYMENT",
    tone: "pending",
  },
  OFFER_EXPIRED: {
    title: "This offer expired",
    body: "The offer's validity window ended before it could be confirmed. Nothing was charged. Refresh to see the offers that are still valid.",
    action: "REFRESH_OFFERS",
    tone: "failure",
  },
  OFFER_NOT_ACTIVE: {
    title: "This offer is no longer available",
    body: "The vendor withdrew or another change made this offer unavailable. Nothing was charged. Refresh to see current offers.",
    action: "REFRESH_OFFERS",
    tone: "failure",
  },
  STOCK_UNAVAILABLE: {
    title: "Stock ran out before confirmation",
    body: "The items could not be reserved — stock changed between the offer and your confirmation. Nothing was charged. Refresh to see updated offers.",
    action: "REFRESH_OFFERS",
    tone: "failure",
  },
  STOCK_SOURCE_UNVERIFIED: {
    title: "Stock could not be verified",
    body: "This vendor's stock could not be confirmed against the national inventory, so the commitment was refused to protect you. Refresh to choose a different offer.",
    action: "REFRESH_OFFERS",
    tone: "failure",
  },
  ELIGIBILITY_LAPSED: {
    title: "Vendor eligibility lapsed",
    body: "This vendor's authorisation changed between offering and your confirmation, so the commitment was refused. Refresh to choose a different offer.",
    action: "REFRESH_OFFERS",
    tone: "failure",
  },
  CONTROLLED_VENDOR_UNAUTHORISED: {
    title: "Vendor not authorised for controlled medicine",
    body: "This order contains controlled medicine and the vendor's authorisation could not be confirmed. Refresh to choose an authorised vendor.",
    action: "REFRESH_OFFERS",
    tone: "failure",
  },
  CLAIM_FAILED: {
    title: "Prescription claim failed",
    body: "The prescription could not be claimed for this fulfilment (it may already be claimed elsewhere). Your order is preserved — refresh to see its current state.",
    action: "REFRESH_OFFERS",
    tone: "failure",
  },
  PA_REQUIRED: {
    title: "Prior authorisation needed",
    body: "Your scheme requires prior authorisation before this can be committed. The request has been routed for approval; your order is preserved and you will be told the outcome. You can also refresh to choose a self-pay offer.",
    action: "REFRESH_OFFERS",
    tone: "pending",
  },
  NOT_COVERED: {
    title: "Not covered by your scheme",
    body: "Your scheme does not cover this offer, so the payer-covered commitment was refused. Refresh to compare self-pay options.",
    action: "REFRESH_OFFERS",
    tone: "failure",
  },
  FINANCIAL_UNVERIFIED: {
    title: "Coverage could not be verified",
    body: "The coverage check could not be completed right now, so the commitment was refused rather than guessing your cost. Nothing was charged. Try again shortly.",
    action: "REFRESH_OFFERS",
    tone: "failure",
  },
  PAYMENT_INTENT_FAILED: {
    title: "Payment could not be started",
    body: "The payment could not be initiated, so the selection was not committed. Nothing was charged. Refresh and try again.",
    action: "REFRESH_OFFERS",
    tone: "failure",
  },
  PAYMENT_FAILED: {
    title: "Payment failed",
    body: "The payment did not complete, so the selection was released. Your clinical order is untouched — refresh to choose again.",
    action: "REFRESH_OFFERS",
    tone: "failure",
  },
  PAYMENT_TIMEOUT: {
    title: "Payment window lapsed",
    body: "Payment did not arrive before the reservation expired, so the selection was released honestly. Refresh to choose again.",
    action: "REFRESH_OFFERS",
    tone: "failure",
  },
};

export function outcomeCopy(code: string | null | undefined): OutcomeCopy {
  if (code && OUTCOME_COPY[code]) return OUTCOME_COPY[code];
  return {
    title: "Selection did not complete",
    body: `The selection could not be committed${code ? ` (${code})` : ""}. Nothing was charged. Refresh to see the current offers.`,
    action: "REFRESH_OFFERS",
    tone: "failure",
  };
}

// ── stock-grade honesty (§8.5 truth grades) ──────────────────────────────────

export const STOCK_GRADE_COPY: Record<string, { label: string; detail: string }> = {
  VERIFIED: { label: "Stock verified", detail: "Confirmed against the national inventory (on-hand minus reserved)." },
  REPORTED: { label: "Stock reported", detail: "Declared by the vendor — not verified against the national inventory." },
};

export function stockGradeCopy(grade: string): { label: string; detail: string } {
  return STOCK_GRADE_COPY[grade] ?? { label: grade, detail: "Unrecognised stock grade — shown as received." };
}

/** The weakest grade across an offer's lines (REPORTED dominates VERIFIED). */
export function worstStockGrade(offer: OfferView): string | null {
  if (offer.lines.length === 0) return null;
  return offer.lines.some((line) => line.stockGrade !== "VERIFIED") ? "REPORTED" : "VERIFIED";
}

export function hasSubstitutions(offer: OfferView): boolean {
  return offer.lines.some((line) => line.substitutionProposed);
}
