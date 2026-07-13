"use client";

import { useMemo, useState } from "react";
import { Loader2, ClipboardList, Plus } from "lucide-react";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useWardRounds, useStartWardRound, useAddWardRoundEntry } from "@/hooks/queries/useInpatient";

const ROUND_TYPES = ["MORNING", "EVENING", "NIGHT", "CONSULTANT"];

interface RoundRow {
  id: string;
  roundType?: string;
  ledBy?: string;
  startedAt?: string;
  entries?: Array<Record<string, unknown>>;
}

function extractRounds(payload: unknown): RoundRow[] {
  if (!payload || typeof payload !== "object") return [];
  const data = (payload as Record<string, unknown>).data;
  const rows = Array.isArray(data) ? data : [];
  return rows.map((row) => {
    const r = row as Record<string, unknown>;
    return {
      id: String(r.id ?? r.roundId ?? r.wardRoundId ?? ""),
      roundType: r.roundType as string | undefined,
      ledBy: r.ledBy as string | undefined,
      startedAt: (r.startedAt ?? r.createdAt) as string | undefined,
      entries: Array.isArray(r.entries) ? (r.entries as Array<Record<string, unknown>>) : [],
    };
  });
}

/**
 * Records ward rounds on an inpatient episode: start a round, then add
 * assessment/plan/orders entries. Wires the already-existing BFF ward-round
 * routes that previously had no UI (G1 remediation).
 */
export function WardRoundRecorder({ admissionRef, wardId }: { admissionRef: string; wardId?: string }) {
  const user = useAuthStore((s) => s.user);
  const clinician = user?.displayName ?? user?.email ?? "";
  const roundsQuery = useWardRounds(admissionRef);
  const startRound = useStartWardRound();
  const addEntry = useAddWardRoundEntry();

  const rounds = useMemo(() => extractRounds(roundsQuery.data), [roundsQuery.data]);
  const [roundType, setRoundType] = useState("MORNING");
  const [activeRoundId, setActiveRoundId] = useState<string | null>(null);
  const [assessment, setAssessment] = useState("");
  const [plan, setPlan] = useState("");
  const [newOrders, setNewOrders] = useState("");

  const currentRoundId = activeRoundId ?? rounds[0]?.id ?? null;

  function handleStartRound() {
    startRound.mutate(
      { admissionRef, wardId, roundType, ledBy: clinician, notes: undefined },
      {
        onSuccess: (res) => {
          const created = (res as { data?: { id?: string; roundId?: string } })?.data;
          const id = created?.id ?? created?.roundId;
          if (id) setActiveRoundId(String(id));
        },
      },
    );
  }

  function handleAddEntry(e: React.FormEvent) {
    e.preventDefault();
    if (!currentRoundId || !assessment.trim() || !plan.trim()) return;
    addEntry.mutate(
      {
        roundId: currentRoundId,
        admissionRef,
        assessment: assessment.trim(),
        plan: plan.trim(),
        newOrders: newOrders.trim() || undefined,
        reviewedBy: clinician || undefined,
      },
      {
        onSuccess: () => {
          setAssessment("");
          setPlan("");
          setNewOrders("");
        },
      },
    );
  }

  return (
    <div className="rounded-xl border border-border p-4">
      <div className="flex items-center justify-between">
        <h3 className="flex items-center gap-2 text-sm font-semibold text-foreground">
          <ClipboardList className="h-4 w-4" /> Ward rounds
        </h3>
        <div className="flex items-center gap-2">
          <select
            value={roundType}
            onChange={(e) => setRoundType(e.target.value)}
            className="rounded-lg border border-border px-2 py-1 text-xs bg-card"
          >
            {ROUND_TYPES.map((t) => (
              <option key={t} value={t}>{t}</option>
            ))}
          </select>
          <button
            type="button"
            onClick={handleStartRound}
            disabled={startRound.isPending}
            className="inline-flex items-center gap-1 rounded-lg bg-primary px-3 py-1.5 text-xs font-medium text-white hover:bg-primary-hover disabled:opacity-50"
          >
            {startRound.isPending ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Plus className="h-3.5 w-3.5" />}
            Start round
          </button>
        </div>
      </div>

      {currentRoundId && (
        <form onSubmit={handleAddEntry} className="mt-3 space-y-2 rounded-lg border border-border bg-background p-3">
          <p className="text-xs font-medium text-muted-foreground">New entry for this round</p>
          <textarea
            value={assessment}
            onChange={(e) => setAssessment(e.target.value)}
            rows={2}
            placeholder="Assessment (required)"
            className="w-full rounded-lg border border-border px-2 py-1.5 text-sm"
          />
          <textarea
            value={plan}
            onChange={(e) => setPlan(e.target.value)}
            rows={2}
            placeholder="Plan (required)"
            className="w-full rounded-lg border border-border px-2 py-1.5 text-sm"
          />
          <input
            value={newOrders}
            onChange={(e) => setNewOrders(e.target.value)}
            placeholder="New orders (optional)"
            className="w-full rounded-lg border border-border px-2 py-1.5 text-sm"
          />
          <button
            type="submit"
            disabled={addEntry.isPending || !assessment.trim() || !plan.trim()}
            className="inline-flex items-center gap-1 rounded-lg border border-primary/25 bg-primary-soft px-3 py-1.5 text-xs font-medium text-primary-hover disabled:opacity-50"
          >
            {addEntry.isPending ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : null}
            Add entry
          </button>
        </form>
      )}

      <div className="mt-3 space-y-2">
        {roundsQuery.isLoading ? (
          <p className="text-sm text-muted-foreground">Loading rounds…</p>
        ) : rounds.length === 0 ? (
          <p className="text-sm text-muted-foreground">No ward rounds recorded yet. Start one above.</p>
        ) : (
          rounds.map((round) => (
            <div key={round.id} className="rounded-lg border border-border p-3">
              <div className="flex items-center justify-between text-xs">
                <span className="font-medium text-foreground">{round.roundType ?? "ROUND"}</span>
                <span className="text-muted-foreground">
                  {round.ledBy ?? "—"}
                  {round.startedAt ? ` · ${new Date(round.startedAt).toLocaleString()}` : ""}
                </span>
              </div>
              {round.entries && round.entries.length > 0 && (
                <ul className="mt-2 space-y-1 text-sm">
                  {round.entries.map((entry, i) => (
                    <li key={i} className="rounded bg-background px-2 py-1">
                      <span className="text-foreground">{String(entry.assessment ?? "")}</span>
                      {entry.plan ? <span className="text-muted-foreground"> — {String(entry.plan)}</span> : null}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          ))
        )}
      </div>
    </div>
  );
}
