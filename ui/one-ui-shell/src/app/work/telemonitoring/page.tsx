"use client";

/**
 * OF-B28 — /work/telemonitoring — the §14.8 remote-monitoring command
 * workspace (clinical desk).
 *
 * Triage board of live alert episodes (OPEN / ACKNOWLEDGED+IN PROGRESS /
 * ESCALATED), severity-sorted with response-window countdowns; per-episode
 * drill-down with contributing readings (quality stamps visible), ladder
 * history and act-from-the-board actions; plan lookup with thresholds,
 * newest-first readings, review recording and honest device-posture badges.
 *
 * Honest-gap doctrine: panel-scoped caseloads, shift handover and programme
 * dashboards are NOT built — nothing pretends otherwise. Everything rendered
 * is backed by the live telemonitoring proxy.
 */

import { useMemo, useState } from "react";
import { Activity, AlertTriangle, ClipboardCheck, Loader2, MonitorSmartphone, RefreshCw } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { AccountableClosureForm } from "@/components/telemonitoring/AccountableClosureForm";
import { DevicePostureBadge } from "@/components/telemonitoring/DevicePostureBadge";
import {
  useAcknowledgeEpisode,
  useAlertEpisodes,
  useDeviceAssignments,
  useMonitoringPlans,
  usePlanReadings,
  useRecordAssumptionOfCare,
  useRecordLadderStep,
  useRecordPlanReview,
  useResolveEpisode,
  useStartEpisodeProgress,
  useThresholdProfiles,
} from "@/hooks/queries/useTelemonitoring";
import { LADDER_RUNGS, responseWindow, rungLabel, triageSort } from "@/lib/telemonitoring/triage";
import type { AlertEpisodeView, MonitoringPlanView, ReadingView } from "@/lib/telemonitoring/types";

const SEVERITY_TONE: Record<string, string> = {
  EMERGENCY: "bg-rose-600 text-white",
  RED: "bg-red-100 text-red-800",
  AMBER: "bg-amber-100 text-amber-800",
};

const READING_STATUS_LABEL: Record<string, string> = {
  VALID: "valid",
  DEGRADED_VALID: "degraded — excluded from value alerts",
  QUARANTINED: "quarantined — evidence only, not chart content",
};

function errMessage(e: unknown): string {
  if (e && typeof e === "object") {
    const obj = e as { error?: { code?: string; message?: string }; status?: number };
    if (obj.error?.message) {
      return obj.error.code ? `${obj.error.code}: ${obj.error.message}` : obj.error.message;
    }
    if (obj.status) return `Request failed (HTTP ${obj.status}).`;
  }
  return "The request failed. Please try again.";
}

