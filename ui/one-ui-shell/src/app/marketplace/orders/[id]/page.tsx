"use client";
import { JsonApiDataTable } from "@/components/common/JsonApiDataTable";
import { FINANCE_MUTATION_COLUMNS, FINANCE_TRACKING_COLUMNS } from "@/lib/json-api/finance-table-columns";
import { useParams } from "next/navigation";
import Link from "next/link";
import { useMemo, useState } from "react";
import { AlertTriangle, ArrowLeft, CheckCircle2, Clock, CreditCard, Loader2, Package, Ticket, Truck, RefreshCw, Undo2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useMarketplaceOrder } from "@/hooks/queries/useMarketplace";
import { unwrapCommerceData, useCommerceOrder, useCommerceOrderAction, useCommerceOrderTracking } from "@/hooks/queries/useCommerceFlow";
import { useCommerceIssuePickup } from "@/hooks/queries/useCommercePickup";
import { CommerceDeliveryMapPanel } from "@/components/maps/CommerceDeliveryMapPanel";

// Legacy /internal/v1/marketplace fallback timeline (procurement shape).
const STATUS_TIMELINE = ["PENDING", "APPROVED", "SHIPPED", "DELIVERED"];
const STATUS_ICON: Record<string, typeof Clock> = {
  PENDING: Clock,
  APPROVED: CheckCircle2,
  SHIPPED: Truck,
  DELIVERED: CheckCircle2,
};

// Commerce (msika-flow) lifecycle — extended M7: PENDING→PAID→OUT_FOR_DELIVERY→DELIVERED/COMPLETED (+ REFUNDED branch).
const COMMERCE_TIMELINE = ["PENDING", "PRICED", "PAID", "OUT_FOR_DELIVERY", "DELIVERED", "COMPLETED"] as const;
const COMMERCE_STAGE_ALIASES: Record<string, string> = {
  DRAFT: "PENDING",
  VALIDATED: "PENDING",
  PENDING_PAYMENT: "PRICED",
  IN_PROGRESS: "PAID",
  READY: "PAID",
  COLLECTED: "DELIVERED",
  ATTENDED: "DELIVERED",
};
const PAYABLE_STATUSES = new Set(["DRAFT", "PENDING", "VALIDATED", "PRICED", "PENDING_PAYMENT"]);
const CANCELLABLE_STATUSES = new Set(["DRAFT", "PENDING", "VALIDATED", "PRICED", "PENDING_PAYMENT", "PAID"]);

function findFirstString(payload: unknown, keys: string[], depth = 0): string | undefined {
  if (payload == null || typeof payload !== "object" || depth > 5) return undefined;
  if (Array.isArray(payload)) {
    for (const entry of payload) {
      const found = findFirstString(entry, keys, depth + 1);
      if (found) return found;
    }
    return undefined;
  }
  const record = payload as Record<string, unknown>;
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "string" && value) return value;
  }
  for (const value of Object.values(record)) {
    if (value && typeof value === "object") {
      const found = findFirstString(value, keys, depth + 1);
      if (found) return found;
    }
  }
  return undefined;
}

