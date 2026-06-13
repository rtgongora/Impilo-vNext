"use client";

import {
  Activity,
  AlertTriangle,
  ClipboardCheck,
  Loader2,
  MapPin,
  Megaphone,
  Radio,
  Siren,
  Users,
} from "lucide-react";
import { NompiloHint } from "@/components/intelligent/NompiloHint";
import {
  useLlmProviderHealth,
  useNompiloPublicHealthSummary,
  usePublicHealthAlertRules,
  usePublicHealthDataQualityIssues,
  usePublicHealthDataSources,
  usePublicHealthOperationsHome,
} from "@/hooks/queries/usePublicHealth";
import { formatPublicHealthCompact } from "./publicHealthDashboardUtils";
import { PublicHealthMapPanel } from "./PublicHealthMapPanel";
import { PublicHealthReportsPanel } from "./PublicHealthReportsPanel";

export function PublicHealthDashboard() {
  const homeQ = usePublicHealthOperationsHome();
  const nompiloQ = useNompiloPublicHealthSummary();
  const rulesQ = usePublicHealthAlertRules();
  const sourcesQ = usePublicHealthDataSources();
  const dqQ = usePublicHealthDataQualityIssues();
  const providerHealthQ = useLlmProviderHealth();

  const home = homeQ.data;
  const kpis = home?.kpis ?? {};
  const worklist = home?.priorityWorklist ?? [];

  const kpiCards = [
    { Icon: Activity, value: String(kpis.active_signals ?? 0), label: "Active signals", color: "text-danger", bg: "bg-danger-soft", border: "border-danger/28" },
    { Icon: ClipboardCheck, value: String(kpis.open_cases ?? 0), label: "Open cases", color: "text-primary", bg: "bg-primary-soft", border: "border-primary/25" },
    { Icon: AlertTriangle, value: String(kpis.open_alerts ?? 0), label: "Open alerts", color: "text-warning-foreground", bg: "bg-warning-soft", border: "border-warning/35" },
    { Icon: Siren, value: String(kpis.active_outbreaks ?? 0), label: "Active outbreaks", color: "text-red-800", bg: "bg-red-100", border: "border-red-300" },
    { Icon: MapPin, value: String(kpis.field_tasks_open ?? 0), label: "Open field tasks", color: "text-primary-hover", bg: "bg-success-soft", border: "border-success/25" },
    { Icon: Users, value: String(kpis.open_investigations ?? 0), label: "Open investigations", color: "text-sky-700", bg: "bg-sky-50", border: "border-sky-200" },
  ];

  return (
    <div className="space-y-4">
      <div className="rounded-lg border border-success/25 bg-success-soft/90 p-3 text-xs text-emerald-950">
        <strong>Operations command centre:</strong> single aggregated read model from{" "}
        <code className="rounded bg-card/70 px-1">GET /internal/v1/public-health/operations-home</code> — outbreaks,
        field tasks, investigations, map markers, and intelligence briefs are now governed lifecycle APIs.
      </div>

      {homeQ.isError && (
        <div className="rounded-lg border border-danger/28 bg-danger-soft p-3 text-xs text-red-900">
          Operations home failed to load. Downstream counts may be unavailable.
        </div>
      )}

      {homeQ.isPending ? (
        <div className="flex items-center justify-center gap-2 py-12 text-sm text-muted-foreground">
          <Loader2 className="h-5 w-5 animate-spin" /> Loading operations home…
        </div>
      ) : (
        <>
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3">
            {kpiCards.map((kpi) => (
              <div key={kpi.label} className={`${kpi.bg} rounded-lg border ${kpi.border} p-3 text-center`}>
                <kpi.Icon className={`h-5 w-5 mx-auto mb-1.5 ${kpi.color}`} />
                <p className={`text-xl font-bold ${kpi.color}`}>{kpi.value}</p>
                <p className="text-[10px] text-muted-foreground">{kpi.label}</p>
              </div>
            ))}
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="bg-card rounded-lg border border-border p-5">
              <h3 className="text-sm font-semibold text-foreground flex items-center gap-2 mb-3">
                <Megaphone className="h-4 w-4" /> Priority worklist
              </h3>
              {worklist.length === 0 ? (
                <p className="text-xs text-muted-foreground">No priority items in the worklist.</p>
              ) : (
                <div className="space-y-2">
                  {worklist.map((item) => (
                    <div key={`${item.type}-${item.id}`} className="flex justify-between p-2.5 border border-border rounded-lg text-xs">
                      <span className="font-medium text-foreground">{item.label}</span>
                      <span className="text-muted-foreground">{item.type} · {item.priority}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="bg-card rounded-lg border border-border p-5">
              <h3 className="text-sm font-semibold text-foreground flex items-center gap-2 mb-3">
                <Radio className="h-4 w-4" /> Map coverage
              </h3>
              <p className="text-sm text-foreground">
                <span className="font-bold text-primary-hover">{home?.mapMarkerCount ?? 0}</span> geo-tagged markers on the operations map.
              </p>
              <p className="text-[10px] text-muted-foreground mt-2">
                Draft briefs pending: {formatPublicHealthCompact(kpis.draft_briefs ?? 0)}
              </p>
            </div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            <PublicHealthMapPanel />
            <PublicHealthReportsPanel />
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="rounded-lg border border-border bg-card p-4">
              <h4 className="text-sm font-semibold text-foreground">Alert rules</h4>
              <p className="mt-1 text-xs text-muted-foreground">{rulesQ.data?.length ?? 0} configured</p>
            </div>
            <div className="rounded-lg border border-border bg-card p-4">
              <h4 className="text-sm font-semibold text-foreground">Data sources</h4>
              <p className="mt-1 text-xs text-muted-foreground">
                {(sourcesQ.data ?? []).filter((s) => s.healthStatus !== "HEALTHY").length} unhealthy / {(sourcesQ.data ?? []).length} total
              </p>
            </div>
            <div className="rounded-lg border border-border bg-card p-4">
              <h4 className="text-sm font-semibold text-foreground">Data quality issues</h4>
              <p className="mt-1 text-xs text-muted-foreground">
                {(dqQ.data ?? []).filter((i) => i.status.toUpperCase() === "OPEN").length} open
              </p>
            </div>
          </div>
        </>
      )}

      {nompiloQ.data?.message && (
        <div className="space-y-2">
          <NompiloHint message={nompiloQ.data.message} suggestions={nompiloQ.data.suggestions} />
          <div className="rounded-lg border border-violet-200 bg-violet-50/70 p-3 text-xs text-violet-900">
            <p>
              Provider: <strong>{nompiloQ.data.provider}</strong> · fallback:{" "}
              <strong>{nompiloQ.data.fallbackUsed ? "yes" : "no"}</strong> · deterministic-only:{" "}
              <strong>{nompiloQ.data.deterministicOnly ? "yes" : "no"}</strong>
            </p>
            <p>
              Confidence: <strong>{nompiloQ.data.confidence.toFixed(2)}</strong> · approval required:{" "}
              <strong>{nompiloQ.data.requiresHumanApproval ? "yes" : "no"}</strong>
              {nompiloQ.data.auditRef ? <> · audit ref: <strong>{nompiloQ.data.auditRef}</strong></> : null}
            </p>
          </div>
        </div>
      )}

      <div className="rounded-lg border border-border bg-card p-4">
        <h4 className="text-sm font-semibold text-foreground">LLM provider operations</h4>
        <div className="mt-3 grid grid-cols-1 gap-2 md:grid-cols-2">
          {(providerHealthQ.data ?? []).map((provider) => (
            <div key={provider.provider} className="rounded border border-border p-2 text-xs">
              <p className="font-medium text-foreground">{provider.provider}</p>
              <p className="text-muted-foreground">
                enabled {provider.enabled ? "yes" : "no"} · healthy {provider.healthy ? "yes" : "no"} · avg latency {provider.avgLatencyMs}ms
              </p>
              <p className="text-muted-foreground">last error: {provider.lastError || "none"}</p>
            </div>
          ))}
        </div>
        {providerHealthQ.isPending && <p className="mt-2 text-xs text-muted-foreground">Loading provider health…</p>}
      </div>
    </div>
  );
}