function EpisodeCard({
  episode,
  selected,
  onSelect,
}: {
  episode: AlertEpisodeView;
  selected: boolean;
  onSelect: () => void;
}) {
  const window_ = responseWindow(episode);
  return (
    <button
      type="button"
      onClick={onSelect}
      data-testid={`episode-card-${episode.episodeId}`}
      className={`w-full rounded-lg border p-3 text-left text-sm transition ${
        selected ? "border-slate-900 bg-slate-50" : "border-slate-200 bg-white hover:border-slate-400"
      }`}
    >
      <div className="flex items-center justify-between gap-2">
        <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${SEVERITY_TONE[episode.severity] ?? "bg-slate-100 text-slate-700"}`}>
          {episode.severity}
        </span>
        {window_.remainingMs !== null ? (
          <span className={`text-xs font-medium ${window_.overdue ? "text-rose-700" : "text-slate-500"}`}>
            {window_.overdue ? "⚠ " : ""}
            {window_.label}
          </span>
        ) : (
          <span className="text-xs text-slate-400">{window_.label}</span>
        )}
      </div>
      <div className="mt-1 font-medium text-slate-800">
        {episode.triggerClass.replaceAll("_", " ").toLowerCase()}
        {episode.metricCode ? ` · ${episode.metricCode}` : ""}
      </div>
      <div className="mt-0.5 text-xs text-slate-500">
        Patient {episode.patientCpid} · breaches {episode.breachCount}
        {episode.currentRung != null ? ` · rung ${episode.currentRung}` : ""}
      </div>
    </button>
  );
}

function ContributingReadingsTable({ episode }: { episode: AlertEpisodeView }) {
  const rows = Array.isArray(episode.contributingReadings) ? episode.contributingReadings : [];
  if (rows.length === 0) {
    return <p className="text-xs text-slate-500">No contributing-reading snapshots on this episode.</p>;
  }
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-xs">
        <thead className="text-slate-500">
          <tr>
            <th className="py-1 pr-3">Metric</th>
            <th className="py-1 pr-3">Value</th>
            <th className="py-1 pr-3">Measured</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={i} className="border-t border-slate-100">
              <td className="py-1 pr-3">{String(r.metricCode ?? episode.metricCode ?? "—")}</td>
              <td className="py-1 pr-3">
                {String(r.value ?? r.metricValue ?? "—")} {String(r.unit ?? "")}
              </td>
              <td className="py-1 pr-3">{r.measuredAt ? new Date(String(r.measuredAt)).toLocaleString() : "—"}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function LadderTimeline({ episode }: { episode: AlertEpisodeView }) {
  const nodes = Array.isArray(episode.ladderHistory) ? episode.ladderHistory : [];
  if (nodes.length === 0) {
    return <p className="text-xs text-slate-500">No ladder actions recorded yet.</p>;
  }
  return (
    <ol className="space-y-1.5">
      {nodes.map((n, i) => (
        <li key={i} className="flex items-start gap-2 text-xs">
          <span className="mt-0.5 inline-flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-slate-900 text-[10px] font-bold text-white">
            {String(n.rung ?? "·")}
          </span>
          <span>
            <span className="font-medium text-slate-800">{rungLabel(typeof n.rung === "number" ? n.rung : null)}</span>
            {n.actor ? <span className="text-slate-500"> — {String(n.actor)}</span> : null}
            {n.note ? <span className="block text-slate-500">{String(n.note)}</span> : null}
            {n.at ? <span className="block text-slate-400">{new Date(String(n.at)).toLocaleString()}</span> : null}
          </span>
        </li>
      ))}
    </ol>
  );
}

function EpisodeDetail({ episode }: { episode: AlertEpisodeView }) {
  const acknowledge = useAcknowledgeEpisode();
  const startProgress = useStartEpisodeProgress();
  const ladderStep = useRecordLadderStep();
  const assumption = useRecordAssumptionOfCare();
  const resolve = useResolveEpisode();

  const [rung, setRung] = useState<number>(episode.currentRung ?? 1);
  const [rungNote, setRungNote] = useState("");

  const live = episode.status !== "RESOLVED";
  const canAcknowledge = episode.status === "OPEN";
  const canStart = episode.status === "ACKNOWLEDGED" || episode.status === "ESCALATED";
  const canResolve = live && episode.status !== "OPEN";

  const actionError: string | null = acknowledge.error
    ? errMessage(acknowledge.error)
    : startProgress.error
      ? errMessage(startProgress.error)
      : ladderStep.error
        ? errMessage(ladderStep.error)
        : null;

  return (
    <div className="space-y-4 rounded-xl border border-slate-200 bg-white p-4">
      <div className="flex flex-wrap items-center gap-2">
        <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${SEVERITY_TONE[episode.severity] ?? "bg-slate-100 text-slate-700"}`}>
          {episode.severity}
        </span>
        <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-700">
          {episode.status.replaceAll("_", " ")}
        </span>
        {episode.severityCeilingApplied ? (
          <span className="rounded-full bg-sky-100 px-2 py-0.5 text-xs text-sky-800" title="Consumer-grade device readings cannot alone raise critical severity.">
            severity ceiling applied
          </span>
        ) : null}
        {episode.chwTaskDispatched ? (
          <span className="rounded-full bg-emerald-100 px-2 py-0.5 text-xs text-emerald-800">CHW task dispatched</span>
        ) : null}
        {episode.emsIncidentRef ? (
          <span className="rounded-full bg-rose-100 px-2 py-0.5 text-xs text-rose-800">EMS {episode.emsIncidentRef}</span>
        ) : null}
      </div>

      <dl className="grid grid-cols-2 gap-2 text-xs sm:grid-cols-4">
        <div>
          <dt className="text-slate-500">Patient</dt>
          <dd className="font-medium text-slate-800">{episode.patientCpid}</dd>
        </div>
        <div>
          <dt className="text-slate-500">Plan</dt>
          <dd className="font-medium text-slate-800">{episode.planId}</dd>
        </div>
        <div>
          <dt className="text-slate-500">Device</dt>
          <dd className="font-medium text-slate-800">{episode.deviceRef ?? "—"}</dd>
        </div>
        <div>
          <dt className="text-slate-500">Opened</dt>
          <dd className="font-medium text-slate-800">{new Date(episode.createdAt).toLocaleString()}</dd>
        </div>
      </dl>

      <div>
        <h4 className="mb-1 text-xs font-semibold uppercase tracking-wide text-slate-500">Contributing readings</h4>
        <ContributingReadingsTable episode={episode} />
      </div>

      <div>
        <h4 className="mb-1 text-xs font-semibold uppercase tracking-wide text-slate-500">Escalation ladder history</h4>
        <LadderTimeline episode={episode} />
      </div>

      {live ? (
        <div className="space-y-3 border-t border-slate-100 pt-3">
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              disabled={!canAcknowledge || acknowledge.isPending}
              onClick={() => acknowledge.mutate({ episodeId: episode.episodeId })}
              className="rounded-md bg-slate-900 px-3 py-1.5 text-xs font-semibold text-white disabled:cursor-not-allowed disabled:opacity-40"
            >
              {acknowledge.isPending ? "Acknowledging…" : "Acknowledge"}
            </button>
            <button
              type="button"
              disabled={!canStart || startProgress.isPending}
              onClick={() => startProgress.mutate({ episodeId: episode.episodeId })}
              className="rounded-md border border-slate-300 px-3 py-1.5 text-xs font-semibold text-slate-700 disabled:cursor-not-allowed disabled:opacity-40"
            >
              {startProgress.isPending ? "Starting…" : "Start progress"}
            </button>
          </div>

          <div className="rounded-lg border border-slate-200 p-3">
            <h5 className="mb-2 text-xs font-semibold text-slate-700">Record an escalation-ladder rung</h5>
            <div className="flex flex-wrap items-end gap-2">
              <label className="text-xs text-slate-600">
                Rung
                <select
                  value={rung}
                  onChange={(e) => setRung(Number(e.target.value))}
                  className="mt-1 block rounded-md border border-slate-300 p-1.5 text-xs"
                >
                  {LADDER_RUNGS.map((r) => (
                    <option key={r.rung} value={r.rung}>
                      {r.rung} — {r.label}
                    </option>
                  ))}
                </select>
              </label>
              <label className="flex-1 text-xs text-slate-600">
                Note
                <input
                  value={rungNote}
                  onChange={(e) => setRungNote(e.target.value)}
                  className="mt-1 block w-full rounded-md border border-slate-300 p-1.5 text-xs"
                  placeholder="What was done at this rung"
                />
              </label>
              <button
                type="button"
                disabled={ladderStep.isPending}
                onClick={() =>
                  ladderStep.mutate({ episodeId: episode.episodeId, rung, note: rungNote || undefined })
                }
                className="rounded-md border border-slate-900 px-3 py-1.5 text-xs font-semibold text-slate-900 disabled:opacity-40"
              >
                {ladderStep.isPending ? "Recording…" : "Record rung"}
              </button>
            </div>
          </div>

          {actionError ? <p className="rounded-md bg-rose-50 p-2 text-xs text-rose-700">{actionError}</p> : null}

          {canResolve ? (
            <div className="rounded-lg border border-slate-200 p-3">
              <h5 className="mb-2 text-xs font-semibold text-slate-700">Accountable closure</h5>
              <AccountableClosureForm
                episode={episode}
                busy={resolve.isPending}
                errorMessage={resolve.error ? errMessage(resolve.error) : null}
                onResolve={(closure) => resolve.mutate({ episodeId: episode.episodeId, ...closure })}
                onRecordAssumptionOfCare={(note) =>
                  assumption.mutate({ episodeId: episode.episodeId, note })
                }
                assumptionBusy={assumption.isPending}
              />
            </div>
          ) : (
            <p className="text-xs text-slate-500">
              Closure opens after acknowledgement — an unacknowledged episode cannot be resolved.
            </p>
          )}
        </div>
      ) : (
        <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-xs text-emerald-900">
          Closed by {episode.resolvedBy} — {episode.assessment} · {episode.actionTaken} · {episode.outcome}
          {episode.resolvedAt ? ` (${new Date(episode.resolvedAt).toLocaleString()})` : ""}
        </div>
      )}
    </div>
  );
}

