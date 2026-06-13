"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { AlertTriangle, Filter, PackageOpen, ShieldCheck, Sparkles } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useMarketplaceCapabilities,
  type MarketplaceCapability,
  type MarketplaceItemType,
} from "@/hooks/queries/useHealthOsLauncher";

const TYPE_LABELS: Record<MarketplaceItemType, string> = {
  APP: "Apps",
  EXTENSION: "Extensions",
  PLUGIN: "Plugins",
  CONNECTOR: "Connectors",
  ADAPTER: "Adapters",
  WORKFLOW_PACK: "Workflow Packs",
  CONTENT_PACK: "Content Packs",
  AI_SKILL: "AI Skills",
  DEVICE_INTEGRATION: "Device Integrations",
};

const ALL_TYPES: MarketplaceItemType[] = [
  "APP",
  "EXTENSION",
  "PLUGIN",
  "CONNECTOR",
  "ADAPTER",
  "WORKFLOW_PACK",
  "CONTENT_PACK",
  "AI_SKILL",
  "DEVICE_INTEGRATION",
];

export default function CapabilityMarketplacePage() {
  const [typeFilter, setTypeFilter] = useState<MarketplaceItemType | undefined>(undefined);
  const [categoryFilter, setCategoryFilter] = useState<string>("");
  const [search, setSearch] = useState<string>("");

  const itemsQuery = useMarketplaceCapabilities({
    type: typeFilter,
    category: categoryFilter || undefined,
  });

  const allItems = itemsQuery.data ?? [];

  const filtered = useMemo<MarketplaceCapability[]>(() => {
    if (!search.trim()) return allItems;
    const q = search.trim().toLowerCase();
    return allItems.filter((i) =>
      [i.name, i.description, i.publisherName, i.category, i.itemCode]
        .filter(Boolean)
        .some((v) => v.toLowerCase().includes(q)),
    );
  }, [allItems, search]);

  const categories = useMemo(() => {
    const set = new Set<string>();
    allItems.forEach((i) => i.category && set.add(i.category));
    return Array.from(set).sort();
  }, [allItems]);

  return (
    <AppLayout>
      <PageShell
        title="Health OS Capability Marketplace"
        subtitle="Discover, request and govern apps, plugins, extensions, connectors, adapters, workflow packs, content packs, AI skills and device integrations approved for the sovereign Health OS."
      >
        <div className="space-y-6">
          <section className="grid gap-4 rounded-2xl border border-border bg-gradient-to-br from-white via-white to-slate-50 p-5 shadow-sm md:grid-cols-3">
            <Stat icon={PackageOpen} label="Capabilities listed" value={allItems.length} loading={itemsQuery.isLoading} error={itemsQuery.isError} />
            <Stat icon={ShieldCheck} label="Approved" value={allItems.filter((i) => i.approvalStatus === "APPROVED").length} loading={itemsQuery.isLoading} error={itemsQuery.isError} />
            <Stat icon={Sparkles} label="AI Skills" value={allItems.filter((i) => i.type === "AI_SKILL").length} loading={itemsQuery.isLoading} error={itemsQuery.isError} />
          </section>

          <section className="flex flex-wrap items-center gap-3 rounded-2xl border border-border bg-card p-4 shadow-sm">
            <div className="flex flex-1 items-center gap-2">
              <Filter className="h-4 w-4 text-muted-foreground" />
              <input
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Search by name, publisher, category"
                className="w-full min-w-[200px] flex-1 rounded-lg border border-border px-3 py-2 text-sm focus:border-impilo-500 focus:outline-none"
              />
            </div>
            <select
              value={typeFilter ?? ""}
              onChange={(e) => setTypeFilter((e.target.value || undefined) as MarketplaceItemType | undefined)}
              className="rounded-lg border border-border px-3 py-2 text-sm"
            >
              <option value="">All types</option>
              {ALL_TYPES.map((t) => (
                <option key={t} value={t}>
                  {TYPE_LABELS[t]}
                </option>
              ))}
            </select>
            <select
              value={categoryFilter}
              onChange={(e) => setCategoryFilter(e.target.value)}
              className="rounded-lg border border-border px-3 py-2 text-sm"
            >
              <option value="">All categories</option>
              {categories.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </select>
            <Link
              href="/marketplace/apps/admin/installations"
              className="rounded-lg border border-border px-3 py-2 text-sm font-medium text-foreground hover:bg-background"
            >
              Installations
            </Link>
            <Link
              href="/marketplace/apps/admin/activation"
              className="rounded-lg border border-border px-3 py-2 text-sm font-medium text-foreground hover:bg-background"
            >
              Approvals queue
            </Link>
            <Link
              href="/marketplace/apps/integration"
              className="rounded-lg border border-border px-3 py-2 text-sm font-medium text-foreground hover:bg-background"
            >
              Integration ops
            </Link>
          </section>

          {itemsQuery.isError ? (
            <div className="flex items-start gap-2 rounded-2xl border border-warning/35 bg-warning-soft p-4 text-sm text-warning-foreground">
              <AlertTriangle className="mt-0.5 h-4 w-4" />
              Couldn’t reach the Msika Apps catalogue via the Experience BFF. Verify {" "}
              <code className="text-[10px]">/internal/v1/marketplace/items</code> is wired and{" "}
              <code className="text-[10px]">msika-apps-service</code> is healthy.
            </div>
          ) : null}

          <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {filtered.map((item) => (
              <CapabilityCard key={item.id} item={item} />
            ))}
            {!itemsQuery.isLoading && filtered.length === 0 ? (
              <div className="col-span-full rounded-2xl border border-dashed border-border bg-card p-6 text-center text-sm text-muted-foreground">
                No capabilities match your filters.
              </div>
            ) : null}
          </section>
        </div>
      </PageShell>
    </AppLayout>
  );
}

function Stat({
  icon: Icon,
  label,
  value,
  loading,
  error,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  value: number;
  loading: boolean;
  error: boolean;
}) {
  return (
    <div className="flex items-center gap-3">
      <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary-soft text-primary-hover">
        <Icon className="h-5 w-5" />
      </div>
      <div>
        <div className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</div>
        <div className="text-2xl font-semibold text-foreground">
          {error ? "—" : loading ? "…" : value}
        </div>
      </div>
    </div>
  );
}

function CapabilityCard({ item }: { item: MarketplaceCapability }) {
  return (
    <Link
      href={`/marketplace/apps/${encodeURIComponent(item.itemCode)}`}
      className="group flex flex-col gap-3 rounded-2xl border border-border bg-card p-5 shadow-sm transition hover:border-impilo-300 hover:shadow-md"
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="text-[11px] font-semibold uppercase tracking-wider text-primary-hover">
            {TYPE_LABELS[item.type] ?? item.type}
          </div>
          <h3 className="mt-1 truncate text-base font-semibold text-foreground group-hover:text-primary-hover">
            {item.name}
          </h3>
          <p className="mt-1 line-clamp-2 text-sm text-muted-foreground">{item.description}</p>
        </div>
        <CapabilityBadge approvalStatus={item.approvalStatus} />
      </div>

      <div className="mt-auto flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
        <span>v{item.version}</span>
        <span>·</span>
        <span>{item.publisherName}</span>
        <span>·</span>
        <span className="rounded-full bg-neutral-100 px-2 py-0.5">{item.category}</span>
        {item.clinicalSafetyClassification ? (
          <span className="rounded-full bg-warning-soft px-2 py-0.5 text-warning-foreground">
            {item.clinicalSafetyClassification}
          </span>
        ) : null}
      </div>
    </Link>
  );
}

function CapabilityBadge({ approvalStatus }: { approvalStatus: string }) {
  const tone: Record<string, string> = {
    APPROVED: "bg-success-soft text-primary-hover",
    PENDING_REVIEW: "bg-warning-soft text-warning-foreground",
    REJECTED: "bg-danger-soft text-danger",
    DEPRECATED: "bg-neutral-100 text-muted-foreground",
    SUSPENDED: "bg-danger-soft text-danger",
  };
  return (
    <span
      className={`rounded-full px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide ${tone[approvalStatus] ?? "bg-neutral-100 text-foreground"}`}
    >
      {approvalStatus}
    </span>
  );
}
