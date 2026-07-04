"use client";

/**
 * Telemedicine operations & helpdesk — /work/telemedicine/operations
 *
 * Real operational data only:
 *  - RTC transport health (rtc-gateway via BFF)
 *  - Facility teleconsult backlog / SLA snapshot (PCT via BFF)
 *  - Lifecycle analytics aggregates (analytics-pipeline via BFF)
 *  - Specialty workbench (submitted referrals awaiting specialty response)
 *
 * Per-session failure diagnostics (ICE/media failures, device state) are an
 * honest gap: rtc webhook events flow to Kafka (W0) but no query API exposes
 * them yet — stated in the helpdesk panel instead of faking a table.
 */

import Link from "next/link";
import { useState } from "react";
import {
  Activity,
  AlertTriangle,
  ArrowLeft,
  BarChart3,
  Loader2,
  MonitorCheck,
  Stethoscope,
  Wrench,
} from "lucide-react";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import {
  useTelemedicineOpsSla,
  useTelemedicineRtcHealth,
  useTelemedicineSpecialtyWorkbench,
} from "@/hooks/queries/useTelemedicine";
import { useTelemedicineSlaAggregates } from "@/hooks/queries/useTelemedicineAnalytics";

export default function TelemedicineOperationsPage() {
  const facility = useFacilityStore((s) => s.facility);
  const rtcHealth = useTelemedicineRtcHealth();
  const opsSla = useTelemedicineOpsSla(facility?.id ?? null);
  const analytics = useTelemedicineSlaAggregates(
    facility?.id ? { facilityId: facility.id } : undefined,
  );
  const [specialty, setSpecialty] = useState("");
  const workbench = useTelemedicineSpecialtyWorkbench({
    facilityId: facility?.id ?? null,
    specialty: specialty || null,
  });

  const rtc = rtcHealth.data?.data;
  const ops = opsSla.data?.data;
  const agg = analytics.data?.data;
  const workbenchItems = extractWorkbenchItems(workbench.data?.data);

  return (
    <div className="mx-auto max-w-6xl space-y-6 p-6">
      <header>
        <Link
          href="/work/telemedicine"
          className="mb-2 inline-flex items-center gap-1 text-sm text-slate-500 hover:text-teal-700"
        >
          <ArrowLeft className="h-4 w-4" /> Telemedicine operating model
        </Link>
        <div className="flex items-center gap-3">
          <MonitorCheck className="h-7 w-7 text-teal-600" />
          <div>
            <h1 className="text-2xl font-semibold text-slate-900">Operations &amp; helpdesk</h1>
            <p className="text-sm text-slate-500">
              Live transport, backlog and lifecycle signals from the real teleconsult stack.
              {facility ? ` Facility context: ${facility.name}.` : " No facility context selected."}
            </p>
          </div>
        </div>
      </header>

      <section className="grid grid-cols-1 gap-4 md:grid-cols-3">
        {/* RTC transport */}
        <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
          <h2 className="mb-2 flex items-center gap-2 text-sm font-medium text-slate-700">
            <Activity className="h-4 w-4 text-teal-600" /> RTC transport health
          </h2>
          {rtcHealth.isLoading ? (
            <LoadingInline />
          ) : rtc ? (
            <dl className="space-y-1 text-sm text-slate-600">
              <div className="flex justify-between">
                <dt>Provider</dt>
                <dd className="font-medium text-slate-800">{rtc.provider}</dd>
              </div>
              <div className="flex justify-between">
                <dt>LiveKit enabled</dt>
                <dd>{rtc.livekitEnabled ? "yes" : "no"}</dd>
              </div>
              <div className="flex justify-between">
                <dt>Configured</dt>
                <dd className={rtc.livekitConfigured ? "text-emerald-600" : "text-amber-600"}>
                  {rtc.livekitConfigured ? "yes" : "no"}
                </dd>
              </div>
              <div className="flex justify-between">
                <dt>Production ready</dt>
                <dd className={rtc.productionReady ? "text-emerald-600" : "text-amber-600"}>
                  {rtc.productionReady ? "yes" : "no"}
                </dd>
              </div>
              {typeof rtc.activeSessions === "number" ? (
                <div className="flex justify-between">
                  <dt>Active sessions</dt>
                  <dd className="font-medium text-slate-800">{rtc.activeSessions}</dd>
                </div>
              ) : null}
            </dl>
          ) : (
            <p className="text-sm text-slate-400">RTC gateway health unavailable.</p>
          )}
        </div>

        {/* Facility backlog */}
        <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
          <h2 className="mb-2 flex items-center gap-2 text-sm font-medium text-slate-700">
            <Stethoscope className="h-4 w-4 text-teal-600" /> Facility backlog (PCT)
          </h2>
          {!facility ? (
            <p className="text-sm text-slate-400">Select a facility context to load its backlog.</p>
          ) : opsSla.isLoading ? (
            <LoadingInline />
          ) : ops ? (
            <dl className="space-y-1 text-sm text-slate-600">
              <div className="flex justify-between">
                <dt>Submitted referral backlog</dt>
                <dd className="font-medium text-slate-800">{ops.submittedReferralBacklog}</dd>
              </div>
              <div className="flex justify-between">
                <dt>In-progress sessions</dt>
                <dd className="font-medium text-slate-800">{ops.inProgressSessions}</dd>
              </div>
              <div className="flex justify-between">
                <dt>Scheduled</dt>
                <dd className="font-medium text-slate-800">{ops.scheduledSessions}</dd>
              </div>
              <div className="flex justify-between">
                <dt>Overdue scheduled</dt>
                <dd className={ops.overdueScheduledSessions > 0 ? "font-medium text-red-600" : ""}>
                  {ops.overdueScheduledSessions}
                </dd>
              </div>
            </dl>
          ) : (
            <p className="text-sm text-slate-400">Backlog snapshot unavailable.</p>
          )}
        </div>

        {/* Lifecycle analytics */}
        <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
          <h2 className="mb-2 flex items-center gap-2 text-sm font-medium text-slate-700">
            <BarChart3 className="h-4 w-4 text-teal-600" /> Lifecycle analytics
          </h2>
          {analytics.isLoading ? (
            <LoadingInline />
          ) : agg ? (
            <div className="text-sm text-slate-600">
              <div className="mb-1 flex justify-between">
                <span>Events accepted</span>
                <span className="font-medium text-slate-800">{agg.eventsAccepted ?? 0}</span>
              </div>
              {agg.eventsByType && Object.keys(agg.eventsByType).length > 0 ? (
                <ul className="space-y-0.5 text-xs text-slate-500">
                  {Object.entries(agg.eventsByType).map(([type, count]) => (
                    <li key={type} className="flex justify-between">
                      <span className="font-mono">{type}</span>
                      <span>{count}</span>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="text-xs text-slate-400">No lifecycle events recorded yet.</p>
              )}
            </div>
          ) : (
            <p className="text-sm text-slate-400">Analytics pipeline unavailable.</p>
          )}
        </div>
      </section>

      {/* Specialty workbench */}
      <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        <div className="mb-3 flex items-center justify-between gap-4">
          <h2 className="flex items-center gap-2 text-base font-semibold text-slate-800">
            <Stethoscope className="h-5 w-5 text-teal-600" /> Specialty workbench — submitted
            referrals
          </h2>
          <input
            value={specialty}
            onChange={(e) => setSpecialty(e.target.value)}
            placeholder="Filter by specialty…"
            className="w-56 rounded-md border border-slate-300 px-3 py-1.5 text-sm focus:border-teal-500 focus:outline-none"
          />
        </div>
        {workbench.isLoading ? (
          <LoadingInline />
        ) : workbenchItems.length === 0 ? (
          <p className="text-sm text-slate-400">
            No submitted referrals awaiting specialty response
            {specialty ? ` for “${specialty}”` : ""}.
          </p>
        ) : (
          <ul className="divide-y divide-slate-100">
            {workbenchItems.slice(0, 25).map((item, index) => (
              <li key={String(item.id ?? index)} className="flex items-center justify-between gap-3 py-2 text-sm">
                <div>
                  <span className="font-medium text-slate-800">
                    {String(item.specialty ?? item.clinical_question ?? item.clinicalQuestion ?? "Referral")}
                  </span>
                  <span className="ml-2 text-xs text-slate-400">
                    {String(item.urgency ?? "routine")}
                  </span>
                </div>
                {item.id ? (
                  <Link
                    href={`/telemedicine/session/${String(item.id)}`}
                    className="text-xs font-medium text-teal-600 hover:underline"
                  >
                    Open
                  </Link>
                ) : null}
              </li>
            ))}
          </ul>
        )}
      </section>

      {/* Helpdesk — honest gap */}
      <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        <h2 className="mb-2 flex items-center gap-2 text-base font-semibold text-slate-800">
          <Wrench className="h-5 w-5 text-teal-600" /> Helpdesk diagnostics
        </h2>
        <div className="rounded-md border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">
          <div className="mb-1 flex items-center gap-2 font-medium">
            <AlertTriangle className="h-4 w-4" /> Per-session failure diagnostics not yet queryable
          </div>
          <p>
            LiveKit webhook events (joins, disconnects, media failures) are ingested by the RTC
            gateway and published to <span className="font-mono">impilo.rtc.*</span> Kafka topics
            (W0 workstream), but no query API exposes per-session failure reasons, participant
            device state or ICE diagnostics yet. Until that API exists this panel will not show a
            pretend failure table. Transport-level readiness above is live; retry and reschedule
            run through the existing session workspace.
          </p>
        </div>
      </section>
    </div>
  );
}

function extractWorkbenchItems(payload: unknown): Array<Record<string, unknown>> {
  if (Array.isArray(payload)) return payload as Array<Record<string, unknown>>;
  if (payload && typeof payload === "object") {
    const obj = payload as Record<string, unknown>;
    for (const key of ["items", "content", "referrals", "data"]) {
      const inner = obj[key];
      if (Array.isArray(inner)) return inner as Array<Record<string, unknown>>;
    }
  }
  return [];
}

function LoadingInline() {
  return (
    <p className="inline-flex items-center gap-1 text-sm text-slate-400">
      <Loader2 className="h-3.5 w-3.5 animate-spin" /> Loading…
    </p>
  );
}
