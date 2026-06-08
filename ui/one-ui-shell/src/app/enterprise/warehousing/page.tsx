"use client";

import Link from "next/link";
import { AlertTriangle, Loader2, Package, Pill, Truck } from "lucide-react";
import { EnterpriseWorkspaceShell } from "@/components/enterprise/EnterpriseWorkspaceShell";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import {
  useInventoryMovements,
  useInventoryNearExpiry,
  useInventoryOnHand,
  useInventoryStockouts,
} from "@/hooks/queries/useInventory";
import { countEnvelopeList } from "@/lib/apiEnvelope";

function MetricCard(props: {
  title: string;
  value: string;
  hint: string;
  icon: typeof Package;
  tone?: "slate" | "amber" | "rose";
  loading?: boolean;
}) {
  const border =
    props.tone === "amber"
      ? "border-amber-200 bg-amber-50"
      : props.tone === "rose"
        ? "border-rose-200 bg-rose-50"
        : "border-slate-200 bg-white";
  const Icon = props.icon;
  return (
    <div className={`rounded-2xl border p-4 shadow-sm ${border}`}>
      <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-slate-500">
        <Icon className="h-4 w-4" aria-hidden />
        {props.title}
      </div>
      <div className="mt-2 text-3xl font-semibold text-slate-900">
        {props.loading ? "…" : props.value}
      </div>
      <p className="mt-1 text-sm text-slate-600">{props.hint}</p>
    </div>
  );
}

export default function EnterpriseWarehousingPage() {
  const facility = useFacilityStore((s) => s.facility);
  const facilityId = facility?.id ?? "";

  const onHandQ = useInventoryOnHand(facilityId || null, { size: 50 });
  const stockoutsQ = useInventoryStockouts(facilityId || null);
  const nearExpiryQ = useInventoryNearExpiry(facilityId || null, 90);
  const movementsQ = useInventoryMovements(facilityId);

  const onHandCount = facilityId ? countEnvelopeList(onHandQ.data) : 0;
  const stockoutCount = facilityId ? countEnvelopeList(stockoutsQ.data) : 0;
  const expiryCount = facilityId ? countEnvelopeList(nearExpiryQ.data) : 0;
  const movements = movementsQ.data ?? [];
  const recentMovements = movements.slice(0, 10);

  return (
    <EnterpriseWorkspaceShell
      title="Warehousing & distribution"
      subtitle="Inventory on-hand, stock risk, and movement trail from inventory-service — warehouse dispatch APIs are not wired yet"
    >
      <div className="space-y-6">
        <div className="flex flex-wrap items-center gap-3">
          <FeatureMaturityBadge
            status="partial"
            detail="On-hand, stockouts, near-expiry, and movements use /internal/v1/inventory/*. Integration-hub dispatch lists are not exposed on the BFF."
          />
        </div>

        {!facilityId ? (
          <div className="flex items-start gap-2 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
            <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
            <div>
              <p className="font-medium">Facility context required</p>
              <p className="mt-1 text-amber-800/90">
                Choose a facility to load warehouse-relevant inventory intelligence.
              </p>
              <Link href="/facility" className="mt-2 inline-block text-sm font-semibold text-amber-950 underline">
                Select facility
              </Link>
            </div>
          </div>
        ) : null}

        <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          <MetricCard
            title="On-hand SKUs"
            value={String(onHandCount)}
            hint="Rows from GET /internal/v1/inventory/on-hand for this facility."
            icon={Package}
            loading={Boolean(facilityId) && onHandQ.isLoading}
          />
          <MetricCard
            title="Stockouts"
            value={String(stockoutCount)}
            hint="Zero on-hand rows from inventory-service."
            icon={Package}
            tone="rose"
            loading={Boolean(facilityId) && stockoutsQ.isLoading}
          />
          <MetricCard
            title="Near expiry (90d)"
            value={String(expiryCount)}
            hint="Near-expiry projection from on-hand API."
            icon={Pill}
            tone="amber"
            loading={Boolean(facilityId) && nearExpiryQ.isLoading}
          />
        </section>

        <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="flex items-center justify-between gap-3">
            <div>
              <h2 className="text-sm font-semibold text-slate-900">Recent stock movements</h2>
              <p className="mt-1 text-xs text-slate-500">
                Distribution trail from{" "}
                <code className="text-[10px]">GET /internal/v1/inventory/movements</code>
              </p>
            </div>
            <Link href="/inventory/movements" className="text-sm font-semibold text-impilo-600 underline">
              Full movements
            </Link>
          </div>

          {!facilityId ? (
            <p className="mt-4 text-sm text-slate-500">Select a facility to load movements.</p>
          ) : movementsQ.isLoading ? (
            <p className="mt-4 flex items-center gap-2 text-sm text-slate-500">
              <Loader2 className="h-4 w-4 animate-spin" /> Loading movements…
            </p>
          ) : movementsQ.isError ? (
            <p className="mt-4 text-sm text-red-700">Could not load inventory movements from the BFF.</p>
          ) : recentMovements.length === 0 ? (
            <p className="mt-4 text-sm text-slate-500">No stock movements recorded for this facility yet.</p>
          ) : (
            <div className="mt-4 overflow-x-auto rounded-lg border border-slate-200">
              <table className="w-full text-xs">
                <thead className="bg-slate-50 text-slate-600">
                  <tr>
                    <th className="px-3 py-2 text-left font-medium">Moved at</th>
                    <th className="px-3 py-2 text-left font-medium">Item</th>
                    <th className="px-3 py-2 text-left font-medium">From → To</th>
                    <th className="px-3 py-2 text-right font-medium">Qty</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {recentMovements.map((m) => (
                    <tr key={m.id}>
                      <td className="px-3 py-2 text-slate-500">{m.movedAt || "—"}</td>
                      <td className="px-3 py-2 text-slate-900">{m.itemName}</td>
                      <td className="px-3 py-2 text-slate-600">
                        {m.fromLocation} → {m.toLocation}
                      </td>
                      <td className="px-3 py-2 text-right text-slate-900">{m.quantity}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>

        <div className="rounded-2xl border border-slate-200 bg-slate-50 p-6 text-sm text-slate-700">
          <p className="font-medium text-slate-900">What is intentionally not fabricated</p>
          <p className="mt-2">
            Dispatch lists, proof-of-delivery, and warehouse master data from{" "}
            <strong>integration-hub-service</strong> are not exposed on a single BFF read yet. Use procurement
            GRN and vendor fulfilment flows until those APIs land.
          </p>
          <div className="mt-4 flex flex-wrap gap-2">
            <Link href="/erp/procurement" className="text-sm font-semibold text-impilo-600 underline">
              Procurement / GRN
            </Link>
            <Link href="/marketplace/vendor" className="text-sm font-semibold text-impilo-600 underline">
              Vendor fulfilment
            </Link>
            <Link href="/inventory/requisitions" className="text-sm font-semibold text-impilo-600 underline">
              Requisitions
            </Link>
          </div>
        </div>
      </div>
    </EnterpriseWorkspaceShell>
  );
}