function TriageBoard() {
  const { data, isLoading, error, refetch, isFetching } = useAlertEpisodes();
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const live = useMemo(() => triageSort((data ?? []).filter((e) => e.status !== "RESOLVED")), [data]);
  const columns: { key: string; title: string; items: AlertEpisodeView[] }[] = useMemo(
    () => [
      { key: "OPEN", title: "Open", items: live.filter((e) => e.status === "OPEN") },
      {
        key: "ACKNOWLEDGED",
        title: "Acknowledged / in progress",
        items: live.filter((e) => e.status === "ACKNOWLEDGED" || e.status === "IN_PROGRESS"),
      },
      { key: "ESCALATED", title: "Escalated", items: live.filter((e) => e.status === "ESCALATED") },
    ],
    [live],
  );

  const selected = live.find((e) => e.episodeId === selectedId) ?? null;

  if (isLoading) {
    return (
      <div className="flex items-center gap-2 py-10 text-sm text-slate-500">
        <Loader2 className="h-4 w-4 animate-spin" /> Loading the alert queue…
      </div>
    );
  }
  if (error) {
    return (
      <div className="rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">
        <AlertTriangle className="mb-1 h-4 w-4" /> {errMessage(error)}
        <button type="button" onClick={() => refetch()} className="ml-3 underline">
          Retry
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <p className="text-xs text-slate-500">
          {live.length} live episode{live.length === 1 ? "" : "s"} · severity-sorted · refreshes every 30s
        </p>
        <button
          type="button"
          onClick={() => refetch()}
          className="inline-flex items-center gap-1 rounded-md border border-slate-300 px-2 py-1 text-xs text-slate-600"
        >
          <RefreshCw className={`h-3 w-3 ${isFetching ? "animate-spin" : ""}`} /> Refresh
        </button>
      </div>
      <div className="grid gap-3 md:grid-cols-3">
        {columns.map((col) => (
          <div key={col.key} className="rounded-xl bg-slate-100 p-2">
            <h3 className="mb-2 px-1 text-xs font-semibold uppercase tracking-wide text-slate-600">
              {col.title} ({col.items.length})
            </h3>
            <div className="space-y-2">
              {col.items.length === 0 ? (
                <p className="px-1 pb-2 text-xs text-slate-400">None.</p>
              ) : (
                col.items.map((e) => (
                  <EpisodeCard
                    key={e.episodeId}
                    episode={e}
                    selected={e.episodeId === selectedId}
                    onSelect={() => setSelectedId(e.episodeId)}
                  />
                ))
              )}
            </div>
          </div>
        ))}
      </div>
      {selected ? <EpisodeDetail key={selected.episodeId} episode={selected} /> : null}
    </div>
  );
}

function parseThresholdRows(parametersJson: string | null | undefined): { key: string; value: string }[] {
  if (!parametersJson) return [];
  try {
    const parsed: unknown = JSON.parse(parametersJson);
    if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
      return Object.entries(parsed as Record<string, unknown>).map(([key, value]) => ({
        key,
        value:
          value && typeof value === "object"
            ? Object.entries(value as Record<string, unknown>)
                .map(([k, v]) => `${k}: ${String(v)}`)
                .join(", ")
            : String(value),
      }));
    }
  } catch {
    // fall through to raw hint
  }
  return [{ key: "parameters", value: "unparseable threshold payload — inspect upstream" }];
}

