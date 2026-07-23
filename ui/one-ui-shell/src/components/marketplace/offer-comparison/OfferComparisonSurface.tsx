"use client";

/**
 * OF-B7 — patient offer-comparison + selection surface (§8.6 / §11.5 / §11.6).
 *
 * Binding UX contract honoured here:
 *  - every valid offer shown, ranked with visible ranked-because labels;
 *  - declared, user-changeable sort (never price-alone default);
 *  - per-offer liability with "estimate — never final" wording (§10.5);
 *  - stock-truth grades labelled honestly (VERIFIED vs REPORTED);
 *  - honest completeness ("covers N of M items") + substitution indicators;
 *  - factual TTL (the true offer expiry — no invented countdown pressure);
 *  - decline/cancel as prominent as accept; no pre-selected anything;
 *  - split composition with §8.6.4 warnings + overlap prevention
 *    (controlled orders cannot split — §13.4);
 *  - coded revalidation failures rendered as honest actionable states;
 *  - AWAITING_PAYMENT is an honest held state with a REAL wallet payment leg.
 */

import { useMemo, useState, useEffect } from "react";
import Link from "next/link";
import {
  AlertTriangle,
  ArrowLeft,
  CheckCircle2,
  Clock,
  Loader2,
  MapPin,
  Package,
  RefreshCw,
  ShieldAlert,
  Truck,
  Wallet,
  XCircle,
} from "lucide-react";
import {
  useCancelMarketplaceRequest,
  useElectPathway,
  useMarketplaceOffers,
  useMarketplaceRequest,
  useSelectOffer,
  useSelectSplit,
  type PathwayElectionPayload,
  type SelectionView,
  type SplitSelectionView,
} from "@/hooks/queries/useMarketplaceOffers";
import { useWalletPay } from "@/hooks/queries/useMusheWallet";
import {
  DEFAULT_SORT_MODE,
  SORT_MODES,
  extractPublishedLines,
  hasSubstitutions,
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
  type PublishedLine,
  type RequestView,
  type SortMode,
} from "@/lib/marketplace/offer-comparison";
import { randomUUID } from "@/lib/uuid";

// ── small shared bits ────────────────────────────────────────────────────────

function fmtMoney(amount: number | null | undefined, currency: string | null | undefined): string | null {
  if (typeof amount !== "number") return null;
  return `${currency ?? "USD"} ${amount.toFixed(2)}`;
}

function errorCode(err: unknown): string {
  const candidate = (err as { error?: { code?: string } } | null)?.error?.code;
  return candidate || "UNKNOWN";
}

function errorMessage(err: unknown): string {
  const candidate = (err as { error?: { message?: string } } | null)?.error?.message;
  return candidate || "Something went wrong. Please try again.";
}

// ── stock grade badge (honest truth grades) ──────────────────────────────────

