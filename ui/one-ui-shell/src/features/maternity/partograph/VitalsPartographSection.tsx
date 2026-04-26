"use client";

import { useState } from "react";
import { AlertTriangle, Loader2, Plus } from "lucide-react";
import {
  useActivePartographSession,
  useClosePartographSession,
  useCreatePartographSession,
  useRecordPartographPoint,
} from "@/hooks/queries/usePartograph";
import { PartographLabourPlot } from "./PartographLabourPlot";

function parseOptionalNumber(value: string) {
  const trimmed = value.trim();
  if (!trimmed) return null;
  const n = Number(trimmed);
  return Number.isFinite(n) ? n : null;
}

export interface VitalsPartographSectionProps {
  patientId: string;
  encounterId: string;
  hasActiveEncounter: boolean;
  isClinical: boolean;
  recordedBy: string;
}

/**
 * Active maternity partograph session + plot + point capture.
 * Intended for /ehr/.../vitals now; same module can mount on /ehr/.../maternity later.
 */
export function VitalsPartographSection({
  patientId,
  encounterId,
  hasActiveEncounter,
  isClinical,
  recordedBy,
}: VitalsPartographSectionProps) {
  const activeQuery = useActivePartographSession(
    patientId,
    hasActiveEncounter && encounterId ? encounterId : null,
  );
  const createSession = useCreatePartographSession();
  const addPoint = useRecordPartographPoint();
  const closeSession = useClosePartographSession();

  const session = activeQuery.data?.data ?? null;
  const points = session?.points ?? [];
  const sessionId = session?.id;

  const [showPointForm, setShowPointForm] = useState(false);
  const [pgFhr, setPgFhr] = useState("");
  const [pgCtxFreq, setPgCtxFreq] = useState("");
  const [pgCtxDur, setPgCtxDur] = useState("");
  const [pgCx, setPgCx] = useState("");
  const [pgDescent, setPgDescent] = useState("");
  const [pgPulse, setPgPulse] = useState("");
  const [pgSys, setPgSys] = useState("");
  const [pgDia, setPgDia] = useState("");
  const [pgTemp, setPgTemp] = useState("");
  const [pgLiquor, setPgLiquor] = useState("CLEAR");
  const [pgMould, setPgMould] = useState("NONE");
  const [pgCaput, setPgCaput] = useState("NONE");
  const [pgOxy, setPgOxy] = useState("");
  const [pgUrineMl, setPgUrineMl] = useState("");
  const [pgUrineProt, setPgUrineProt] = useState("");
  const [pgUrineAce, setPgUrineAce] = useState("");
  const [pgMatCond, setPgMatCond] = useState("");
  const [pgNotes, setPgNotes] = useState("");

  function resetPointForm() {
    setPgFhr("");
    setPgCtxFreq("");
    setPgCtxDur("");
    setPgCx("");
    setPgDescent("");
    setPgPulse("");
    setPgSys("");
    setPgDia("");
    setPgTemp("");
    setPgLiquor("CLEAR");
    setPgMould("NONE");
    setPgCaput("NONE");
    setPgOxy("");
    setPgUrineMl("");
    setPgUrineProt("");
    setPgUrineAce("");
    setPgMatCond("");
    setPgNotes("");
    setShowPointForm(false);
  }

  function handleCreateSession() {
    createSession.mutate(
      {
        patientId,
        encounterId: hasActiveEncounter && encounterId ? encounterId : null,
        labourPhase: "ACTIVE_LABOUR",
        startedBy: recordedBy,
      },
      { onSuccess: () => void activeQuery.refetch() },
    );
  }

  function handleAddPoint(e: React.FormEvent) {
    e.preventDefault();
    if (!sessionId) return;
    addPoint.mutate(
      {
        patientId,
        sessionId,
        recordedBy,
        fetalHeartRateBpm: parseOptionalNumber(pgFhr),
        contractionFrequency10Min: parseOptionalNumber(pgCtxFreq),
        contractionDurationSec: parseOptionalNumber(pgCtxDur),
        cervicalDilationCm: parseOptionalNumber(pgCx),
        fetalDescentFifths: parseOptionalNumber(pgDescent),
        maternalPulseBpm: parseOptionalNumber(pgPulse),
        systolicBp: parseOptionalNumber(pgSys),
        diastolicBp: parseOptionalNumber(pgDia),
        temperatureC: parseOptionalNumber(pgTemp),
        liquor: pgLiquor,
        moulding: pgMould,
        caput: pgCaput,
        oxytocinRateMiuMin: parseOptionalNumber(pgOxy),
        urineVolumeMl: parseOptionalNumber(pgUrineMl),
        urineProtein: pgUrineProt.trim() || null,
        urineAcetone: pgUrineAce.trim() || null,
        maternalCondition: pgMatCond.trim() || null,
        notes: pgNotes.trim() || null,
      },
      {
        onSuccess: () => {
          resetPointForm();
          void activeQuery.refetch();
        },
      },
    );
  }

  function handleCloseSession() {
    if (!sessionId) return;
    if (!window.confirm("Close this partograph session? You can start a new session later if needed.")) return;
    closeSession.mutate(
      { patientId, sessionId, closedBy: recordedBy },
      {
        onSuccess: () => {
          resetPointForm();
          void activeQuery.refetch();
        },
      },
    );
  }

  return (
    <div className="mt-4 rounded-xl border border-fuchsia-200/80 bg-gradient-to-b from-fuchsia-50/40 to-white p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="text-sm font-semibold text-fuchsia-950">Clinical partograph session</h3>
        <div className="flex flex-wrap gap-2">
          {isClinical && !session && (
            <button
              type="button"
              onClick={() => handleCreateSession()}
              disabled={createSession.isPending}
              className="inline-flex items-center gap-1.5 rounded-lg bg-fuchsia-700 px-3 py-1.5 text-xs font-medium text-white hover:bg-fuchsia-800 disabled:opacity-50"
            >
              {createSession.isPending && <Loader2 className="h-3.5 w-3.5 animate-spin" />}
              <Plus className="h-3.5 w-3.5" />
              Start partograph session
            </button>
          )}
          {isClinical && session?.status === "ACTIVE" && sessionId && (
            <>
              <button
                type="button"
                onClick={() => setShowPointForm((v) => !v)}
                className="rounded-lg border border-fuchsia-300 bg-white px-3 py-1.5 text-xs font-medium text-fuchsia-900 hover:bg-fuchsia-50"
              >
                {showPointForm ? "Hide observation form" : "Record observation"}
              </button>
              <button
                type="button"
                onClick={() => void handleCloseSession()}
                disabled={closeSession.isPending}
                className="rounded-lg border border-gray-300 bg-white px-3 py-1.5 text-xs font-medium text-gray-800 hover:bg-gray-50 disabled:opacity-50"
              >
                Close session
              </button>
            </>
          )}
        </div>
      </div>
      <p className="mt-1 text-xs text-gray-600">
        Live graph from{" "}
        <code className="rounded bg-gray-100 px-1">/internal/v1/maternity/partograph/sessions</code>. Active session is
        resolved with <code className="rounded bg-gray-100 px-1">GET …/sessions/active</code>
        {hasActiveEncounter ? " (scoped to this encounter when one is active)." : " (patient-wide when no encounter is in scope)."}
      </p>

      {activeQuery.isError && (
        <div className="mt-2 flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-950">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
          <div>
            <p className="font-medium">Could not load partograph session</p>
            <button type="button" className="mt-1 text-xs underline" onClick={() => void activeQuery.refetch()}>
              Retry
            </button>
          </div>
        </div>
      )}

      {activeQuery.isLoading && (
        <div className="mt-3 flex items-center gap-2 text-sm text-gray-500">
          <Loader2 className="h-4 w-4 animate-spin" />
          Loading partograph…
        </div>
      )}

      {!activeQuery.isLoading && !session && (
        <p className="mt-3 text-sm text-gray-600">
          No active partograph session for this patient
          {hasActiveEncounter ? " on this encounter" : ""}. {isClinical ? "Start a session to plot observations over time." : null}
        </p>
      )}

      {session && (
        <div className="mt-3 space-y-2">
          <p className="text-xs text-gray-700">
            Session <code className="rounded bg-gray-100 px-1">{String(session.id)}</code>
            {session.started_at ? (
              <>
                {" "}
                · started {new Date(String(session.started_at)).toLocaleString()}
              </>
            ) : null}
            {session.labour_phase ? ` · ${session.labour_phase}` : null}
            {session.summary?.point_count != null ? ` · ${session.summary.point_count} point(s)` : null}
          </p>
          {Array.isArray(session.summary?.latest_alert_flags) && session.summary!.latest_alert_flags!.length > 0 ? (
            <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-xs font-medium text-red-900">
              Latest alerts: {session.summary!.latest_alert_flags!.join(", ")}
            </div>
          ) : null}

          <PartographLabourPlot startedAt={session.started_at} points={points} className="mt-2" />

          {showPointForm && session.status === "ACTIVE" && isClinical && (
            <form onSubmit={handleAddPoint} className="mt-3 space-y-3 rounded-lg border border-gray-100 bg-gray-50/90 p-4">
              <p className="text-xs font-medium text-gray-700">Add plotted observation (POST …/points)</p>
              <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
                <label className="text-xs font-medium text-gray-600">
                  Fetal heart bpm
                  <input
                    value={pgFhr}
                    onChange={(e) => setPgFhr(e.target.value)}
                    className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                  />
                </label>
                <label className="text-xs font-medium text-gray-600">
                  Contractions / 10 min
                  <input
                    value={pgCtxFreq}
                    onChange={(e) => setPgCtxFreq(e.target.value)}
                    className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                  />
                </label>
                <label className="text-xs font-medium text-gray-600">
                  Duration sec
                  <input
                    value={pgCtxDur}
                    onChange={(e) => setPgCtxDur(e.target.value)}
                    className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                  />
                </label>
                <label className="text-xs font-medium text-gray-600">
                  Cervical dilation cm
                  <input
                    value={pgCx}
                    onChange={(e) => setPgCx(e.target.value)}
                    className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                  />
                </label>
                <label className="text-xs font-medium text-gray-600">
                  Descent fifths
                  <input
                    value={pgDescent}
                    onChange={(e) => setPgDescent(e.target.value)}
                    className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                  />
                </label>
                <label className="text-xs font-medium text-gray-600">
                  Maternal pulse
                  <input
                    value={pgPulse}
                    onChange={(e) => setPgPulse(e.target.value)}
                    className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                  />
                </label>
                <label className="text-xs font-medium text-gray-600">
                  Systolic BP
                  <input
                    value={pgSys}
                    onChange={(e) => setPgSys(e.target.value)}
                    className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                  />
                </label>
                <label className="text-xs font-medium text-gray-600">
                  Diastolic BP
                  <input
                    value={pgDia}
                    onChange={(e) => setPgDia(e.target.value)}
                    className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                  />
                </label>
                <label className="text-xs font-medium text-gray-600">
                  Temperature °C
                  <input
                    value={pgTemp}
                    onChange={(e) => setPgTemp(e.target.value)}
                    className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                  />
                </label>
                <label className="text-xs font-medium text-gray-600">
                  Liquor
                  <select
                    value={pgLiquor}
                    onChange={(e) => setPgLiquor(e.target.value)}
                    className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                  >
                    <option value="CLEAR">Clear</option>
                    <option value="MECONIUM">Meconium</option>
                    <option value="BLOOD_STAINED">Blood-stained</option>
                    <option value="ABSENT">Absent / drained</option>
                  </select>
                </label>
                <label className="text-xs font-medium text-gray-600">
                  Moulding
                  <select
                    value={pgMould}
                    onChange={(e) => setPgMould(e.target.value)}
                    className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                  >
                    <option value="NONE">None</option>
                    <option value="+">+</option>
                    <option value="++">++</option>
                    <option value="+++">+++</option>
                  </select>
                </label>
                <label className="text-xs font-medium text-gray-600">
                  Caput
                  <select
                    value={pgCaput}
                    onChange={(e) => setPgCaput(e.target.value)}
                    className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                  >
                    <option value="NONE">None</option>
                    <option value="+">+</option>
                    <option value="++">++</option>
                    <option value="+++">+++</option>
                  </select>
                </label>
                <label className="text-xs font-medium text-gray-600">
                  Oxytocin mIU/min
                  <input
                    value={pgOxy}
                    onChange={(e) => setPgOxy(e.target.value)}
                    className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                  />
                </label>
                <label className="text-xs font-medium text-gray-600">
                  Urine volume ml
                  <input
                    value={pgUrineMl}
                    onChange={(e) => setPgUrineMl(e.target.value)}
                    className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                  />
                </label>
                <label className="text-xs font-medium text-gray-600">
                  Urine protein
                  <input
                    value={pgUrineProt}
                    onChange={(e) => setPgUrineProt(e.target.value)}
                    className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                  />
                </label>
                <label className="text-xs font-medium text-gray-600">
                  Urine acetone
                  <input
                    value={pgUrineAce}
                    onChange={(e) => setPgUrineAce(e.target.value)}
                    className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                  />
                </label>
                <label className="text-xs font-medium text-gray-600">
                  Maternal condition
                  <input
                    value={pgMatCond}
                    onChange={(e) => setPgMatCond(e.target.value)}
                    className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                  />
                </label>
              </div>
              <label className="block text-xs font-medium text-gray-600">
                Notes
                <textarea
                  value={pgNotes}
                  onChange={(e) => setPgNotes(e.target.value)}
                  rows={2}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                />
              </label>
              <button
                type="submit"
                disabled={addPoint.isPending}
                className="inline-flex items-center gap-1.5 rounded-lg bg-fuchsia-700 px-4 py-2 text-sm font-medium text-white hover:bg-fuchsia-800 disabled:opacity-50"
              >
                {addPoint.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
                Save observation
              </button>
              {addPoint.isError && <p className="text-xs text-red-600">Failed to save observation.</p>}
            </form>
          )}
        </div>
      )}

      {createSession.isError && <p className="mt-2 text-xs text-red-600">Could not start session.</p>}
    </div>
  );
}
