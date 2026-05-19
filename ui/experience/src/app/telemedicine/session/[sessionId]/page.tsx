"use client";

/**
 * Teleconsult Session Workspace — 3-pane layout (Stage 5).
 *
 * LEFT:   Communication (chat, audio/video buttons, call log)
 * CENTER: Response note draft (structured form, auto-save indicator)
 * RIGHT:  Patient info (summary, referral, attachments, timeline)
 *
 * Also handles Stage 6 (submit response) and Stage 7 (completion note).
 */

import { useEffect, useMemo, useRef, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, CheckCircle2, FileText, Loader2, Lock, Mic, Send, Shield, User, Video, AlertTriangle, Activity } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { TelemedicineWorkflowStrip } from "@/components/clinical/TelemedicineWorkflowStrip";
import { apiClient } from "@/lib/api-client";
import { useAuthStore } from "@/hooks/useAuthStore";

interface Message {
  id: string;
  senderId: string;
  senderName: string;
  content: string;
  type: string;
  timestamp: string;
}

export default function TeleconsultSessionPage() {
  const params = useParams();
  const router = useRouter();
  const sessionId = params.sessionId as string;
  const user = useAuthStore((s) => s.user);

  // Session state
  const [session, setSession] = useState<Record<string, unknown> | null>(null);
  const [loading, setLoading] = useState(true);
  const [messages, setMessages] = useState<Message[]>([]);
  const [newMessage, setNewMessage] = useState("");
  const [sending, setSending] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  // Response draft (Stage 6)
  const [responseNote, setResponseNote] = useState("");
  const [diagnosis, setDiagnosis] = useState("");
  const [actionPlan, setActionPlan] = useState("");
  const [redFlags, setRedFlags] = useState("");
  const [followUp, setFollowUp] = useState("");
  const [orders, setOrders] = useState("");
  const [submittingResponse, setSubmittingResponse] = useState(false);

  // Completion (Stage 7)
  const [showCompletion, setShowCompletion] = useState(false);
  const [actionsTaken, setActionsTaken] = useState("");
  const [patientOutcome, setPatientOutcome] = useState("");
  const [followUpExecution, setFollowUpExecution] = useState("");
  const [closureNarrative, setClosureNarrative] = useState("");
  const [submittingCompletion, setSubmittingCompletion] = useState(false);

  const chatEndRef = useRef<HTMLDivElement>(null);

  // Load session
  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      setSession(null);
      try {
        const res = await apiClient.get<{ data: Record<string, unknown> }>(`/internal/v1/teleconsult/sessions/${sessionId}`);
        if (!cancelled) setSession(res.data);
      } catch {
        if (!cancelled) setSession(null);
      }
      try {
        const msgs = await apiClient.get<{ data: Message[] }>(`/internal/v1/teleconsult/sessions/${sessionId}/messages`);
        if (!cancelled) setMessages(msgs.data ?? []);
      } catch {
        if (!cancelled) setMessages([]);
      }
      if (!cancelled) setLoading(false);
    }
    void load();
    return () => {
      cancelled = true;
    };
  }, [sessionId]);

  // Auto-scroll chat
  useEffect(() => {
    if (typeof chatEndRef.current?.scrollIntoView === "function") {
      chatEndRef.current.scrollIntoView({ behavior: "smooth" });
    }
  }, [messages]);

  const responseDraftDirty = useMemo(
    () =>
      Boolean(
        responseNote.trim() ||
          diagnosis.trim() ||
          actionPlan.trim() ||
          redFlags.trim() ||
          followUp.trim() ||
          orders.trim(),
      ),
    [actionPlan, diagnosis, followUp, orders, redFlags, responseNote],
  );

  async function handleSendMessage() {
    if (!newMessage.trim()) return;
    setSending(true);
    try {
      const res = await apiClient.post<{ data: Message }>(`/internal/v1/teleconsult/sessions/${sessionId}/messages`, {
        content: newMessage.trim(),
        senderName: user?.displayName || user?.email || "Unknown",
        type: "TEXT",
      });
      setMessages((prev) => [...prev, res.data]);
      setActionError(null);
    } catch {
      setActionError("Messaging backend is unavailable. Message was not sent.");
    }
    setNewMessage("");
    setSending(false);
  }

  async function handleSubmitResponse() {
    setSubmittingResponse(true);
    try {
      await apiClient.post(`/internal/v1/teleconsult/sessions/${sessionId}/response`, {
        responseNote, diagnosis, actionPlan, redFlags, followUp,
        orders: orders.split("\n").filter(Boolean),
      });
      setActionError(null);
    } catch {
      setActionError("Unable to submit response package because teleconsult backend is unavailable.");
    }
    setSubmittingResponse(false);
  }

  async function handleSubmitCompletion() {
    setSubmittingCompletion(true);
    try {
      await apiClient.post(`/internal/v1/teleconsult/sessions/${sessionId}/complete`, {
        actionsTaken, patientOutcome, followUpExecution, closureNarrative,
      });
      setActionError(null);
    } catch {
      setActionError("Unable to close this case because teleconsult backend is unavailable.");
    }
    setSubmittingCompletion(false);
  }

  const status = (session?.status as string) || "ACTIVE";
  const isResponded = status === "RESPONDED" || status === "CLOSED";
  const isClosed = status === "CLOSED";

  if (loading) {
    return (
      <AppLayout>
        <div className="flex items-center justify-center h-[calc(100vh-8rem)]">
          <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
          <span className="ml-2 text-sm text-gray-500">Loading session...</span>
        </div>
      </AppLayout>
    );
  }

  if (!session) {
    return (
      <AppLayout>
        <div className="mx-auto max-w-lg space-y-4 p-8">
          <Link href="/telemedicine" className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-800">
            <ArrowLeft className="h-4 w-4" />
            Back to Telemedicine Hub
          </Link>
          <div className="rounded-xl border border-amber-200 bg-amber-50 p-6 text-sm text-amber-950">
            <p className="font-semibold text-amber-900">Teleconsult session unavailable</p>
            <p className="mt-2 text-amber-900/90">
              The Experience BFF did not return a session payload for this identifier. No synthetic session is shown in production navigation — verify the session id or try again after the teleconsult service is reachable.
            </p>
          </div>
        </div>
      </AppLayout>
    );
  }

  // ── Stage 7: Show completion form if responded ──
  if (showCompletion && isResponded && !isClosed) {
    return (
      <AppLayout>
        <div className="max-w-2xl mx-auto p-4 space-y-4">
          <Link href={`/telemedicine/session/${sessionId}`} onClick={(e) => { e.preventDefault(); setShowCompletion(false); }}
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700">
            <ArrowLeft className="w-4 h-4" /> Back to session
          </Link>
          <div className="bg-white rounded-xl border-2 border-impilo-200 p-6 space-y-5">
            <h2 className="text-lg font-semibold text-gray-900 flex items-center gap-2">
              <CheckCircle2 className="w-5 h-5 text-impilo-500" /> Stage 7 — Completion Note & Loop Closure
            </h2>
            <label className="block" htmlFor="teleconsult-completion-actions">
              <span className="text-sm font-medium text-gray-700">Actions taken *</span>
              <textarea
                id="teleconsult-completion-actions"
                value={actionsTaken}
                onChange={(e) => setActionsTaken(e.target.value)}
                rows={3}
                className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                placeholder="Medications administered, tests done, procedures, monitoring, counseling..."
              />
            </label>
            <label className="block" htmlFor="teleconsult-completion-outcome">
              <span className="text-sm font-medium text-gray-700">Patient outcome *</span>
              <select
                id="teleconsult-completion-outcome"
                value={patientOutcome}
                onChange={(e) => setPatientOutcome(e.target.value)}
                className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
              >
                <option value="">Select...</option>
                <option value="IMPROVED">Improved</option>
                <option value="STABLE">Stable</option>
                <option value="DETERIORATED">Deteriorated</option>
                <option value="REFERRED">Referred / Transferred</option>
                <option value="DISCHARGED">Discharged</option>
                <option value="RETURNED_FOR_REVIEW">Returned for review</option>
              </select>
            </label>
            <label className="block" htmlFor="teleconsult-completion-followup">
              <span className="text-sm font-medium text-gray-700">Follow-up execution</span>
              <select
                id="teleconsult-completion-followup"
                value={followUpExecution}
                onChange={(e) => setFollowUpExecution(e.target.value)}
                className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
              >
                <option value="">Select...</option>
                <option value="COMPLETED">Completed</option>
                <option value="PARTIALLY_COMPLETED">Partially completed</option>
                <option value="NOT_COMPLETED">Not completed</option>
              </select>
            </label>
            <label className="block" htmlFor="teleconsult-completion-narrative">
              <span className="text-sm font-medium text-gray-700">Case closure narrative</span>
              <textarea
                id="teleconsult-completion-narrative"
                value={closureNarrative}
                onChange={(e) => setClosureNarrative(e.target.value)}
                rows={3}
                className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                placeholder="Brief summary of the case and its resolution..."
              />
            </label>
            <button onClick={handleSubmitCompletion} disabled={submittingCompletion || !actionsTaken.trim()}
              className="w-full flex items-center justify-center gap-2 py-2.5 bg-impilo-500 text-white text-sm font-medium rounded-lg hover:bg-impilo-600 disabled:opacity-40 transition-colors">
              {submittingCompletion ? <Loader2 className="w-4 h-4 animate-spin" /> : <Lock className="w-4 h-4" />}
              {submittingCompletion ? "Closing..." : "Close Case & Archive"}
            </button>
          </div>
        </div>
      </AppLayout>
    );
  }

  // ── 3-Pane Session Workspace ──
  return (
    <div className="flex flex-col h-screen bg-gray-50">
      {/* Top bar */}
      <header className="flex h-12 shrink-0 items-center justify-between border-b bg-white px-4">
        <div className="flex items-center gap-3">
          <Link href="/telemedicine" className="text-gray-400 hover:text-gray-600">
            <ArrowLeft className="w-4 h-4" />
          </Link>
          <Video className="w-4 h-4 text-impilo-500" />
          <span className="text-sm font-semibold text-gray-900">Teleconsult Session</span>
          <span className={`px-2 py-0.5 text-[10px] font-medium rounded-full ${
            isClosed ? "bg-gray-100 text-gray-600"
            : isResponded ? "bg-blue-100 text-blue-700"
            : "bg-amber-100 text-amber-700"
          }`}>
            {isClosed ? "CLOSED" : isResponded ? "RESPONDED" : status}
          </span>
        </div>
        <div className="flex items-center gap-2">
          {isResponded && !isClosed && (
            <button onClick={() => setShowCompletion(true)}
              className="px-3 py-1 text-xs font-medium bg-impilo-500 text-white rounded-md hover:bg-impilo-600">
              Complete & Close
            </button>
          )}
          <span className="text-xs text-gray-400">{sessionId}</span>
        </div>
      </header>

      <div className="shrink-0 border-b border-slate-200 bg-slate-50/80 px-3 py-2">
        <TelemedicineWorkflowStrip
          status={status}
          bffStage={typeof session?.stage === "number" ? (session.stage as number) : null}
        />
      </div>
      {actionError && (
        <div className="shrink-0 border-b border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-900">
          {actionError}
        </div>
      )}

      {/* 3-pane body */}
      <div className="flex flex-1 min-h-0">

        {/* ═══ LEFT PANE — Communication ═══ */}
        <div className="w-80 border-r bg-white flex flex-col shrink-0">
          {/* Call controls (mode represented; live execution blocked) */}
          <div className="p-3 border-b flex items-center justify-center gap-2">
            <button disabled className="p-2.5 rounded-full bg-gray-100 text-gray-400 cursor-not-allowed" title="Audio mode not available">
              <Mic className="w-5 h-5" />
            </button>
            <button disabled className="p-2.5 rounded-full bg-gray-100 text-gray-400 cursor-not-allowed" title="Video mode not available">
              <Video className="w-5 h-5" />
            </button>
            <button disabled className="p-2.5 rounded-full bg-gray-100 text-gray-400 cursor-not-allowed" title="Voice note mode not available">
              <Mic className="w-5 h-5" />
            </button>
          </div>
          <div className="px-3 py-2 text-[11px] text-gray-500 border-b">
            Live audio/video execution is unavailable until canonical teleconsult backend wiring is complete.
          </div>

          {/* Chat messages */}
          <div className="flex-1 overflow-y-auto p-3 space-y-2">
            {messages.length === 0 && (
              <p className="text-xs text-gray-400 text-center py-8">No messages yet. Start the conversation.</p>
            )}
            {messages.map((msg) => {
              const isMe = msg.senderId === user?.id;
              return (
                <div key={msg.id} className={`flex ${isMe ? "justify-end" : "justify-start"}`}>
                  <div className={`max-w-[85%] rounded-xl px-3 py-2 ${
                    isMe ? "bg-impilo-500 text-white" : "bg-gray-100 text-gray-900"
                  }`}>
                    {!isMe && <p className="text-[10px] font-semibold opacity-70 mb-0.5">{msg.senderName}</p>}
                    <p className="text-sm">{msg.content}</p>
                    <p className={`text-[9px] mt-0.5 ${isMe ? "text-impilo-100" : "text-gray-400"}`}>
                      {new Date(msg.timestamp).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
                    </p>
                  </div>
                </div>
              );
            })}
            <div ref={chatEndRef} />
          </div>

          {/* Chat input */}
          <div className="p-2 border-t">
            <div className="flex gap-1.5">
              <input value={newMessage} onChange={(e) => setNewMessage(e.target.value)}
                onKeyDown={(e) => { if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); handleSendMessage(); } }}
                placeholder="Type a message..."
                className="flex-1 px-3 py-2 text-sm border border-gray-200 rounded-lg focus:ring-1 focus:ring-impilo-400" />
              <button onClick={handleSendMessage} disabled={sending || !newMessage.trim()}
                className="p-2 bg-impilo-500 text-white rounded-lg hover:bg-impilo-600 disabled:opacity-40">
                <Send className="w-4 h-4" />
              </button>
            </div>
          </div>
        </div>

        {/* ═══ CENTER PANE — Response Note Draft ═══ */}
        <div className="flex-1 overflow-y-auto p-4 space-y-4 min-w-0">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-semibold text-gray-900 flex items-center gap-2">
              <FileText className="w-4 h-4 text-gray-500" /> Response Note
            </h3>
            <div className="flex items-center gap-2 text-xs text-gray-400">
              <span>{responseDraftDirty ? "Draft changed locally (not persisted)" : "No local draft changes"}</span>
            </div>
          </div>

          <div className="space-y-3">
            <label className="block">
              <span className="text-xs font-medium text-gray-600">Clinical interpretation & response *</span>
              <textarea value={responseNote} onChange={(e) => setResponseNote(e.target.value)} rows={6}
                className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                placeholder="Thank you for this referral. On review of the clinical information provided...&#10;&#10;Impression: ...&#10;Recommendations: ..." />
            </label>

            <div className="grid grid-cols-2 gap-3">
              <label className="block">
                <span className="text-xs font-medium text-gray-600">Working / final diagnosis</span>
                <input value={diagnosis} onChange={(e) => setDiagnosis(e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="ICD-11 or free text..." />
              </label>
              <label className="block">
                <span className="text-xs font-medium text-gray-600">Red flags</span>
                <input value={redFlags} onChange={(e) => setRedFlags(e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="Danger signs to watch for..." />
              </label>
            </div>

            <label className="block">
              <span className="text-xs font-medium text-gray-600">Action plan *</span>
              <textarea value={actionPlan} onChange={(e) => setActionPlan(e.target.value)} rows={3}
                className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                placeholder="1. Continue current management&#10;2. Add ...&#10;3. Monitor for ..." />
            </label>

            <label className="block">
              <span className="text-xs font-medium text-gray-600">Orders (one per line)</span>
              <textarea value={orders} onChange={(e) => setOrders(e.target.value)} rows={3}
                className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                placeholder="FBC + differential&#10;Chest X-ray PA&#10;Start Amoxicillin 500mg TDS x 5 days" />
            </label>

            <label className="block">
              <span className="text-xs font-medium text-gray-600">Follow-up instructions</span>
              <input value={followUp} onChange={(e) => setFollowUp(e.target.value)}
                className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                placeholder="Review in 1 week, or sooner if deterioration..." />
            </label>

            {!isResponded && (
              <button onClick={handleSubmitResponse} disabled={submittingResponse || !responseNote.trim()}
                className="w-full flex items-center justify-center gap-2 py-2.5 bg-impilo-500 text-white text-sm font-medium rounded-lg hover:bg-impilo-600 disabled:opacity-40 transition-colors">
                {submittingResponse ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
                {submittingResponse ? "Submitting..." : "Submit Response Package"}
              </button>
            )}
            {isResponded && (
              <div className="p-3 bg-green-50 border border-green-200 rounded-lg text-sm text-green-700 flex items-center gap-2">
                <CheckCircle2 className="w-4 h-4" /> Response submitted. {!isClosed && "Waiting for referrer to close the loop."}
              </div>
            )}
          </div>
        </div>

        {/* ═══ RIGHT PANE — Information ═══ */}
        <div className="w-72 border-l bg-white overflow-y-auto shrink-0">
          {/* Patient summary */}
          <div className="p-3 border-b">
            <h4 className="text-[10px] font-semibold text-gray-400 uppercase tracking-wider mb-2">Patient</h4>
            <div className="flex items-center gap-2">
              <div className="w-8 h-8 rounded-full bg-impilo-100 flex items-center justify-center">
                <User className="w-4 h-4 text-impilo-600" />
              </div>
              <div>
                <p className="text-sm font-medium text-gray-900">{(session?.patientId as string)?.substring(0, 12) || "Patient"}</p>
                <p className="text-[10px] text-gray-400">Click to view chart</p>
              </div>
            </div>
          </div>

          {/* Referral info */}
          <div className="p-3 border-b">
            <h4 className="text-[10px] font-semibold text-gray-400 uppercase tracking-wider mb-2">Referral</h4>
            <div className="space-y-1.5 text-xs">
              <div className="flex justify-between"><span className="text-gray-500">Urgency</span><span className="font-medium">{(session?.urgency as string) || "—"}</span></div>
              <div className="flex justify-between"><span className="text-gray-500">Specialty</span><span className="font-medium">{(session?.specialty as string) || "—"}</span></div>
              <div className="flex justify-between"><span className="text-gray-500">Routing</span><span className="font-medium">{(session?.routingType as string) || "—"}</span></div>
              <div className="flex justify-between"><span className="text-gray-500">Stage</span><span className="font-medium">{(session?.stage as number) || "—"}</span></div>
            </div>
          </div>

          {/* Consent */}
          <div className="p-3 border-b">
            <h4 className="text-[10px] font-semibold text-gray-400 uppercase tracking-wider mb-2">Consent</h4>
            {Boolean(session?.consentToken) ? (
              <div className="flex items-center gap-1.5 text-xs text-green-700">
                <Shield className="w-3.5 h-3.5" /> Verified
              </div>
            ) : (
              <p className="text-xs text-gray-400">Pending</p>
            )}
          </div>

          {/* Referral letter excerpt */}
          {Boolean(session?.referralLetter) && (
            <div className="p-3 border-b">
              <h4 className="text-[10px] font-semibold text-gray-400 uppercase tracking-wider mb-2">Referral Letter</h4>
              <p className="text-xs text-gray-600 line-clamp-6">{session?.referralLetter as string}</p>
            </div>
          )}

          {/* Timeline */}
          <div className="p-3">
            <h4 className="text-[10px] font-semibold text-gray-400 uppercase tracking-wider mb-2">Timeline</h4>
            <div className="space-y-2">
              {[
                session?.createdAt && { label: "Created", time: session.createdAt as string, icon: Activity },
                session?.submittedAt && { label: "Submitted", time: session.submittedAt as string, icon: Send },
                session?.acceptedAt && { label: "Accepted", time: session.acceptedAt as string, icon: CheckCircle2 },
                session?.respondedAt && { label: "Responded", time: session.respondedAt as string, icon: FileText },
                session?.closedAt && { label: "Closed", time: session.closedAt as string, icon: Lock },
              ].filter(Boolean).map((event) => {
                const ev = event as { label: string; time: string; icon: React.ElementType };
                const Icon = ev.icon;
                return (
                  <div key={ev.label} className="flex items-center gap-2 text-xs">
                    <Icon className="w-3 h-3 text-gray-400 shrink-0" />
                    <span className="text-gray-600">{ev.label}</span>
                    <span className="text-gray-400 ml-auto">{new Date(ev.time).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}</span>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
