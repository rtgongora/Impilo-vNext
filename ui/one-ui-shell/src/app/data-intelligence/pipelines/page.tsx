"use client";

import Link from "next/link";
import { AlertTriangle, GitBranch, Loader2, Radio } from "lucide-react";
import { PlaneWorkspaceShell } from "@/components/workspace/PlaneWorkspaceShell";
import { TrustContextBanner } from "@/components/experience/TrustContextBanner";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";
import { useDataPipelineMetrics } from "@/hooks/queries/useDataPipelineMetrics";

const DATA_TABS = [
  { label: "Overview", href: "/data-intelligence" },
  { label: "Quality", href: "/data-intelligence/quality" },
  { label: "Pipelines", href: "/data-intelligence/pipelines" },
  { label: "Integration", href: "/data-intelligence/integration" },
  { label: "Reports", href: "/data-intelligence/reports" },
  { label: "Audit", href: "/data-intelligence/audit" },
];

function MetricCard({
  label,
  value,
  detail,
  tone = "slate",
}: {
  label: string;
  value: string;
  detail: string;
  tone?: "slate" | "emerald" | "amber" | "rose";
}) {
  const tones = {
    slate: "border-slate-200 bg-white text-slate-900",
    emerald: "border-emerald-200 bg-emerald-50 text-emerald-900",
    amber: "border-amber-200 bg-amber-50 text-amber-900",
    rose: "border-rose-200 bg-rose-50 text-rose-900",
  } as const;

  return (
    <div className={`rounded-xl border p-4 ${tones[tone]}`}>
      <p className="text-xs font-semibold uppercase tracking-wide opacity-80">{label}</p>
      <p className="mt-2 text-2xl font-semibold">{value}</p>
      <p className="mt-2 text-xs leading-relaxed opacity-90">{detail}</p>
    </div>
  );
}

export default function DataPipelinesPage() {
  const metrics = useDataPipelineMetrics();

  return (
    <PlaneWorkspaceShell
      title="Data pipelines & event streams"
      subtitle="Live integration-hub and core-transaction signals — data-pipeline-service metrics pending dedicated BFF surface"
      plane="Data & Intelligence Plane"
      maturity="partial"
      tabs={DATA_TABS}
      nompiloHint="Ask Nompilo which pipeline or integration route is failing and where to retry."
      relatedLinks={[
        { label: "Integration monitor", href: "/admin/integration-status" },
        { label: "Platform journey monitor", href: "/platform-journey" },
      ]}
    >
      <TrustContextBanner purposeOfUse="DATA_OPERATIONS" />

      <div className="mb-4 flex flex-wrap items-center gap-2">
        <FeatureMaturityBadge status="partial" detail="Hub + core-tx live when BFF up" />
        <FeatureMaturityBadge status="not_wired" detail="data-pipeline-service :8215 not in BFF UI yet" />
      </div>

      {metrics.isLoading ? (
        <div className="flex items-center gap-2 py-8 text-sm text-slate-500">
          <Loader2 className="h-4 w-4 animate-spin" />
          Loading pipeline signals from Experience BFF…
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <MetricCard
            label="Integration hub routes"
            value={metrics.integrationHubRouteCount == null ? "—" : String(metrics.integrationHubRouteCount)}
            detail={
              metrics.hubAvailable
                ? "Parsed from GET /internal/v1/integration-hub/routes"
                : "BFF or integration-hub unavailable — start Experience stack"
            }
            tone={metrics.hubAvailable ? "emerald" : "amber"}
          />
          <MetricCard
            label="Dead letters (page 0)"
            value={metrics.deadLetterTotal == null ? "—" : String(metrics.deadLetterTotal)}
            detail="GET /internal/v1/integration-hub/deadletters — failed adapter deliveries"
            tone={metrics.deadLetterTotal && metrics.deadLetterTotal > 0 ? "rose" : "slate"}
          />
          <MetricCard
            label="Core transactions (feed)"
            value={metrics.coreTransactionCount == null ? "—" : String(metrics.coreTransactionCount)}
            detail={
              metrics.transactionsAvailable
                ? "Composed operator feed from /internal/v1/core-transactions"
                : "Core transaction feed unavailable without live BFF"
            }
            tone={metrics.transactionsAvailable ? "emerald" : "amber"}
          />
          <MetricCard
            label="Transactions w/ failure modes"
            value={
              metrics.coreTransactionFailureCount == null ? "—" : String(metrics.coreTransactionFailureCount)
            }
            detail="Journey steps reporting blocked, offline, or payment failures"
            tone={metrics.coreTransactionFailureCount && metrics.coreTransactionFailureCount > 0 ? "rose" : "slate"}
          />
        </div>
      )}

      {!metrics.isLoading && metrics.isPartial ? (
        <div className="mt-6 flex gap-3 rounded-xl border border-amber-200 bg-amber-50/80 p-4 text-sm text-amber-950">
          <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0" />
          <div>
            <p className="font-medium">Partial pipeline visibility</p>
            <p className="mt-1 text-xs leading-relaxed text-amber-900/90">
              Wave 20 surfaces integration-hub and core-transaction telemetry. Direct{" "}
              <code className="text-[11px]">data-pipeline-service</code> watermarks and ingest lag require a future BFF
              proxy — not silently mocked here.
            </p>
          </div>
        </div>
      ) : null}

      <div className="mt-6 grid gap-3 sm:grid-cols-2">
        <Link
          href="/admin/integration-status"
          className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm font-medium text-slate-800 hover:border-impilo-200 hover:bg-impilo-50"
        >
          <Radio className="h-4 w-4 text-teal-600" />
          Full integration status admin
        </Link>
        <Link
          href="/platform-journey"
          className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm font-medium text-slate-800 hover:border-impilo-200 hover:bg-impilo-50"
        >
          <GitBranch className="h-4 w-4 text-impilo-600" />
          Platform journey monitor
        </Link>
      </div>
    </PlaneWorkspaceShell>
  );
}
