"use client";

/**
 * Council officer registration-review console (ROM regulator lane). An officer of a regulatory
 * council works the queue of open professional applications for their council: they read each
 * application's full correspondence (including INTERNAL notes), open and close information requests
 * (RFIs), post applicant-visible or internal messages, and record the final decision.
 *
 * The gateway scopes the officer to their own council from the trust context, so the [regulatorId]
 * route param is context only — it is never sent as a query. The regulator POST actions can return
 * HTTP 403 RECUSAL_REQUIRED: a person may not process their OWN application. That is surfaced as a
 * clear amber inline message, not a generic error.
 *
 * Route: /work/regulators/[regulatorId]/registration-review
 */

import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import {
  ClipboardList,
  FileCheck2,
  Gavel,
  Inbox,
  Loader2,
  Lock,
  MessageSquare,
  Plus,
  ShieldAlert,
} from "lucide-react";
import { LuminousStage } from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

interface Application {
  id: number;
  applicationType?: string | null;
  workflowState?: string | null;
  councilCode?: string | null;
  submittedAt?: string | null;
}
interface Message {
  direction: string;
  visibility: string;
  body: string;
  authorCapacity?: string | null;
  createdAt?: string | null;
}
interface InformationRequest {
  id: number;
  message: string;
  status: string;
  dueDate?: string | null;
  responseNotes?: string | null;
  respondedAt?: string | null;
}
interface Correspondence {
  messages: Message[];
  informationRequests: InformationRequest[];
}

/** Tolerate raw-or-{data} and array-or-{data:array} envelopes from the BFF. */
function unwrapApplications(payload: unknown): Application[] {
  const p = (payload as { data?: unknown })?.data ?? payload;
  if (!Array.isArray(p)) return [];
  return p.filter((x): x is Application => typeof x === "object" && x !== null && "id" in x);
}

function unwrapCorrespondence(payload: unknown): Correspondence {
  const p = (payload as { data?: unknown })?.data ?? payload;
  const c = p as Partial<Correspondence>;
  return {
    messages: Array.isArray(c?.messages) ? c.messages : [],
    informationRequests: Array.isArray(c?.informationRequests) ? c.informationRequests : [],
  };
}

/** A regulator POST can 403 with a RECUSAL_REQUIRED message (own-application guard). */
function isRecusalError(err: unknown): boolean {
  try {
    return JSON.stringify(err).includes("RECUSAL_REQUIRED");
  } catch {
    return false;
  }
}

const RECUSAL_MESSAGE =
  "You cannot process your own application — this must be handled by another officer.";

const RFI_STYLE: Record<string, string> = {
  OPEN: "bg-amber-50 text-amber-800 border-amber-200",
  RESPONDED: "bg-teal-50 text-teal-700 border-teal-200",
  CLOSED: "border-border text-muted-foreground",
};

const DECISION_OPTIONS = ["APPROVED", "REFUSED", "DEFERRED"];

