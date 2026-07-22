"use client";

/**
 * Committee & hearings console (ROM regulator lane). A committee member sees the cases docketed to
 * them, dockets another member onto a case, schedules a hearing, and files an appeal against a
 * decision.
 *
 * The docket is the visibility seam: a committee member may see ONLY the cases docketed to them —
 * the my-docket endpoint is scoped to the signed-in member from the trust context, so the
 * [regulatorId] route param is context only and is never sent as a query. Docketing a member can
 * return HTTP 403 RECUSAL_REQUIRED: a member cannot be docketed to their OWN case. That is surfaced
 * as a clear amber inline message, not a generic error.
 *
 * Route: /work/regulators/[regulatorId]/committee
 */

import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import {
  CalendarClock,
  FileWarning,
  Gavel,
  Loader2,
  Lock,
  ShieldAlert,
  UserPlus,
} from "lucide-react";
import { LuminousStage } from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

interface DocketAssignment {
  id: number;
  caseId: string;
  committeeMemberHealthId: string | null;
  committeeRef: string | null;
  status: string;
}

/** Tolerate raw-or-{data} and array-or-{data:array} envelopes from the BFF. */
function unwrapDocket(payload: unknown): DocketAssignment[] {
  const p = (payload as { data?: unknown })?.data ?? payload;
  if (!Array.isArray(p)) return [];
  return p
    .filter((x): x is Record<string, unknown> => typeof x === "object" && x !== null && "id" in x)
    .map((x) => ({
      id: Number(x.id),
      caseId: typeof x.caseId === "string" ? x.caseId : String(x.caseId ?? ""),
      committeeMemberHealthId:
        typeof x.committeeMemberHealthId === "string" ? x.committeeMemberHealthId : null,
      committeeRef: typeof x.committeeRef === "string" ? x.committeeRef : null,
      status: typeof x.status === "string" ? x.status : "",
    }));
}

/** A docket POST can 403 with a RECUSAL_REQUIRED message (a member cannot sit on their own case). */
function isRecusalError(err: unknown): boolean {
  try {
    return JSON.stringify(err).includes("RECUSAL_REQUIRED");
  } catch {
    return false;
  }
}

const RECUSAL_MESSAGE =
  "A member cannot sit on a case against themselves — recusal required.";

