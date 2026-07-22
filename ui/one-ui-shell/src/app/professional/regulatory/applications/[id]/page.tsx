"use client";

/**
 * A single regulatory application's correspondence view (ROM applicant self-service). The signed-in
 * professional sees the council's information requests (RFIs) they must answer and the message
 * thread with the regulator, and can respond in place. Reads
 * /internal/v1/me/regulatory/applications/{id}/correspondence — which resolves the caller's OWN
 * application from the trust context (never a providerId param) and filters internal-only notes
 * server-side, so this is genuinely self-service and applicant-safe.
 *
 * Route: /professional/regulatory/applications/[id]
 */

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, ClipboardList, Loader2, MessageSquare, Send, ShieldCheck } from "lucide-react";
import { LuminousStage } from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

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

function unwrap(payload: unknown): Correspondence {
  const p = (payload as { data?: unknown })?.data ?? payload;
  const c = p as Partial<Correspondence>;
  return {
    messages: Array.isArray(c?.messages) ? c.messages : [],
    informationRequests: Array.isArray(c?.informationRequests) ? c.informationRequests : [],
  };
}

const RFI_STYLE: Record<string, string> = {
  OPEN: "bg-amber-50 text-amber-800 border-amber-200",
  RESPONDED: "bg-teal-50 text-teal-700 border-teal-200",
  CLOSED: "border-border text-muted-foreground",
};

export default function RegulatoryApplicationPage() {
  const params = useParams<{ id: string }>();
  const id = params?.id ?? "";

  const [data, setData] = useState<Correspondence | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get<unknown>(
        `/internal/v1/me/regulatory/applications/${encodeURIComponent(id)}/correspondence`,
      );
      setData(unwrap(res));
    } catch {
      setError("Could not load this application's correspondence.");
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <AppLayout>
      <PageShell
        title="Application"
        subtitle="Your regulatory application and correspondence"
        serviceSlug="varapi"
      >
        <LuminousStage className="space-y-6 p-5 sm:p-6">
          <Link
            href="/professional/regulatory"
            className="inline-flex items-center gap-1.5 text-xs font-medium text-teal-700 hover:underline"
          >
            <ArrowLeft className="h-3.5 w-3.5" /> Back to My Regulatory Affairs
          </Link>

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
                <h2 className="flex items-center gap-2 text-sm font-semibold text-foreground">
                  <ClipboardList className="h-4 w-4 text-teal-600" /> Information requests
                </h2>
                {data.informationRequests.length === 0 ? (
                  <p className="rounded-lg border border-border bg-muted/40 p-3 text-sm text-muted-foreground">
                    The council has not requested any further information.
                  </p>
                ) : (
                  data.informationRequests.map((rfi) => (
                    <RfiRow key={rfi.id} applicationId={id} rfi={rfi} onResponded={load} />
                  ))
                )}
              </section>

              <section className="space-y-3">
                <h2 className="flex items-center gap-2 text-sm font-semibold text-foreground">
                  <MessageSquare className="h-4 w-4 text-teal-600" /> Messages
                </h2>
                {data.messages.length === 0 ? (
                  <p className="text-sm text-muted-foreground">No messages yet.</p>
                ) : (
                  <div className="space-y-2">
                    {data.messages.map((m, i) => (
                      <MessageBubble key={i} message={m} />
                    ))}
                  </div>
                )}
                <SendMessageBox applicationId={id} onSent={load} />
              </section>

              <div className="flex items-start gap-2 rounded-lg border border-border bg-muted/40 p-3 text-xs text-muted-foreground">
                <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-teal-600" />
                <span>
                  This is your correspondence with the council on your own application. Only messages
                  shared with you are shown — the council&apos;s internal notes are never displayed.
                </span>
              </div>
            </>
          )}
        </LuminousStage>
      </PageShell>
    </AppLayout>
  );
}

