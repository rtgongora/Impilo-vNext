"use client";

import {
  Activity,
  AlertTriangle,
  BarChart3,
  CheckCircle2,
  ClipboardList,
  Loader2,
  Route,
  ShieldCheck,
  Users,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useWellnessAggregate } from "@/hooks/queries/useSimbaWellnessCore";

interface Metric {
  key: string;
  label: string;
  icon: React.ReactNode;
  suffix?: string;
  tone: string;
}

/** Above-site wellness metrics — aggregate only, never client-level (mirrors WellnessAggregateService). */
const METRICS: Metric[] = [
  { key: "enrolled_persons", label: "Enrolled persons", icon: <Users className="h-5 w-5" />, tone: "text-emerald-600" },
  { key: "active_enrolments", label: "Active enrolments", icon: <Route className="h-5 w-5" />, tone: "text-emerald-600" },
  { key: "assessments_completed", label: "Assessments completed", icon: <ClipboardList className="h-5 w-5" />, tone: "text-sky-600" },
  { key: "screening_coverage_pct", label: "Screening coverage", icon: <ShieldCheck className="h-5 w-5" />, suffix: "%", tone: "text-sky-600" },
  { key: "screening_reminders_scheduled", label: "Screening reminders scheduled", icon: <Activity className="h-5 w-5" />, tone: "text-slate-600" },
  { key: "follow_ups_open", label: "Open follow-ups", icon: <ClipboardList className="h-5 w-5" />, tone: "text-amber-600" },
  { key: "follow_up_completion_pct", label: "Follow-up completion", icon: <CheckCircle2 className="h-5 w-5" />, suffix: "%", tone: "text-emerald-600" },
  { key: "high_risk_escalations", label: "High-risk escalations", icon: <AlertTriangle className="h-5 w-5" />, tone: "text-red-600" },
  { key: "care_linkages_routed", label: "Care linkages routed", icon: <Route className="h-5 w-5" />, tone: "text-red-600" },
];

/** Wellness insights — above-site aggregate dashboard for providers / programme admins. No cpid. */
export default function WellnessInsightsPage() {
  const q = useWellnessAggregate();
  const data = q.data?.data ?? {};

  return (
    <AppLayout>
      <PageShell
        title="Wellness insights"
        subtitle="Above-site wellness performance — screening coverage, risk escalations and follow-up completion. Aggregate only."
        icon={<BarChart3 className="h-6 w-6" />}
      >
        <div className="mx-auto max-w-4xl">
          {q.isLoading && (
            <div className="flex items-center justify-center py-16 text-slate-400">
              <Loader2 className="h-6 w-6 animate-spin" />
            </div>
          )}

          {q.isError && (
            <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
              You may not have access to aggregate wellness insights, or the data could not be loaded.
            </div>
          )}

          {!q.isLoading && !q.isError && (
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {METRICS.map((m) => (
                <div key={m.key} className="rounded-xl border border-slate-200 bg-white p-4">
                  <div className={`mb-2 inline-flex items-center gap-2 ${m.tone}`}>
                    {m.icon}
                    <span className="text-xs font-medium uppercase tracking-wide text-slate-500">
                      {m.label}
                    </span>
                  </div>
                  <div className="text-2xl font-semibold text-slate-800">
                    {Number(data[m.key] ?? 0).toLocaleString()}
                    {m.suffix ?? ""}
                  </div>
                </div>
              ))}
            </div>
          )}

          <p className="mt-6 text-xs text-slate-400">
            Figures are population-level totals for your facility / programme scope. No individual
            client records are shown here.
          </p>
        </div>
      </PageShell>
    </AppLayout>
  );
}