export default function OrderDetailPage() {
  const params = useParams();
  const id = params.id as string;
  const commerceQ = useCommerceOrder(id);
  const trackingQ = useCommerceOrderTracking(id);
  const commerceAction = useCommerceOrderAction();
  const issuePickup = useCommerceIssuePickup();
  const [lastActionResponse, setLastActionResponse] = useState<unknown>(null);

  const marketplaceQ = useMarketplaceOrder(id);
  const marketplaceOrder = marketplaceQ.data;

  const commerceOrder = useMemo(() => (commerceQ.data && typeof commerceQ.data === "object" ? commerceQ.data as Record<string, unknown> : null), [commerceQ.data]);
  const commerceBody = useMemo(() => unwrapCommerceData(commerceQ.data), [commerceQ.data]);
  const commerceStatus = typeof commerceBody?.status === "string" ? commerceBody.status : undefined;
  const commerceStage = commerceStatus ? (COMMERCE_STAGE_ALIASES[commerceStatus] ?? commerceStatus) : undefined;
  const commerceStageIndex = commerceStage ? COMMERCE_TIMELINE.indexOf(commerceStage as (typeof COMMERCE_TIMELINE)[number]) : -1;
  const refunded = commerceStatus === "REFUNDED";

  const showPayNow = commerceStatus != null && PAYABLE_STATUSES.has(commerceStatus);
  // Unknown status (older payloads) keeps cancel available; msika-flow guards the FSM server-side.
  const showCancel = commerceStatus == null || CANCELLABLE_STATUSES.has(commerceStatus);

  const commerceLines = Array.isArray(commerceBody?.lines) ? (commerceBody?.lines as Array<Record<string, unknown>>) : [];
  const isPickupOrder = commerceLines.some(
    (line) => String(line.fulfillmentMode ?? line.fulfillment_mode ?? "").toUpperCase() === "PICKUP",
  );

  // Nhume dispatch seam — delivery plans carry nhume_delivery_id after the M4 seam.
  const nhumeDeliveryId = findFirstString({ order: commerceBody, tracking: trackingQ.data }, ["nhumeDeliveryId", "nhume_delivery_id"]);
  const nhumeStatus = findFirstString(trackingQ.data, ["deliveryStatus", "dispatchStatus", "planStatus"]);
  const nhumeProofRef = findFirstString({ order: commerceBody, tracking: trackingQ.data }, ["proofRef", "proof_ref", "proofId"]);

  const shouldShowMarketplace = commerceQ.isError;

  const currentIndex = marketplaceOrder ? STATUS_TIMELINE.indexOf(marketplaceOrder.status) : -1;

  return (
    <AppLayout>
      <PageShell title="Order Details" subtitle="View order information, line items, and fulfilment status.">
        <div className="mb-4">
          <Link href="/marketplace/orders" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
            <ArrowLeft className="h-4 w-4" /> Back to orders
          </Link>
        </div>

        {commerceQ.isLoading ? (
          <div className="flex items-center justify-center py-16 text-sm text-muted-foreground">
            <Loader2 className="mr-2 h-5 w-5 animate-spin" /> Loading order...
          </div>
        ) : commerceOrder ? (
          <div className="max-w-3xl space-y-6">
            <div className="rounded-2xl border border-border bg-card p-5 shadow-sm">
              <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                <div>
                  <h3 className="text-lg font-semibold text-foreground">Commerce order</h3>
                  <p className="mt-1 text-sm text-muted-foreground">
                    Backed by <code className="text-xs">GET /internal/v1/commerce/orders/{`{orderId}`}</code>.
                  </p>
                  <p className="mt-1 text-xs text-muted-foreground break-all">
                    Order ID: {id}
                    {commerceStatus ? <span className="ml-2 rounded-full border border-border px-2 py-0.5 text-[11px] font-semibold">{commerceStatus}</span> : null}
                  </p>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  {showPayNow ? (
                    <Link
                      href={`/marketplace/orders/${encodeURIComponent(id)}/pay`}
                      className="inline-flex items-center gap-1 rounded-lg bg-primary px-3 py-1.5 text-xs font-semibold text-primary-foreground"
                    >
                      <CreditCard className="h-3.5 w-3.5" /> Pay now
                    </Link>
                  ) : null}
                  {(["validate", "price"] as const).map((action) => (
                    <button
                      key={action}
                      type="button"
                      disabled={commerceAction.isPending}
                      onClick={() => {
                        setLastActionResponse(null);
                        commerceAction.mutate(
                          { orderId: id, action },
                          { onSuccess: (res) => setLastActionResponse(res) }
                        );
                      }}
                      className="rounded-lg border border-border bg-card px-3 py-1.5 text-xs font-medium text-foreground hover:bg-background disabled:opacity-50"
                    >
                      {action.toUpperCase()}
                    </button>
                  ))}
                  {showCancel ? (
                    <button
                      type="button"
                      disabled={commerceAction.isPending}
                      onClick={() => {
                        setLastActionResponse(null);
                        commerceAction.mutate(
                          { orderId: id, action: "cancel" },
                          { onSuccess: (res) => setLastActionResponse(res) }
                        );
                      }}
                      className="rounded-lg border border-danger/35 bg-card px-3 py-1.5 text-xs font-medium text-danger hover:bg-danger-soft disabled:opacity-50"
                    >
                      CANCEL
                    </button>
                  ) : null}
                  <button
                    type="button"
                    onClick={() => void trackingQ.refetch()}
                    className="inline-flex items-center gap-1 rounded-lg border border-border bg-card px-3 py-1.5 text-xs font-medium text-foreground hover:bg-background"
                  >
                    <RefreshCw className="h-3.5 w-3.5" /> Refresh tracking
                  </button>
                </div>
              </div>
            </div>

            {/* Commerce lifecycle timeline */}
            <div className="rounded-2xl border border-border bg-card p-5 shadow-sm">
              <h3 className="text-lg font-semibold text-foreground">Order lifecycle</h3>
              {refunded ? (
                <p className="mt-3 flex items-center gap-2 rounded-lg border border-warning/35 bg-warning-soft px-3 py-2 text-sm text-warning-foreground">
                  <Undo2 className="h-4 w-4" /> This order was refunded.
                </p>
              ) : commerceStatus === "CANCELLED" || commerceStatus === "FAILED" ? (
                <p className="mt-3 flex items-center gap-2 rounded-lg border border-danger/28 bg-danger-soft px-3 py-2 text-sm text-red-800">
                  <AlertTriangle className="h-4 w-4" /> This order ended as {commerceStatus}.
                </p>
              ) : (
                <div className="mt-4 flex flex-wrap items-center gap-2">
                  {COMMERCE_TIMELINE.map((step, index) => {
                    const completed = commerceStageIndex >= 0 && index <= commerceStageIndex;
                    const current = index === commerceStageIndex;
                    return (
                      <div key={step} className="flex items-center gap-2">
                        <span
                          className={`rounded-full px-2.5 py-1 text-[11px] font-semibold ${completed ? "bg-emerald-100 text-emerald-800" : "bg-neutral-100 text-muted-foreground"} ${current ? "ring-2 ring-emerald-300" : ""}`}
                        >
                          {step.replace(/_/g, " ")}
                        </span>
                        {index < COMMERCE_TIMELINE.length - 1 ? <span className="text-muted-foreground">→</span> : null}
                      </div>
                    );
                  })}
                </div>
              )}
            </div>

            {/* Nhume delivery panel (dispatch SoR) */}
            {nhumeDeliveryId || nhumeStatus || nhumeProofRef ? (
              <div className="rounded-2xl border border-border bg-card p-5 shadow-sm">
                <h3 className="flex items-center gap-2 text-lg font-semibold text-foreground">
                  <Truck className="h-5 w-5 text-primary" /> Delivery (Nhume dispatch)
                </h3>
                <dl className="mt-3 grid gap-3 text-sm sm:grid-cols-3">
                  <div>
                    <dt className="text-muted-foreground">Mission ref</dt>
                    <dd className="font-mono text-xs text-foreground break-all">{nhumeDeliveryId ?? "—"}</dd>
                  </div>
                  <div>
                    <dt className="text-muted-foreground">Dispatch status</dt>
                    <dd className="font-medium text-foreground">{nhumeStatus ?? "—"}</dd>
                  </div>
                  <div>
                    <dt className="text-muted-foreground">Proof</dt>
                    <dd className="font-mono text-xs text-foreground break-all">{nhumeProofRef ?? "Not captured yet"}</dd>
                  </div>
                </dl>
                <p className="mt-3 text-xs text-muted-foreground">
                  Dispatch truth lives in Nhume; msika-flow keeps order-side custody and handoff records.
                </p>
              </div>
            ) : null}

            {/* Pickup slip block for pickup orders */}
            {isPickupOrder ? (
              <div className="rounded-2xl border border-border bg-card p-5 shadow-sm">
                <h3 className="flex items-center gap-2 text-lg font-semibold text-foreground">
                  <Ticket className="h-5 w-5 text-primary" /> Pickup handoff
                </h3>
                <p className="mt-1 text-sm text-muted-foreground">
                  This order is collected in person. Issue a pickup token once it is ready, then present it at the counter.
                </p>
                <div className="mt-3 flex flex-wrap items-center gap-3">
                  <button
                    type="button"
                    disabled={issuePickup.isPending}
                    onClick={() => issuePickup.mutate(id)}
                    className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground disabled:opacity-50"
                  >
                    {issuePickup.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Ticket className="h-4 w-4" />}
                    Issue pickup token
                  </button>
                  <Link href="/marketplace/pickup" className="text-sm font-medium text-primary hover:underline">
                    Open pickup desk
                  </Link>
                </div>
                {issuePickup.data != null ? (
                  <div className="mt-3">
                    <JsonApiDataTable
                      data={issuePickup.data}
                      columns={FINANCE_MUTATION_COLUMNS}
                      emptyTitle="No pickup token fields"
                    />
                  </div>
                ) : null}
              </div>
            ) : null}

            <CommerceDeliveryMapPanel order={commerceOrder} trackingPayload={trackingQ.data} />

            {trackingQ.data ? (
              <div className="rounded-2xl border border-border bg-card p-5 shadow-sm">
                <h3 className="text-lg font-semibold text-foreground">Tracking events</h3>
                <JsonApiDataTable
                  data={trackingQ.data}
                  columns={FINANCE_TRACKING_COLUMNS}
                  isLoading={trackingQ.isPending}
                  error={trackingQ.error as Error | null}
                  emptyTitle="No tracking events"
                  emptyHint="Tracking may populate after fulfilment starts."
                />
              </div>
            ) : trackingQ.isError ? (
              <div className="rounded-lg border border-warning/35 bg-warning-soft p-4 text-sm text-warning-foreground">
                Tracking is not available for this order (or the upstream rejected the request).
              </div>
            ) : null}

            {lastActionResponse ? (
              <div className="rounded-2xl border border-border bg-card p-5 shadow-sm">
                <h3 className="text-lg font-semibold text-foreground">Last action result</h3>
                <JsonApiDataTable
                  data={lastActionResponse}
                  columns={FINANCE_MUTATION_COLUMNS}
                  emptyTitle="No action result fields"
                />
              </div>
            ) : null}

            <div className="rounded-2xl border border-border bg-card p-5 shadow-sm">
              <h3 className="text-lg font-semibold text-foreground">Order summary</h3>
              <JsonApiDataTable
                data={commerceOrder}
                columns={[
                  { key: "id", header: "Order ID", fields: ["id", "orderId"] },
                  { key: "status", header: "Status", fields: ["status", "state"] },
                  { key: "total", header: "Total", fields: ["totalAmount", "amountTotal", "amount"] },
                  { key: "currency", header: "Currency", fields: ["currency"] },
                  { key: "facility", header: "Facility", fields: ["facilityId", "facility"] },
                  { key: "updated", header: "Updated", fields: ["updatedAt", "createdAt"] },
                ]}
                emptyTitle="No order fields"
              />
            </div>
          </div>
        ) : shouldShowMarketplace && marketplaceQ.isLoading ? (
          <div className="flex items-center justify-center py-16 text-sm text-muted-foreground">
            <Loader2 className="mr-2 h-5 w-5 animate-spin" /> Loading order...
          </div>
        ) : shouldShowMarketplace && !marketplaceOrder ? (
          <div className="rounded-lg border border-danger/28 bg-danger-soft p-6 text-center">
            <AlertTriangle className="mx-auto mb-2 h-8 w-8 text-red-400" />
            <p className="text-sm text-red-600">Failed to load order details.</p>
          </div>
        ) : shouldShowMarketplace && marketplaceOrder ? (
          <div className="max-w-3xl space-y-6">
            <div className="rounded-2xl border border-border bg-card p-5 shadow-sm">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <h3 className="text-lg font-semibold text-foreground">{marketplaceOrder.orderNumber}</h3>
                  <p className="mt-1 text-sm text-muted-foreground">Placed {new Date(marketplaceOrder.createdAt).toLocaleString()} - Ordered by {marketplaceOrder.orderedBy}</p>
                </div>
                <span className={`rounded-full px-3 py-1 text-xs font-semibold ${marketplaceOrder.status === "DELIVERED" ? "bg-emerald-100 text-primary-hover" : marketplaceOrder.status === "SHIPPED" ? "bg-indigo-100 text-primary-hover" : "bg-amber-100 text-warning-foreground"}`}>
                  {marketplaceOrder.status}
                </span>
              </div>
              <dl className="mt-4 grid gap-4 text-sm sm:grid-cols-2">
                <div>
                  <dt className="text-muted-foreground">Facility</dt>
                  <dd className="font-medium text-foreground">{marketplaceOrder.facilityId}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Total amount</dt>
                  <dd className="font-medium text-foreground">{marketplaceOrder.currency} {marketplaceOrder.totalAmount.toFixed(2)}</dd>
                </div>
              </dl>
            </div>

            <div className="rounded-2xl border border-border bg-card p-5 shadow-sm">
              <h3 className="text-lg font-semibold text-foreground">Order items</h3>
              <div className="mt-4 space-y-3">
                {marketplaceOrder.items.map((item, index) => (
                  <div key={`${item.productId}-${index}`} className="flex items-center justify-between rounded-xl bg-background p-3">
                    <div className="flex items-center gap-3">
                      <Package className="h-5 w-5 text-muted-foreground" />
                      <div>
                        <p className="text-sm font-medium text-foreground">{item.description || item.productId}</p>
                        <p className="text-xs text-muted-foreground">{item.productId} - Qty {item.quantity}</p>
                      </div>
                    </div>
                    <span className="text-sm font-medium text-foreground">{marketplaceOrder.currency} {(item.unitPrice * item.quantity).toFixed(2)}</span>
                  </div>
                ))}
              </div>
            </div>

            <div className="rounded-2xl border border-border bg-card p-5 shadow-sm">
              <h3 className="text-lg font-semibold text-foreground">Fulfilment timeline</h3>
              <div className="mt-4 space-y-3">
                {STATUS_TIMELINE.map((step, index) => {
                  const Icon = STATUS_ICON[step] ?? Clock;
                  const completed = index <= currentIndex;
                  const current = index === currentIndex;
                  return (
                    <div key={step} className="flex items-center gap-3">
                      <div className={`flex h-8 w-8 items-center justify-center rounded-full ${completed ? "bg-emerald-100 text-primary-hover" : "bg-neutral-100 text-muted-foreground"} ${current ? "ring-2 ring-emerald-300" : ""}`}>
                        <Icon className="h-4 w-4" />
                      </div>
                      <span className={`text-sm ${completed ? "font-medium text-foreground" : "text-muted-foreground"}`}>{step}</span>
                    </div>
                  );
                })}
              </div>
            </div>
          </div>
        ) : (
          <div className="rounded-lg border border-warning/35 bg-warning-soft p-6 text-sm text-warning-foreground">
            This order id did not resolve via commerce or marketplace endpoints.
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
