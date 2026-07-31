"use client";

/**
 * OF-B27 — /my/monitoring — the citizen's remote-monitoring home.
 *
 * §14.6 patient-wording law throughout: plain language, what was observed and
 * what to do next, NEVER a diagnosis or raw risk grade, every alert carries
 * the standing safety line. Quality stamps are made humane ("needs a
 * re-check"), device silence becomes "reconnect your device".
 *
 * Honest deferrals: caregiver (MVUMO-delegated) views, manual
 * reading-submission and notification-channel preferences are not built yet
 * and are shown as deferred — nothing fake renders.
 */

import { useMemo } from "react";
import { AlertTriangle, HeartPulse, Info, Loader2, MonitorSmartphone } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useAlertEpisodes,
  useMyDeviceAssignments,
  useMyMonitoringPlans,
  usePlanReadings,
} from "@/hooks/queries/useTelemonitoring";
import { isSmbpUnavailableError, useSmbpVerdict } from "@/hooks/queries/useConfidentialMaternity";
import {
  deviceStaleNotice,
  metricPlainPhrase,
  patientAlertNotice,
  planPlainStatus,
  readingQualityLabel,
} from "@/lib/telemonitoring/patient-wording";
import { smbpUnavailableNotice, smbpVerdictBadge } from "@/lib/maternity/smbp-wording";
import type { MonitoringPlanView } from "@/lib/telemonitoring/types";