export function StockGradeBadge({ grade }: { grade: string }) {
  const copy = stockGradeCopy(grade);
  const verified = grade === "VERIFIED";
  return (
    <span
      title={copy.detail}
      data-testid="stock-grade-badge"
      className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-medium ${
        verified ? "bg-green-100 text-green-700" : "bg-amber-100 text-amber-700"
      }`}
    >
      {copy.label}
    </span>
  );
}

// ── financial block (estimate — never final, §10.5 binding) ──────────────────

export function FinancialsBlock({ offer }: { offer: OfferView }) {
  const fin = offer.financials;
  const gross = fmtMoney(offer.priceTotal, offer.currency);
  if (!fin) {
    return (
      <div className="text-xs text-muted-foreground" data-testid="financials-none">
        {gross && <p className="text-sm font-semibold text-foreground">{gross}</p>}
        <p>No cost estimate is available for this offer yet.</p>
      </div>
    );
  }
  const covered = fmtMoney(fin.coveredAmount, fin.currency ?? offer.currency);
  const liability = fmtMoney(fin.patientLiability, fin.currency ?? offer.currency);
  return (
    <div className="space-y-0.5" data-testid="financials-block">
      {liability && (
        <p className="text-sm font-semibold text-foreground" data-testid="due-now">
          You pay (estimate): {liability}
        </p>
      )}
      {gross && <p className="text-xs text-muted-foreground">Total price: {gross}</p>}
      {fin.status === "VERIFIED" && covered && (
        <p className="text-xs text-green-700">Covered by your scheme (estimate): {covered}</p>
      )}
      {fin.status === "SELF_PAY" && <p className="text-xs text-muted-foreground">Self-pay — no scheme coverage applied.</p>}
      {fin.status === "NOT_COVERED" && (
        <p className="text-xs text-amber-700">Your scheme does not cover this offer — the full amount is self-pay.</p>
      )}
      {fin.status === "UNVERIFIED" && (
        <p className="text-xs text-amber-700">
          Coverage could not be verified right now — the amount shown is unconfirmed.
        </p>
      )}
      {fin.paRequired && (
        <p className="text-xs text-amber-700">
          Prior authorisation required{fin.paStatus ? ` — status: ${fin.paStatus}` : ""}.
        </p>
      )}
      <p className="text-[10px] text-muted-foreground italic" data-testid="estimate-never-final">
        Estimate — never final. The final amount is confirmed when your payer adjudicates the claim.
      </p>
    </div>
  );
}

// ── one offer card ───────────────────────────────────────────────────────────

export function OfferCard({
  offer,
  requestLines,
  now,
  selectable,
  splitMode,
  splitChecked,
  splitBlocked,
  onToggleSplit,
  onChoose,
}: {
  offer: OfferView;
  requestLines: PublishedLine[];
  now: Date;
  selectable: boolean;
  splitMode: boolean;
  splitChecked: boolean;
  splitBlocked: boolean;
  onToggleSplit: () => void;
  onChoose: () => void;
}) {
  const ttl = ttlState(offer.ttlExpiresAt, now);
  const completeness = offerCompleteness(offer, requestLines);
  const grade = worstStockGrade(offer);
  const substitutions = hasSubstitutions(offer);
  const active = isSelectable(offer, now);

  return (
    <li
      className={`rounded-xl border bg-card p-4 space-y-3 ${active ? "border-border" : "border-border opacity-70"}`}
      data-testid="offer-card"
    >
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="space-y-1">
          <div className="flex flex-wrap gap-1" data-testid="ranked-because">
            {(offer.rankedBecause ?? []).map((code) => (
              <span
                key={code}
                className="inline-flex items-center rounded-full bg-primary-soft px-2 py-0.5 text-[10px] font-medium text-primary"
                title={rankedBecauseLabel(code)}
              >
                {rankedBecauseLabel(code)}
              </span>
            ))}
          </div>
          <p className="text-xs text-muted-foreground">Provider {offer.vendorId}</p>
        </div>
        <div className="text-right">
          <FinancialsBlock offer={offer} />
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
        {grade && <StockGradeBadge grade={grade} />}
        <span
          className={completeness.complete ? "text-green-700" : "text-amber-700"}
          data-testid="completeness"
        >
          {completeness.complete
            ? `Covers all ${completeness.total} item${completeness.total === 1 ? "" : "s"}`
            : `Covers ${completeness.covered} of ${completeness.total} items`}
        </span>
        {substitutions && (
          <span className="text-amber-700" data-testid="substitution-indicator">
            Substitution proposed — shown per line, never silent
          </span>
        )}
        {typeof offer.fulfillmentWindowHours === "number" && (
          <span className="inline-flex items-center gap-1">
            <Clock className="h-3 w-3" /> Ready within {offer.fulfillmentWindowHours}h
          </span>
        )}
        {offer.homeCollection && (
          <span className="inline-flex items-center gap-1">
            <MapPin className="h-3 w-3" /> Home sample collection offered
          </span>
        )}
      </div>

      {offer.lines.length > 0 && (
        <ul className="space-y-1 border-t border-border pt-2">
          {offer.lines.map((line) => (
            <li key={line.offerLineId} className="flex items-center justify-between gap-2 text-xs">
              <span className="text-foreground">
                {line.itemCode} × {line.quantity}
                {line.substitutionProposed && (
                  <span className="ml-1 text-amber-700">(substitute proposed)</span>
                )}
              </span>
              <span className="flex items-center gap-2 text-muted-foreground">
                {fmtMoney(line.unitPrice, offer.currency) ?? ""}
                <StockGradeBadge grade={line.stockGrade} />
              </span>
            </li>
          ))}
        </ul>
      )}

      <div className="flex flex-wrap items-center justify-between gap-2 border-t border-border pt-2">
        <span className="text-xs text-muted-foreground" data-testid="ttl">
          {ttl.expired
            ? "Offer expired"
            : ttl.minutesLeft != null
              ? `Offer valid until ${new Date(offer.ttlExpiresAt!).toLocaleString([], { dateStyle: "medium", timeStyle: "short" })} (${ttl.minutesLeft} min left)`
              : "No expiry declared"}
        </span>
        {splitMode ? (
          <label
            className={`flex items-center gap-2 text-sm ${splitBlocked && !splitChecked ? "opacity-50" : ""}`}
            title={
              splitBlocked && !splitChecked
                ? "This offer covers an item already covered by your current picks."
                : undefined
            }
          >
            <input
              type="checkbox"
              checked={splitChecked}
              disabled={!active || (splitBlocked && !splitChecked)}
              onChange={onToggleSplit}
              data-testid="split-checkbox"
            />
            Include in combination
          </label>
        ) : (
          <button
            type="button"
            onClick={onChoose}
            disabled={!active || !selectable}
            className="rounded-lg bg-primary px-4 py-1.5 text-sm font-medium text-primary-foreground disabled:opacity-50"
            data-testid="choose-offer"
          >
            {active ? "Choose this offer" : "No longer available"}
          </button>
        )}
      </div>
    </li>
  );
}

// ── pathway election (OF-B17 — pickup vs delivery, before selection) ─────────

function PathwayStep({
  requestId,
  elected,
  onElected,
}: {
  requestId: string;
  elected: PathwayElectionPayload | null;
  onElected: (payload: PathwayElectionPayload | null) => void;
}) {
  const electPathway = useElectPathway();
  const [mode, setMode] = useState<"PICKUP" | "DELIVERY">(elected?.pathway ?? "PICKUP");
  const [name, setName] = useState(elected?.deliveryName ?? "");
  const [phone, setPhone] = useState(elected?.deliveryPhone ?? "");
  const [address, setAddress] = useState(elected?.deliveryAddress ?? "");
  const [error, setError] = useState<string | null>(null);

  const deliveryIncomplete = mode === "DELIVERY" && (!name.trim() || !phone.trim() || !address.trim());

  function confirm() {
    setError(null);
    const payload: PathwayElectionPayload =
      mode === "DELIVERY"
        ? { pathway: "DELIVERY", deliveryName: name.trim(), deliveryPhone: phone.trim(), deliveryAddress: address.trim() }
        : { pathway: "PICKUP" };
    electPathway.mutate(
      { requestId, ...payload },
      {
        onSuccess: () => onElected(payload),
        onError: (err) => setError(errorMessage(err)),
      },
    );
  }

  if (elected) {
    return (
      <div className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-border bg-card p-3 text-sm" data-testid="pathway-summary">
        <span className="inline-flex items-center gap-2">
          {elected.pathway === "DELIVERY" ? <Truck className="h-4 w-4 text-primary" /> : <Package className="h-4 w-4 text-primary" />}
          {elected.pathway === "DELIVERY" ? `Delivery to ${elected.deliveryAddress}` : "Pickup — you collect from the provider"}
        </span>
        <button
          type="button"
          className="text-xs text-primary underline"
          onClick={() => onElected(null)}
          data-testid="change-pathway"
        >
          Change
        </button>
      </div>
    );
  }

  return (
    <div className="rounded-xl border border-border bg-card p-4 space-y-3" data-testid="pathway-step">
      <h2 className="text-sm font-semibold text-foreground">How do you want to receive this order?</h2>
      <p className="text-xs text-muted-foreground">
        Choose before selecting an offer. Delivery details go only to the courier at dispatch — never to competing providers.
      </p>
      <div className="flex gap-2">
        <button
          type="button"
          onClick={() => setMode("PICKUP")}
          className={`flex-1 rounded-lg border p-3 text-sm ${mode === "PICKUP" ? "border-primary bg-primary-soft" : "border-border"}`}
          data-testid="pathway-pickup"
        >
          <Package className="mx-auto mb-1 h-4 w-4" /> Pickup
        </button>
        <button
          type="button"
          onClick={() => setMode("DELIVERY")}
          className={`flex-1 rounded-lg border p-3 text-sm ${mode === "DELIVERY" ? "border-primary bg-primary-soft" : "border-border"}`}
          data-testid="pathway-delivery"
        >
          <Truck className="mx-auto mb-1 h-4 w-4" /> Delivery
        </button>
      </div>
      {mode === "DELIVERY" && (
        <div className="space-y-2" data-testid="delivery-form">
          <input
            className="w-full rounded-lg border border-border bg-background px-3 py-2 text-sm"
            placeholder="Recipient name"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
          <input
            className="w-full rounded-lg border border-border bg-background px-3 py-2 text-sm"
            placeholder="Phone number"
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
          />
          <textarea
            className="w-full rounded-lg border border-border bg-background px-3 py-2 text-sm"
            placeholder="Delivery address"
            rows={2}
            value={address}
            onChange={(e) => setAddress(e.target.value)}
          />
        </div>
      )}
      {error && <p className="text-xs text-red-600">{error}</p>}
      <button
        type="button"
        onClick={confirm}
        disabled={deliveryIncomplete || electPathway.isPending}
        className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground disabled:opacity-50"
        data-testid="confirm-pathway"
      >
        {electPathway.isPending ? "Saving..." : "Confirm and see offers"}
      </button>
    </div>
  );
}

// ── outcome panel (honest coded states) ──────────────────────────────────────

function OutcomePanel({
  selection,
  split,
  onRefreshOffers,
  onDone,
}: {
  selection: SelectionView | null;
  split: SplitSelectionView | null;
  onRefreshOffers: () => void;
  onDone: () => void;
}) {
  const walletPay = useWalletPay();
  const [paySubmitted, setPaySubmitted] = useState(false);
  const [payError, setPayError] = useState<string | null>(null);

  const code = split ? split.outcome : selection?.outcomeCode;
  const copy = outcomeCopy(code);
  const Icon = copy.tone === "success" ? CheckCircle2 : copy.tone === "pending" ? Clock : XCircle;
  const shortfall = selection?.shortfallAmount ?? null;
  const shortfallCurrency = selection?.shortfallCurrency ?? null;
  const intentId = selection?.paymentIntentId ?? null;

  function payFromWallet() {
    if (!intentId || typeof shortfall !== "number") return;
    setPayError(null);
    walletPay.mutate(
      {
        method: "MUSHE_WALLET",
        amount: shortfall,
        currency: shortfallCurrency ?? undefined,
        reference: intentId,
        description: `Marketplace selection ${selection?.selectionId ?? ""}`.trim(),
      },
      {
        onSuccess: () => setPaySubmitted(true),
        onError: (err) => setPayError(errorMessage(err)),
      },
    );
  }

  return (
    <div className="rounded-xl border border-border bg-card p-5 space-y-3" data-testid="outcome-panel" data-outcome={code ?? "UNKNOWN"}>
      <div className="flex items-center gap-2">
        <Icon
          className={`h-5 w-5 ${copy.tone === "success" ? "text-green-600" : copy.tone === "pending" ? "text-amber-600" : "text-red-600"}`}
        />
        <h2 className="text-base font-semibold text-foreground">{copy.title}</h2>
      </div>
      <p className="text-sm text-muted-foreground">{copy.body}</p>

      {split && (
        <ul className="space-y-1 border-t border-border pt-2 text-xs" data-testid="split-members">
          {split.members.map((member) => {
            const memberCopy = outcomeCopy(member.outcomeCode);
            return (
              <li key={member.offerId} className="flex items-center justify-between gap-2">
                <span>Offer {member.offerId}</span>
                <span className={member.committed ? "text-green-700" : "text-amber-700"}>{memberCopy.title}</span>
              </li>
            );
          })}
          {!split.coverage.complete && (
            <li className="text-amber-700">
              Not all items are covered by the committed offers — the remaining lines will be re-offered.
            </li>
          )}
        </ul>
      )}

      {copy.action === "COMPLETE_PAYMENT" && (
        <div className="space-y-2 border-t border-border pt-3" data-testid="awaiting-payment">
          {typeof shortfall === "number" && (
            <p className="text-sm font-semibold text-foreground">
              Amount due: {fmtMoney(shortfall, shortfallCurrency)}
            </p>
          )}
          {intentId && !paySubmitted && (
            <button
              type="button"
              onClick={payFromWallet}
              disabled={walletPay.isPending || typeof shortfall !== "number"}
              className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground disabled:opacity-50"
              data-testid="pay-from-wallet"
            >
              <Wallet className="h-4 w-4" /> {walletPay.isPending ? "Paying..." : "Pay from Mushe wallet"}
            </button>
          )}
          {paySubmitted && (
            <p className="text-sm text-green-700">
              Payment submitted. Your selection completes automatically once the payment settles — check back here or in
              your orders.
            </p>
          )}
          {payError && <p className="text-xs text-red-600">{payError}</p>}
          {!intentId && (
            <p className="text-xs text-muted-foreground">
              No payment reference was returned — refresh to see the current state of your selection.
            </p>
          )}
        </div>
      )}

      <div className="flex gap-2 pt-1">
        {copy.action === "REFRESH_OFFERS" && (
          <button
            type="button"
            onClick={onRefreshOffers}
            className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground"
            data-testid="refresh-offers"
          >
            <RefreshCw className="h-4 w-4" /> Refresh offers
          </button>
        )}
        <button
          type="button"
          onClick={onDone}
          className="rounded-lg border border-border px-4 py-2 text-sm"
          data-testid="outcome-done"
        >
          {copy.tone === "success" ? "Done" : "Back to offers"}
        </button>
      </div>
    </div>
  );
}

// ── the surface ──────────────────────────────────────────────────────────────

const CANCELLABLE_STATUSES = new Set(["DRAFT", "PUBLISHED", "COLLECTING_OFFERS", "OFFERS_AVAILABLE"]);

export function OfferComparisonSurface({ requestId }: { requestId: string }) {
  const requestQ = useMarketplaceRequest(requestId);
  const offersQ = useMarketplaceOffers(requestId);
  const selectOffer = useSelectOffer();
  const selectSplit = useSelectSplit();
  const cancelRequest = useCancelMarketplaceRequest();

  const [sortMode, setSortMode] = useState<SortMode>(DEFAULT_SORT_MODE);
  const [pathway, setPathway] = useState<PathwayElectionPayload | null>(null);
  const [splitMode, setSplitMode] = useState(false);
  const [splitPicks, setSplitPicks] = useState<string[]>([]);
  const [confirming, setConfirming] = useState<{ offerIds: string[]; idempotencyKey: string } | null>(null);
  const [selectionOutcome, setSelectionOutcome] = useState<SelectionView | null>(null);
  const [splitOutcome, setSplitOutcome] = useState<SplitSelectionView | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [now, setNow] = useState(() => new Date());

  // Factual TTL tick — refresh the displayed remaining validity every 30s.
  useEffect(() => {
    const timer = setInterval(() => setNow(new Date()), 30_000);
    return () => clearInterval(timer);
  }, []);

  const request = useMemo(() => unwrapFlowData<RequestView>(requestQ.data), [requestQ.data]);
  const offers = useMemo(() => unwrapFlowData<OfferView[]>(offersQ.data) ?? [], [offersQ.data]);
  const requestLines = useMemo(() => extractPublishedLines(request), [request]);
  const sorted = useMemo(() => sortOffers(offers, sortMode), [offers, sortMode]);
  const selectedOffers = useMemo(
    () => offers.filter((offer) => splitPicks.includes(offer.offerId)),
    [offers, splitPicks],
  );
  const combined = useMemo(() => splitSummary(selectedOffers, requestLines), [selectedOffers, requestLines]);
  const activeSort = SORT_MODES.find((m) => m.id === sortMode) ?? SORT_MODES[0];
  const splitAllowed = !request?.controlled && offers.length > 1;
  const showOutcome = selectionOutcome != null || splitOutcome != null;

  function refreshOffers() {
    setSelectionOutcome(null);
    setSplitOutcome(null);
    setConfirming(null);
    setActionError(null);
    void offersQ.refetch();
    void requestQ.refetch();
  }

  function beginConfirm(offerIds: string[]) {
    // ONE idempotency key per selection attempt (RC-1): a retried confirm tap
    // replays the same commit rather than double-committing.
    setActionError(null);
    setConfirming({ offerIds, idempotencyKey: randomUUID() });
  }

  function runConfirm() {
    if (!confirming) return;
    setActionError(null);
    if (confirming.offerIds.length === 1) {
      selectOffer.mutate(
        { requestId, offerId: confirming.offerIds[0], idempotencyKey: confirming.idempotencyKey },
        {
          onSuccess: (envelope) => {
            const view = unwrapFlowData<SelectionView>(envelope);
            if (view) {
              setSelectionOutcome(view);
              setConfirming(null);
            } else {
              setActionError("The marketplace returned no selection result — refresh to see the current state.");
            }
          },
          onError: (err) => setActionError(`${errorMessage(err)} (${errorCode(err)})`),
        },
      );
    } else {
      selectSplit.mutate(
        { requestId, offerIds: confirming.offerIds, idempotencyKey: confirming.idempotencyKey },
        {
          onSuccess: (envelope) => {
            const view = unwrapFlowData<SplitSelectionView>(envelope);
            if (view) {
              setSplitOutcome(view);
              setConfirming(null);
              setSplitPicks([]);
              setSplitMode(false);
            } else {
              setActionError("The marketplace returned no split result — refresh to see the current state.");
            }
          },
          onError: (err) => setActionError(`${errorMessage(err)} (${errorCode(err)})`),
        },
      );
    }
  }

  // ── loading / error / not-found states ─────────────────────────────────────

  if (requestQ.isLoading || offersQ.isLoading) {
    return (
      <div className="flex items-center justify-center py-16 text-sm text-muted-foreground">
        <Loader2 className="mr-2 h-5 w-5 animate-spin" /> Loading your offers...
      </div>
    );
  }

  if (requestQ.isError || (!request && !requestQ.isLoading)) {
    return (
      <div className="max-w-lg mx-auto py-12 text-center space-y-3" data-testid="request-error">
        <ShieldAlert className="mx-auto h-10 w-10 text-muted-foreground" />
        <h2 className="text-base font-semibold text-foreground">Could not load this order&apos;s offers</h2>
        <p className="text-sm text-muted-foreground">
          {requestQ.isError
            ? `${errorMessage(requestQ.error)} (${errorCode(requestQ.error)})`
            : "This marketplace request was not found."}
        </p>
        <div className="flex justify-center gap-2">
          <button
            type="button"
            onClick={() => {
              void requestQ.refetch();
              void offersQ.refetch();
            }}
            className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground"
          >
            <RefreshCw className="h-4 w-4" /> Try again
          </button>
          <Link href="/marketplace/orders" className="rounded-lg border border-border px-4 py-2 text-sm">
            Back to my orders
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-3xl space-y-4">
      <Link
        href="/marketplace/orders"
        className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="h-4 w-4" /> Back to my orders
      </Link>

      {request?.controlled && (
        <div className="flex items-start gap-2 rounded-xl border border-amber-300 bg-amber-50 p-3 text-sm text-amber-800" data-testid="controlled-banner">
          <ShieldAlert className="mt-0.5 h-4 w-4 shrink-0" />
          <span>
            This order contains controlled medicine. It must be fulfilled by one authorised provider — combining
            providers is not permitted for controlled items.
          </span>
        </div>
      )}

      {showOutcome ? (
        <OutcomePanel
          selection={selectionOutcome}
          split={splitOutcome}
          onRefreshOffers={refreshOffers}
          onDone={refreshOffers}
        />
      ) : (
        <>
          <PathwayStep
            requestId={requestId}
            elected={pathway}
            onElected={(payload) => setPathway(payload ?? null)}
          />

          {offers.length === 0 ? (
            <div className="max-w-lg mx-auto py-10 text-center space-y-2" data-testid="offers-empty">
              <Package className="mx-auto h-10 w-10 text-muted-foreground" />
              <h2 className="text-base font-semibold text-foreground">No offers yet</h2>
              <p className="text-sm text-muted-foreground">
                {request?.status === "COLLECTING_OFFERS" || request?.status === "PUBLISHED"
                  ? `Providers are preparing offers for your order${
                      request?.offerWindowEndsAt
                        ? ` — the offer window closes ${new Date(request.offerWindowEndsAt).toLocaleString([], { dateStyle: "medium", timeStyle: "short" })}`
                        : ""
                    }. Check back soon.`
                  : request?.status === "CANCELLED"
                    ? "This request was cancelled, so no offers are collected."
                    : "There are no active offers on this request right now."}
              </p>
              <button
                type="button"
                onClick={refreshOffers}
                className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground"
              >
                <RefreshCw className="h-4 w-4" /> Refresh
              </button>
            </div>
          ) : (
            <>
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div>
                  <label className="text-xs text-muted-foreground" htmlFor="offer-sort">
                    Sort order (you can change it)
                  </label>
                  <select
                    id="offer-sort"
                    value={sortMode}
                    onChange={(e) => setSortMode(e.target.value as SortMode)}
                    className="ml-2 rounded-lg border border-border bg-background px-2 py-1 text-sm"
                    data-testid="sort-select"
                  >
                    {SORT_MODES.map((m) => (
                      <option key={m.id} value={m.id}>
                        {m.label}
                      </option>
                    ))}
                  </select>
                </div>
                {splitAllowed && (
                  <label className="flex items-center gap-2 text-sm">
                    <input
                      type="checkbox"
                      checked={splitMode}
                      onChange={(e) => {
                        setSplitMode(e.target.checked);
                        setSplitPicks([]);
                      }}
                      data-testid="split-mode-toggle"
                    />
                    Combine offers from more than one provider
                  </label>
                )}
              </div>
              <p className="text-xs text-muted-foreground" data-testid="sort-rationale">
                {activeSort.rationale}
              </p>

              {splitMode && (
                <div className="rounded-xl border border-amber-300 bg-amber-50 p-3 text-xs text-amber-800 space-y-1" data-testid="split-warnings">
                  <p className="font-medium">Before combining providers, know that splitting means:</p>
                  <ul className="list-disc pl-4 space-y-0.5">
                    <li>separate payments to each provider;</li>
                    <li>multiple pickups or deliveries (and any delivery fee applies per provider);</li>
                    <li>parts of your order may be ready on different days;</li>
                    <li>each provider checks only its own items for interactions.</li>
                  </ul>
                </div>
              )}

              <ul className="space-y-3">
                {sorted.map((offer) => (
                  <OfferCard
                    key={offer.offerId}
                    offer={offer}
                    requestLines={requestLines}
                    now={now}
                    selectable={pathway != null}
                    splitMode={splitMode}
                    splitChecked={splitPicks.includes(offer.offerId)}
                    splitBlocked={overlapsSelection(offer, selectedOffers)}
                    onToggleSplit={() =>
                      setSplitPicks((picks) =>
                        picks.includes(offer.offerId)
                          ? picks.filter((id) => id !== offer.offerId)
                          : [...picks, offer.offerId],
                      )
                    }
                    onChoose={() => beginConfirm([offer.offerId])}
                  />
                ))}
              </ul>

              {!pathway && (
                <p className="text-xs text-muted-foreground" data-testid="pathway-gate-hint">
                  Confirm pickup or delivery above to enable selection.
                </p>
              )}

              {splitMode && (
                <div className="sticky bottom-2 rounded-xl border border-border bg-card p-3 shadow-sm space-y-2" data-testid="split-summary-bar">
                  <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
                    <span>
                      {combined.offerCount} offer{combined.offerCount === 1 ? "" : "s"} picked —{" "}
                      {combined.complete
                        ? "covers your whole order"
                        : `${combined.uncoveredLineRefs.length} item${combined.uncoveredLineRefs.length === 1 ? "" : "s"} still uncovered`}
                    </span>
                    <span className="font-semibold">
                      {combined.dueNowComplete
                        ? `You pay (estimate): ${fmtMoney(combined.combinedDueNow, combined.currency)}`
                        : `Combined price: ${fmtMoney(combined.combinedPrice, combined.currency) ?? "—"}`}
                    </span>
                  </div>
                  {combined.dueNowComplete && (
                    <p className="text-[10px] text-muted-foreground italic">
                      Estimate — never final. Each provider&apos;s amount is confirmed at adjudication.
                    </p>
                  )}
                  <button
                    type="button"
                    onClick={() => beginConfirm(splitPicks)}
                    disabled={combined.offerCount < 2 || !pathway}
                    className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground disabled:opacity-50"
                    data-testid="confirm-split"
                  >
                    Review combination
                  </button>
                </div>
              )}
            </>
          )}

          {confirming && (
            <div className="rounded-xl border border-primary/40 bg-card p-4 space-y-3" data-testid="confirm-panel">
              <h2 className="text-sm font-semibold text-foreground">
                {confirming.offerIds.length === 1
                  ? "Confirm this offer?"
                  : `Confirm this combination of ${confirming.offerIds.length} offers?`}
              </h2>
              <p className="text-xs text-muted-foreground">
                The marketplace re-checks eligibility, stock and price at this moment — if anything changed you will be
                told honestly and nothing is charged.
              </p>
              {actionError && (
                <p className="flex items-center gap-1 text-xs text-red-600" data-testid="confirm-error">
                  <AlertTriangle className="h-3 w-3" /> {actionError}
                </p>
              )}
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={runConfirm}
                  disabled={selectOffer.isPending || selectSplit.isPending}
                  className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground disabled:opacity-50"
                  data-testid="confirm-selection"
                >
                  {selectOffer.isPending || selectSplit.isPending ? "Confirming..." : "Yes, confirm"}
                </button>
                <button
                  type="button"
                  onClick={() => setConfirming(null)}
                  className="rounded-lg border border-border px-4 py-2 text-sm"
                  data-testid="cancel-confirm"
                >
                  Keep comparing
                </button>
              </div>
            </div>
          )}

          {request && CANCELLABLE_STATUSES.has(request.status) && (
            <div className="border-t border-border pt-3">
              <button
                type="button"
                onClick={() => {
                  cancelRequest.mutate(
                    { requestId },
                    { onError: (err) => setActionError(`${errorMessage(err)} (${errorCode(err)})`) },
                  );
                }}
                disabled={cancelRequest.isPending}
                className="text-sm text-red-600 underline disabled:opacity-50"
                data-testid="cancel-request"
              >
                {cancelRequest.isPending ? "Cancelling..." : "Cancel this marketplace request"}
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