export default function RegistrationReviewPage() {
  const params = useParams<{ regulatorId: string }>();
  const regulatorId = params?.regulatorId ?? "";

  const [applications, setApplications] = useState<Application[]>([]);
  const [activeId, setActiveId] = useState<number | null>(null);
  const [loadingQueue, setLoadingQueue] = useState(false);
  const [queueError, setQueueError] = useState<string | null>(null);

  const loadQueue = useCallback(async () => {
    setLoadingQueue(true);
    setQueueError(null);
    try {
      const res = await apiClient.get<unknown>("/internal/v1/regulatory/console/applications");
      setApplications(unwrapApplications(res));
    } catch {
      setQueueError("Could not load the application queue. Please try again.");
    } finally {
      setLoadingQueue(false);
    }
  }, []);

  useEffect(() => {
    void loadQueue();
  }, [loadQueue]);

  return (
    <AppLayout>
      <PageShell
        title="Registration review"
        subtitle="Process professional applications for your council"
        serviceSlug="varapi"
      >
        <LuminousStage className="space-y-5 p-5 sm:p-6">
          {regulatorId && (
            <span className="inline-flex items-center gap-1.5 rounded-full border border-border bg-muted/40 px-2.5 py-1 text-[11px] font-medium text-muted-foreground">
              <ShieldAlert className="h-3.5 w-3.5 text-teal-600" /> Council {regulatorId}
            </span>
          )}

          <div className="grid gap-5 lg:grid-cols-[minmax(0,20rem)_1fr]">
            {/* Left: application queue */}
            <section className="space-y-3">
              <h2 className="flex items-center gap-2 text-sm font-semibold text-foreground">
                <Inbox className="h-4 w-4 text-teal-600" /> Application queue
              </h2>
              {loadingQueue ? (
                <div className="flex items-center gap-2 text-sm text-muted-foreground">
                  <Loader2 className="h-4 w-4 animate-spin" /> Loading…
                </div>
              ) : queueError ? (
                <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">
                  {queueError}
                </div>
              ) : applications.length === 0 ? (
                <div className="rounded-lg border border-border bg-muted/40 p-4 text-sm text-muted-foreground">
                  No open applications in your queue.
                </div>
              ) : (
                <ul className="space-y-2">
                  {applications.map((app) => {
                    const isActive = app.id === activeId;
                    return (
                      <li key={app.id}>
                        <button
                          type="button"
                          onClick={() => setActiveId(app.id)}
                          className={`w-full rounded-xl border p-3 text-left transition-colors ${
                            isActive
                              ? "border-teal-300 bg-teal-50"
                              : "border-border bg-card hover:border-teal-200 hover:bg-muted/40"
                          }`}
                        >
                          <div className="text-sm font-semibold text-foreground">
                            {app.applicationType?.replace(/_/g, " ") || `Application #${app.id}`}
                          </div>
                          <div className="mt-0.5 flex flex-wrap items-center gap-1.5 text-[11px] text-muted-foreground">
                            <span className="rounded-full border border-border px-1.5 py-0.5">
                              {(app.workflowState || "—").replace(/_/g, " ").toLowerCase()}
                            </span>
                            {app.councilCode && <span>{app.councilCode}</span>}
                            {app.submittedAt && <span>· {app.submittedAt}</span>}
                          </div>
                        </button>
                      </li>
                    );
                  })}
                </ul>
              )}
            </section>

            {/* Right: correspondence + actions for the selected application */}
            <section>
              {activeId == null ? (
                <div className="flex h-full min-h-[12rem] items-center justify-center rounded-xl border border-dashed border-border bg-muted/20 p-6 text-center text-sm text-muted-foreground">
                  Select an application from the queue to review its correspondence and record a
                  decision.
                </div>
              ) : (
                <ApplicationWorkspace
                  key={activeId}
                  applicationId={activeId}
                  onDecisionRecorded={loadQueue}
                />
              )}
            </section>
          </div>
        </LuminousStage>
      </PageShell>
    </AppLayout>
  );
}

