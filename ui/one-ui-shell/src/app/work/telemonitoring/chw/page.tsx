"use client";

/**
 * OF-B23 — /work/telemonitoring/chw — the CHW monitoring view (web).
 *
 * My monitoring tasks come from the REAL PCT generic-task lane
 * (`/v1/tasks/my` via the existing BFF mobile-provider proxy) — the tasks
 * telemonitoring raises on plan activation (MONITORING_ONBOARDING_VISIT) and
 * on the §14.7 rung-4 CHW dispatch (MONITORING_ALERT_FOLLOWUP). Episodes with
 * a dispatched CHW task are listed as visits needing attention.
 *
 * Visit capture is NOT rebuilt here: the link goes to the existing community
 * visit surface (/wellness/community — community units + outreach visits over
 * community-service). Offline-first doorstep capture remains the provider
 * mobile app (R71) and is stated honestly, not imitated.
 */

import { ClipboardList, ExternalLink, Loader2, MapPin, Users } from "lucide-react";
import Link from "next/link";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAlertEpisodes, useMyMonitoringTasks } from "@/hooks/queries/useTelemonitoring";
import { responseWindow } from "@/lib/telemonitoring/triage";

const TASK_TYPE_LABEL: Record<string, string> = {
  MONITORING_ONBOARDING_VISIT: "Onboarding visit — help the patient start their monitoring plan",
  MONITORING_ALERT_FOLLOWUP: "Alert follow-up — repeat the measurement and check on the patient",
};

function errText(): string {
  return "Couldn't load right now — please retry.";
}

export default function ChwMonitoringPage() {
  const tasks = useMyMonitoringTasks();
  const episodes = useAlertEpisodes();

  const chwEpisodes = (episodes.data ?? []).filter(
    (e) => e.status !== "RESOLVED" && e.chwTaskDispatched === true,
  );

  return (
    <AppLayout>
      <PageShell
        title="CHW monitoring"
        subtitle="Your monitoring tasks and the alert follow-ups dispatched to community health workers — scope-safe: repeat the measurement, run the checklist, help the patient reach care."
        icon={<Users className="h-5 w-5" />}
        density="compact"
      >
        <div className="space-y-5">
          {/* My monitoring tasks (PCT generic-task lane) */}
          <section className="rounded-xl border border-slate-200 bg-white p-4">
            <h2 className="mb-2 flex items-center gap-2 text-sm font-semibold text-slate-800">
              <ClipboardList className="h-4 w-4" /> My monitoring tasks
            </h2>
            {tasks.isLoading ? (
              <p className="flex items-center gap-2 text-sm text-slate-500">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading your tasks…
              </p>
            ) : tasks.error ? (
              <p className="text-sm text-rose-700">
                {errText()}{" "}
                <button type="button" className="underline" onClick={() => tasks.refetch()}>
                  Retry
                </button>
              </p>
            ) : (tasks.data?.length ?? 0) === 0 ? (
              <p className="text-sm text-slate-500">
                No monitoring tasks assigned to you right now. Tasks appear here when a plan is
                activated in your area or an alert dispatches a CHW visit.
              </p>
            ) : (
              <ul className="divide-y divide-slate-100">
                {(tasks.data ?? []).map((t, i) => (
                  <li key={String(t.id ?? i)} className="py-2">
                    <p className="text-sm font-medium text-slate-800">
                      {TASK_TYPE_LABEL[t.taskType ?? ""] ?? (t.taskType ?? "Monitoring task")}
                    </p>
                    <p className="text-xs text-slate-500">
                      {t.status ? `Status ${t.status}` : "Status unknown"}
                      {t.dueAt ? ` · due ${new Date(String(t.dueAt)).toLocaleString()}` : ""}
                    </p>
                    {t.notes ? <p className="mt-0.5 text-xs text-slate-600">{String(t.notes)}</p> : null}
                  </li>
                ))}
              </ul>
            )}
          </section>

          {/* Episodes needing a CHW visit */}
          <section className="rounded-xl border border-slate-200 bg-white p-4">
            <h2 className="mb-2 flex items-center gap-2 text-sm font-semibold text-slate-800">
              <MapPin className="h-4 w-4" /> Alerts with a CHW visit dispatched
            </h2>
            {episodes.isLoading ? (
              <p className="flex items-center gap-2 text-sm text-slate-500">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading…
              </p>
            ) : episodes.error ? (
              <p className="text-sm text-rose-700">{errText()}</p>
            ) : chwEpisodes.length === 0 ? (
              <p className="text-sm text-slate-500">No open alerts with a dispatched CHW visit.</p>
            ) : (
              <ul className="divide-y divide-slate-100">
                {chwEpisodes.map((e) => {
                  const window_ = responseWindow(e);
                  return (
                    <li key={e.episodeId} className="flex flex-wrap items-center justify-between gap-2 py-2">
                      <div>
                        <p className="text-sm font-medium text-slate-800">
                          Repeat the measurement and check on patient {e.patientCpid}
                        </p>
                        <p className="text-xs text-slate-500">
                          {e.metricCode ? `${e.metricCode} alert` : "Monitoring alert"} · severity{" "}
                          {e.severity} · your task: measure again, run the checklist, help the
                          patient contact their care team — escalate with one tap if unwell.
                        </p>
                      </div>
                      <span className={`text-xs ${window_.overdue ? "font-semibold text-rose-700" : "text-slate-500"}`}>
                        {window_.label}
                      </span>
                    </li>
                  );
                })}
              </ul>
            )}
          </section>

          {/* Visit capture — link, never a rebuild */}
          <section className="rounded-xl border border-emerald-200 bg-emerald-50 p-4">
            <h2 className="text-sm font-semibold text-emerald-900">Capture a visit</h2>
            <p className="mt-1 text-xs text-emerald-800">
              Visit capture lives in the community workspace (units, outreach visits, start/complete)
              — one capture flow, not two.
            </p>
            <Link
              href="/wellness/community"
              className="mt-2 inline-flex items-center gap-1 rounded-md bg-emerald-700 px-3 py-1.5 text-xs font-semibold text-white"
            >
              Open community visit capture <ExternalLink className="h-3 w-3" />
            </Link>
            <p className="mt-2 text-[11px] text-emerald-700">
              Offline-first doorstep capture (queued readings, per-reading patient attribution,
              break-glass) is the provider mobile app — this web view does not imitate offline
              capability it doesn&apos;t have.
            </p>
          </section>

          <p className="text-[11px] text-slate-400">
            Deferred honestly: household-grouped cohort navigation and the rejected-reading
            reconciliation queue (§15.8) need lanes PCT/offline-edge do not yet expose to the web
            shell; danger-sign checklists and Nompilo technique guidance land with the guidance
            wave.
          </p>
        </div>
      </PageShell>
    </AppLayout>
  );
}