export default function CommitteeConsolePage() {
  const params = useParams<{ regulatorId: string }>();
  const regulatorId = params?.regulatorId ?? "";

  const [docket, setDocket] = useState<DocketAssignment[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [recused, setRecused] = useState(false);

  const loadDocket = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get<unknown>(
        "/internal/v1/regulatory/console/hearings/my-docket",
      );
      setDocket(unwrapDocket(res));
    } catch {
      setError("Could not load your docket. Please try again.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadDocket();
  }, [loadDocket]);

  // Shared action runner: traps RECUSAL_REQUIRED as an amber banner. Callers decide whether to
  // refetch the docket on success.
  const runAction = useCallback(
    async (fn: () => Promise<void>, refetch: boolean): Promise<boolean> => {
      setRecused(false);
      setError(null);
      try {
        await fn();
        if (refetch) await loadDocket();
        return true;
      } catch (err) {
        if (isRecusalError(err)) {
          setRecused(true);
        } else {
          setError("That action could not be completed. Please try again.");
        }
        return false;
      }
    },
    [loadDocket],
  );

  return (
    <AppLayout>
      <PageShell
        title="Committee & hearings"
        subtitle="Dockets, hearing scheduling and appeals"
        serviceSlug="varapi"
      >
        <LuminousStage className="space-y-5 p-5 sm:p-6">
          {regulatorId && (
            <span className="inline-flex items-center gap-1.5 rounded-full border border-border bg-muted/40 px-2.5 py-1 text-[11px] font-medium text-muted-foreground">
              <ShieldAlert className="h-3.5 w-3.5 text-teal-600" /> Council {regulatorId}
            </span>
          )}

          {recused && (
            <div className="flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
              <Lock className="mt-0.5 h-4 w-4 shrink-0" />
              <span>{RECUSAL_MESSAGE}</span>
            </div>
          )}

          <div className="grid gap-5 lg:grid-cols-[minmax(0,22rem)_1fr]">
            {/* Left: committee actions */}
            <div className="space-y-5">
              <DocketMemberForm
                onDocket={(body) =>
                  runAction(async () => {
                    await apiClient.post<unknown>(
                      "/internal/v1/regulatory/console/hearings/docket",
                      body,
                    );
                  }, true)
                }
              />

              <ScheduleHearingForm
                onSchedule={(caseId, body) =>
                  runAction(async () => {
                    await apiClient.post<unknown>(
                      `/internal/v1/regulatory/console/hearings/${encodeURIComponent(caseId)}/schedule`,
                      body,
                    );
                  }, false)
                }
              />

              <FileAppealForm
                onAppeal={(caseId, body) =>
                  runAction(async () => {
                    await apiClient.post<unknown>(
                      `/internal/v1/regulatory/console/hearings/${encodeURIComponent(caseId)}/appeal`,
                      body,
                    );
                  }, false)
                }
              />
            </div>

            {/* Right: my docket */}
            <section className="space-y-3">
              <h2 className="flex items-center gap-2 text-sm font-semibold text-foreground">
                <Gavel className="h-4 w-4 text-teal-600" /> My docket
              </h2>
              <p className="text-[11px] text-muted-foreground">
                A committee member sees only the cases docketed to them.
              </p>

              {error && (
                <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">
                  {error}
                </div>
              )}

              {loading ? (
                <div className="flex items-center gap-2 text-sm text-muted-foreground">
                  <Loader2 className="h-4 w-4 animate-spin" /> Loading…
                </div>
              ) : docket.length === 0 ? (
                <div className="flex min-h-[8rem] items-center justify-center rounded-xl border border-dashed border-border bg-muted/20 p-6 text-center text-sm text-muted-foreground">
                  No cases are docketed to you.
                </div>
              ) : (
                <ul className="space-y-2">
                  {docket.map((d) => (
                    <li key={d.id}>
                      <div className="w-full rounded-xl border border-border bg-card p-3 text-left">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <span className="text-sm font-semibold text-foreground">
                            Case {d.caseId}
                          </span>
                          <span className="rounded-full border border-border px-2 py-0.5 text-[11px] font-medium text-muted-foreground">
                            {(d.status || "—").replace(/_/g, " ").toLowerCase()}
                          </span>
                        </div>
                        <div className="mt-1 flex flex-wrap items-center gap-2 text-[11px] text-muted-foreground">
                          {d.committeeRef && <span>Committee {d.committeeRef}</span>}
                          <span>· Docket #{d.id}</span>
                        </div>
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </div>

          <div className="flex items-start gap-2 rounded-lg border border-border bg-muted/40 p-3 text-xs text-muted-foreground">
            <ShieldAlert className="mt-0.5 h-4 w-4 shrink-0 text-teal-600" />
            <span>
              Docketing, scheduling and appeals are formal, recorded acts of the committee. A member
              may not be docketed to a case against themselves — recusal is enforced by the
              regulator, not left to convention.
            </span>
          </div>
        </LuminousStage>
      </PageShell>
    </AppLayout>
  );
}

interface DocketBody {
  caseId: string;
  memberHealthId: string;
  committeeRef: string;
}

function DocketMemberForm({
  onDocket,
}: {
  onDocket: (body: DocketBody) => Promise<boolean>;
}) {
  const [caseId, setCaseId] = useState("");
  const [memberHealthId, setMemberHealthId] = useState("");
  const [committeeRef, setCommitteeRef] = useState("");
  const [saving, setSaving] = useState(false);

  const submit = async () => {
    if (!caseId.trim() || !memberHealthId.trim() || !committeeRef.trim()) return;
    setSaving(true);
    try {
      const ok = await onDocket({
        caseId: caseId.trim(),
        memberHealthId: memberHealthId.trim(),
        committeeRef: committeeRef.trim(),
      });
      if (ok) {
        setCaseId("");
        setMemberHealthId("");
        setCommitteeRef("");
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="space-y-2 rounded-xl border border-border bg-card p-3">
      <h2 className="flex items-center gap-2 text-sm font-semibold text-foreground">
        <UserPlus className="h-4 w-4 text-teal-600" /> Docket a member
      </h2>
      <div className="grid gap-2">
        <input
          type="text"
          value={caseId}
          onChange={(e) => setCaseId(e.target.value)}
          placeholder="Case ID"
          className="w-full rounded-lg border border-border px-3 py-1.5 text-sm"
        />
        <input
          type="text"
          value={memberHealthId}
          onChange={(e) => setMemberHealthId(e.target.value)}
          placeholder="Member Health ID"
          className="w-full rounded-lg border border-border px-3 py-1.5 text-sm"
        />
        <input
          type="text"
          value={committeeRef}
          onChange={(e) => setCommitteeRef(e.target.value)}
          placeholder="Committee reference"
          className="w-full rounded-lg border border-border px-3 py-1.5 text-sm"
        />
      </div>
      <button
        type="button"
        onClick={submit}
        disabled={saving || !caseId.trim() || !memberHealthId.trim() || !committeeRef.trim()}
        className="inline-flex items-center gap-2 rounded-lg bg-teal-600 px-4 py-1.5 text-xs font-medium text-white hover:bg-teal-700 disabled:opacity-50"
      >
        {saving ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <UserPlus className="h-3.5 w-3.5" />}
        {saving ? "Docketing…" : "Docket member"}
      </button>
      <p className="text-[11px] text-muted-foreground">
        A member cannot be docketed to their own case — recusal is required.
      </p>
    </section>
  );
}

interface ScheduleBody {
  committeeRef: string;
  scheduledAt: string;
}

function ScheduleHearingForm({
  onSchedule,
}: {
  onSchedule: (caseId: string, body: ScheduleBody) => Promise<boolean>;
}) {
  const [caseId, setCaseId] = useState("");
  const [committeeRef, setCommitteeRef] = useState("");
  const [scheduledAt, setScheduledAt] = useState("");
  const [saving, setSaving] = useState(false);

  const submit = async () => {
    if (!caseId.trim() || !committeeRef.trim() || !scheduledAt) return;
    setSaving(true);
    try {
      const ok = await onSchedule(caseId.trim(), {
        committeeRef: committeeRef.trim(),
        scheduledAt: new Date(scheduledAt).toISOString(),
      });
      if (ok) {
        setCaseId("");
        setCommitteeRef("");
        setScheduledAt("");
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="space-y-2 rounded-xl border border-border bg-card p-3">
      <h2 className="flex items-center gap-2 text-sm font-semibold text-foreground">
        <CalendarClock className="h-4 w-4 text-teal-600" /> Schedule a hearing
      </h2>
      <div className="grid gap-2">
        <input
          type="text"
          value={caseId}
          onChange={(e) => setCaseId(e.target.value)}
          placeholder="Case ID"
          className="w-full rounded-lg border border-border px-3 py-1.5 text-sm"
        />
        <input
          type="text"
          value={committeeRef}
          onChange={(e) => setCommitteeRef(e.target.value)}
          placeholder="Committee reference"
          className="w-full rounded-lg border border-border px-3 py-1.5 text-sm"
        />
        <input
          type="datetime-local"
          value={scheduledAt}
          onChange={(e) => setScheduledAt(e.target.value)}
          className="w-full rounded-lg border border-border px-3 py-1.5 text-sm"
        />
      </div>
      <button
        type="button"
        onClick={submit}
        disabled={saving || !caseId.trim() || !committeeRef.trim() || !scheduledAt}
        className="inline-flex items-center gap-2 rounded-lg bg-teal-600 px-4 py-1.5 text-xs font-medium text-white hover:bg-teal-700 disabled:opacity-50"
      >
        {saving ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <CalendarClock className="h-3.5 w-3.5" />}
        {saving ? "Scheduling…" : "Schedule hearing"}
      </button>
    </section>
  );
}

interface AppealBody {
  appealedDecision: string;
  grounds: string;
}

function FileAppealForm({
  onAppeal,
}: {
  onAppeal: (caseId: string, body: AppealBody) => Promise<boolean>;
}) {
  const [caseId, setCaseId] = useState("");
  const [appealedDecision, setAppealedDecision] = useState("");
  const [grounds, setGrounds] = useState("");
  const [saving, setSaving] = useState(false);

  const submit = async () => {
    if (!caseId.trim() || !appealedDecision.trim() || !grounds.trim()) return;
    setSaving(true);
    try {
      const ok = await onAppeal(caseId.trim(), {
        appealedDecision: appealedDecision.trim(),
        grounds: grounds.trim(),
      });
      if (ok) {
        setCaseId("");
        setAppealedDecision("");
        setGrounds("");
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="space-y-2 rounded-xl border border-amber-200 bg-amber-50 p-3">
      <h2 className="flex items-center gap-2 text-sm font-semibold text-amber-900">
        <FileWarning className="h-4 w-4" /> File an appeal
      </h2>
      <div className="grid gap-2">
        <input
          type="text"
          value={caseId}
          onChange={(e) => setCaseId(e.target.value)}
          placeholder="Case ID"
          className="w-full rounded-lg border border-border px-3 py-1.5 text-sm"
        />
        <input
          type="text"
          value={appealedDecision}
          onChange={(e) => setAppealedDecision(e.target.value)}
          placeholder="Decision being appealed"
          className="w-full rounded-lg border border-border px-3 py-1.5 text-sm"
        />
        <textarea
          value={grounds}
          onChange={(e) => setGrounds(e.target.value)}
          rows={3}
          placeholder="Grounds for the appeal."
          className="w-full rounded-lg border border-border px-3 py-2 text-sm"
        />
      </div>
      <button
        type="button"
        onClick={submit}
        disabled={saving || !caseId.trim() || !appealedDecision.trim() || !grounds.trim()}
        className="inline-flex items-center gap-2 rounded-lg bg-amber-600 px-4 py-1.5 text-xs font-medium text-white hover:bg-amber-700 disabled:opacity-50"
      >
        {saving ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <FileWarning className="h-3.5 w-3.5" />}
        {saving ? "Filing…" : "File appeal"}
      </button>
    </section>
  );
}
