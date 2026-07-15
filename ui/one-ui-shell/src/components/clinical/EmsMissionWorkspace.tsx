"use client";

/**
 * EMS mission workspace (WU4) — drives the validated daidzai EMS state machine and
 * captures the prehospital ePCR (primary survey + timed obs) that lands on the ED
 * pre-arrival projection before the ambulance arrives. Consumes the Daidzai BFF
 * passthroughs via useEmsMission / useEmsEpcr / useEmsMissionActions. No mocks.
 */

import { useState } from "react";
import { Activity, Ambulance, ArrowRight, Loader2, Radio } from "lucide-react";
import {
  nextEmsState,
  useEmsEpcr,
  useEmsMission,
  useEmsMissionActions,
} from "@/hooks/queries/useDaidzai";

function str(v: unknown): string {
  return v == null ? "" : String(v);
}

const STANDDOWN_STATES = ["STANDDOWN", "CANCELLED"] as const;

export function EmsMissionWorkspace({
  missionId,
  pctEncounterRef,
}: {
  missionId: string;
  pctEncounterRef?: string;
}) {
  const mission = useEmsMission(missionId);
  const epcr = useEmsEpcr(missionId);
  const actions = useEmsMissionActions(missionId);

  const [encounterRef, setEncounterRef] = useState(pctEncounterRef ?? "");
  const [healthId, setHealthId] = useState("");
  const [narrative, setNarrative] = useState("");
  const [airway, setAirway] = useState("patent");
  const [vitals, setVitals] = useState({ hr: "", sbp: "", spo2: "", rr: "" });
  const [gcs, setGcs] = useState({ e: "", v: "", m: "" });

  const m = mission.data;
  const state = str(m?.state);
  const next = nextEmsState(state);
  const needsEncounter = next === "HANDOVER";

  const snapshot = (epcr.data?.snapshot ?? {}) as Record<string, unknown>;
  const latestVitals = (snapshot.latestVitals ?? {}) as Record<string, unknown>;
  const latestGcs = (snapshot.latestGcs ?? {}) as Record<string, unknown>;
  const events = epcr.data?.events ?? [];

  if (mission.isLoading) {
    return <div className="flex items-center gap-2 py-8 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Loading mission…</div>;
  }
  if (mission.isError || !m) {
    return (
      <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800">
        No EMS mission found. Dispatch one from the incident first.
      </div>
    );
  }

  const numeric = (obj: Record<string, string>) => {
    const out: Record<string, number> = {};
    for (const [k, v] of Object.entries(obj)) {
      const n = Number(v);
      if (v !== "" && !Number.isNaN(n)) out[k] = n;
    }
    return out;
  };

  return (
    <div className="space-y-4" data-testid="ems-mission-workspace">
      {/* Mission header */}
      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 rounded-xl border border-border bg-card px-4 py-3 text-sm shadow-sm">
        <span className="flex items-center gap-1.5 font-semibold text-foreground"><Ambulance className="h-4 w-4 text-teal-600" /> {str(m.missionReference) || "EMS mission"}</span>
        <span className="rounded-full bg-teal-100 px-2 py-0.5 text-xs font-semibold text-teal-900">{state || "—"}</span>
        {m.dispatchPriority ? <span className="text-xs text-muted-foreground">Priority {str(m.dispatchPriority)}</span> : null}
        {m.traumaEpisodeId ? <span className="font-mono text-[11px] text-muted-foreground">episode {str(m.traumaEpisodeId).slice(0, 8)}</span> : null}
      </div>

      {/* State machine */}
      <section className="rounded-xl border border-border bg-card p-4 shadow-sm">
        <h3 className="flex items-center gap-2 text-sm font-semibold text-foreground"><Radio className="h-4 w-4 text-teal-600" /> Mission state</h3>
        <div className="mt-3 flex flex-wrap items-center gap-2">
          {next ? (
            <>
              {needsEncounter && (
                <input
                  value={encounterRef}
                  onChange={(e) => setEncounterRef(e.target.value)}
                  placeholder="ED visit / encounter ref (for handover)"
                  className="w-64 rounded-lg border border-border px-3 py-2 text-sm"
                />
              )}
              <button
                type="button"
                disabled={actions.advance.isPending || (needsEncounter && !encounterRef.trim())}
                onClick={() =>
                  actions.advance.mutate({
                    toState: next,
                    pctEncounterRef: needsEncounter ? encounterRef.trim() : undefined,
                  })
                }
                className="inline-flex items-center gap-2 rounded-lg bg-teal-600 px-4 py-2 text-sm font-semibold text-white hover:bg-teal-700 disabled:opacity-50"
              >
                {actions.advance.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <ArrowRight className="h-4 w-4" />}
                Advance to {next.replace(/_/g, " ")}
              </button>
            </>
          ) : (
            <span className="text-sm text-muted-foreground">Mission at terminal state {state}.</span>
          )}
          {next && !["HANDOVER"].includes(next) &&
            STANDDOWN_STATES.map((s) => (
              <button
                key={s}
                type="button"
                disabled={actions.advance.isPending}
                onClick={() => actions.advance.mutate({ toState: s })}
                className="rounded-lg border border-border px-3 py-2 text-xs text-muted-foreground hover:bg-background disabled:opacity-50"
              >
                {s}
              </button>
            ))}
        </div>
        {actions.advance.isError && <p className="mt-2 text-xs text-danger">Transition rejected — illegal for the current state.</p>}
      </section>

      {/* ePCR */}
      <section className="rounded-xl border border-border bg-card p-4 shadow-sm">
        <h3 className="flex items-center gap-2 text-sm font-semibold text-foreground"><Activity className="h-4 w-4 text-primary" /> Prehospital ePCR</h3>

        {epcr.isError || !epcr.data?.epcrId ? (
          <div className="mt-3 space-y-2">
            <p className="text-xs text-muted-foreground">No ePCR yet. Open one to start recording obs that pre-warn the ED.</p>
            <div className="flex flex-wrap gap-2">
              <input value={healthId} onChange={(e) => setHealthId(e.target.value)} placeholder="Patient / temp ID" className="w-40 rounded-lg border border-border px-3 py-2 text-sm" />
              <select value={airway} onChange={(e) => setAirway(e.target.value)} className="rounded-lg border border-border px-3 py-2 text-sm">
                {["patent", "at risk", "obstructed", "adjunct", "intubated"].map((a) => <option key={a} value={a}>Airway: {a}</option>)}
              </select>
              <input value={narrative} onChange={(e) => setNarrative(e.target.value)} placeholder="Narrative" className="min-w-[200px] flex-1 rounded-lg border border-border px-3 py-2 text-sm" />
              <button
                type="button"
                disabled={actions.upsertEpcr.isPending}
                onClick={() => actions.upsertEpcr.mutate({ patientHealthId: healthId.trim() || undefined, primarySurvey: { airway }, narrative: narrative.trim() || undefined })}
                className="rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
              >
                {actions.upsertEpcr.isPending ? "Opening…" : "Open ePCR"}
              </button>
            </div>
          </div>
        ) : (
          <div className="mt-3 space-y-3">
            {/* Snapshot */}
            <div className="flex flex-wrap gap-3 rounded-lg bg-background px-3 py-2 text-sm">
              <span className="font-medium">Latest:</span>
              {Object.keys(latestVitals).length ? (
                <span>HR {str(latestVitals.hr)} · SBP {str(latestVitals.sbp)} · SpO₂ {str(latestVitals.spo2)} · RR {str(latestVitals.rr)}</span>
              ) : <span className="text-muted-foreground">no vitals yet</span>}
              {Object.keys(latestGcs).length ? <span>· GCS {str(latestGcs.gcs)}</span> : null}
              <span className="text-muted-foreground">({str(snapshot.obsCount) || 0} obs)</span>
            </div>

            {/* Vitals entry */}
            <div className="flex flex-wrap items-end gap-2">
              {(["hr", "sbp", "spo2", "rr"] as const).map((k) => (
                <label key={k} className="text-xs font-medium text-muted-foreground">
                  {k.toUpperCase()}
                  <input type="number" value={vitals[k]} onChange={(e) => setVitals((p) => ({ ...p, [k]: e.target.value }))} className="ml-1 w-16 rounded border px-2 py-1 text-sm" />
                </label>
              ))}
              <button
                type="button"
                disabled={actions.addEpcrEvent.isPending}
                onClick={() => actions.addEpcrEvent.mutate({ eventType: "VITALS", channel: "OBS", payload: numeric(vitals) }, { onSuccess: () => setVitals({ hr: "", sbp: "", spo2: "", rr: "" }) })}
                className="rounded-lg bg-primary px-3 py-2 text-sm font-medium text-white disabled:opacity-50"
              >
                Log vitals
              </button>
            </div>

            {/* GCS entry */}
            <div className="flex flex-wrap items-end gap-2">
              {(["e", "v", "m"] as const).map((k) => (
                <label key={k} className="text-xs font-medium text-muted-foreground">
                  GCS-{k.toUpperCase()}
                  <input type="number" value={gcs[k]} onChange={(e) => setGcs((p) => ({ ...p, [k]: e.target.value }))} className="ml-1 w-14 rounded border px-2 py-1 text-sm" />
                </label>
              ))}
              <button
                type="button"
                disabled={actions.addEpcrEvent.isPending}
                onClick={() => {
                  const n = numeric(gcs);
                  const total = (n.e ?? 0) + (n.v ?? 0) + (n.m ?? 0);
                  actions.addEpcrEvent.mutate({ eventType: "GCS", channel: "OBS", payload: { ...n, gcs: total } }, { onSuccess: () => setGcs({ e: "", v: "", m: "" }) });
                }}
                className="rounded-lg border border-border px-3 py-2 text-sm hover:bg-background disabled:opacity-50"
              >
                Log GCS
              </button>
            </div>

            {/* Obs timeline */}
            {events.length > 0 && (
              <ol className="space-y-1 border-t border-border pt-2 text-xs">
                {events.slice().reverse().map((ev, i) => (
                  <li key={i} className="flex gap-2 text-muted-foreground">
                    <span className="font-medium text-foreground">{str(ev.eventType ?? ev.event_type)}</span>
                    <span>{JSON.stringify(ev.payload ?? {})}</span>
                  </li>
                ))}
              </ol>
            )}
          </div>
        )}
      </section>
    </div>
  );
}
