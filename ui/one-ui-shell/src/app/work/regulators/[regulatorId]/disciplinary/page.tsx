"use client";

/**
 * Council officer disciplinary console (ROM regulator lane). An officer looks up a provider's
 * disciplinary cases, opens a formal proceeding, advances a case through the statutory lifecycle,
 * and records a determination once a case reaches hearing.
 *
 * The gateway scopes the officer to their own council from the trust context, so the [regulatorId]
 * route param is context only — it is never sent as a query. The regulator POST actions can return
 * HTTP 403 RECUSAL_REQUIRED: an officer may not run a proceeding against themselves. That is
 * surfaced as a clear amber inline message, not a generic error.
 *
 * The firewall between reputation and regulation is deliberate: opening a proceeding is a formal,
 * recorded act — a complaint or rating never becomes a proceeding automatically.
 *
 * Route: /work/regulators/[regulatorId]/disciplinary
 */

import { useCallback, useState } from "react";
import { useParams } from "next/navigation";
import {
  ArrowRightCircle,
  FileCheck2,
  Gavel,
  Loader2,
  Lock,
  Plus,
  Search,
  ShieldAlert,
} from "lucide-react";
import { LuminousStage } from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

interface DisciplinaryCase {
  id: number;
  caseStage: string;
  status: string;
  summary?: string | null;
  sourceRitoCaseId?: string | null;
  councilId?: string | null;
  openedDate?: string | null;
}

/** Tolerate raw-or-{data} and array-or-{data:array} envelopes from the BFF. */
function unwrapCases(payload: unknown): DisciplinaryCase[] {
  const p = (payload as { data?: unknown })?.data ?? payload;
  if (!Array.isArray(p)) return [];
  return p
    .filter((x): x is Record<string, unknown> => typeof x === "object" && x !== null && "id" in x)
    .map((x) => ({
      id: Number(x.id),
      caseStage: typeof x.caseStage === "string" ? x.caseStage : "",
      status: typeof x.status === "string" ? x.status : "",
      summary: typeof x.summary === "string" ? x.summary : null,
      sourceRitoCaseId: typeof x.sourceRitoCaseId === "string" ? x.sourceRitoCaseId : null,
      councilId: typeof x.councilId === "string" ? x.councilId : null,
      openedDate: typeof x.openedDate === "string" ? x.openedDate : null,
    }));
}

/** A regulator POST can 403 with a RECUSAL_REQUIRED message (own-proceeding guard). */
function isRecusalError(err: unknown): boolean {
  try {
    return JSON.stringify(err).includes("RECUSAL_REQUIRED");
  } catch {
    return false;
  }
}

const RECUSAL_MESSAGE =
  "You cannot run a proceeding against yourself — this must be handled by another officer.";

/** Statutory disciplinary lifecycle in order. */
const STAGES = [
  "RECEIVED",
  "PRELIMINARY_ASSESSMENT",
  "UNDER_INVESTIGATION",
  "CHARGES_FORMULATED",
  "REFERRED_TO_COMMITTEE",
  "HEARING",
  "DETERMINED",
  "APPEALED",
  "CLOSED",
];

const STAGE_LABELS: Record<string, string> = {
  RECEIVED: "Received",
  PRELIMINARY_ASSESSMENT: "Preliminary assessment",
  UNDER_INVESTIGATION: "Under investigation",
  CHARGES_FORMULATED: "Charges formulated",
  REFERRED_TO_COMMITTEE: "Referred to committee",
  HEARING: "Hearing",
  DETERMINED: "Determined",
  APPEALED: "Appealed",
  CLOSED: "Closed",
};

function stageLabel(stage: string): string {
  return STAGE_LABELS[stage] ?? stage.replace(/_/g, " ").toLowerCase();
}

/** Stages an officer may legally advance to from the current stage (everything after it). */
function nextStages(currentStage: string): string[] {
  const idx = STAGES.indexOf(currentStage);
  if (idx < 0) return STAGES;
  return STAGES.slice(idx + 1);
}

