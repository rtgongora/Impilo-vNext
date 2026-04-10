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
import {
  usePublicHealthAlerts,
  usePublicHealthCampaigns,
  usePublicHealthCases,
  usePublicHealthCounters,
  usePublicHealthSignals,
  usePublicHealthSites,
} from "@/hooks/queries/usePublicHealth";
import {
  formatPublicHealthCompact,
  isPublicHealthCampaignActive,
  isPublicHealthCaseOpen,
  isPublicHealthSignalActive,
} from "./publicHealthDashboardUtils";

export function PublicHealthDashboard() {
  const signalsQ = usePublicHealthSignals();
  const casesQ = usePublicHealthCases();
  const alertsQ = usePublicHealthAlerts();
  const campaignsQ = usePublicHealthCampaigns();
  const countersQ = usePublicHealthCounters();
  const sitesQ = usePublicHealthSites();

  const allStillPending =
    signalsQ.isPending &&
    casesQ.isPending &&
    alertsQ.isPending &&
    campaignsQ.isPending &&
    countersQ.isPending &&
    sitesQ.isPending;

  const errors = [
    signalsQ.isError && "signals",
    casesQ.isError && "cases",
    alertsQ.isError && "alerts",
    campaignsQ.isError && "campaigns",
    countersQ.isError && "counters",
    sitesQ.isError && "sites",
  ].filter(Boolean) as string[];

  const signals = signalsQ.data ?? [];
  const cases = casesQ.data ?? [];
  const alerts = alertsQ.data ?? [];
  const campaigns = campaignsQ.data ?? [];
  const counters = countersQ.data ?? [];
  const sites = sitesQ.data ?? [];

  const activeSignals = signals.filter((s) => isPublicHealthSignalActive(s.status));
  const openCases = cases.filter((c) => isPublicHealthCaseOpen(c.status));
  const openAlerts = alerts.filter((a) => a.status !== "acknowledged");
  const activeCampaigns = campaigns.filter((c) => isPublicHealthCampaignActive(c.status));
  const peopleReached = campaigns.reduce((sum, c) => sum + (c.reachedPopulation || 0), 0);
  const siteCount =
    sites.filter((s) => {
      const st = s.operationalStatus.toUpperCase();
      return st === "ACTIVE" || st === "OPERATIONAL" || st === "OPEN";
    }).length || sites.length;

  const kpis = [
    {
      Icon: Activity,
      value: String(activeSignals.length),
      label: "Active signals",
      color: "text-red-700",
      bg: "bg-red-50",
      border: "border-red-200",
    },
    {
      Icon: ClipboardCheck,
      value: String(openCases.length),
      label: "Open surveillance cases",
      color: "text-blue-700",
      bg: "bg-blue-50",
      border: "border-blue-200",
    },
    {
      Icon: AlertTriangle,
      value: String(openAlerts.length),
      label: "Open alerts",
      color: "text-amber-700",
      bg: "bg-amber-50",
      border: "border-amber-200",
    },
    {
      Icon: Megaphone,
      value: String(activeCampaigns.length),
      label: "Active campaigns",
      color: "text-green-700",
      bg: "bg-green-50",
      border: "border-green-200",
    },
    {
      Icon: Users,
      value: formatPublicHealthCompact(peopleReached),
      label: "People reached (campaigns)",
      color: "text-sky-700",
      bg: "bg-sky-50",
      border: "border-sky-200",
    },
    {
      Icon: MapPin,
      value: String(siteCount),
      label: "Field / Indawo sites",
      color: "text-indigo-700",
      bg: "bg-indigo-50",
      border: "border-indigo-200",
    },
  ];

  const recentCases = [...cases]
    .sort((a, b) => (b.reportedAt || "").localeCompare(a.reportedAt || ""))
    .slice(0, 5);

  const recentAlerts = [...alerts]
    .sort((a, b) => (b.detectedAt || "").localeCompare(a.detectedAt || ""))
    .slice(0, 5);

  const campaignPreview = campaigns.slice(0, 5);
  const counterPreview = counters.slice(0, 8);

  return (
    <div className="space-y-4">
      <div className="rounded-lg border border-amber-200 bg-amber-50/90 p-3 text-xs text-amber-950">
        <strong>Live summary:</strong> KPIs and lists below use the same Experience BFF public-health endpoints as Surveillance
        and Field Operations. Outbreak register, environmental complaints, and inspections tabs remain illustrative until
        those services are wired.
      </div>

      {errors.length > 0 && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-xs text-red-900">
          Some data sources failed to load: {errors.join(", ")}. Counts may be partial.
        </div>
      )}

      {allStillPending ? (
        <div className="flex items-center justify-center gap-2 py-12 text-sm text-gray-500">
          <Loader2 className="h-5 w-5 animate-spin" /> Loading public health summary…
        </div>
      ) : (
        <>
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3">
            {kpis.map((kpi) => (
              <div
                key={kpi.label}
                className={`${kpi.bg} rounded-lg border ${kpi.border} p-3 text-center`}
              >
                <kpi.Icon className={`h-5 w-5 mx-auto mb-1.5 ${kpi.color}`} />
                <p className={`text-xl font-bold ${kpi.color}`}>{kpi.value}</p>
                <p className="text-[10px] text-gray-600">{kpi.label}</p>
              </div>
            ))}
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="bg-white rounded-lg border border-gray-200 p-5">
              <h3 className="text-sm font-semibold text-gray-900 flex items-center gap-2 mb-3">
                <Siren className="h-4 w-4" /> Recent surveillance cases
              </h3>
              {recentCases.length === 0 ? (
                <p className="text-xs text-gray-500">No cases returned (empty register or service unavailable).</p>
              ) : (
                <div className="space-y-2">
                  {recentCases.map((ob) => (
                    <div key={ob.id} className="flex items-center justify-between p-2.5 border border-gray-200 rounded-lg">
                      <div>
                        <p className="font-medium text-sm text-gray-900">{ob.disease}</p>
                        <p className="text-xs text-gray-500">
                          {ob.facility} · {ob.patientRef}
                        </p>
                      </div>
                      <span className="px-2 py-0.5 rounded-full text-[10px] font-medium bg-slate-100 text-slate-700">
                        {ob.status}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="bg-white rounded-lg border border-gray-200 p-5">
              <h3 className="text-sm font-semibold text-gray-900 flex items-center gap-2 mb-3">
                <AlertTriangle className="h-4 w-4" /> Recent surveillance alerts
              </h3>
              {recentAlerts.length === 0 ? (
                <p className="text-xs text-gray-500">No alerts returned.</p>
              ) : (
                <div className="space-y-2">
                  {recentAlerts.map((c) => (
                    <div key={c.id} className="flex items-center justify-between p-2.5 border border-gray-200 rounded-lg">
                      <div>
                        <p className="font-medium text-sm text-gray-900">{c.title}</p>
                        <p className="text-xs text-gray-500">
                          {c.location} · {c.severity}
                        </p>
                      </div>
                      <span
                        className={`px-2 py-0.5 rounded-full text-[10px] font-medium ${
                          c.severity === "high" || c.severity === "critical"
                            ? "bg-red-100 text-red-700"
                            : "bg-amber-100 text-amber-700"
                        }`}
                      >
                        {c.status}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="bg-white rounded-lg border border-gray-200 p-5">
              <h3 className="text-sm font-semibold text-gray-900 flex items-center gap-2 mb-3">
                <Megaphone className="h-4 w-4" /> Campaign progress
              </h3>
              {campaignPreview.length === 0 ? (
                <p className="text-xs text-gray-500">No campaigns returned.</p>
              ) : (
                <div className="space-y-3">
                  {campaignPreview.map((c) => {
                    const pct =
                      c.targetPopulation > 0
                        ? Math.min(100, Math.round((c.reachedPopulation / c.targetPopulation) * 100))
                        : 0;
                    return (
                      <div key={c.id}>
                        <div className="flex justify-between text-xs mb-1">
                          <span className="font-medium text-gray-900">{c.name}</span>
                          <span className="text-gray-600">{c.targetPopulation > 0 ? `${pct}%` : "—"}</span>
                        </div>
                        <div className="w-full bg-gray-200 rounded-full h-2">
                          <div className="h-2 rounded-full bg-green-500" style={{ width: `${pct}%` }} />
                        </div>
                        <p className="text-[10px] text-gray-500 mt-0.5">
                          {formatPublicHealthCompact(c.reachedPopulation)} / {formatPublicHealthCompact(c.targetPopulation)} · {c.status}
                        </p>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>

            <div className="bg-white rounded-lg border border-gray-200 p-5">
              <h3 className="text-sm font-semibold text-gray-900 flex items-center gap-2 mb-3">
                <Radio className="h-4 w-4" /> Syndrome / counter feed
              </h3>
              <p className="text-[10px] text-gray-500 mb-2">
                Aggregated counter rows from public-health counters API (not the same as Weekly IDSR completeness in
                Surveillance).
              </p>
              {counterPreview.length === 0 ? (
                <p className="text-xs text-gray-500">No counter rows returned.</p>
              ) : (
                <div className="space-y-2">
                  {counterPreview.map((m) => (
                    <div key={m.id} className="flex justify-between text-xs gap-2">
                      <span className="font-medium text-gray-900 truncate">{m.label}</span>
                      <span className="text-gray-600 shrink-0">{m.value}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </>
      )}
    </div>
  );
}
