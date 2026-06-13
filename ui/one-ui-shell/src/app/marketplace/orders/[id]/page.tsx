"use client";
import { JsonApiDataTable } from "@/components/common/JsonApiDataTable";
import { FINANCE_MUTATION_COLUMNS, FINANCE_TRACKING_COLUMNS } from "@/lib/json-api/finance-table-columns";
import { useParams } from "next/navigation";
import Link from "next/link";
import { useMemo, useState } from "react";
import { AlertTriangle, ArrowLeft, CheckCircle2, Clock, Loader2, Package, Truck, RefreshCw } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useMarketplaceOrder } from "@/hooks/queries/useMarketplace";
import { useCommerceOrder, useCommerceOrderAction, useCommerceOrderTracking } from "@/hooks/queries/useCommerceFlow";
import { CommerceDeliveryMapPanel } from "@/components/maps/CommerceDeliveryMapPanel";

const STATUS_TIMELINE = ["PENDING", "APPROVED", "SHIPPED", "DELIVERED"];
const STATUS_ICON: Record<string, typeof Clock> = {
  PENDING: Clock,
  APPROVED: CheckCircle2,
  SHIPPED: Truck,
  DELIVERED: CheckCircle2,
};

export default function OrderDetailPage() {
  const params = useParams();
  const id = params.id as string;
  const commerceQ = useCommerceOrder(id);
  const trackingQ = useCommerceOrderTracking(id);
  const commerceAction = useCommerceOrderAction();
  const [lastActionResponse, setLastActionResponse] = useState<unknown>(null);

  const marketplaceQ = useMarketplaceOrder(id);
  const marketplaceOrder = marketplaceQ.data;

  const commerceOrder = useMemo(() => (commerceQ.data && typeof commerceQ.data === "object" ? commerceQ.data as Record<string, unknown> : null), [commerceQ.data]);

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
                  <p className="mt-1 text-xs text-muted-foreground break-all">Order ID: {id}</p>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  {(["validate", "price", "pay", "cancel"] as const).map((action) => (
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
                  { key: "total", header: "Total", fields: ["totalAmount", "amount"] },
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
                        <p className="text-xs text-muted-foreground">{item.productId} � Qty {item.quantity}</p>
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
