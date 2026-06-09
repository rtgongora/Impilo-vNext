"use client";
import { JsonApiDataTable } from "@/components/common/JsonApiDataTable";
import Link from "next/link";
import { useState } from "react";
import { ArrowLeft, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useApproveMarketplaceReview,
  useMarketplaceOpsAudit,
  useMarketplaceOpsReviews,
  useMarketplaceOpsStuckOrders,
  useMarketplaceOpsVendor,
  useMarketplaceOpsVendors,
  useMarketplaceLogisticsExceptions,
  useMarketplaceLogisticsHandoffs,
  useMarketplaceLogisticsPlans,
  useMarketplaceLogisticsProviders,
  useMarketplaceLogisticsCustody,
  useMarketplaceLogisticsProofs,
  useRejectMarketplaceReview,
  useReinstateMarketplaceVendor,
  useSuspendMarketplaceVendor,
} from "@/hooks/queries/useMarketplaceOps";

export default function MarketplaceOpsPage() {
  const [reviewPage, setReviewPage] = useState("0");
  const [reviewSize, setReviewSize] = useState("20");
  const [reviewsArmed, setReviewsArmed] = useState(true);
  const reviewsQ = useMarketplaceOpsReviews(
    { page: Number(reviewPage) || 0, size: Number(reviewSize) || 20 },
    reviewsArmed,
  );
  const approveReview = useApproveMarketplaceReview();
  const rejectReview = useRejectMarketplaceReview();
  const [reviewId, setReviewId] = useState("");
  const [reviewJson, setReviewJson] = useState("{}");

  const [stuckArmed, setStuckArmed] = useState(false);
  const stuckQ = useMarketplaceOpsStuckOrders(stuckArmed);

  const [auditPage, setAuditPage] = useState("0");
  const [auditSize, setAuditSize] = useState("20");
  const [auditArmed, setAuditArmed] = useState(false);
  const auditQ = useMarketplaceOpsAudit(
    { page: Number(auditPage) || 0, size: Number(auditSize) || 20 },
    auditArmed,
  );

  const [vendorPage, setVendorPage] = useState("0");
  const [vendorSize, setVendorSize] = useState("20");
  const [vendorsArmed, setVendorsArmed] = useState(false);
  const vendorsQ = useMarketplaceOpsVendors(
    { page: Number(vendorPage) || 0, size: Number(vendorSize) || 20 },
    vendorsArmed,
  );
  const [vendorId, setVendorId] = useState("");
  const vendorQ = useMarketplaceOpsVendor(vendorId.trim() || undefined);
  const suspendVendor = useSuspendMarketplaceVendor();
  const reinstateVendor = useReinstateMarketplaceVendor();
  const [vendorJson, setVendorJson] = useState("{}");

  const [fulfillmentId, setFulfillmentId] = useState("");
  const [plansArmed, setPlansArmed] = useState(false);
  const plansQ = useMarketplaceLogisticsPlans({ fulfillmentId: fulfillmentId.trim() || undefined }, plansArmed);

  const [planId, setPlanId] = useState("");
  const [exceptionsArmed, setExceptionsArmed] = useState(false);
  const exceptionsQ = useMarketplaceLogisticsExceptions({ planId: planId.trim() || undefined }, exceptionsArmed);

  const [proofsArmed, setProofsArmed] = useState(false);
  const proofsQ = useMarketplaceLogisticsProofs({ planId: planId.trim() || undefined }, proofsArmed);

  const [providersArmed, setProvidersArmed] = useState(false);
  const providersQ = useMarketplaceLogisticsProviders({ activeOnly: true }, providersArmed);

  const [handoffsArmed, setHandoffsArmed] = useState(false);
  const handoffsQ = useMarketplaceLogisticsHandoffs({ fulfillmentId: fulfillmentId.trim() || undefined }, handoffsArmed);

  const [custodyArmed, setCustodyArmed] = useState(false);
  const custodyQ = useMarketplaceLogisticsCustody({ fulfillmentId: fulfillmentId.trim() || undefined }, custodyArmed);

  function parseJson(body: string) {
    return JSON.parse(body) as unknown;
  }

  return (
    <AppLayout>
      <PageShell title="Marketplace ops" subtitle="Operational review queues for commerce workflows.">
        <div className="mb-4">
          <Link
            href="/marketplace"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700"
          >
            <ArrowLeft className="h-4 w-4" /> Back to marketplace
          </Link>
        </div>

        <div className="space-y-6">
          <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-900">Ops reviews</h2>
            <p className="mt-1 text-xs text-slate-500">
              GET/POST <code className="text-[11px]">/internal/v1/commerce/ops/reviews*</code>
            </p>
            <div className="mt-3 flex flex-wrap items-end gap-3">
              <label className="text-xs text-slate-600">
                page
                <input value={reviewPage} onChange={(e) => setReviewPage(e.target.value)} className="mt-1 block w-24 rounded-lg border border-slate-200 px-2 py-1.5 text-sm" />
              </label>
              <label className="text-xs text-slate-600">
                size
                <input value={reviewSize} onChange={(e) => setReviewSize(e.target.value)} className="mt-1 block w-24 rounded-lg border border-slate-200 px-2 py-1.5 text-sm" />
              </label>
              <button type="button" className="rounded-lg border border-slate-200 px-4 py-2 text-sm hover:bg-slate-50" onClick={() => { setReviewsArmed(true); void reviewsQ.refetch(); }}>
                Fetch reviews
              </button>
            </div>
            {reviewsQ.isLoading ? <p className="mt-3 flex items-center gap-2 text-sm text-slate-500"><Loader2 className="h-4 w-4 animate-spin" /> Loading reviews...</p> : null}
            {reviewsQ.isError ? <p className="mt-3 text-sm text-red-700">Could not load reviews.</p> : null}
            {reviewsQ.data != null ? (
              <div className="mt-3">
                <JsonApiDataTable
                  data={reviewsQ.data}
                  columns={[
                    { key: "id", header: "Review", fields: ["reviewId", "id"] },
                    { key: "status", header: "Status", fields: ["status", "reviewStatus"] },
                    { key: "subject", header: "Subject", fields: ["subjectType", "subjectId", "orderId"] },
                    { key: "created", header: "Created", fields: ["createdAt", "created_at"] },
                  ]}
                  isLoading={reviewsQ.isPending}
                  error={reviewsQ.error as Error | null}
                />
              </div>
            ) : null}
            <div className="mt-4 grid gap-3 lg:grid-cols-[0.4fr_1fr_auto_auto] lg:items-end">
              <label className="text-xs text-slate-600">
                Review id
                <input value={reviewId} onChange={(e) => setReviewId(e.target.value)} className="mt-1 block rounded-lg border border-slate-200 px-3 py-2 text-sm font-mono" />
              </label>
              <label className="text-xs text-slate-600">
                Body JSON
                <textarea value={reviewJson} onChange={(e) => setReviewJson(e.target.value)} rows={3} className="mt-1 block w-full rounded-lg border border-slate-200 p-2 font-mono text-xs" />
              </label>
              <button type="button" disabled={approveReview.isPending || !reviewId.trim()} className="rounded-lg bg-emerald-700 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-800 disabled:opacity-50" onClick={() => approveReview.mutate({ reviewId: reviewId.trim(), body: parseJson(reviewJson) })}>
                Approve
              </button>
              <button type="button" disabled={rejectReview.isPending || !reviewId.trim()} className="rounded-lg bg-red-700 px-4 py-2 text-sm font-medium text-white hover:bg-red-800 disabled:opacity-50" onClick={() => rejectReview.mutate({ reviewId: reviewId.trim(), body: parseJson(reviewJson) })}>
                Reject
              </button>
            </div>
          </section>

          <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div>
                <h2 className="text-sm font-semibold text-slate-900">Stuck orders</h2>
                <p className="mt-1 text-xs text-slate-500">GET <code className="text-[11px]">/internal/v1/commerce/ops/stuck-orders</code></p>
              </div>
              <button type="button" className="rounded-lg border border-slate-200 px-4 py-2 text-sm hover:bg-slate-50" onClick={() => { setStuckArmed(true); void stuckQ.refetch(); }}>
                Fetch stuck orders
              </button>
            </div>
            {stuckQ.data != null ? (
              <div className="mt-3">
                <JsonApiDataTable
                  data={stuckQ.data}
                  columns={[
                    { key: "order", header: "Order", fields: ["orderId", "id"] },
                    { key: "status", header: "Status", fields: ["status", "orderStatus"] },
                    { key: "reason", header: "Stuck reason", fields: ["stuckReason", "reason", "blocker"] },
                    { key: "since", header: "Since", fields: ["stuckSince", "updatedAt"] },
                  ]}
                  isLoading={stuckQ.isPending}
                  error={stuckQ.error as Error | null}
                />
              </div>
            ) : null}
          </section>

          <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-900">Audit trail</h2>
            <p className="mt-1 text-xs text-slate-500">GET <code className="text-[11px]">/internal/v1/commerce/ops/audit</code></p>
            <div className="mt-3 flex flex-wrap items-end gap-3">
              <label className="text-xs text-slate-600">
                page
                <input value={auditPage} onChange={(e) => setAuditPage(e.target.value)} className="mt-1 block w-24 rounded-lg border border-slate-200 px-2 py-1.5 text-sm" />
              </label>
              <label className="text-xs text-slate-600">
                size
                <input value={auditSize} onChange={(e) => setAuditSize(e.target.value)} className="mt-1 block w-24 rounded-lg border border-slate-200 px-2 py-1.5 text-sm" />
              </label>
              <button type="button" className="rounded-lg border border-slate-200 px-4 py-2 text-sm hover:bg-slate-50" onClick={() => { setAuditArmed(true); void auditQ.refetch(); }}>
                Fetch audit
              </button>
            </div>
            {auditQ.data != null ? (
              <div className="mt-3">
                <JsonApiDataTable
                  data={auditQ.data}
                  columns={[
                    { key: "event", header: "Event", fields: ["eventType", "action", "type"] },
                    { key: "actor", header: "Actor", fields: ["actorId", "actor"] },
                    { key: "target", header: "Target", fields: ["targetId", "resourceId"] },
                    { key: "at", header: "At", fields: ["occurredAt", "createdAt"] },
                  ]}
                  isLoading={auditQ.isPending}
                  error={auditQ.error as Error | null}
                />
              </div>
            ) : null}
          </section>

          <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-900">Vendor governance</h2>
            <p className="mt-1 text-xs text-slate-500">
              GET/POST <code className="text-[11px]">/internal/v1/commerce/ops/vendors*</code>
            </p>
            <div className="mt-3 flex flex-wrap items-end gap-3">
              <label className="text-xs text-slate-600">
                page
                <input value={vendorPage} onChange={(e) => setVendorPage(e.target.value)} className="mt-1 block w-24 rounded-lg border border-slate-200 px-2 py-1.5 text-sm" />
              </label>
              <label className="text-xs text-slate-600">
                size
                <input value={vendorSize} onChange={(e) => setVendorSize(e.target.value)} className="mt-1 block w-24 rounded-lg border border-slate-200 px-2 py-1.5 text-sm" />
              </label>
              <button type="button" className="rounded-lg border border-slate-200 px-4 py-2 text-sm hover:bg-slate-50" onClick={() => { setVendorsArmed(true); void vendorsQ.refetch(); }}>
                Fetch vendors
              </button>
            </div>
            {vendorsQ.data != null ? (
              <div className="mt-3">
                <JsonApiDataTable
                  data={vendorsQ.data}
                  columns={[
                    { key: "vendor", header: "Vendor", fields: ["vendorId", "id", "name"] },
                    { key: "status", header: "Status", fields: ["status", "vendorStatus"] },
                    { key: "tier", header: "Tier", fields: ["tier", "vendorTier"] },
                    { key: "score", header: "Score", fields: ["trustScore", "score"] },
                  ]}
                  isLoading={vendorsQ.isPending}
                  error={vendorsQ.error as Error | null}
                />
              </div>
            ) : null}

            <div className="mt-5 grid gap-3 lg:grid-cols-[0.4fr_1fr_auto_auto] lg:items-end">
              <label className="text-xs text-slate-600">
                Vendor id
                <input value={vendorId} onChange={(e) => setVendorId(e.target.value)} className="mt-1 block rounded-lg border border-slate-200 px-3 py-2 text-sm font-mono" />
              </label>
              <label className="text-xs text-slate-600">
                Suspend body JSON
                <textarea value={vendorJson} onChange={(e) => setVendorJson(e.target.value)} rows={3} className="mt-1 block w-full rounded-lg border border-slate-200 p-2 font-mono text-xs" />
              </label>
              <button type="button" disabled={suspendVendor.isPending || !vendorId.trim()} className="rounded-lg bg-amber-700 px-4 py-2 text-sm font-medium text-white hover:bg-amber-800 disabled:opacity-50" onClick={() => suspendVendor.mutate({ vendorId: vendorId.trim(), body: parseJson(vendorJson) })}>
                Suspend
              </button>
              <button type="button" disabled={reinstateVendor.isPending || !vendorId.trim()} className="rounded-lg bg-emerald-700 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-800 disabled:opacity-50" onClick={() => reinstateVendor.mutate(vendorId.trim())}>
                Reinstate
              </button>
            </div>
            {vendorQ.data != null ? (
              <div className="mt-3">
                <JsonApiDataTable
                  data={vendorQ.data}
                  columns={[
                    { key: "vendor", header: "Vendor", fields: ["vendorId", "id"] },
                    { key: "name", header: "Name", fields: ["name", "displayName"] },
                    { key: "status", header: "Status", fields: ["status"] },
                    { key: "suspended", header: "Suspended", fields: ["suspendedAt", "suspensionReason"] },
                  ]}
                  isLoading={vendorQ.isPending}
                  error={vendorQ.error as Error | null}
                />
              </div>
            ) : null}
          </section>

          <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-900">Logistics desk</h2>
            <p className="mt-1 text-xs text-slate-500">
              GET/POST <code className="text-[11px]">/internal/v1/commerce/logistics/*</code>
            </p>

            <div className="mt-3 grid gap-3 lg:grid-cols-[0.45fr_auto_1fr] lg:items-end">
              <label className="text-xs text-slate-600">
                Fulfillment id
                <input value={fulfillmentId} onChange={(e) => setFulfillmentId(e.target.value)} className="mt-1 block rounded-lg border border-slate-200 px-3 py-2 text-sm font-mono" />
              </label>
              <button type="button" className="rounded-lg border border-slate-200 px-4 py-2 text-sm hover:bg-slate-50" onClick={() => { setPlansArmed(true); void plansQ.refetch(); }}>
                Fetch plans
              </button>
              {plansQ.data != null ? (
                <JsonApiDataTable
                  data={plansQ.data}
                  columns={[
                    { key: "plan", header: "Plan", fields: ["planId", "id"] },
                    { key: "status", header: "Status", fields: ["status"] },
                    { key: "provider", header: "Provider", fields: ["providerId", "logisticsProvider"] },
                  ]}
                  isLoading={plansQ.isPending}
                  error={plansQ.error as Error | null}
                />
              ) : <div />}
            </div>

            <div className="mt-4 grid gap-3 lg:grid-cols-[0.45fr_auto_auto_1fr] lg:items-end">
              <label className="text-xs text-slate-600">
                Plan id
                <input value={planId} onChange={(e) => setPlanId(e.target.value)} className="mt-1 block rounded-lg border border-slate-200 px-3 py-2 text-sm font-mono" />
              </label>
              <button type="button" className="rounded-lg border border-slate-200 px-4 py-2 text-sm hover:bg-slate-50" onClick={() => { setExceptionsArmed(true); void exceptionsQ.refetch(); }}>
                Fetch exceptions
              </button>
              <button type="button" className="rounded-lg border border-slate-200 px-4 py-2 text-sm hover:bg-slate-50" onClick={() => { setProofsArmed(true); void proofsQ.refetch(); }}>
                Fetch proofs
              </button>
              <div className="grid gap-2">
                {exceptionsQ.data != null ? (
                  <JsonApiDataTable
                    data={exceptionsQ.data}
                    columns={[
                      { key: "type", header: "Type", fields: ["exceptionType", "type"] },
                      { key: "status", header: "Status", fields: ["status"] },
                      { key: "detail", header: "Detail", fields: ["message", "reason"] },
                    ]}
                    isLoading={exceptionsQ.isPending}
                    error={exceptionsQ.error as Error | null}
                  />
                ) : null}
                {proofsQ.data != null ? (
                  <JsonApiDataTable
                    data={proofsQ.data}
                    columns={[
                      { key: "proof", header: "Proof", fields: ["proofId", "id"] },
                      { key: "type", header: "Type", fields: ["proofType", "type"] },
                      { key: "captured", header: "Captured", fields: ["capturedAt", "createdAt"] },
                    ]}
                    isLoading={proofsQ.isPending}
                    error={proofsQ.error as Error | null}
                  />
                ) : null}
              </div>
            </div>

            <div className="mt-4 grid gap-3 lg:grid-cols-[auto_1fr] lg:items-start">
              <button
                type="button"
                className="rounded-lg border border-slate-200 px-4 py-2 text-sm hover:bg-slate-50"
                onClick={() => { setProvidersArmed(true); void providersQ.refetch(); }}
              >
                Fetch providers
              </button>
              {providersQ.data != null ? (
                <JsonApiDataTable
                  data={providersQ.data}
                  columns={[
                    { key: "provider", header: "Provider", fields: ["providerId", "id", "name"] },
                    { key: "mode", header: "Mode", fields: ["deliveryMode", "mode"] },
                    { key: "active", header: "Active", fields: ["active", "status"] },
                  ]}
                  isLoading={providersQ.isPending}
                  error={providersQ.error as Error | null}
                />
              ) : <div />}
            </div>

            <div className="mt-4 grid gap-3 lg:grid-cols-[auto_1fr] lg:items-start">
              <button
                type="button"
                className="rounded-lg border border-slate-200 px-4 py-2 text-sm hover:bg-slate-50 disabled:opacity-50"
                onClick={() => { setHandoffsArmed(true); void handoffsQ.refetch(); }}
                disabled={!fulfillmentId.trim()}
              >
                Fetch handoffs (fulfillment)
              </button>
              {handoffsQ.data != null ? (
                <JsonApiDataTable
                  data={handoffsQ.data}
                  columns={[
                    { key: "handoff", header: "Handoff", fields: ["handoffId", "id"] },
                    { key: "from", header: "From", fields: ["fromParty", "from"] },
                    { key: "to", header: "To", fields: ["toParty", "to"] },
                    { key: "status", header: "Status", fields: ["status"] },
                  ]}
                  isLoading={handoffsQ.isPending}
                  error={handoffsQ.error as Error | null}
                />
              ) : <div />}
            </div>

            <div className="mt-4 grid gap-3 lg:grid-cols-[auto_1fr] lg:items-start">
              <button
                type="button"
                className="rounded-lg border border-slate-200 px-4 py-2 text-sm hover:bg-slate-50 disabled:opacity-50"
                onClick={() => { setCustodyArmed(true); void custodyQ.refetch(); }}
                disabled={!fulfillmentId.trim()}
              >
                Fetch custody (fulfillment)
              </button>
              {custodyQ.data != null ? (
                <JsonApiDataTable
                  data={custodyQ.data}
                  columns={[
                    { key: "custody", header: "Custody", fields: ["custodyId", "id"] },
                    { key: "holder", header: "Holder", fields: ["custodian", "holder"] },
                    { key: "state", header: "State", fields: ["custodyState", "status"] },
                    { key: "since", header: "Since", fields: ["since", "updatedAt"] },
                  ]}
                  isLoading={custodyQ.isPending}
                  error={custodyQ.error as Error | null}
                />
              ) : <div />}
            </div>
          </section>
        </div>
      </PageShell>
    </AppLayout>
  );
}