function RfiRow({
  applicationId,
  rfi,
  onResponded,
}: {
  applicationId: string;
  rfi: InformationRequest;
  onResponded: () => void;
}) {
  const [text, setText] = useState("");
  const [saving, setSaving] = useState(false);
  const isOpen = rfi.status?.toUpperCase() === "OPEN";

  const respond = async () => {
    if (!text.trim()) return;
    setSaving(true);
    try {
      await apiClient.post<unknown>(
        `/internal/v1/me/regulatory/applications/${encodeURIComponent(applicationId)}/rfis/${encodeURIComponent(
          rfi.id,
        )}/respond`,
        { responseNotes: text.trim() },
      );
      setText("");
      onResponded();
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="rounded-xl border border-border bg-card p-3">
      <div className="mb-1 flex flex-wrap items-center justify-between gap-2">
        <span
          className={`rounded-full border px-2 py-0.5 text-[11px] font-medium ${
            RFI_STYLE[rfi.status?.toUpperCase()] ?? "border-border text-muted-foreground"
          }`}
        >
          {rfi.status?.toLowerCase()}
        </span>
        {rfi.dueDate && <span className="text-[11px] text-muted-foreground">due {rfi.dueDate}</span>}
      </div>
      <p className="text-sm text-foreground">{rfi.message}</p>
      {rfi.responseNotes && (
        <div className="mt-2 rounded-lg border border-border bg-muted/40 p-2.5 text-xs text-foreground">
          <span className="font-medium text-muted-foreground">Your response:</span> {rfi.responseNotes}
          {rfi.respondedAt ? <span className="ml-1 text-muted-foreground">({rfi.respondedAt})</span> : null}
        </div>
      )}
      {isOpen && (
        <div className="mt-2 space-y-2">
          <textarea
            value={text}
            onChange={(e) => setText(e.target.value)}
            rows={3}
            placeholder="Provide the information the council has requested."
            className="w-full rounded-lg border border-border px-3 py-2 text-sm"
          />
          <button
            type="button"
            onClick={respond}
            disabled={saving || !text.trim()}
            className="inline-flex items-center gap-2 rounded-lg bg-teal-600 px-4 py-1.5 text-xs font-medium text-white hover:bg-teal-700 disabled:opacity-50"
          >
            {saving && <Loader2 className="h-3.5 w-3.5 animate-spin" />}
            {saving ? "Sending…" : "Respond"}
          </button>
        </div>
      )}
    </div>
  );
}

function MessageBubble({ message }: { message: Message }) {
  // INBOUND = the applicant (you); OUTBOUND = the regulator.
  const fromYou = message.direction?.toUpperCase() === "INBOUND";
  return (
    <div className={`flex ${fromYou ? "justify-end" : "justify-start"}`}>
      <div
        className={`max-w-[85%] rounded-2xl border px-3 py-2 text-sm ${
          fromYou
            ? "border-teal-200 bg-teal-50 text-teal-900"
            : "border-border bg-card text-foreground"
        }`}
      >
        <div className="mb-0.5 text-[11px] font-medium text-muted-foreground">
          {fromYou ? "You" : message.authorCapacity?.replace(/_/g, " ") || "Regulator"}
          {message.createdAt ? ` · ${message.createdAt}` : ""}
        </div>
        <p className="whitespace-pre-wrap">{message.body}</p>
      </div>
    </div>
  );
}

function SendMessageBox({ applicationId, onSent }: { applicationId: string; onSent: () => void }) {
  const [text, setText] = useState("");
  const [saving, setSaving] = useState(false);

  const send = async () => {
    if (!text.trim()) return;
    setSaving(true);
    try {
      await apiClient.post<unknown>(
        `/internal/v1/me/regulatory/applications/${encodeURIComponent(applicationId)}/messages`,
        { body: text.trim() },
      );
      setText("");
      onSent();
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-2 rounded-xl border border-border bg-card p-3">
      <label className="text-xs font-medium text-muted-foreground">Send a message</label>
      <textarea
        value={text}
        onChange={(e) => setText(e.target.value)}
        rows={3}
        placeholder="Write a message to the council about this application."
        className="w-full rounded-lg border border-border px-3 py-2 text-sm"
      />
      <button
        type="button"
        onClick={send}
        disabled={saving || !text.trim()}
        className="inline-flex items-center gap-2 rounded-lg bg-teal-600 px-4 py-1.5 text-xs font-medium text-white hover:bg-teal-700 disabled:opacity-50"
      >
        {saving ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Send className="h-3.5 w-3.5" />}
        {saving ? "Sending…" : "Send message"}
      </button>
    </div>
  );
}