const RESTRICTION_TYPES = ["SCOPE_LIMIT", "SUPERVISION", "CONDITION", "INTERDICTION"];

export default function DisciplinaryConsolePage() {
  const params = useParams<{ regulatorId: string }>();
  const regulatorId = params?.regulatorId ?? "";

  const [providerId, setProviderId] = useState("");
  const [lookedUpProviderId, setLookedUpProviderId] = useState<string | null>(null);
  const [cases, setCases] = useState<DisciplinaryCase[]>([]);
  const [activeId, setActiveId] = useState<number | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [recused, setRecused] = useState(false);

  const loadCases = useCallback(async (pid: string) => {
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get<unknown>(
        `/internal/v1/regulatory/console/disciplinary/provider/${encodeURIComponent(pid)}`,
      );
      setCases(unwrapCases(res));
      setLookedUpProviderId(pid);
    } catch {
      setError("Could not load this provider's disciplinary cases. Please try again.");
    } finally {
      setLoading(false);
    }
  }, []);

  const lookUp = () => {
    const pid = providerId.trim();
    if (!pid) return;
    setActiveId(null);
    void loadCases(pid);
  };

  // Shared action runner: refetches the current provider's cases on success and traps
  // RECUSAL_REQUIRED as an amber banner.
  const runAction = useCallback(
    async (fn: () => Promise<void>): Promise<boolean> => {
      setRecused(false);
      setError(null);
      try {
        await fn();
        if (lookedUpProviderId) await loadCases(lookedUpProviderId);
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
    [loadCases, lookedUpProviderId],
  );

  const activeCase = cases.find((c) => c.id === activeId) ?? null;

  return (
    <AppLayout>
      <PageShell
        title="Disciplinary"
        subtitle="Investigations, proceedings and determinations"
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
            {/* Left: provider lookup + open a proceeding */}
            <div className="space-y-5">
              <section className="space-y-2 rounded-xl border border-border bg-card p-3">
                <label className="flex items-center gap-2 text-sm font-semibold text-foreground">
                  <Search className="h-4 w-4 text-teal-600" /> Look up a provider
                </label>
                <div className="flex items-center gap-2">
                  <input
                    type="number"
                    inputMode="numeric"
                    value={providerId}
                    onChange={(e) => setProviderId(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === "Enter") lookUp();
                    }}
                    placeholder="Provider ID"
                    className="w-full rounded-lg border border-border px-3 py-1.5 text-sm"
                  />
                  <button
                    type="button"
                    onClick={lookUp}
                    disabled={loading || !providerId.trim()}
                    className="inline-flex items-center gap-2 rounded-lg bg-teal-600 px-4 py-1.5 text-xs font-medium text-white hover:bg-teal-700 disabled:opacity-50"
                  >
                    {loading ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Search className="h-3.5 w-3.5" />}
                    {loading ? "Loading…" : "Look up"}
                  </button>
                </div>
              </section>

              <OpenProceedingForm
                defaultProviderId={providerId}
                onOpen={(body) =>
                  runAction(async () => {
                    await apiClient.post<unknown>("/internal/v1/regulatory/console/disciplinary", body);
                  })
                }
              />
            </div>

            {/* Right: this provider's cases + selected case actions */}
            <section className="space-y-3">
              <h2 className="flex items-center gap-2 text-sm font-semibold text-foreground">
                <Gavel className="h-4 w-4 text-teal-600" /> Disciplinary cases
              </h2>

              {error && (
                <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">
                  {error}
                </div>
              )}

              {lookedUpProviderId == null ? (
                <div className="flex min-h-[8rem] items-center justify-center rounded-xl border border-dashed border-border bg-muted/20 p-6 text-center text-sm text-muted-foreground">
                  Look up a provider by ID to see their disciplinary cases.
                </div>
              ) : loading ? (
                <div className="flex items-center gap-2 text-sm text-muted-foreground">
                  <Loader2 className="h-4 w-4 animate-spin" /> Loading…
                </div>
              ) : cases.length === 0 ? (
                <div className="rounded-lg border border-border bg-muted/40 p-4 text-sm text-muted-foreground">
                  No disciplinary cases on record for provider {lookedUpProviderId}.
                </div>
              ) : (
                <ul className="space-y-2">
                  {cases.map((c) => {
                    const isActive = c.id === activeId;
                    return (
                      <li key={c.id}>
                        <button
                          type="button"
                          onClick={() => setActiveId(isActive ? null : c.id)}
                          className={`w-full rounded-xl border p-3 text-left transition-colors ${
                            isActive
                              ? "border-teal-300 bg-teal-50"
                              : "border-border bg-card hover:border-teal-200 hover:bg-muted/40"
                          }`}
                        >
                          <div className="flex flex-wrap items-center justify-between gap-2">
                            <span className="text-sm font-semibold text-foreground">
                              {stageLabel(c.caseStage)}
                            </span>
                            <span className="rounded-full border border-border px-2 py-0.5 text-[11px] font-medium text-muted-foreground">
                              {(c.status || "—").replace(/_/g, " ").toLowerCase()}
                            </span>
                          </div>
                          {c.summary && (
                            <p className="mt-1 text-xs text-muted-foreground">{c.summary}</p>
                          )}
                          <div className="mt-1 flex flex-wrap items-center gap-2 text-[11px] text-muted-foreground">
                            <span>Case #{c.id}</span>
                            {c.sourceRitoCaseId && <span>· from complaint {c.sourceRitoCaseId}</span>}
                            {c.openedDate && <span>· opened {c.openedDate}</span>}
                          </div>
                        </button>
                      </li>
                    );
                  })}
                </ul>
              )}

              {activeCase && (
                <CaseWorkspace
                  key={activeCase.id}
                  activeCase={activeCase}
                  onAdvance={(toStage) =>
                    runAction(async () => {
                      await apiClient.post<unknown>(
                        `/internal/v1/regulatory/console/disciplinary/${activeCase.id}/advance`,
                        { toStage },
                      );
                    })
                  }
                  onDetermine={(body) =>
                    runAction(async () => {
                      await apiClient.post<unknown>(
                        `/internal/v1/regulatory/console/disciplinary/${activeCase.id}/determine`,
                        body,
                      );
                    })
                  }
                />
              )}
            </section>
          </div>

          <div className="flex items-start gap-2 rounded-lg border border-border bg-muted/40 p-3 text-xs text-muted-foreground">
            <ShieldAlert className="mt-0.5 h-4 w-4 shrink-0 text-teal-600" />
            <span>
              Opening a proceeding is a formal, recorded act — a complaint never becomes a proceeding
              automatically. Determinations are made by the council following due process.
            </span>
          </div>
        </LuminousStage>
      </PageShell>
    </AppLayout>
  );
}

