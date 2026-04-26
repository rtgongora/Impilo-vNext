"use client";

import Link from "next/link";
import { AlertTriangle, ClipboardList, Package, Pill, Truck, Wallet } from "lucide-react";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import {
  useInventoryNearExpiry,
  useInventoryReconcilePending,
  useInventoryStockouts,
} from "@/hooks/queries/useInventory";
import { useProcPurchaseOrders, useProcRequisitions } from "@/hooks/queries/useProcurement";
import { useAssets } from "@/hooks/queries/useAssets";
import { countEnvelopeList } from "@/lib/apiEnvelope";

type TileProps = {
  title: string;
  value: string;
  hint?: string;
  icon: typeof Package;
  tone?: "slate" | "amber" | "rose";
  loading?: boolean;
  error?: boolean;
};

function MetricTile({ title, value, hint, icon: Icon, tone = "slate", loading, error }: TileProps) {
  const border =
    tone === "amber" ? "border-amber-200 bg-amber-50" : tone === "rose" ? "border-rose-200 bg-rose-50" : "border-slate-200 bg-white";
  return (
    <div className={`rounded-2xl border p-4 shadow-sm ${border}`}>
      <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-slate-500">
        <Icon className="h-4 w-4" aria-hidden />
        {title}
      </div>
      <div className="mt-2 text-3xl font-semibold text-slate-900">{loading ? "…" : error ? "—" : value}</div>
      {hint ? <p className="mt-1 text-sm text-slate-600">{hint}</p> : null}
    </div>
  );
}

export function EnterpriseResourceDashboard() {
  const facility = useFacilityStore((s) => s.facility);
  const facilityId = facility?.id ?? "";
  const { hasRole } = useAuthStore();

  const stockouts = useInventoryStockouts(facilityId || null);
  const nearExpiry = useInventoryNearExpiry(facilityId || null, 90);
  const reconcile = useInventoryReconcilePending(0, 50);
  const reqs = useProcRequisitions();
  const pos = useProcPurchaseOrders();
  const assets = useAssets({ facilityId: facilityId || undefined, limit: 200 });

  const canFinance = ["SYSTEM_ADMIN", "FACILITY_ADMIN", "FINANCE"].some((r) => hasRole(r));
  const canProcure = ["FINANCE", "PHARMACIST", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"].some((r) => hasRole(r));

  const stockoutCount = facilityId ? countEnvelopeList(stockouts.data) : 0;
  const expiryCount = facilityId ? countEnvelopeList(nearExpiry.data) : 0;
  const reconCount = countEnvelopeList(reconcile.data);
  const reqCount = canProcure ? countEnvelopeList(reqs.data) : 0;
  const poCount = canProcure ? countEnvelopeList(pos.data) : 0;
  const assetCount = facilityId ? countEnvelopeList(assets.data) : 0;

  return (
    <div className="space-y-6">
      {!facilityId ? (
        <div className="flex items-start gap-2 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
          <div>
            <p className="font-medium">Facility context required</p>
            <p className="mt-1 text-amber-800/90">
              Choose a facility from the workspace switcher to load inventory risk, assets, and on-hand intelligence. Finance
              and procurement tiles still respect your roles.
            </p>
            <Link href="/facility" className="mt-2 inline-block text-sm font-semibold text-amber-950 underline">
              Select facility
            </Link>
          </div>
        </div>
      ) : null}

      <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        <MetricTile
          title="Stockouts (on-hand)"
          value={String(stockoutCount)}
          hint="Rows returned by inventory-service for zero on-hand at this facility."
          icon={Package}
          tone="rose"
          loading={Boolean(facilityId) && stockouts.isLoading}
          error={Boolean(stockouts.error)}
        />
        <MetricTile
          title="Near expiry (90d)"
          value={String(expiryCount)}
          hint="Near-expiry projection from inventory on-hand API."
          icon={Pill}
          tone="amber"
          loading={Boolean(facilityId) && nearExpiry.isLoading}
          error={Boolean(nearExpiry.error)}
        />
        <MetricTile
          title="Reconciliation queue"
          value={String(reconCount)}
          hint="Pending reconciliation rows from inventory BFF."
          icon={ClipboardList}
          loading={reconcile.isLoading}
          error={Boolean(reconcile.error)}
        />
        {canProcure ? (
          <MetricTile
            title="Procurement requisitions"
            value={String(reqCount)}
            hint="Rows from `/internal/v1/erp/procurement/requisitions`."
            icon={Truck}
            loading={reqs.isLoading}
            error={Boolean(reqs.error)}
          />
        ) : null}
        {canProcure ? (
          <MetricTile
            title="Purchase orders"
            value={String(poCount)}
            hint="Rows from `/internal/v1/erp/procurement/purchase-orders`."
            icon={Truck}
            loading={pos.isLoading}
            error={Boolean(pos.error)}
          />
        ) : null}
        {facilityId ? (
          <MetricTile
            title="Registered assets"
            value={String(assetCount)}
            hint="Asset registry list for this facility (cursor page)."
            icon={Package}
            loading={assets.isLoading}
            error={Boolean(assets.error)}
          />
        ) : null}
      </section>

      <section className="rounded-2xl border border-slate-200 bg-slate-50 p-5 text-sm text-slate-700">
        <p className="font-semibold text-slate-900">What is intentionally not fabricated</p>
        <p className="mt-2">
          Revenue today/month, fleet alerts, cold-chain breaches, payer settlement health, and patient-level balances require
          reporting joins across <strong>reporting-service</strong>, <strong>mushex-service</strong>, and{" "}
          <strong>costing-engine-service</strong> that are not all exposed on a single dashboard endpoint yet. Those tiles stay
          absent rather than showing synthetic KPIs.
        </p>
        {canFinance ? (
          <p className="mt-3 flex items-center gap-2 text-slate-800">
            <Wallet className="h-4 w-4" aria-hidden />
            <span>
              Open the{" "}
              <Link href="/finance" className="font-semibold text-impilo-600 underline">
                finance workspace
              </Link>{" "}
              for billing, claims, settlements, and MusheX-aligned payment flows.
            </span>
          </p>
        ) : null}
      </section>
    </div>
  );
}