function ApplicationWorkspace({
  applicationId,
  onDecisionRecorded,
}: {
  applicationId: number;
  onDecisionRecorded: () => void;
}) {
  const [data, setData] = useState<Correspondence | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [recused, setRecused] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get<unknown>(
        `/internal/v1/regulatory/console/applications/${applicationId}/correspondence`,
      );
      setData(unwrapCorrespondence(res));
    } catch {
      setError("Could not load this application's correspondence.");
    } finally {
      setLoading(false);
    }
  }, [applicationId]);

  useEffect(() => {
    void load();
  }, [load]);

  // Shared action runner: refetches on success and traps RECUSAL_REQUIRED as an amber banner.
  const runAction = useCallback(
    async (fn: () => Promise<void>): Promise<boolean> => {
      setRecused(false);
      try {
        await fn();
        await load();
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
    [load],
  );

  const recordDecision = useCallback(
    (finalDecision: string, notes: string) =>
      runAction(async () => {
        await apiClient.post<unknown>(
          `/internal/v1/regulatory/console/applications/${applicationId}/decision`,
          { finalDecision, ...(notes ? { notes } : {}) },
        );
        onDecisionRecorded();
      }),
    [applicationId, onDecisionRecorded, runAction],
  );

  return (
    <div className="space-y-5">
      {recused && (
        <div className="flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
          <Lock className="mt-0.5 h-4 w-4 shrink-0" />
          <span>{RECUSAL_MESSAGE}</span>
        </div>
      )}

      {loading ? (
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading…
        </div>
      ) : error ? (
        <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">{error}</div>
      ) : !data ? (
        <div className="rounded-lg border border-border bg-muted/40 p-4 text-sm text-muted-foreground">
          Nothing to show for this application yet.
        </div>
      ) : (
        <>
          <section className="space-y-3">
            <h3 className="flex items-center gap-2 text-sm font-semibold text-foreground">
              <ClipboardList className="h-4 w-4 text-teal-600" /> Information requests
            </h3>
            <OpenRfiForm
              onOpen={(message, dueDate) =>
                runAction(async () => {
                  await apiClient.post<unknown>(
                    `/internal/v1/regulatory/console/applications/${applicationId}/rfis`,
                    { message, ...(dueDate ? { dueDate } : {}) },
                  );
                })
              }
            />
            {data.informationRequests.length === 0 ? (
              <p className="text-sm text-muted-foreground">No information requests raised yet.</p>
            ) : (
              data.informationRequests.map((rfi) => (
                <RfiRow
                  key={rfi.id}
                  rfi={rfi}
                  onClose={() =>
                    runAction(async () => {
                      await apiClient.post<unknown>(
                        `/internal/v1/regulatory/console/applications/${applicationId}/rfis/${rfi.id}/close`,
                      );
                    })
                  }
                />
              ))
            )}
          </section>

          <section className="space-y-3">
            <h3 className="flex items-center gap-2 text-sm font-semibold text-foreground">
              <MessageSquare className="h-4 w-4 text-teal-600" /> Messages
            </h3>
            {data.messages.length === 0 ? (
              <p className="text-sm text-muted-foreground">No messages yet.</p>
            ) : (
              <div className="space-y-2">
                {data.messages.map((m, i) => (
                  <MessageBubble key={i} message={m} />
                ))}
              </div>
            )}
            <AddMessageForm
              onSend={(bodyText, visibility) =>
                runAction(async () => {
                  await apiClient.post<unknown>(
                    `/internal/v1/regulatory/console/applications/${applicationId}/messages`,
                    { body: bodyText, visibility },
                  );
                })
              }
            />
          </section>

          <DecisionForm onRecord={recordDecision} />
        </>
      )}
    </div>
  );
}

function OpenRfiForm({
  onOpen,
}: {
  onOpen: (message: string, dueDate: string) => Promise<boolean>;
}) {
  const [message, setMessage] = useState("");
  const [dueDate, setDueDate] = useState("");
  const [saving, setSaving] = useState(false);

  const submit = async () => {
    if (!message.trim()) return;
    setSaving(true);
    try {
      const ok = await onOpen(message.trim(), dueDate);
      if (ok) {
        setMessage("");
        setDueDate("");
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-2 rounded-xl border border-border bg-card p-3">
      <label className="text-xs font-medium text-muted-foreground">Open an information request</label>
      <textarea
        value={message}
        onChange={(e) => setMessage(e.target.value)}
        rows={2}
        placeholder="Describe the information the applicant must provide."
        className="w-full rounded-lg border border-border px-3 py-2 text-sm"
      />
      <div className="flex flex-wrap items-center gap-2">
        <label className="text-[11px] text-muted-foreground">
          Due date
          <input
            type="date"
            value={dueDate}
            onChange={(e) => setDueDate(e.target.value)}
            className="ml-2 rounded-lg border border-border px-2 py-1 text-xs"
          />
        </label>
        <button
          type="button"
          onClick={submit}
          disabled={saving || !message.trim()}
          className="inline-flex items-center gap-2 rounded-lg bg-teal-600 px-4 py-1.5 text-xs font-medium text-white hover:bg-teal-700 disabled:opacity-50"
        >
          {saving ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Plus className="h-3.5 w-3.5" />}
          {saving ? "Opening…" : "Open RFI"}
        </button>
      </div>
    </div>
  );
}

function RfiRow({ rfi, onClose }: { rfi: InformationRequest; onClose: () => Promise<boolean> }) {
  const [saving, setSaving] = useState(false);
  const status = rfi.status?.toUpperCase() ?? "";
  const isOpen = status === "OPEN";

  const close = async () => {
    setSaving(true);
    try {
      await onClose();
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="rounded-xl border border-border bg-card p-3">
      <div className="mb-1 flex flex-wrap items-center justify-between gap-2">
        <span
          className={`rounded-full border px-2 py-0.5 text-[11px] font-medium ${
            RFI_STYLE[status] ?? "border-border text-muted-foreground"
          }`}
        >
          {rfi.status?.toLowerCase()}
        </span>
        <div className="flex items-center gap-2">
          {rfi.dueDate && <span className="text-[11px] text-muted-foreground">due {rfi.dueDate}</span>}
          {isOpen && (
            <button
              type="button"
              onClick={close}
              disabled={saving}
              className="inline-flex items-center gap-1 rounded-lg border border-border px-2.5 py-1 text-[11px] font-medium text-foreground hover:bg-muted/40 disabled:opacity-50"
            >
              {saving && <Loader2 className="h-3 w-3 animate-spin" />} Close
            </button>
          )}
        </div>
      </div>
      <p className="text-sm text-foreground">{rfi.message}</p>
      {rfi.responseNotes && (
        <div className="mt-2 rounded-lg border border-border bg-muted/40 p-2.5 text-xs text-foreground">
          <span className="font-medium text-muted-foreground">Applicant response:</span>{" "}
          {rfi.responseNotes}
          {rfi.respondedAt ? <span className="ml-1 text-muted-foreground">({rfi.respondedAt})</span> : null}
        </div>
      )}
    </div>
  );
}

function MessageBubble({ message }: { message: Message }) {
  // OUTBOUND = from the council (this officer's side); INBOUND = from the applicant.
  const fromCouncil = message.direction?.toUpperCase() === "OUTBOUND";
  const isInternal = message.visibility?.toUpperCase() === "INTERNAL";
  return (
    <div className={`flex ${fromCouncil ? "justify-end" : "justify-start"}`}>
      <div
        className={`max-w-[85%] rounded-2xl border px-3 py-2 text-sm ${
          isInternal
            ? "border-amber-200 bg-amber-50 text-amber-900"
            : fromCouncil
              ? "border-teal-200 bg-teal-50 text-teal-900"
              : "border-border bg-card text-foreground"
        }`}
      >
        <div className="mb-0.5 flex items-center gap-1.5 text-[11px] font-medium text-muted-foreground">
          <span>
            {fromCouncil ? message.authorCapacity?.replace(/_/g, " ") || "Council" : "Applicant"}
            {message.createdAt ? ` · ${message.createdAt}` : ""}
          </span>
          {isInternal && (
            <span className="rounded-full bg-amber-100 px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-amber-800">
              internal
            </span>
          )}
        </div>
        <p className="whitespace-pre-wrap">{message.body}</p>
      </div>
    </div>
  );
}

function AddMessageForm({
  onSend,
}: {
  onSend: (body: string, visibility: string) => Promise<boolean>;
}) {
  const [text, setText] = useState("");
  const [visibility, setVisibility] = useState<"APPLICANT" | "INTERNAL">("APPLICANT");
  const [saving, setSaving] = useState(false);

  const send = async () => {
    if (!text.trim()) return;
    setSaving(true);
    try {
      const ok = await onSend(text.trim(), visibility);
      if (ok) setText("");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-2 rounded-xl border border-border bg-card p-3">
      <label className="text-xs font-medium text-muted-foreground">Add a message</label>
      <textarea
        value={text}
        onChange={(e) => setText(e.target.value)}
        rows={3}
        placeholder="Write a message about this application."
        className="w-full rounded-lg border border-border px-3 py-2 text-sm"
      />
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="inline-flex rounded-lg border border-border p-0.5 text-xs">
          <button
            type="button"
            onClick={() => setVisibility("APPLICANT")}
            className={`rounded-md px-2.5 py-1 font-medium ${
              visibility === "APPLICANT" ? "bg-teal-600 text-white" : "text-muted-foreground"
            }`}
          >
            Applicant-visible
          </button>
          <button
            type="button"
            onClick={() => setVisibility("INTERNAL")}
            className={`rounded-md px-2.5 py-1 font-medium ${
              visibility === "INTERNAL" ? "bg-amber-500 text-white" : "text-muted-foreground"
            }`}
          >
            Internal note
          </button>
        </div>
        <button
          type="button"
          onClick={send}
          disabled={saving || !text.trim()}
          className="inline-flex items-center gap-2 rounded-lg bg-teal-600 px-4 py-1.5 text-xs font-medium text-white hover:bg-teal-700 disabled:opacity-50"
        >
          {saving && <Loader2 className="h-3.5 w-3.5 animate-spin" />}
          {saving ? "Sending…" : "Send message"}
        </button>
      </div>
    </div>
  );
}

function DecisionForm({
  onRecord,
}: {
  onRecord: (finalDecision: string, notes: string) => Promise<boolean>;
}) {
  const [finalDecision, setFinalDecision] = useState(DECISION_OPTIONS[0]);
  const [notes, setNotes] = useState("");
  const [saving, setSaving] = useState(false);

  const submit = async () => {
    if (!finalDecision.trim()) return;
    setSaving(true);
    try {
      const ok = await onRecord(finalDecision, notes.trim());
      if (ok) setNotes("");
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="space-y-2 rounded-xl border border-border bg-card p-3">
      <h3 className="flex items-center gap-2 text-sm font-semibold text-foreground">
        <Gavel className="h-4 w-4 text-teal-600" /> Record decision
      </h3>
      <div className="flex flex-wrap items-center gap-2">
        <select
          value={finalDecision}
          onChange={(e) => setFinalDecision(e.target.value)}
          className="rounded-lg border border-border px-3 py-1.5 text-sm"
        >
          {DECISION_OPTIONS.map((opt) => (
            <option key={opt} value={opt}>
              {opt.charAt(0) + opt.slice(1).toLowerCase()}
            </option>
          ))}
        </select>
      </div>
      <textarea
        value={notes}
        onChange={(e) => setNotes(e.target.value)}
        rows={3}
        placeholder="Decision notes (optional)."
        className="w-full rounded-lg border border-border px-3 py-2 text-sm"
      />
      <button
        type="button"
        onClick={submit}
        disabled={saving || !finalDecision.trim()}
        className="inline-flex items-center gap-2 rounded-lg bg-teal-600 px-4 py-1.5 text-xs font-medium text-white hover:bg-teal-700 disabled:opacity-50"
      >
        {saving ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <FileCheck2 className="h-3.5 w-3.5" />}
        {saving ? "Recording…" : "Record decision"}
      </button>
    </section>
  );
}
