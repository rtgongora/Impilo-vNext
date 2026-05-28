"use client";
import { QueryResultPanel } from "@/components/common/QueryResultPanel";
import Link from "next/link";
import { useState } from "react";
import { ArrowLeft, Loader2, Ticket } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useCommerceClaimPickup, useCommerceIssuePickup } from "@/hooks/queries/useCommercePickup";

const DEFAULT_CLAIM_JSON = "{\n  \"pickupToken\": \"\",\n  \"claimedBy\": \"\",\n  \"claimantId\": \"\"\n}\n";

export default function MarketplacePickupPage() {
  const [orderId, setOrderId] = useState("");
  const [claimJson, setClaimJson] = useState(DEFAULT_CLAIM_JSON);
  const [claimError, setClaimError] = useState<string | null>(null);
  const issueM = useCommerceIssuePickup();
  const claimM = useCommerceClaimPickup();

  return (
    <AppLayout>
      <PageShell
        title="Pickup handoff"
        subtitle="Issue pickup tokens and claim them through the canonical commerce BFF, instead of using the MSIKA Flow sidecar directly."
        icon={<Ticket className="h-6 w-6" />}
      >
        <div className="mb-4">
          <Link href="/marketplace" className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700">
            <ArrowLeft className="h-4 w-4" /> Back to marketplace
          </Link>
        </div>

        <div className="grid gap-6 xl:grid-cols-2">
          <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-900">Issue pickup token</h2>
            <p className="mt-1 text-xs text-slate-500">
              POST <code className="text-[11px]">/internal/v1/commerce/orders/{"{orderId}"}/pickup/issue</code>
            </p>
            <label className="mt-3 block text-xs text-slate-600">
              Order id
              <input
                value={orderId}
                onChange={(event) => setOrderId(event.target.value)}
                className="mt-1 block w-full rounded-lg border border-slate-200 px-3 py-2 text-sm font-mono"
                placeholder="order-123"
                aria-label="Pickup order id"
              />
            </label>
            <button
              type="button"
              disabled={issueM.isPending || !orderId.trim()}
              className="mt-3 rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-50"
              onClick={() => issueM.mutate(orderId.trim())}
            >
              {issueM.isPending ? (
                <span className="inline-flex items-center gap-2">
                  <Loader2 className="h-4 w-4 animate-spin" /> Issuing...
                </span>
              ) : (
                "Issue token"
              )}
            </button>
            {issueM.data != null ? (
              <QueryResultPanel title="Issue M" isPending={issueM.isPending} isLoading={issueM.isPending} isError={issueM.isError} error={issueM.error} data={issueM.data} />
            ) : null}
          </section>

          <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-900">Claim pickup</h2>
            <p className="mt-1 text-xs text-slate-500">
              POST <code className="text-[11px]">/internal/v1/commerce/pickup/claim</code> with the upstream claim payload.
            </p>
            <textarea
              value={claimJson}
              onChange={(event) => setClaimJson(event.target.value)}
              rows={9}
              className="mt-3 block w-full rounded-lg border border-slate-200 p-3 font-mono text-xs"
              aria-label="Pickup claim JSON"
            />
            {claimError ? <p className="mt-2 text-xs text-red-700">{claimError}</p> : null}
            <button
              type="button"
              disabled={claimM.isPending}
              className="mt-3 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
              onClick={() => {
                try {
                  const body = JSON.parse(claimJson) as unknown;
                  setClaimError(null);
                  claimM.mutate(body);
                } catch {
                  setClaimError("Invalid JSON.");
                }
              }}
            >
              {claimM.isPending ? "Posting..." : "Claim pickup"}
            </button>
            {claimM.data != null ? (
              <QueryResultPanel title="Claim M" isPending={claimM.isPending} isLoading={claimM.isPending} isError={claimM.isError} error={claimM.error} data={claimM.data} />
            ) : null}
          </section>
        </div>
      </PageShell>
    </AppLayout>
  );
}