/** Home-BP verdict card for a monitoring plan — programme-gated so it never shows for non-BP plans. */
function SmbpVerdictCard({ planId }: { planId: string }) {
  // dangerSignPresent is intentionally omitted here — this card reads a series she has already
  // submitted, it does not ask her a new question, so the field must stay unset, never `false`.
  const verdict = useSmbpVerdict(planId);

  if (verdict.isLoading) {
    return (
      <p className="mt-3 flex items-center gap-2 text-sm text-slate-500">
        <Loader2 className="h-4 w-4 animate-spin" /> Checking your blood-pressure readings…
      </p>
    );
  }

  if (verdict.error) {
    if (isSmbpUnavailableError(verdict.error)) {
      return (
        <div className="mt-3 flex items-start gap-2 rounded-xl border border-slate-200 bg-slate-50 p-3 text-sm text-slate-600">
          <Info className="mt-0.5 h-4 w-4 shrink-0" />
          <span>{smbpUnavailableNotice(verdict.error)}</span>
        </div>
      );
    }
    // Any other failure stays quiet here rather than duplicating the page-level error banner.
    return null;
  }

  const data = verdict.data?.data;
  if (!data) return null;

  const badge = smbpVerdictBadge(data.verdict);
  const isUrgent = data.verdict === "URGENT" || data.verdict === "ELEVATED_REFER";

  return (
    <div className={`mt-3 rounded-xl border p-3 ${badge.className}`}>
      <div className="flex items-center gap-2">
        {isUrgent ? <AlertTriangle className="h-4 w-4 shrink-0" /> : null}
        <span className="text-sm font-semibold">{badge.label}</span>
      </div>
      <p className="mt-1 text-sm">{data.message}</p>
      {data.note ? <p className="mt-1 text-xs opacity-80">{data.note}</p> : null}
      {data.alerts.length > 0 ? (
        <ul className="mt-2 space-y-1">
          {data.alerts.map((a) => (
            <li key={a.code} className="text-xs">
              <span className="font-medium">{a.message}</span>
              {a.requiredAction ? <span> — {a.requiredAction}</span> : null}
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  );
}

function errText(e: unknown): string {
  if (e && typeof e === "object" && (e as { status?: number }).status) {
    return "We couldn't load this right now. Please try again in a moment.";
  }
  return "We couldn't load this right now. Please try again in a moment.";
}

function PlanCard({ plan }: { plan: MonitoringPlanView }) {
  const readings = usePlanReadings(plan.id, 0, 10);
  const alerts = useAlertEpisodes({ planId: plan.id });

  const latest = readings.data?.readings?.[0] ?? null;
  const staleNotice = useMemo(
    () => deviceStaleNotice(latest?.measuredAt ?? null),
    [latest?.measuredAt],
  );

  const liveAlerts = (alerts.data ?? []).filter((a) => a.status !== "RESOLVED");

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <h2 className="text-base font-semibold text-slate-900">{planPlainStatus(plan)}</h2>
      {plan.reviewDueAt ? (
        <p className="mt-0.5 text-xs text-slate-500">
          Your next planned check-in with your care team is around{" "}
          {new Date(plan.reviewDueAt).toLocaleDateString()}.
        </p>
      ) : null}

      {/* Home blood-pressure series verdict — hypertension plans only, §4 contract wording */}
      {plan.programmeCode === "HYPERTENSION" ? <SmbpVerdictCard planId={plan.id} /> : null}

      {/* Alert notices — §14.6 wording law */}
      {liveAlerts.length > 0 ? (
        <div className="mt-3 space-y-2">
          {liveAlerts.map((a) => {
            const notice = patientAlertNotice(a);
            return (
              <div key={a.episodeId} className="rounded-xl border border-amber-200 bg-amber-50 p-3">
                <p className="text-sm font-medium text-amber-900">{notice.headline}</p>
                <p className="mt-1 text-sm text-amber-800">{notice.guidance}</p>
                <p className="mt-1 text-xs text-amber-700">{notice.safetyLine}</p>
              </div>
            );
          })}
        </div>
      ) : null}

      {/* Recent readings */}
      <div className="mt-4">
        <h3 className="mb-1 text-sm font-medium text-slate-700">Your recent readings</h3>
        {readings.isLoading ? (
          <p className="flex items-center gap-2 text-sm text-slate-500">
            <Loader2 className="h-4 w-4 animate-spin" /> Loading your readings…
          </p>
        ) : readings.error ? (
          <p className="text-sm text-slate-500">{errText(readings.error)}</p>
        ) : (readings.data?.readings?.length ?? 0) === 0 ? (
          <p className="text-sm text-slate-500">
            No readings yet — once your device sends its first reading it will appear here.
          </p>
        ) : (
          <ul className="divide-y divide-slate-100">
            {(readings.data?.readings ?? []).map((r) => {
              const quality = readingQualityLabel(r);
              return (
                <li key={r.id} className="flex flex-wrap items-center justify-between gap-2 py-2">
                  <div>
                    <span className="text-sm font-medium capitalize text-slate-800">
                      {metricPlainPhrase(r.metricCode)}
                    </span>
                    <span className="ml-2 text-sm text-slate-700">
                      {r.metricValue} {r.unit ?? ""}
                    </span>
                    {quality ? <span className="ml-2 text-xs text-amber-700">{quality}</span> : null}
                  </div>
                  <span className="text-xs text-slate-400">
                    {new Date(r.measuredAt).toLocaleString()}
                  </span>
                </li>
              );
            })}
          </ul>
        )}
      </div>

      {staleNotice && plan.status === "ACTIVE" ? (
        <div className="mt-3 flex items-start gap-2 rounded-xl border border-sky-200 bg-sky-50 p-3 text-sm text-sky-900">
          <MonitorSmartphone className="mt-0.5 h-4 w-4 shrink-0" />
          <span>{staleNotice}</span>
        </div>
      ) : null}
    </section>
  );
}

function MyDevices() {
  const { data, isLoading, error } = useMyDeviceAssignments();
  if (isLoading || error) return null; // devices also surface per-plan; stay quiet rather than alarm
  const active = (data ?? []).filter((a) => a.status === "ACTIVE" || a.status === "ASSIGNED");
  if (active.length === 0) return null;
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <h2 className="text-sm font-medium text-slate-700">Your monitoring devices</h2>
      <ul className="mt-2 space-y-1.5">
        {active.map((a) => (
          <li key={a.assignmentId} className="flex items-center gap-2 text-sm text-slate-700">
            <MonitorSmartphone className="h-4 w-4 text-slate-400" />
            <span>{a.deviceRef}</span>
            <span className="text-xs text-slate-500">
              {a.status === "ACTIVE"
                ? "set up and active"
                : "assigned — your care team will help you set it up"}
            </span>
          </li>
        ))}
      </ul>
    </section>
  );
}

export default function MyMonitoringPage() {
  const plans = useMyMonitoringPlans();

  const visiblePlans = (plans.data ?? []).filter(
    (p) => p.status === "ACTIVE" || p.status === "SUSPENDED" || p.status === "PENDING_APPROVAL",
  );

  return (
    <AppLayout>
      <PageShell
        title="My monitoring"
        subtitle="Your health plan, your readings and your device — explained in plain language."
        icon={<HeartPulse className="h-5 w-5" />}
        density="hero"
      >
        {plans.isLoading ? (
          <p className="flex items-center gap-2 py-8 text-sm text-slate-500">
            <Loader2 className="h-4 w-4 animate-spin" /> Loading your monitoring plan…
          </p>
        ) : plans.error ? (
          <div className="rounded-2xl border border-slate-200 bg-white p-6 text-sm text-slate-600">
            {errText(plans.error)}
          </div>
        ) : visiblePlans.length === 0 ? (
          <div className="rounded-2xl border border-slate-200 bg-white p-6">
            <p className="text-sm text-slate-700">
              You don&apos;t have a remote-monitoring plan at the moment.
            </p>
            <p className="mt-1 text-xs text-slate-500">
              If your clinician starts one for you — for example for blood pressure or blood sugar —
              it will appear here with your readings and what they mean.
            </p>
          </div>
        ) : (
          <div className="space-y-4">
            {visiblePlans.map((p) => (
              <PlanCard key={p.id} plan={p} />
            ))}
            <MyDevices />
          </div>
        )}

        <div className="mt-4 flex items-start gap-2 rounded-2xl border border-slate-200 bg-slate-50 p-4 text-xs text-slate-500">
          <Info className="mt-0.5 h-4 w-4 shrink-0" />
          <span>
            Coming later (not yet available, honestly): caregiver access for a family member,
            typing in a reading yourself, and choosing how you get notified. Your care team
            currently records readings through your connected device.
          </span>
        </div>
      </PageShell>
    </AppLayout>
  );
}
