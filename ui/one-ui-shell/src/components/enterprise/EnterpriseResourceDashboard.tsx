"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import {
  AlertTriangle,
  ClipboardList,
  Globe2,
  Package,
  Pill,
  Snowflake,
  Truck,
  Wallet,
} from "lucide-react";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import {
  useInventoryNearExpiry,
  useInventoryReconcilePending,
  useInventoryStockouts,
} from "@/hooks/queries/useInventory";
import { useProcPurchaseOrders, useProcRequisitions } from "@/hooks/queries/useProcurement";
import { useAssets } from "@/hooks/queries/useAssets";
import { useFacilities } from "@/hooks/queries/useFacilities";
import { useRevenueSummary } from "@/hooks/queries/useFinancialReports";
import { extractCount, useMushexPlatformRemittanceTransfers } from "@/hooks/queries/useMushexPlatformAdmin";
import { countEnvelopeList } from "@/lib/apiEnvelope";
import { CinematicStage, GlassSurface } from "shared-ui";

type GeoLevel = "national" | "province" | "district" | "facility";

type TileProps = {
  title: string;
  value: string;
  hint?: string;
  icon: typeof Package;
  tone?: "slate" | "amber" | "rose" | "sky" | "violet";
  loading?: boolean;
  error?: boolean;
  href?: string;
};

function MetricTile({ title, value, hint, icon: Icon, tone = "slate", loading, error, href }: TileProps) {
  // Glass command-centre tile (Future-Realism §8 "Floating Analytics Command Center"): a frosted
  // GlassSurface with a tone-coloured LEFT ACCENT + icon. Ops/clinical severity (stockout, near-
  // expiry, cold-chain) stays a full-contrast signal and is never washed out by the gloss (§1
  // "Honest", §9 accessibility). GlassSurface degrades to a solid fallback at the baseline tier.
  const accent =
    tone === "amber"
      ? "border-l-warning"
      : tone === "rose"
        ? "border-l-danger"
        : tone === "sky"
          ? "border-l-sky-400"
          : tone === "violet"
            ? "border-l-violet-400"
            : "border-l-border";
  const iconTone =
    tone === "amber"
      ? "text-warning"
      : tone === "rose"
        ? "text-danger"
        : tone === "sky"
          ? "text-sky-500"
          : tone === "violet"
            ? "text-violet-500"
            : "text-muted-foreground";

  const body = (
    <GlassSurface
      blur="soft"
      className={`border-l-4 ${accent} p-4 ${href ? "transition hover:shadow-glow-teal" : ""}`}
    >
      <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
        <Icon className={`h-4 w-4 ${iconTone}`} aria-hidden />
        {title}
      </div>
      <div className="mt-2 text-3xl font-semibold text-foreground">{loading ? "…" : error ? "—" : value}</div>
      {hint ? <p className="mt-1 text-sm text-muted-foreground">{hint}</p> : null}
    </GlassSurface>
  );

  return href ? <Link href={href}>{body}</Link> : body;
}

function formatRevenue(data: Record<string, unknown> | undefined): string {
  if (!data) return "—";
  const total = data.total_revenue ?? data.totalRevenue ?? data.revenue_total ?? data.amount;
  if (typeof total === "number") return total.toLocaleString(undefined, { maximumFractionDigits: 0 });
  if (typeof total === "string" && total.trim()) return total;
  return "—";
}

