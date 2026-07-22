"use client";

/**
 * TM-B5 (B5-2): media-modality downgrade ladder control.
 *
 * Surfaces the session's current media rung (VIDEO → AUDIO → ASYNC) and lets the provider step
 * DOWN on a failing/low-bandwidth link, or RESTORE up when it recovers. ASYNC drops live media and
 * continues on the durable case chat (B5-1). Backed by POST /teleconsult/sessions/{id}/media-downgrade
 * (pct enforces the monotonic guard — step-up requires restore=true).
 */

import { useState } from "react";
import { apiClient } from "@/lib/api-client";

type Rung = "VIDEO" | "AUDIO" | "ASYNC";
const RUNGS: Rung[] = ["VIDEO", "AUDIO", "ASYNC"]; // descending capability
const LABEL: Record<Rung, string> = { VIDEO: "Video", AUDIO: "Audio only", ASYNC: "Secure chat" };

function deriveRung(mediaModality?: string | null, virtualMode?: string | null): Rung {
  const m = (mediaModality || "").toUpperCase();
  if (m === "VIDEO" || m === "AUDIO" || m === "ASYNC") return m as Rung;
  const v = (virtualMode || "video").toLowerCase();
  if (v === "audio") return "AUDIO";
  if (v === "message" || v === "async") return "ASYNC";
  return "VIDEO";
}

export function TeleconsultMediaLadder({
  sessionId,
  mediaModality,
  virtualMode,
  onChanged,
}: {
  sessionId: string;
  mediaModality?: string | null;
  virtualMode?: string | null;
  onChanged?: (rung: Rung) => void;
}) {
  const [rung, setRung] = useState<Rung>(deriveRung(mediaModality, virtualMode));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const idx = RUNGS.indexOf(rung);
  const canStepDown = idx < RUNGS.length - 1;
  const canRestore = idx > 0;

  async function change(target: Rung, restore: boolean) {
    setBusy(true);
    setError(null);
    try {
      await apiClient.post(`/internal/v1/teleconsult/sessions/${sessionId}/media-downgrade`, {
        modality: target,
        restore,
        reason: restore ? "Provider restored media" : `Stepped down to ${LABEL[target]}`,
      });
      setRung(target);
      onChanged?.(target);
    } catch {
      setError("Could not change media mode — try again.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="rounded-lg border border-border bg-card/60 p-3" data-testid="teleconsult-media-ladder">
      <div className="flex items-center justify-between gap-2">
        <span className="text-[10px] font-semibold uppercase tracking-[0.16em] text-muted-foreground">
          Media mode
        </span>
        <div className="flex items-center gap-1" aria-label="media ladder">
          {RUNGS.map((r, i) => (
            <span
              key={r}
              title={LABEL[r]}
              className={`h-1.5 w-6 rounded-full ${i <= idx ? "bg-sky-500" : "bg-border"}`}
            />
          ))}
        </div>
      </div>
      <p className="mt-1.5 text-sm font-medium text-foreground">{LABEL[rung]}</p>
      {rung === "ASYNC" && (
        <p className="mt-0.5 text-[11px] text-muted-foreground">
          Live media ended — continuing on secure chat. Messages are saved to the case.
        </p>
      )}
      <div className="mt-2 flex flex-wrap gap-1.5">
        {canStepDown && (
          <button
            type="button"
            disabled={busy}
            onClick={() => change(RUNGS[idx + 1], false)}
            className="rounded-md border border-amber-300 bg-amber-50 px-2 py-1 text-xs font-medium text-amber-900 hover:bg-amber-100 disabled:opacity-50"
          >
            Step down to {LABEL[RUNGS[idx + 1]]}
          </button>
        )}
        {canRestore && (
          <button
            type="button"
            disabled={busy}
            onClick={() => change(RUNGS[idx - 1], true)}
            className="rounded-md border border-sky-300 bg-sky-50 px-2 py-1 text-xs font-medium text-sky-900 hover:bg-sky-100 disabled:opacity-50"
          >
            Restore {LABEL[RUNGS[idx - 1]]}
          </button>
        )}
      </div>
      {error && <p className="mt-1.5 text-[11px] text-destructive">{error}</p>}
    </div>
  );
}
