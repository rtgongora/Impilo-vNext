"use client";

/**
 * TM-B15 (B15-2): the MDT board workspace — agenda, per-case recommendation with consensus +
 * dissent (dissent always carries a note), and the pseudonymisation the pct read model enforces
 * server-side (non-privileged viewers receive "Case N" with no patient identity — this page just
 * renders what it is given).
 */

import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, Gavel, Plus, ShieldQuestion } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

interface Position { participant: string; position: "AGREE" | "DISSENT"; note?: string }
interface CaseItem {
  caseItemId: string;
  ordinal: number;
  label: string;
  status: string;
  recommendation?: string | null;
  positions?: Position[];
  recordedBy?: string | null;
  referralId?: string | null;
  patientCpid?: string | null;
  pseudonymised?: boolean;
}
interface Board {
  mdtSessionId: string;
  title?: string;
  chairId: string;
  scribeId?: string;
  specialty?: string;
  identityVisibility: string;
  status: string;
  agenda: CaseItem[];
  viewerPrivileged?: boolean;
}

export default function MdtBoardPage() {
  const params = useParams();
  const boardId = params.boardId as string;
  const [board, setBoard] = useState<Board | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [newReferralId, setNewReferralId] = useState("");
  const [outcomeFor, setOutcomeFor] = useState<string | null>(null);
  const [recommendation, setRecommendation] = useState("");
  const [positions, setPositions] = useState<Position[]>([{ participant: "", position: "AGREE" }]);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const res = await apiClient.get<{ data: Board }>(`/internal/v1/teleconsult/mdt/sessions/${boardId}`);
      setBoard(res.data);
      setError(null);
    } catch {
      setError("Board not found or unavailable.");
    } finally {
      setLoading(false);
    }
  }, [boardId]);

  useEffect(() => { void load(); }, [load]);

  async function addCase() {
    if (!newReferralId.trim() || busy) return;
    setBusy(true);
    try {
      await apiClient.post(`/internal/v1/teleconsult/mdt/sessions/${boardId}/cases`, {
        referral_id: newReferralId.trim(),
      });
      setNewReferralId("");
      await load();
    } catch {
      setError("Could not add the case — check the referral id.");
    } finally {
      setBusy(false);
    }
  }

  async function recordOutcome(caseItemId: string) {
    const filled = positions.filter((p) => p.participant.trim());
    if (!recommendation.trim() || filled.length === 0 || busy) return;
    setBusy(true);
    setError(null);
    try {
      await apiClient.post(`/internal/v1/teleconsult/mdt/cases/${caseItemId}/outcome`, {
        recommendation: recommendation.trim(),
        positions: filled,
      });
      setOutcomeFor(null);
      setRecommendation("");
      setPositions([{ participant: "", position: "AGREE" }]);
      await load();
    } catch {
      setError("Outcome refused — every dissent needs a note; check inputs.");
    } finally {
      setBusy(false);
    }
  }

  function setPosition(i: number, patch: Partial<Position>) {
    setPositions((rows) => rows.map((row, idx) => (idx === i ? { ...row, ...patch } : row)));
  }

  return (
    <AppLayout>
      <PageShell
        title={board?.title || "MDT Board"}
        subtitle={board ? `Chair ${board.chairId} · ${board.identityVisibility} · ${board.status}` : "Loading board"}
      >
        <div className="max-w-3xl space-y-4">
          <Link href="/work/telemedicine/mdt" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
            <ArrowLeft className="h-4 w-4" /> All boards
          </Link>

          {loading && <p className="text-sm text-muted-foreground">Loading…</p>}
          {error && <p className="text-sm text-destructive">{error}</p>}

          {board && (
            <>
              {!board.viewerPrivileged && (
                <div className="flex items-center gap-2 rounded-md border border-amber-300 bg-amber-50 px-3 py-2 text-xs text-amber-900 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-200">
                  <ShieldQuestion className="h-4 w-4 shrink-0" />
                  Pseudonymised presentation — patient identity is withheld server-side for panel members.
                </div>
              )}

              <p className="text-[11px] text-muted-foreground">
                V051 owns the session; V114 owns the decision. Board minutes here are input to a
                governed decision recorded on the patient&rsquo;s Consultations page.
              </p>

              <ul className="space-y-3" data-testid="mdt-agenda">
                {board.agenda.length === 0 && (
                  <li className="text-sm italic text-muted-foreground">No cases on the agenda yet.</li>
                )}
                {board.agenda.map((item) => (
                  <li key={item.caseItemId} className="rounded-lg border border-border bg-card p-3">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="text-sm font-semibold text-foreground">{item.label}</span>
                      <span className="rounded-full bg-muted px-2 py-0.5 text-[10px] uppercase tracking-wide text-muted-foreground">{item.status}</span>
                      {item.pseudonymised
                        ? <span className="text-[11px] text-muted-foreground">identity withheld</span>
                        : item.referralId && (
                          <Link href={`/telemedicine/session/${item.referralId}`} className="text-[11px] text-primary hover:underline">
                            case {item.referralId.slice(0, 8)}…
                          </Link>
                        )}
                    </div>
                    {item.recommendation ? (
                      <div className="mt-2 space-y-1.5">
                        <p className="text-sm text-foreground"><Gavel className="mr-1 inline h-3.5 w-3.5 text-primary" />{item.recommendation}</p>
                        <ul className="space-y-0.5">
                          {(item.positions ?? []).map((p, i) => (
                            <li key={i} className="text-xs text-muted-foreground">
                              {p.participant}: <span className={p.position === "DISSENT" ? "font-medium text-amber-700 dark:text-amber-400" : ""}>{p.position}</span>
                              {p.note ? ` — ${p.note}` : ""}
                            </li>
                          ))}
                        </ul>
                        {item.recordedBy && <p className="text-[10px] text-muted-foreground">Recorded by {item.recordedBy}</p>}
                        {item.patientCpid && (
                          <p className="text-[11px] text-muted-foreground">
                            This is the board&rsquo;s recommendation, not the governed decision.{" "}
                            <Link href={`/ehr/${item.patientCpid}/consultations`} className="text-primary hover:underline">
                              Record the decision on this patient&rsquo;s Consultations page
                            </Link>.
                          </p>
                        )}
                      </div>
                    ) : outcomeFor === item.caseItemId ? (
                      <div className="mt-2 space-y-2">
                        <textarea value={recommendation} onChange={(e) => setRecommendation(e.target.value)} rows={2}
                          placeholder="Board recommendation (recommendations only — no orders)"
                          className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm" />
                        {positions.map((p, i) => (
                          <div key={i} className="flex flex-wrap gap-1.5">
                            <input value={p.participant} onChange={(e) => setPosition(i, { participant: e.target.value })}
                              placeholder="Participant id" className="min-w-0 flex-1 rounded-md border border-border bg-background px-2 py-1 text-xs" />
                            <select value={p.position} onChange={(e) => setPosition(i, { position: e.target.value as Position["position"] })}
                              className="rounded-md border border-border bg-background px-2 py-1 text-xs">
                              <option value="AGREE">Agree</option>
                              <option value="DISSENT">Dissent</option>
                            </select>
                            {p.position === "DISSENT" && (
                              <input value={p.note ?? ""} onChange={(e) => setPosition(i, { note: e.target.value })}
                                placeholder="Dissent note (required)" className="min-w-0 flex-1 rounded-md border border-amber-300 bg-background px-2 py-1 text-xs" />
                            )}
                          </div>
                        ))}
                        <div className="flex gap-2">
                          <button type="button" onClick={() => setPositions((r) => [...r, { participant: "", position: "AGREE" }])}
                            className="rounded-md border border-border px-2 py-1 text-xs">+ participant</button>
                          <button type="button" disabled={busy} onClick={() => recordOutcome(item.caseItemId)}
                            className="rounded-md bg-primary px-2.5 py-1 text-xs font-medium text-primary-foreground disabled:opacity-50">
                            Record outcome
                          </button>
                          <button type="button" onClick={() => setOutcomeFor(null)} className="rounded-md border border-border px-2 py-1 text-xs">Cancel</button>
                        </div>
                      </div>
                    ) : (
                      <button type="button" onClick={() => setOutcomeFor(item.caseItemId)}
                        className="mt-2 rounded-md border border-border px-2.5 py-1 text-xs hover:bg-background">
                        Record outcome…
                      </button>
                    )}
                  </li>
                ))}
              </ul>

              <div className="flex gap-2">
                <input value={newReferralId} onChange={(e) => setNewReferralId(e.target.value)}
                  placeholder="Referral id to present" className="min-w-0 flex-1 rounded-md border border-border bg-background px-2 py-1.5 text-sm" />
                <button type="button" disabled={busy || !newReferralId.trim()} onClick={addCase}
                  className="inline-flex items-center gap-1 rounded-md border border-border px-3 py-1.5 text-sm disabled:opacity-50">
                  <Plus className="h-4 w-4" /> Add case
                </button>
              </div>
            </>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