export function EnterpriseResourceDashboard() {
  const facility = useFacilityStore((s) => s.facility);
  const { hasRole } = useAuthStore();

  const [geoLevel, setGeoLevel] = useState<GeoLevel>("facility");
  const [province, setProvince] = useState("");
  const [district, setDistrict] = useState("");

  const now = new Date();
  const revenueFacilityId = geoLevel === "facility" ? facility?.id : undefined;
  const revenueQ = useRevenueSummary(revenueFacilityId, now.getFullYear(), now.getMonth() + 1);

  const facilitiesQ = useFacilities({
    province: geoLevel === "province" || geoLevel === "district" ? province || undefined : undefined,
    page: 0,
    size: 200,
  });

  const scopedFacilityIds = useMemo(() => {
    const rows = facilitiesQ.data?.data ?? [];
    if (geoLevel === "national") return rows.map((f) => f.id);
    if (geoLevel === "province") return rows.filter((f) => !province || f.attributes.province === province).map((f) => f.id);
    if (geoLevel === "district") {
      return rows
        .filter((f) => (!province || f.attributes.province === province) && (!district || f.attributes.district === district))
        .map((f) => f.id);
    }
    return facility?.id ? [facility.id] : [];
  }, [facilitiesQ.data?.data, geoLevel, province, district, facility?.id]);

  const activeFacilityId = geoLevel === "facility" ? facility?.id ?? "" : scopedFacilityIds[0] ?? "";

  const stockouts = useInventoryStockouts(activeFacilityId || null);
  const nearExpiry = useInventoryNearExpiry(activeFacilityId || null, 90);
  const coldChain = useInventoryNearExpiry(activeFacilityId || null, 30);
  const reconcile = useInventoryReconcilePending(0, 50);
  const reqs = useProcRequisitions();
  const pos = useProcPurchaseOrders();
  const assets = useAssets({ facilityId: activeFacilityId || undefined, limit: 200 });
  const remittancesQ = useMushexPlatformRemittanceTransfers();

  const canFinance = ["SYSTEM_ADMIN", "FACILITY_ADMIN", "FINANCE"].some((r) => hasRole(r));
  const canProcure = ["FINANCE", "PHARMACIST", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"].some((r) => hasRole(r));

  const stockoutCount = activeFacilityId ? countEnvelopeList(stockouts.data) : 0;
  const expiryCount = activeFacilityId ? countEnvelopeList(nearExpiry.data) : 0;
  const coldChainCount = activeFacilityId ? countEnvelopeList(coldChain.data) : 0;
  const reconCount = countEnvelopeList(reconcile.data);
  const reqCount = canProcure ? countEnvelopeList(reqs.data) : 0;
  const poCount = canProcure ? countEnvelopeList(pos.data) : 0;
  const assetCount = activeFacilityId ? countEnvelopeList(assets.data) : 0;
  const settlementCount = canFinance ? extractCount(remittancesQ.data) : 0;

  const revenueByPayer = useMemo(() => {
    const raw = revenueQ.data?.byPayerType;
    return Array.isArray(raw) ? raw.length : 0;
  }, [revenueQ.data]);

  const revenueHint =
    geoLevel === "national" || geoLevel === "province" || geoLevel === "district"
      ? revenueQ.isError
        ? "National Costa aggregate unavailable from reporting BFF."
        : revenueByPayer > 0
          ? `National Costa aggregate — ${revenueByPayer} payer type bucket(s) in breakdown.`
          : "National Costa aggregate (no facility_id) — payer breakdown empty for this period."
      : "COSTA revenue-summary via reporting BFF for the scoped facility or national aggregate.";

  const provinceOptions = useMemo(() => {
    const set = new Set<string>();
    for (const f of facilitiesQ.data?.data ?? []) {
      if (f.attributes.province) set.add(f.attributes.province);
    }
    return [...set].sort();
  }, [facilitiesQ.data?.data]);

  const districtOptions = useMemo(() => {
    const set = new Set<string>();
    for (const f of facilitiesQ.data?.data ?? []) {
      if (province && f.attributes.province !== province) continue;
      if (f.attributes.district) set.add(f.attributes.district);
    }
    return [...set].sort();
  }, [facilitiesQ.data?.data, province]);

  return (
    <CinematicStage className="space-y-6 p-5 sm:p-6">
      <div className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.22em] text-[color:var(--text-secondary)]">
        <span className="h-1.5 w-1.5 rounded-full bg-neon-teal shadow-glow-teal" aria-hidden />
        Command centre · live estate signals
      </div>
      <GlassSurface blur="soft" className="p-4">
        <div className="flex flex-wrap items-end gap-3">
          <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            <Globe2 className="h-4 w-4" />
            Geography scope
          </div>
          <select
            value={geoLevel}
            onChange={(e) => setGeoLevel(e.target.value as GeoLevel)}
            className="rounded-lg border border-border bg-transparent px-3 py-2 text-sm text-[color:var(--text-primary)] [color-scheme:dark]"
          >
            <option value="national">National</option>
            <option value="province">Province</option>
            <option value="district">District</option>
            <option value="facility">Facility (workspace)</option>
          </select>
          {geoLevel === "province" || geoLevel === "district" ? (
            <select
              value={province}
              onChange={(e) => setProvince(e.target.value)}
              className="rounded-lg border border-border bg-transparent px-3 py-2 text-sm text-[color:var(--text-primary)] [color-scheme:dark]"
            >
              <option value="">All provinces</option>
              {provinceOptions.map((p) => (
                <option key={p} value={p}>
                  {p}
                </option>
              ))}
            </select>
          ) : null}
          {geoLevel === "district" ? (
            <select
              value={district}
              onChange={(e) => setDistrict(e.target.value)}
              className="rounded-lg border border-border bg-transparent px-3 py-2 text-sm text-[color:var(--text-primary)] [color-scheme:dark]"
            >
              <option value="">All districts</option>
              {districtOptions.map((d) => (
                <option key={d} value={d}>
                  {d}
                </option>
              ))}
            </select>
          ) : null}
          <span className="text-xs text-muted-foreground">
            {geoLevel === "facility"
              ? facility?.name ?? "No facility selected"
              : `${scopedFacilityIds.length} facilities in scope`}
          </span>
          <Link href="/enterprise/oversight" className="ml-auto text-xs font-semibold text-primary hover:underline">
            National oversight links →
          </Link>
        </div>
      </GlassSurface>

      {geoLevel === "facility" && !facility?.id ? (
        <div className="flex items-start gap-2 rounded-xl border border-warning/35 bg-warning-soft p-4 text-sm text-warning-foreground">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
          <div>
            <p className="font-medium">Facility context required</p>
            <p className="mt-1 text-warning-foreground/90">
              Choose a facility from the workspace switcher for facility-scoped inventory and revenue tiles.
            </p>
            <Link href="/facility" className="mt-2 inline-block text-sm font-semibold text-warning-foreground underline">
              Select facility
            </Link>
          </div>
        </div>
      ) : null}

      <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        {canFinance ? (
          <MetricTile
            title="Revenue (month)"
            value={formatRevenue(revenueQ.data)}
            hint={revenueHint}
            icon={Wallet}
            tone="violet"
            loading={revenueQ.isLoading}
            error={Boolean(revenueQ.error)}
            href="/finance/reports"
          />
        ) : null}
        {canFinance ? (
          <MetricTile
            title="MusheX remittances"
            value={String(settlementCount)}
            hint="Settlement-adjacent remittance transfer rows from mushex-platform BFF."
            icon={Wallet}
            tone="sky"
            loading={remittancesQ.isLoading}
            error={Boolean(remittancesQ.error)}
            href="/finance/settlements"
          />
        ) : null}
        <MetricTile
          title="Stockouts (on-hand)"
          value={String(stockoutCount)}
          hint="Zero on-hand rows for the scoped facility."
          icon={Package}
          tone="rose"
          loading={Boolean(activeFacilityId) && stockouts.isLoading}
          error={Boolean(stockouts.error)}
        />
        <MetricTile
          title="Near expiry (90d)"
          value={String(expiryCount)}
          hint="Near-expiry projection from inventory on-hand API."
          icon={Pill}
          tone="amber"
          loading={Boolean(activeFacilityId) && nearExpiry.isLoading}
          error={Boolean(nearExpiry.error)}
        />
        <MetricTile
          title="Cold-chain risk (30d)"
          value={String(coldChainCount)}
          hint="Short-window near-expiry count for cold-chain SKUs at scoped facility."
          icon={Snowflake}
          tone="sky"
          loading={Boolean(activeFacilityId) && coldChain.isLoading}
          error={Boolean(coldChain.error)}
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
            hint="Rows from procurement requisitions API."
            icon={Truck}
            loading={reqs.isLoading}
            error={Boolean(reqs.error)}
          />
        ) : null}
        {canProcure ? (
          <MetricTile
            title="Purchase orders"
            value={String(poCount)}
            hint="Rows from purchase orders API."
            icon={Truck}
            loading={pos.isLoading}
            error={Boolean(pos.error)}
          />
        ) : null}
        {activeFacilityId ? (
          <MetricTile
            title="Registered assets"
            value={String(assetCount)}
            hint="Asset registry list for scoped facility."
            icon={Package}
            loading={assets.isLoading}
            error={Boolean(assets.error)}
          />
        ) : null}
      </section>
    </CinematicStage>
  );
}