interface OpenProceedingBody {
  providerId: number;
  councilId?: string;
  ritoCaseId?: string;
  summary: string;
}

function OpenProceedingForm({
  defaultProviderId,
  onOpen,
}: {
  defaultProviderId: string;
  onOpen: (body: OpenProceedingBody) => Promise<boolean>;
}) {
  const [providerId, setProviderId] = useState("");
  const [councilId, setCouncilId] = useState("");
  const [ritoCaseId, setRitoCaseId] = useState("");
  const [summary, setSummary] = useState("");
  const [saving, setSaving] = useState(false);

  // Prefill the provider ID from the lookup box, but let the officer override it.
  const effectiveProviderId = providerId || defaultProviderId;

  const submit = async () => {
    const pid = Number(effectiveProviderId);
    if (!Number.isFinite(pid) || pid <= 0 || !summary.trim()) return;
    setSaving(true);
    try {
      const ok = await onOpen({
        providerId: pid,
        ...(councilId.trim() ? { councilId: councilId.trim() } : {}),
        ...(ritoCaseId.trim() ? { ritoCaseId: ritoCaseId.trim() } : {}),
        summary: summary.trim(),
      });
      if (ok) {
        setProviderId("");
        setCouncilId("");
        setRitoCaseId("");
        setSummary("");
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="space-y-2 rounded-xl border border-border bg-card p-3">
      <h2 className="flex items-center gap-2 text-sm font-semibold text-foreground">
        <Plus className="h-4 w-4 text-teal-600" /> Open a proceeding
      </h2>
      <div className="grid gap-2">
        <input
          type="number"
          inputMode="numeric"
          value={effectiveProviderId}
          onChange={(e) => setProviderId(e.target.value)}
          placeholder="Provider ID"
          className="w-full rounded-lg border border-border px-3 py-1.5 text-sm"
        />
        <input
          type="text"
          value={councilId}
          onChange={(e) => setCouncilId(e.target.value)}
          placeholder="Council ID (optional)"
          className="w-full rounded-lg border border-border px-3 py-1.5 text-sm"
        />
        <input
          type="text"
          value={ritoCaseId}
          onChange={(e) => setRitoCaseId(e.target.value)}
          placeholder="Originating complaint / Rito case ID (optional)"
          className="w-full rounded-lg border border-border px-3 py-1.5 text-sm"
        />
        <textarea
          value={summary}
          onChange={(e) => setSummary(e.target.value)}
          rows={3}
          placeholder="Summary of the matter to be investigated."
          className="w-full rounded-lg border border-border px-3 py-2 text-sm"
        />
      </div>
      <button
        type="button"
        onClick={submit}
        disabled={saving || !summary.trim() || !effectiveProviderId.trim()}
        className="inline-flex items-center gap-2 rounded-lg bg-teal-600 px-4 py-1.5 text-xs font-medium text-white hover:bg-teal-700 disabled:opacity-50"
      >
        {saving ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Plus className="h-3.5 w-3.5" />}
        {saving ? "Opening…" : "Open proceeding"}
      </button>
      <p className="text-[11px] text-muted-foreground">
        Opening a proceeding is a formal, recorded act — a complaint never becomes a proceeding
        automatically.
      </p>
    </section>
  );
}

interface DetermineBody {
  decision: string;
  restrictionType?: string;
  restrictionDescription?: string;
}

function CaseWorkspace({
  activeCase,
  onAdvance,
  onDetermine,
}: {
  activeCase: DisciplinaryCase;
  onAdvance: (toStage: string) => Promise<boolean>;
  onDetermine: (body: DetermineBody) => Promise<boolean>;
}) {
  const isHearing = activeCase.caseStage?.toUpperCase() === "HEARING";

  return (
    <div className="space-y-3 rounded-xl border border-teal-200 bg-teal-50/40 p-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="text-sm font-semibold text-foreground">
          Case #{activeCase.id} · {stageLabel(activeCase.caseStage)}
        </span>
        <span className="rounded-full border border-border px-2 py-0.5 text-[11px] font-medium text-muted-foreground">
          {(activeCase.status || "—").replace(/_/g, " ").toLowerCase()}
        </span>
      </div>

      <AdvanceForm currentStage={activeCase.caseStage} onAdvance={onAdvance} />

      {isHearing && <DetermineForm onDetermine={onDetermine} />}
    </div>
  );
}

function AdvanceForm({
  currentStage,
  onAdvance,
}: {
  currentStage: string;
  onAdvance: (toStage: string) => Promise<boolean>;
}) {
  const options = nextStages(currentStage?.toUpperCase() ?? "");
  const [toStage, setToStage] = useState(options[0] ?? "");
  const [saving, setSaving] = useState(false);

  const submit = async () => {
    if (!toStage) return;
    setSaving(true);
    try {
      await onAdvance(toStage);
    } finally {
      setSaving(false);
    }
  };

  if (options.length === 0) {
    return (
      <p className="text-xs text-muted-foreground">
        This case has reached the end of the disciplinary lifecycle.
      </p>
    );
  }

  return (
    <div className="space-y-2 rounded-lg border border-border bg-card p-3">
      <label className="flex items-center gap-2 text-xs font-medium text-muted-foreground">
        <ArrowRightCircle className="h-3.5 w-3.5 text-teal-600" /> Advance stage
      </label>
      <div className="flex flex-wrap items-center gap-2">
        <select
          value={toStage}
          onChange={(e) => setToStage(e.target.value)}
          className="rounded-lg border border-border px-3 py-1.5 text-sm"
        >
          {options.map((s) => (
            <option key={s} value={s}>
              {stageLabel(s)}
            </option>
          ))}
        </select>
        <button
          type="button"
          onClick={submit}
          disabled={saving || !toStage}
          className="inline-flex items-center gap-2 rounded-lg bg-teal-600 px-4 py-1.5 text-xs font-medium text-white hover:bg-teal-700 disabled:opacity-50"
        >
          {saving ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <ArrowRightCircle className="h-3.5 w-3.5" />}
          {saving ? "Advancing…" : "Advance"}
        </button>
      </div>
    </div>
  );
}

function DetermineForm({ onDetermine }: { onDetermine: (body: DetermineBody) => Promise<boolean> }) {
  const [decision, setDecision] = useState("");
  const [restrictionType, setRestrictionType] = useState("");
  const [restrictionDescription, setRestrictionDescription] = useState("");
  const [saving, setSaving] = useState(false);

  const submit = async () => {
    if (!decision.trim()) return;
    setSaving(true);
    try {
      const ok = await onDetermine({
        decision: decision.trim(),
        ...(restrictionType ? { restrictionType } : {}),
        ...(restrictionDescription.trim()
          ? { restrictionDescription: restrictionDescription.trim() }
          : {}),
      });
      if (ok) {
        setDecision("");
        setRestrictionType("");
        setRestrictionDescription("");
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-2 rounded-lg border border-amber-200 bg-amber-50 p-3">
      <label className="flex items-center gap-2 text-xs font-semibold text-amber-900">
        <Gavel className="h-3.5 w-3.5" /> Record determination
      </label>
      <input
        type="text"
        value={decision}
        onChange={(e) => setDecision(e.target.value)}
        placeholder="Decision (e.g. no case to answer, reprimand, restriction imposed)"
        className="w-full rounded-lg border border-border px-3 py-1.5 text-sm"
      />
      <div className="grid gap-2 sm:grid-cols-[minmax(0,14rem)_1fr]">
        <select
          value={restrictionType}
          onChange={(e) => setRestrictionType(e.target.value)}
          className="rounded-lg border border-border px-3 py-1.5 text-sm"
        >
          <option value="">No restriction</option>
          {RESTRICTION_TYPES.map((t) => (
            <option key={t} value={t}>
              {t.replace(/_/g, " ").toLowerCase()}
            </option>
          ))}
        </select>
        <input
          type="text"
          value={restrictionDescription}
          onChange={(e) => setRestrictionDescription(e.target.value)}
          placeholder="Restriction description (optional)"
          className="w-full rounded-lg border border-border px-3 py-1.5 text-sm"
        />
      </div>
      <button
        type="button"
        onClick={submit}
        disabled={saving || !decision.trim()}
        className="inline-flex items-center gap-2 rounded-lg bg-amber-600 px-4 py-1.5 text-xs font-medium text-white hover:bg-amber-700 disabled:opacity-50"
      >
        {saving ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <FileCheck2 className="h-3.5 w-3.5" />}
        {saving ? "Recording…" : "Record determination"}
      </button>
    </div>
  );
}
