"use client";

/**
 * Surveillance dashboard — signals, cases, counters, alerts.
 * Route: /public-health/surveillance
 */

import Link from "next/link";
import { useMemo, useState } from "react";
import {
  Activity,
  AlertTriangle,
  Loader2,
  Radio,
  RefreshCw,
  Shield,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { NdilaMap } from "@/components/ndila/NdilaMap";
import { OrganizationPlaneContextBar } from "@/components/experience/OrganizationPlaneContextBar";
import { PageShell } from "@/components/PageShell";
import { SurveillanceOutbreakOrchestrationPanel } from "@/components/public-health/SurveillanceOutbreakOrchestrationPanel";
import {
  useAlerts,
  useCases,
  useCounters,
  useCreateCase,
  useCreateSignal,
  useIngestEvent,
  useSignals,
} from "@/hooks/queries/useSurveillance";

const DISTRICT_BUCKETS = [
  "Harare",
  "Bulawayo",
  "Manicaland",
  "Mashonaland Central",
  "Mashonaland East",
  "Mashonaland West",
  "Masvingo",
  "Matabeleland North",
  "Matabeleland South",
  "Midlands",
] as const;

function bucketDistrict(key: string): string {
  let h = 0;
  for (let i = 0; i < key.length; i++) h = (h + key.charCodeAt(i)) % DISTRICT_BUCKETS.length;
  return DISTRICT_BUCKETS[h];
}

function severityTone(sev: string): "red" | "amber" | "green" {
  const s = sev.toLowerCase();
  if (s.includes("high") || s.includes("critical") || s.includes("red")) return "red";
  if (s.includes("medium") || s.includes("moderate") || s.includes("amber") || s.includes("warn")) return "amber";
  return "green";
}

function badgeClass(status: string): string {
  const u = status.toUpperCase();
  if (u.includes("ACTIVE") || u.includes("OPEN")) return "bg-emerald-100 text-primary-hover";
  if (u.includes("CLOSED") || u.includes("RESOLVED")) return "bg-neutral-100 text-foreground";
  if (u.includes("ESCAL")) return "bg-rose-100 text-rose-800";
  return "bg-warning-soft text-warning-foreground";
}

export default function SurveillanceDashboardPage() {
  const [syndromeFilter, setSyndromeFilter] = useState("");
  const [periodFrom, setPeriodFrom] = useState("");
  const [periodTo, setPeriodTo] = useState("");

  const signalsQ = useSignals({ syndrome: syndromeFilter.trim() || undefined });
  const casesQ = useCases();
  const countersQ = useCounters(undefined, { from: periodFrom || undefined, to: periodTo || undefined });
  const alertsQ = useAlerts();

  const createSignalM = useCreateSignal();
  const ingestM = useIngestEvent();
  const createCaseM = useCreateCase();

  const [sigName, setSigName] = useState("");
  const [sigEventType, setSigEventType] = useState("");
  const [sigThreshold, setSigThreshold] = useState("3");
  const [ingestType, setIngestType] = useState("");
  const [ingestFacility, setIngestFacility] = useState("");
  const [caseEventType, setCaseEventType] = useState("");
  const [caseTitle, setCaseTitle] = useState("");

  const severitySummary = useMemo(() => {
    const alerts = alertsQ.data ?? [];
    let red = 0;
    let amber = 0;
    let green = 0;
    for (const a of alerts) {
      const t = severityTone(a.severity);
      if (t === "red") red++;
      else if (t === "amber") amber++;
      else green++;
    }
    return { red, amber, green, total: alerts.length };
  }, [alertsQ.data]);

  const districtSummary = useMemo(() => {
    const rows = countersQ.data ?? [];
    const map = new Map<string, number>();
    for (const c of rows) {
      const key = c.detail.split("·")[0]?.trim() || c.label;
      const d = bucketDistrict(key);
      map.set(d, (map.get(d) ?? 0) + Number(c.value || 0));
    }
    return [...map.entries()].sort((a, b) => b[1] - a[1]);
  }, [countersQ.data]);

  const errorBanner = (err: unknown) => {
    const msg =
      err && typeof err === "object" && "error" in err
        ? String((err as { error?: { message?: string } }).error?.message ?? "Request failed")
        : "Request failed";
    return (
      <div className="rounded-lg border border-danger/28 bg-danger-soft px-3 py-2 text-sm text-red-800 flex items-center gap-2">
        <AlertTriangle className="h-4 w-4 shrink-0" />
        {msg}
      </div>
    );
  };

  return (
    <AppLayout>
      <PageShell
        title="Surveillance dashboard"
        subtitle="Syndrome signals, case registry, counters, and live alerts (Experience BFF → surveillance-service)"
      >
        <OrganizationPlaneContextBar />
        <SurveillanceOutbreakOrchestrationPanel />
        <div className="mb-4 flex flex-wrap items-center gap-3 text-sm">
          <Link href="/public-health?tab=surveillance" className="text-indigo-600 hover:underline">
            ← Public health hub
          </Link>
        </div>

        <div className="space-y-8">
          <section className="rounded-xl border border-border bg-card p-4">
            <h2 className="text-sm font-semibold text-foreground mb-1">Surveillance geography (Ndila layers)</h2>
            <p className="text-xs text-muted-foreground mb-3">
              Sovereign map tiles with syndrome signal markers — facility tables below remain authoritative for counts.
            </p>
            <NdilaMap
              center={{ latitude: -17.8252, longitude: 31.0335 }}
              zoom={6}
              height={280}
              mode="DASHBOARD"
              layers={["surveillance", "facilities"]}
              markers={(signalsQ.data ?? []).slice(0, 12).map((s, index) => ({
                id: s.id || `signal-${index}`,
                label: s.disease,
                latitude: -17.8252 + (index % 4) * 0.35,
                longitude: 31.0335 + Math.floor(index / 4) * 0.45,
                markerType: "SURVEILLANCE_SIGNAL",
                status: s.status,
              }))}
              fitToMarkers
            />
          </section>

          <section>
            <h2 className="text-sm font-semibold text-foreground mb-3 flex items-center gap-2">
              <Radio className="h-4 w-4 text-muted-foreground" />
              Active alerts by severity
            </h2>
            {alertsQ.isLoading && (
              <p className="text-sm text-muted-foreground flex items-center gap-2">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading alerts…
              </p>
            )}
            {alertsQ.isError && errorBanner(alertsQ.error)}
            <div className="grid gap-3 sm:grid-cols-3">
              <div className="rounded-xl border border-danger/28 bg-danger-soft/80 p-4 shadow-sm">
                <p className="text-xs font-medium uppercase tracking-wide text-rose-800">Red / high</p>
                <p className="mt-2 text-3xl font-semibold text-danger">{severitySummary.red}</p>
                <p className="text-xs text-danger mt-1">Critical or high-priority surveillance items</p>
              </div>
              <div className="rounded-xl border border-warning/35 bg-warning-soft/80 p-4 shadow-sm">
                <p className="text-xs font-medium uppercase tracking-wide text-warning-foreground">Amber / watch</p>
                <p className="mt-2 text-3xl font-semibold text-warning-foreground">{severitySummary.amber}</p>
                <p className="text-xs text-warning-foreground mt-1">Medium priority — monitor closely</p>
              </div>
              <div className="rounded-xl border border-success/25 bg-success-soft/80 p-4 shadow-sm">
                <p className="text-xs font-medium uppercase tracking-wide text-primary-hover">Green / stable</p>
                <p className="mt-2 text-3xl font-semibold text-emerald-950">{severitySummary.green}</p>
                <p className="text-xs text-primary-hover mt-1">Low priority or routine signals</p>
              </div>
            </div>
            {!alertsQ.isLoading && severitySummary.total === 0 && (
              <p className="mt-2 text-sm text-muted-foreground">No alerts returned for this tenant.</p>
            )}
          </section>

          <section className="rounded-xl border border-border bg-card p-4 space-y-3">
            <h2 className="text-sm font-semibold text-foreground flex items-center gap-2">
              <Shield className="h-4 w-4 text-muted-foreground" />
              Operational actions
            </h2>
            <div className="grid gap-4 lg:grid-cols-3">
              <div className="space-y-2 rounded-lg border border-border p-3">
                <p className="text-xs font-medium text-muted-foreground">Create signal definition</p>
                <input
                  className="w-full rounded-md border border-border px-2 py-1.5 text-sm"
                  placeholder="Name"
                  value={sigName}
                  onChange={(e) => setSigName(e.target.value)}
                />
                <input
                  className="w-full rounded-md border border-border px-2 py-1.5 text-sm"
                  placeholder="Event type"
                  value={sigEventType}
                  onChange={(e) => setSigEventType(e.target.value)}
                />
                <input
                  className="w-full rounded-md border border-border px-2 py-1.5 text-sm"
                  placeholder="Threshold"
                  value={sigThreshold}
                  onChange={(e) => setSigThreshold(e.target.value)}
                />
                <button
                  type="button"
                  disabled={createSignalM.isPending}
                  onClick={() =>
                    createSignalM.mutate({
                      name: sigName,
                      description: "",
                      eventType: sigEventType,
                      conditionField: "count",
                      threshold: Number(sigThreshold) || 1,
                      windowHours: 24,
                      severity: "MEDIUM",
                    })
                  }
                  className="inline-flex items-center gap-1 rounded-md bg-neutral-900 px-3 py-1.5 text-xs font-medium text-white hover:bg-primary-hover disabled:opacity-50"
                >
                  {createSignalM.isPending ? <Loader2 className="h-3 w-3 animate-spin" /> : <RefreshCw className="h-3 w-3" />}
                  Create signal
                </button>
                {createSignalM.isError && errorBanner(createSignalM.error)}
                {createSignalM.isSuccess && <p className="text-xs text-primary-hover">Signal created.</p>}
              </div>
              <div className="space-y-2 rounded-lg border border-border p-3">
                <p className="text-xs font-medium text-muted-foreground">Ingest surveillance event</p>
                <input
                  className="w-full rounded-md border border-border px-2 py-1.5 text-sm"
                  placeholder="Event type"
                  value={ingestType}
                  onChange={(e) => setIngestType(e.target.value)}
                />
                <input
                  className="w-full rounded-md border border-border px-2 py-1.5 text-sm"
                  placeholder="Facility UUID (optional)"
                  value={ingestFacility}
                  onChange={(e) => setIngestFacility(e.target.value)}
                />
                <button
                  type="button"
                  disabled={ingestM.isPending}
                  onClick={() =>
                    ingestM.mutate({
                      eventType: ingestType,
                      payload: "{}",
                      facilityId: ingestFacility.trim() || undefined,
                    })
                  }
                  className="inline-flex items-center gap-1 rounded-md border border-border bg-card px-3 py-1.5 text-xs font-medium text-foreground hover:bg-background disabled:opacity-50"
                >
                  {ingestM.isPending ? <Loader2 className="h-3 w-3 animate-spin" /> : <Activity className="h-3 w-3" />}
                  Ingest event
                </button>
                {ingestM.isError && errorBanner(ingestM.error)}
              </div>
              <div className="space-y-2 rounded-lg border border-border p-3">
                <p className="text-xs font-medium text-muted-foreground">Case intake (via ingest)</p>
                <input
                  className="w-full rounded-md border border-border px-2 py-1.5 text-sm"
                  placeholder="Event type for case linkage"
                  value={caseEventType}
                  onChange={(e) => setCaseEventType(e.target.value)}
                />
                <input
                  className="w-full rounded-md border border-border px-2 py-1.5 text-sm"
                  placeholder="Title / syndrome"
                  value={caseTitle}
                  onChange={(e) => setCaseTitle(e.target.value)}
                />
                <button
                  type="button"
                  disabled={createCaseM.isPending}
                  onClick={() =>
                    createCaseM.mutate({
                      eventType: caseEventType,
                      title: caseTitle,
                      facilityId: ingestFacility.trim() || undefined,
                    })
                  }
                  className="inline-flex items-center gap-1 rounded-md bg-primary px-3 py-1.5 text-xs font-medium text-white hover:bg-indigo-800 disabled:opacity-50"
                >
                  {createCaseM.isPending ? <Loader2 className="h-3 w-3 animate-spin" /> : <Shield className="h-3 w-3" />}
                  Submit case intake
                </button>
                {createCaseM.isError && errorBanner(createCaseM.error)}
              </div>
            </div>
          </section>

          <div className="flex flex-wrap items-end gap-3 rounded-xl border border-border bg-card p-4">
            <label className="text-xs font-medium text-muted-foreground">
              Filter signals (syndrome text)
              <input
                className="mt-1 block w-48 rounded-md border border-border px-2 py-1.5 text-sm"
                value={syndromeFilter}
                onChange={(e) => setSyndromeFilter(e.target.value)}
              />
            </label>
            <label className="text-xs font-medium text-muted-foreground">
              Counters from (ISO date)
              <input
                className="mt-1 block w-40 rounded-md border border-border px-2 py-1.5 text-sm"
                value={periodFrom}
                onChange={(e) => setPeriodFrom(e.target.value)}
                placeholder="2025-01-01"
              />
            </label>
            <label className="text-xs font-medium text-muted-foreground">
              Counters to
              <input
                className="mt-1 block w-40 rounded-md border border-border px-2 py-1.5 text-sm"
                value={periodTo}
                onChange={(e) => setPeriodTo(e.target.value)}
              />
            </label>
          </div>

          <section className="rounded-xl border border-border bg-card overflow-hidden">
            <div className="border-b border-border px-4 py-3 flex items-center gap-2">
              <Activity className="h-4 w-4 text-muted-foreground" />
              <h2 className="text-sm font-semibold text-foreground">Signal detection</h2>
            </div>
            {signalsQ.isLoading && (
              <p className="px-4 py-6 text-sm text-muted-foreground flex items-center gap-2">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading signals…
              </p>
            )}
            {signalsQ.isError && <div className="p-4">{errorBanner(signalsQ.error)}</div>}
            {!signalsQ.isLoading && (signalsQ.data?.length ?? 0) === 0 && (
              <p className="px-4 py-6 text-sm text-muted-foreground">No signals for the current filters.</p>
            )}
            {(signalsQ.data?.length ?? 0) > 0 && (
              <table className="w-full text-sm">
                <thead className="bg-background text-left text-xs text-muted-foreground">
                  <tr>
                    <th className="px-3 py-2">Syndrome / signal</th>
                    <th className="px-3 py-2">Facility</th>
                    <th className="px-3 py-2 text-right">Count</th>
                    <th className="px-3 py-2 text-right">Threshold</th>
                    <th className="px-3 py-2">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {signalsQ.data!.map((s) => (
                    <tr key={s.id} className="border-t border-border">
                      <td className="px-3 py-2 font-medium text-foreground">{s.disease}</td>
                      <td className="px-3 py-2 text-muted-foreground">{s.facility}</td>
                      <td className="px-3 py-2 text-right">{s.cases}</td>
                      <td className="px-3 py-2 text-right">{s.threshold}</td>
                      <td className="px-3 py-2">
                        <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${badgeClass(s.status)}`}>
                          {s.status}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </section>

          <div className="grid gap-6 lg:grid-cols-2">
            <section className="rounded-xl border border-border bg-card overflow-hidden">
              <div className="border-b border-border px-4 py-3">
                <h2 className="text-sm font-semibold text-foreground">Case registry</h2>
              </div>
              {casesQ.isLoading && (
                <p className="px-4 py-6 text-sm text-muted-foreground flex items-center gap-2">
                  <Loader2 className="h-4 w-4 animate-spin" /> Loading cases…
                </p>
              )}
              {casesQ.isError && <div className="p-4">{errorBanner(casesQ.error)}</div>}
              {!casesQ.isLoading && (casesQ.data?.length ?? 0) === 0 && (
                <p className="px-4 py-6 text-sm text-muted-foreground">No cases returned.</p>
              )}
              {(casesQ.data?.length ?? 0) > 0 && (
                <table className="w-full text-sm">
                  <thead className="bg-background text-left text-xs text-muted-foreground">
                    <tr>
                      <th className="px-3 py-2">Case ID</th>
                      <th className="px-3 py-2">Syndrome</th>
                      <th className="px-3 py-2">Status</th>
                      <th className="px-3 py-2">Facility</th>
                      <th className="px-3 py-2">Opened</th>
                    </tr>
                  </thead>
                  <tbody>
                    {casesQ.data!.map((c) => (
                      <tr key={c.id} className="border-t border-border">
                        <td className="px-3 py-2 font-mono text-xs">{c.id || "—"}</td>
                        <td className="px-3 py-2">{c.disease}</td>
                        <td className="px-3 py-2">
                          <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${badgeClass(c.status)}`}>
                            {c.status}
                          </span>
                        </td>
                        <td className="px-3 py-2 text-muted-foreground">{c.facility}</td>
                        <td className="px-3 py-2 text-muted-foreground">{c.reportedAt || "—"}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </section>

            <section className="rounded-xl border border-border bg-card overflow-hidden">
              <div className="border-b border-border px-4 py-3">
                <h2 className="text-sm font-semibold text-foreground">Daily counter summary (by district bucket)</h2>
                <p className="text-xs text-muted-foreground mt-1">
                  Aggregated from facility-level counters when district metadata is unavailable.
                </p>
              </div>
              {countersQ.isLoading && (
                <p className="px-4 py-6 text-sm text-muted-foreground flex items-center gap-2">
                  <Loader2 className="h-4 w-4 animate-spin" /> Loading counters…
                </p>
              )}
              {countersQ.isError && <div className="p-4">{errorBanner(countersQ.error)}</div>}
              {!countersQ.isLoading && districtSummary.length === 0 && (
                <p className="px-4 py-6 text-sm text-muted-foreground">No counter rows for this period.</p>
              )}
              {districtSummary.length > 0 && (
                <table className="w-full text-sm">
                  <thead className="bg-background text-left text-xs text-muted-foreground">
                    <tr>
                      <th className="px-3 py-2">District (bucket)</th>
                      <th className="px-3 py-2 text-right">Events (sum)</th>
                    </tr>
                  </thead>
                  <tbody>
                    {districtSummary.map(([d, n]) => (
                      <tr key={d} className="border-t border-border">
                        <td className="px-3 py-2 font-medium">{d}</td>
                        <td className="px-3 py-2 text-right">{n}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </section>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
