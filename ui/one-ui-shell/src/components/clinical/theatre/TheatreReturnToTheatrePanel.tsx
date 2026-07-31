"use client";

/**
 * V305 return-to-theatre — records a complication-categorised return on the same episode.
 * Visible only from PACU / RECOVERED / IN_PROGRESS (the statuses the service accepts).
 * Binds: POST /internal/v1/theatre/cases/{id}/return-to-theatre
 *
 * Prior returns are passed from the case detail (`returns_to_theatre`). A failed case load is
 * the page's problem; this panel never invents an empty roster from a partial failure.
 */

import { useState } from "react";
import { RotateCcw, Loader2 } from "lucide-react";
import { apiClient } from "@/lib/api-client";

export interface ReturnToTheatreRecord {
  id?: string;
  seq?: number;
  returned_at?: string;
  returned_by?: string;
  reason?: string;
  complication_category?: string;
  planned?: boolean;
  originating_note_id?: string | null;
}

export const RETURN_COMPLICATION_CATEGORIES = [
  "HAEMORRHAGE",
  "SEPSIS",
  "ANASTOMOTIC_LEAK",
  "WOUND_DEHISCENCE",
  "ISCHAEMIA",
  "OBSTRUCTION",
  "RETAINED_ITEM",
  "DEVICE_OR_IMPLANT_FAILURE",
  "ORGAN_INJURY",
  "PLANNED_SECOND_LOOK",
  "OTHER",
] as const;

const REOPENABLE_STATUSES = new Set(["PACU", "RECOVERED", "IN_PROGRESS"]);

function errMessage(e: unknown): string {
  const o = e as { error?: { message?: string }; status?: number };
  if (o?.error?.message) return o.error.message;
  if (o?.status) return `Request failed (HTTP ${o.status}).`;
  return "Return to theatre was NOT recorded.";
}

export function TheatreReturnToTheatrePanel({
  caseId,
  status,
  returns,
  onRecorded,
}: {
  caseId: string;
  status?: string;
  returns: ReturnToTheatreRecord[];
  onRecorded: () => void;
}) {
  const [reason, setReason] = useState("");
  const [category, setCategory] = useState("");
  const [planned, setPlanned] = useState(false);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);

  if (!status || !REOPENABLE_STATUSES.has(status)) return null;

  // Server: PLANNED_SECOND_LOOK ⇔ planned. Mirror that in the submit gate.
  const plannedMismatch =
    (category === "PLANNED_SECOND_LOOK") !== planned;
  const canSubmit = reason.trim() !== "" && category !== "" && !plannedMismatch && !busy;

  const submit = async () => {
    if (!canSubmit) return;
    setBusy(true);
    setMsg(null);
    setFailed(false);
    try {
      await apiClient.post(`/internal/v1/theatre/cases/${caseId}/return-to-theatre`, {
        reason: reason.trim(),
        complicationCategory: category,
        planned,
      });
      setReason("");
      setCategory("");
      setPlanned(false);
      setMsg("Return to theatre recorded — the case is ready for theatre again.");
      onRecorded();
    } catch (e) {
      setFailed(true);
      setMsg(errMessage(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <section
      className="rounded-xl border border-rose-200 bg-rose-50/40 p-4"
      data-testid="return-to-theatre-panel"
    >
      <h3 className="mb-1 flex items-center gap-1.5 text-sm font-semibold text-rose-800">
        <RotateCcw className="h-4 w-4" /> Return to theatre
      </h3>
      <p className="mb-3 text-xs text-muted-foreground">
        Records a reason and a closed complication category against your name. A return without a
        cause is refused — that is the point of the morbidity indicator.
      </p>

      {returns.length > 0 && (
        <ul className="mb-3 space-y-1" data-testid="return-to-theatre-list">
          {returns.map((r) => (
            <li key={r.id ?? r.seq} className="flex flex-wrap items-center gap-2 text-xs">
              {r.seq != null && <span className="text-muted-foreground">#{r.seq}</span>}
              <span className="rounded bg-rose-100 px-1.5 py-0.5 font-semibold uppercase text-rose-800">
                {r.complication_category}
              </span>
              {r.planned ? (
                <span className="text-muted-foreground">planned</span>
              ) : (
                <span className="text-muted-foreground">unplanned</span>
              )}
              <span>{r.reason}</span>
              {r.returned_by && (
                <span className="text-muted-foreground">by {r.returned_by}</span>
              )}
            </li>
          ))}
        </ul>
      )}

      <div className="flex flex-wrap items-end gap-2">
        <label className="block text-xs min-w-[200px] flex-1">
          <span className="mb-1 block font-medium text-muted-foreground">Reason (required)</span>
          <input
            type="text"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="e.g. post-operative haemorrhage requiring re-exploration"
            className="w-full rounded-lg border border-border bg-background px-2 py-1.5"
            data-testid="return-to-theatre-reason"
          />
        </label>
        <label className="block text-xs">
          <span className="mb-1 block font-medium text-muted-foreground">Complication category</span>
          <select
            value={category}
            onChange={(e) => {
              const next = e.target.value;
              setCategory(next);
              // Keep the planned flag in lockstep with the second-look category.
              setPlanned(next === "PLANNED_SECOND_LOOK");
            }}
            className="rounded-lg border border-border bg-background px-2 py-1.5"
            data-testid="return-to-theatre-category"
          >
            <option value="">Choose…</option>
            {RETURN_COMPLICATION_CATEGORIES.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>
        </label>
        <label className="flex items-center gap-1.5 text-xs pb-1.5">
          <input
            type="checkbox"
            checked={planned}
            onChange={(e) => {
              const next = e.target.checked;
              setPlanned(next);
              if (next) setCategory("PLANNED_SECOND_LOOK");
              else if (category === "PLANNED_SECOND_LOOK") setCategory("");
            }}
            data-testid="return-to-theatre-planned"
          />
          Planned second look
        </label>
        <button
          type="button"
          disabled={!canSubmit}
          onClick={() => void submit()}
          className="rounded-lg border border-rose-300 bg-white px-3 py-1.5 text-sm font-medium text-rose-800 hover:bg-rose-100 disabled:opacity-50"
          data-testid="return-to-theatre-submit"
        >
          {busy ? (
            <span className="inline-flex items-center gap-1">
              <Loader2 className="h-3.5 w-3.5 animate-spin" /> Recording…
            </span>
          ) : (
            "Record return to theatre"
          )}
        </button>
      </div>
      {plannedMismatch && category && (
        <p className="mt-2 text-xs text-muted-foreground" data-testid="return-to-theatre-planned-mismatch">
          A planned second look must use PLANNED_SECOND_LOOK; every other category is unplanned.
        </p>
      )}
      {msg && (
        <p
          className={`mt-2 text-sm ${failed ? "text-danger" : "text-muted-foreground"}`}
          role={failed ? "alert" : undefined}
          data-testid={failed ? "return-to-theatre-failed" : "return-to-theatre-ok"}
        >
          {msg}
        </p>
      )}
    </section>
  );
}
