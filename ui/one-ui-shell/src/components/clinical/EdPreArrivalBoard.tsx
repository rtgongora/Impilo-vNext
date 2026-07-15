"use client";

/**
 * ED pre-arrival board (WU4) — inbound EMS patients the ED can see BEFORE the ambulance
 * arrives, with the latest prehospital snapshot (vitals / GCS / narrative) pushed from
 * the crew's ePCR. Consumes GET /internal/v1/ed/pre-arrival?facilityId= (pct read-model).
 * Gives the resus team a head start; no mocks.
 */

import { Ambulance, HeartPulse, Loader2, RefreshCw } from "lucide-react";
import { useEdPreArrival } from "@/hooks/queries/useDaidzai";

function str(v: unknown): string {
  return v == null ? "" : String(v);
}

/** The pre_arrival column is a JSON string snapshot; parse defensively. */
function parseSnapshot(raw: unknown): { vitals: Record<string, unknown>; gcs: Record<string, unknown>; narrative: string; missionState: string; obsCount: unknown } {
  let obj: Record<string, unknown> = {};
  if (typeof raw === "string" && raw.trim()) {
    try { obj = JSON.parse(raw); } catch { obj = {}; }
  } else if (raw && typeof raw === "object") {
    obj = raw as Record<string, unknown>;
  }
  const snap = (obj.snapshot ?? {}) as Record<string, unknown>;
  return {
    vitals: (snap.latestVitals ?? {}) as Record<string, unknown>,
    gcs: (snap.latestGcs ?? {}) as Record<string, unknown>,
    narrative: str(snap.narrative),
    missionState: str(obj.missionState),
    obsCount: snap.obsCount,
  };
}

export function EdPreArrivalBoard({ facilityId }: { facilityId?: string }) {
  const board = useEdPreArrival(facilityId);
  const rows = board.data ?? [];

  return (
    <section className="rounded-xl border border-teal-200 bg-teal-50/40 p-4 shadow-sm" data-testid="ed-pre-arrival-board">
      <div className="flex items-center justify-between">
        <h2 className="flex items-center gap-2 text-sm font-semibold text-teal-900">
          <Ambulance className="h-4 w-4" /> Inbound (pre-arrival)
          <span className="text-xs font-normal text-teal-800/70">{rows.length} en route</span>
        </h2>
        <button type="button" onClick={() => void board.refetch()} className="inline-flex items-center gap-1 text-xs text-teal-800 hover:underline">
          <RefreshCw className="h-3 w-3" /> Refresh
        </button>
      </div>

      {board.isLoading ? (
        <div className="mt-3 flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Loading…</div>
      ) : board.isError ? (
        <p className="mt-3 text-sm text-red-700">Could not load the pre-arrival board.</p>
      ) : rows.length === 0 ? (
        <p className="mt-3 rounded-lg border border-dashed border-teal-200 bg-white/60 px-3 py-4 text-center text-xs text-muted-foreground">
          No inbound EMS patients. Dispatched ambulances with an ePCR appear here before they arrive.
        </p>
      ) : (
        <ul className="mt-3 space-y-2">
          {rows.map((v) => {
            const snap = parseSnapshot(v.pre_arrival);
            return (
              <li key={str(v.visit_id)} className="rounded-lg border border-teal-200 bg-white px-3 py-2 text-sm">
                <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                  <span className="font-medium text-foreground">{str(v.patient_cpid) || "Unidentified"}</span>
                  <span className="rounded-full bg-teal-100 px-2 py-0.5 text-xs font-semibold text-teal-900">{snap.missionState || str(v.arrival_mode)}</span>
                  {v.ems_mission_ref ? <span className="font-mono text-[11px] text-muted-foreground">mission {str(v.ems_mission_ref).slice(0, 8)}</span> : null}
                </div>
                <div className="mt-1 flex flex-wrap items-center gap-x-3 text-xs text-muted-foreground">
                  <span className="flex items-center gap-1 text-red-700"><HeartPulse className="h-3 w-3" />
                    HR {str(snap.vitals.hr) || "—"} · SBP {str(snap.vitals.sbp) || "—"} · SpO₂ {str(snap.vitals.spo2) || "—"} · RR {str(snap.vitals.rr) || "—"}
                  </span>
                  {Object.keys(snap.gcs).length ? <span>GCS {str(snap.gcs.gcs)}</span> : null}
                  {snap.narrative ? <span className="truncate">“{snap.narrative}”</span> : null}
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