function PlanDetail({ plan }: { plan: MonitoringPlanView }) {
  const thresholds = useThresholdProfiles(plan.id);
  const readings = usePlanReadings(plan.id, 0, 50);
  const assignments = useDeviceAssignments({ planId: plan.id });
  const recordReview = useRecordPlanReview();

  const current = useMemo(() => {
    const list = thresholds.data ?? [];
    return list.length ? [...list].sort((a, b) => b.version - a.version)[0] : null;
  }, [thresholds.data]);

  return (
    <div className="space-y-4 rounded-xl border border-slate-200 bg-white p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h3 className="text-sm font-semibold text-slate-800">
            {plan.programmeCode} plan · {plan.status}
          </h3>
          <p className="text-xs text-slate-500">
            Patient {plan.patientCpid}
            {plan.reviewDueAt ? ` · review due ${new Date(plan.reviewDueAt).toLocaleDateString()}` : ""}
            {plan.consentStatus ? ` · consent ${plan.consentStatus}` : ""}
          </p>
        </div>
        <button
          type="button"
          disabled={recordReview.isPending || plan.status !== "ACTIVE"}
          onClick={() => recordReview.mutate({ planId: plan.id })}
          className="inline-flex items-center gap-1 rounded-md bg-slate-900 px-3 py-1.5 text-xs font-semibold text-white disabled:cursor-not-allowed disabled:opacity-40"
          title={plan.status !== "ACTIVE" ? "Reviews are recorded against active plans." : undefined}
        >
          <ClipboardCheck className="h-3.5 w-3.5" />
          {recordReview.isPending ? "Recording…" : "Record completed review"}
        </button>
      </div>
      {recordReview.error ? (
        <p className="rounded-md bg-rose-50 p-2 text-xs text-rose-700">{errMessage(recordReview.error)}</p>
      ) : null}
      {recordReview.isSuccess ? (
        <p className="rounded-md bg-emerald-50 p-2 text-xs text-emerald-700">
          Review recorded — the review-cadence timer has been re-armed.
        </p>
      ) : null}

      <div>
        <h4 className="mb-1 text-xs font-semibold uppercase tracking-wide text-slate-500">
          Current thresholds {current ? `(version ${current.version})` : ""}
        </h4>
        {thresholds.isLoading ? (
          <p className="text-xs text-slate-500">Loading thresholds…</p>
        ) : current ? (
          <table className="w-full text-left text-xs">
            <tbody>
              {parseThresholdRows(current.parametersJson).map((row) => (
                <tr key={row.key} className="border-t border-slate-100">
                  <td className="py-1 pr-3 font-medium text-slate-700">{row.key}</td>
                  <td className="py-1 text-slate-600">{row.value}</td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <p className="text-xs text-slate-500">No threshold profile recorded for this plan.</p>
        )}
      </div>

      <div>
        <h4 className="mb-1 text-xs font-semibold uppercase tracking-wide text-slate-500">
          Readings (newest first)
        </h4>
        {readings.isLoading ? (
          <p className="text-xs text-slate-500">Loading readings…</p>
        ) : readings.error ? (
          <p className="text-xs text-rose-700">{errMessage(readings.error)}</p>
        ) : (readings.data?.readings?.length ?? 0) === 0 ? (
          <p className="text-xs text-slate-500">No readings received for this plan yet.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="text-slate-500">
                <tr>
                  <th className="py-1 pr-3">Measured</th>
                  <th className="py-1 pr-3">Metric</th>
                  <th className="py-1 pr-3">Value</th>
                  <th className="py-1 pr-3">Quality</th>
                  <th className="py-1 pr-3">SHR</th>
                </tr>
              </thead>
              <tbody>
                {(readings.data?.readings ?? []).map((r: ReadingView) => (
                  <tr key={r.id} className="border-t border-slate-100">
                    <td className="py-1 pr-3">{new Date(r.measuredAt).toLocaleString()}</td>
                    <td className="py-1 pr-3">{r.metricCode}</td>
                    <td className="py-1 pr-3">
                      {r.metricValue} {r.unit ?? ""}
                    </td>
                    <td className="py-1 pr-3">
                      <span
                        className={
                          r.status === "VALID"
                            ? "text-emerald-700"
                            : r.status === "DEGRADED_VALID"
                              ? "text-amber-700"
                              : "text-rose-700"
                        }
                        title={r.quarantineReason ?? undefined}
                      >
                        {READING_STATUS_LABEL[r.status] ?? r.status}
                      </span>
                    </td>
                    <td className="py-1 pr-3 text-slate-500">{r.shrWriteState}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            {(readings.data?.totalElements ?? 0) > 50 ? (
              <p className="mt-1 text-[11px] text-slate-400">
                Showing the latest 50 of {readings.data?.totalElements} readings.
              </p>
            ) : null}
          </div>
        )}
      </div>

      <div>
        <h4 className="mb-1 text-xs font-semibold uppercase tracking-wide text-slate-500">Device assignments</h4>
        {assignments.isLoading ? (
          <p className="text-xs text-slate-500">Loading assignments…</p>
        ) : (assignments.data?.length ?? 0) === 0 ? (
          <p className="text-xs text-slate-500">No devices assigned under this plan.</p>
        ) : (
          <ul className="space-y-1.5">
            {(assignments.data ?? []).map((a) => (
              <li key={a.assignmentId} className="flex flex-wrap items-center gap-2 text-xs">
                <MonitorSmartphone className="h-3.5 w-3.5 text-slate-400" />
                <span className="font-medium text-slate-800">{a.deviceRef}</span>
                <span className="rounded-full bg-slate-100 px-2 py-0.5 text-slate-600">{a.status}</span>
                <DevicePostureBadge deviceRef={a.deviceRef} />
                {!a.trainingConfirmed && a.status === "ASSIGNED" ? (
                  <span className="text-amber-700">training not confirmed</span>
                ) : null}
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}

function PlansPanel() {
  const [cpidInput, setCpidInput] = useState("");
  const [patientCpid, setPatientCpid] = useState<string | null>(null);
  const [selectedPlanId, setSelectedPlanId] = useState<string | null>(null);
  const plans = useMonitoringPlans(patientCpid ?? undefined);

  const selectedPlan = (plans.data ?? []).find((p) => p.id === selectedPlanId) ?? null;

  return (
    <div className="space-y-4">
      <form
        className="flex flex-wrap items-end gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          const value = cpidInput.trim();
          if (!value) return;
          setSelectedPlanId(null);
          setPatientCpid(value);
        }}
      >
        <label className="text-xs text-slate-600">
          Patient CPID
          <input
            value={cpidInput}
            onChange={(e) => setCpidInput(e.target.value)}
            className="mt-1 block w-64 rounded-md border border-slate-300 p-2 text-sm"
            placeholder="The plan list is patient-scoped"
          />
        </label>
        <button
          type="submit"
          disabled={cpidInput.trim().length === 0}
          className="rounded-md bg-slate-900 px-3 py-2 text-xs font-semibold text-white disabled:opacity-40"
        >
          Look up plans
        </button>
      </form>
      <p className="text-[11px] text-slate-400">
        A cross-panel caseload listing is not yet exposed by telemonitoring-service — lookup is
        by patient, honestly.
      </p>

      {patientCpid ? (
        plans.isLoading ? (
          <p className="text-xs text-slate-500">Loading plans…</p>
        ) : plans.error ? (
          <p className="rounded-md bg-rose-50 p-2 text-xs text-rose-700">{errMessage(plans.error)}</p>
        ) : (plans.data?.length ?? 0) === 0 ? (
          <p className="text-xs text-slate-500">No monitoring plans for this patient.</p>
        ) : (
          <div className="flex flex-wrap gap-2">
            {(plans.data ?? []).map((p) => (
              <button
                key={p.id}
                type="button"
                onClick={() => setSelectedPlanId(p.id)}
                className={`rounded-lg border px-3 py-2 text-left text-xs ${
                  p.id === selectedPlanId
                    ? "border-slate-900 bg-slate-50"
                    : "border-slate-200 bg-white hover:border-slate-400"
                }`}
              >
                <span className="block font-semibold text-slate-800">{p.programmeCode}</span>
                <span className="text-slate-500">{p.status}</span>
              </button>
            ))}
          </div>
        )
      ) : null}

      {selectedPlan ? <PlanDetail key={selectedPlan.id} plan={selectedPlan} /> : null}
    </div>
  );
}

export default function TelemonitoringCommandPage() {
  const [tab, setTab] = useState<"triage" | "plans">("triage");

  return (
    <AppLayout>
      <PageShell
        title="Remote monitoring command"
        subtitle="Alert triage with accountable closure, plan drill-down and honest device posture — §14.8 clinical desk over the live telemonitoring engine."
        icon={<Activity className="h-5 w-5" />}
        density="compact"
      >
        <div className="mb-4 flex gap-2">
          <button
            type="button"
            onClick={() => setTab("triage")}
            className={`rounded-md px-3 py-1.5 text-xs font-semibold ${
              tab === "triage" ? "bg-slate-900 text-white" : "border border-slate-300 text-slate-600"
            }`}
          >
            Alert triage
          </button>
          <button
            type="button"
            onClick={() => setTab("plans")}
            className={`rounded-md px-3 py-1.5 text-xs font-semibold ${
              tab === "plans" ? "bg-slate-900 text-white" : "border border-slate-300 text-slate-600"
            }`}
          >
            Plans &amp; devices
          </button>
        </div>
        {tab === "triage" ? <TriageBoard /> : <PlansPanel />}
      </PageShell>
    </AppLayout>
  );
}
