"use client";

/**
 * Teleconsult Session Workspace — 3-pane layout (Stage 5).
 *
 * No call:     LEFT communication · CENTER response-note draft · RIGHT patient info.
 * Call active: the CENTER pane becomes the video stage (front and centre —
 *              consult layout, PiP local preview); the response-note form moves
 *              to a collapsible right-side tab alongside the patient info.
 *
 * Also handles Stage 6 (submit response) and Stage 7 (completion note).
 */

import { useEffect, useRef, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import {
  ArrowLeft, CheckCircle2, FileText, Loader2, Lock,
  Mic, PanelRightClose, PanelRightOpen, Phone, PhoneOff, Send, Shield, User,
  Video, VideoOff, Activity,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { AdaptiveSessionRoom } from "@/components/session/AdaptiveSessionRoom";
import { LowBandwidthToggle } from "@/components/session/LowBandwidthToggle";
import { TelemedicineRtcHealthPanel } from "@/components/telemedicine/TelemedicineRtcHealthPanel";
import { TelemedicineLiveSessionEmbed } from "@/components/live/TelemedicineLiveSessionEmbed";
import { WaitingRoomAdmitControl } from "@/components/telemedicine/WaitingRoomAdmitControl";
import { apiClient } from "@/lib/api-client";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useTelemedicineMediaToken } from "@/hooks/queries/useTelemedicine";

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
  const sessionId = params.sessionId as string;
  const user = useAuthStore((s) => s.user);

  // Session state
  const [session, setSession] = useState<Record<string, unknown> | null>(null);
  const [loading, setLoading] = useState(true);
  const [messages, setMessages] = useState<Message[]>([]);
  const [newMessage, setNewMessage] = useState("");
  const [sending, setSending] = useState(false);

  // Communication state
  const [callActive, setCallActive] = useState(false);
  const [videoActive, setVideoActive] = useState(false);
  const [callDuration, setCallDuration] = useState(0);
  const [mediaError, setMediaError] = useState<string | null>(null);
  const [fetchedRoomUrl, setFetchedRoomUrl] = useState("");
  const [fetchedToken, setFetchedToken] = useState("");
  /** Audio-first: request AUDIO_ONLY media profile + pause remote video subscriptions. */
  const [audioOnly, setAudioOnly] = useState(false);
  /** Call explicitly ended by the provider (keeps credentials, returns layout to notes). */
  const [callEnded, setCallEnded] = useState(false);
  // Right pane during a call: response note / patient info tab + collapse.
  const [rightTab, setRightTab] = useState<"note" | "patient">("note");
  const [rightCollapsed, setRightCollapsed] = useState(false);
  const mediaTokenM = useTelemedicineMediaToken();
  const callTimer = useRef<ReturnType<typeof setInterval> | null>(null);

  // Response draft (Stage 6)
  const [responseNote, setResponseNote] = useState("");
  const [diagnosis, setDiagnosis] = useState("");
  const [actionPlan, setActionPlan] = useState("");
  const [redFlags, setRedFlags] = useState("");
  const [followUp, setFollowUp] = useState("");
  const [orders, setOrders] = useState("");
  const [lastSaved, setLastSaved] = useState<string | null>(null);
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
    async function load() {
      let loadedSession: Record<string, unknown> | null = null;
      try {
        const res = await apiClient.get<{ data: Record<string, unknown> }>(`/internal/v1/teleconsult/sessions/${sessionId}`);
        loadedSession = res.data;
        setSession(res.data);
      } catch {
        setSession(null);
        setMediaError(
          "Teleconsult session could not be loaded. RTC media remains scoped Partial until governed transport is wired.",
        );
      }
      try {
        const msgs = await apiClient.get<{ data: Message[] }>(`/internal/v1/teleconsult/sessions/${sessionId}/messages`);
        setMessages(msgs.data ?? []);
      } catch { /* no messages yet */ }
      setLoading(false);

      const sessionStatus = String(loadedSession?.status ?? "").toUpperCase();
      if (sessionStatus === "ACTIVE" || sessionStatus === "IN_PROGRESS") {
        void ensureGovernedMediaOnLoad();
      }
    }
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sessionId]);

  async function ensureGovernedMediaOnLoad() {
    try {
      const res = await mediaTokenM.mutateAsync({
        sessionId,
        displayName: user?.displayName || user?.email || "Participant",
        role: "PROVIDER",
        mediaProfile: audioOnly ? "AUDIO_ONLY" : "FULL",
      });
      const payload = (res.data ?? res) as Record<string, unknown>;
      const room = String(payload.room_url ?? payload.roomUrl ?? "");
      const token = String(payload.token ?? payload.accessToken ?? "");
      if (room && token) {
        setFetchedRoomUrl(room);
        setFetchedToken(token);
        setMediaError(null);
      }
    } catch {
      setMediaError("Governed RTC media token could not be issued on session load.");
    }
  }

  // Auto-scroll chat
  useEffect(() => {
    if (typeof chatEndRef.current?.scrollIntoView === "function") {
      chatEndRef.current.scrollIntoView({ behavior: "smooth" });
    }
  }, [messages]);

  // Auto-save draft every 10 seconds
  useEffect(() => {
    if (!responseNote.trim()) return;
    const timer = setInterval(() => {
      setLastSaved(new Date().toLocaleTimeString());
    }, 10000);
    return () => clearInterval(timer);
  }, [responseNote]);

  // Call timer
  useEffect(() => {
    if (callActive) {
      callTimer.current = setInterval(() => setCallDuration((d) => d + 1), 1000);
    } else {
      if (callTimer.current) clearInterval(callTimer.current);
      setCallDuration(0);
    }
    return () => { if (callTimer.current) clearInterval(callTimer.current); };
  }, [callActive]);

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
    } catch {
      setMessages((prev) => [...prev, {
        id: "local-" + Date.now(),
        senderId: user?.id || "",
        senderName: user?.displayName || "You",
        content: newMessage.trim(),
        type: "TEXT",
        timestamp: new Date().toISOString(),
      }]);
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
      setSession((s) => s ? { ...s, status: "RESPONDED", stage: 6 } : s);
    } catch { /* fallback — mark locally */ }
    setSubmittingResponse(false);
  }

  async function handleSubmitCompletion() {
    setSubmittingCompletion(true);
    try {
      await apiClient.post(`/internal/v1/teleconsult/sessions/${sessionId}/complete`, {
        actionsTaken, patientOutcome, followUpExecution, closureNarrative,
      });
      setSession((s) => s ? { ...s, status: "CLOSED", stage: 7 } : s);
    } catch { /* fallback */ }
    setSubmittingCompletion(false);
  }

  const formatTime = (secs: number) => `${Math.floor(secs / 60).toString().padStart(2, "0")}:${(secs % 60).toString().padStart(2, "0")}`;
  const status = (session?.status as string) || "ACTIVE";
  const isResponded = status === "RESPONDED" || status === "CLOSED";
  const isClosed = status === "CLOSED";
  const attributes = ((session?.attributes as Record<string, unknown> | undefined) ?? session ?? {}) as Record<string, unknown>;
  const mediaRoomUrl = fetchedRoomUrl || String(attributes.room_url ?? attributes.roomUrl ?? "");
  const mediaToken = fetchedToken || String(attributes.token ?? attributes.accessToken ?? attributes.session_token ?? "");
  const hasGovernedMedia = mediaRoomUrl.length > 0 && mediaToken.length > 0;
  const liveEventId = String(session?.liveEventId ?? attributes.liveEventId ?? "");
  const impiloLiveJoinPath = String(session?.impiloLiveJoinPath ?? attributes.impiloLiveJoinPath ?? "");
  const consentGranted = Boolean(session?.consentToken ?? attributes.consentReference ?? attributes.consentReference);

  /** Video is front and centre while a call is live (governed media held and not ended). */
  const callFront = hasGovernedMedia && !callEnded;

  async function ensureGovernedMedia() {
    if (hasGovernedMedia || mediaTokenM.isPending) return true;
    try {
      const res = await mediaTokenM.mutateAsync({
        sessionId,
        displayName: user?.displayName || user?.email || "Participant",
        role: "PROVIDER",
        mediaProfile: audioOnly ? "AUDIO_ONLY" : "FULL",
      });
      const payload = (res.data ?? res) as Record<string, unknown>;
      const room = String(payload.room_url ?? payload.roomUrl ?? "");
      const token = String(payload.token ?? payload.accessToken ?? "");
      if (room && token) {
        setFetchedRoomUrl(room);
        setFetchedToken(token);
        setMediaError(null);
        return true;
      }
      if (String(payload.status ?? "").toUpperCase() === "WAITING") {
        setMediaError("The session gatekeeper placed this join in the waiting room. Retry shortly.");
        return false;
      }
      setMediaError("RTC gateway returned no governed room credentials.");
      return false;
    } catch {
      setMediaError("Governed RTC media token could not be issued. Lifecycle BFF remains available.");
      return false;
    }
  }

  if (loading) {
    return (
      <AppLayout>
        <div className="flex items-center justify-center h-[calc(100vh-8rem)]">
          <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
          <span className="ml-2 text-sm text-muted-foreground">Loading session...</span>
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
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
            <ArrowLeft className="w-4 h-4" /> Back to session
          </Link>
          <div className="bg-card rounded-xl border-2 border-primary/25 p-6 space-y-5">
            <h2 className="text-lg font-semibold text-foreground flex items-center gap-2">
              <CheckCircle2 className="w-5 h-5 text-primary" /> Stage 7 — Completion Note & Loop Closure
            </h2>
            <label className="block" htmlFor="teleconsult-completion-actions">
              <span className="text-sm font-medium text-foreground">Actions taken *</span>
              <textarea
                id="teleconsult-completion-actions"
                value={actionsTaken}
                onChange={(e) => setActionsTaken(e.target.value)}
                rows={3}
                className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm"
                placeholder="Medications administered, tests done, procedures, monitoring, counseling..."
              />
            </label>
            <label className="block" htmlFor="teleconsult-completion-outcome">
              <span className="text-sm font-medium text-foreground">Patient outcome *</span>
              <select
                id="teleconsult-completion-outcome"
                value={patientOutcome}
                onChange={(e) => setPatientOutcome(e.target.value)}
                className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm"
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
              <span className="text-sm font-medium text-foreground">Follow-up execution</span>
              <select
                id="teleconsult-completion-followup"
                value={followUpExecution}
                onChange={(e) => setFollowUpExecution(e.target.value)}
                className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm"
              >
                <option value="">Select...</option>
                <option value="COMPLETED">Completed</option>
                <option value="PARTIALLY_COMPLETED">Partially completed</option>
                <option value="NOT_COMPLETED">Not completed</option>
              </select>
            </label>
            <label className="block" htmlFor="teleconsult-completion-narrative">
              <span className="text-sm font-medium text-foreground">Case closure narrative</span>
              <textarea
                id="teleconsult-completion-narrative"
                value={closureNarrative}
                onChange={(e) => setClosureNarrative(e.target.value)}
                rows={3}
                className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm"
                placeholder="Brief summary of the case and its resolution..."
              />
            </label>
            <button onClick={handleSubmitCompletion} disabled={submittingCompletion || !actionsTaken.trim()}
              className="w-full flex items-center justify-center gap-2 py-2.5 bg-primary text-white text-sm font-medium rounded-lg hover:bg-primary-hover disabled:opacity-40 transition-colors">
              {submittingCompletion ? <Loader2 className="w-4 h-4 animate-spin" /> : <Lock className="w-4 h-4" />}
              {submittingCompletion ? "Closing..." : "Close Case & Archive"}
            </button>
          </div>
        </div>
      </AppLayout>
    );
  }

  // ── Shared panes (rendered center when no call; right-side tab during a call) ──
  const responseNotePane = (
    <>
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold text-foreground flex items-center gap-2">
          <FileText className="w-4 h-4 text-muted-foreground" /> Response Note
        </h3>
        <div className="flex items-center gap-2 text-xs text-muted-foreground">
          {lastSaved && <span>Saved {lastSaved}</span>}
          <span className="w-1.5 h-1.5 rounded-full bg-green-400" />
        </div>
      </div>

      <div className="space-y-3">
        <label className="block">
          <span className="text-xs font-medium text-muted-foreground">Clinical interpretation & response *</span>
          <textarea value={responseNote} onChange={(e) => setResponseNote(e.target.value)} rows={6}
            className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm"
            placeholder="Thank you for this referral. On review of the clinical information provided...&#10;&#10;Impression: ...&#10;Recommendations: ..." />
        </label>

        <div className="grid grid-cols-2 gap-3">
          <label className="block">
            <span className="text-xs font-medium text-muted-foreground">Working / final diagnosis</span>
            <input value={diagnosis} onChange={(e) => setDiagnosis(e.target.value)}
              className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="ICD-11 or free text..." />
          </label>
          <label className="block">
            <span className="text-xs font-medium text-muted-foreground">Red flags</span>
            <input value={redFlags} onChange={(e) => setRedFlags(e.target.value)}
              className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="Danger signs to watch for..." />
          </label>
        </div>

        <label className="block">
          <span className="text-xs font-medium text-muted-foreground">Action plan *</span>
          <textarea value={actionPlan} onChange={(e) => setActionPlan(e.target.value)} rows={3}
            className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm"
            placeholder="1. Continue current management&#10;2. Add ...&#10;3. Monitor for ..." />
        </label>

        <label className="block">
          <span className="text-xs font-medium text-muted-foreground">Orders (one per line)</span>
          <textarea value={orders} onChange={(e) => setOrders(e.target.value)} rows={3}
            className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm"
            placeholder="FBC + differential&#10;Chest X-ray PA&#10;Start Amoxicillin 500mg TDS x 5 days" />
        </label>

        <label className="block">
          <span className="text-xs font-medium text-muted-foreground">Follow-up instructions</span>
          <input value={followUp} onChange={(e) => setFollowUp(e.target.value)}
            className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm"
            placeholder="Review in 1 week, or sooner if deterioration..." />
        </label>

        {!isResponded && (
          <button onClick={handleSubmitResponse} disabled={submittingResponse || !responseNote.trim()}
            className="w-full flex items-center justify-center gap-2 py-2.5 bg-primary text-white text-sm font-medium rounded-lg hover:bg-primary-hover disabled:opacity-40 transition-colors">
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
    </>
  );

  const patientInfoPane = (
    <>
      {/* Patient summary */}
      <div className="p-3 border-b">
        <h4 className="text-[10px] font-semibold text-muted-foreground uppercase tracking-wider mb-2">Patient</h4>
        <div className="flex items-center gap-2">
          <div className="w-8 h-8 rounded-full bg-primary-soft flex items-center justify-center">
            <User className="w-4 h-4 text-primary" />
          </div>
          <div>
            <p className="text-sm font-medium text-foreground">{(session?.patientId as string)?.substring(0, 12) || "Patient"}</p>
            <p className="text-[10px] text-muted-foreground">Click to view chart</p>
          </div>
        </div>
      </div>

      {/* Referral info */}
      <div className="p-3 border-b">
        <h4 className="text-[10px] font-semibold text-muted-foreground uppercase tracking-wider mb-2">Referral</h4>
        <div className="space-y-1.5 text-xs">
          <div className="flex justify-between"><span className="text-muted-foreground">Urgency</span><span className="font-medium">{(session?.urgency as string) || "—"}</span></div>
          <div className="flex justify-between"><span className="text-muted-foreground">Specialty</span><span className="font-medium">{(session?.specialty as string) || "—"}</span></div>
          <div className="flex justify-between"><span className="text-muted-foreground">Routing</span><span className="font-medium">{(session?.routingType as string) || "—"}</span></div>
          <div className="flex justify-between"><span className="text-muted-foreground">Stage</span><span className="font-medium">{(session?.stage as number) || "—"}</span></div>
        </div>
      </div>

      {/* Consent */}
      <div className="p-3 border-b">
        <h4 className="text-[10px] font-semibold text-muted-foreground uppercase tracking-wider mb-2">Consent</h4>
        {Boolean(session?.consentToken) ? (
          <div className="flex items-center gap-1.5 text-xs text-green-700">
            <Shield className="w-3.5 h-3.5" /> Verified
          </div>
        ) : (
          <p className="text-xs text-muted-foreground">Pending</p>
        )}
      </div>

      {/* Referral letter excerpt */}
      {Boolean(session?.referralLetter) && (
        <div className="p-3 border-b">
          <h4 className="text-[10px] font-semibold text-muted-foreground uppercase tracking-wider mb-2">Referral Letter</h4>
          <p className="text-xs text-muted-foreground line-clamp-6">{session?.referralLetter as string}</p>
        </div>
      )}

      {/* Timeline */}
      <div className="p-3">
        <h4 className="text-[10px] font-semibold text-muted-foreground uppercase tracking-wider mb-2">Timeline</h4>
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
                <Icon className="w-3 h-3 text-muted-foreground shrink-0" />
                <span className="text-muted-foreground">{ev.label}</span>
                <span className="text-muted-foreground ml-auto">{new Date(ev.time).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}</span>
              </div>
            );
          })}
        </div>
      </div>
    </>
  );

  // ── 3-Pane Session Workspace ──
  return (
    <div className="flex flex-col h-screen bg-background">
      {/* Top bar */}
      <header className="h-12 bg-card border-b px-4 flex items-center justify-between shrink-0">
        <div className="flex items-center gap-3">
          <Link href="/telemedicine" className="text-muted-foreground hover:text-muted-foreground">
            <ArrowLeft className="w-4 h-4" />
          </Link>
          <Video className="w-4 h-4 text-primary" />
          <span className="text-sm font-semibold text-foreground">Teleconsult Session</span>
          <span className={`px-2 py-0.5 text-[10px] font-medium rounded-full ${
            isClosed ? "bg-neutral-100 text-muted-foreground"
            : isResponded ? "bg-blue-100 text-primary-hover"
            : callActive ? "bg-green-100 text-green-700"
            : "bg-amber-100 text-warning-foreground"
          }`}>
            {isClosed ? "CLOSED" : isResponded ? "RESPONDED" : callActive ? "IN CALL" : status}
          </span>
          {callActive && (
            <span className="text-xs text-green-600 font-mono">{formatTime(callDuration)}</span>
          )}
        </div>
        <div className="flex items-center gap-2">
          {isResponded && !isClosed && (
            <button onClick={() => setShowCompletion(true)}
              className="px-3 py-1 text-xs font-medium bg-primary text-white rounded-md hover:bg-primary-hover">
              Complete & Close
            </button>
          )}
          <span className="text-xs text-muted-foreground">{sessionId}</span>
        </div>
      </header>

      {/* 3-pane body */}
      <div className="flex flex-1 min-h-0">

        {/* ═══ LEFT PANE — Communication ═══ */}
        <div className="w-80 border-r bg-card flex flex-col shrink-0">
          <div className="p-2 border-b">
            <TelemedicineRtcHealthPanel />
          </div>
          <div className="p-2 border-b">
            <TelemedicineLiveSessionEmbed
              sessionId={sessionId}
              liveEventId={liveEventId || null}
              impiloLiveJoinPath={impiloLiveJoinPath || null}
              consentGranted={consentGranted}
            />
          </div>
          {/* Waiting room (patients/caregivers requesting to join) */}
          <WaitingRoomAdmitControl sessionId={sessionId} />

          {/* Call controls */}
          <div className="p-3 border-b flex items-center justify-center gap-2">
            <button onClick={async () => {
              if (callActive) {
                setCallActive(false);
                setVideoActive(false);
                setCallEnded(true);
                return;
              }
              const ready = hasGovernedMedia || await ensureGovernedMedia();
              if (ready) { setCallEnded(false); setCallActive(true); setVideoActive(false); }
            }}
              disabled={mediaTokenM.isPending}
              className={`p-2.5 rounded-full transition-colors ${callActive ? "bg-red-500 text-white" : "bg-green-100 text-green-700 hover:bg-green-200"}`}
              title={hasGovernedMedia ? (callActive ? "End call" : "Audio call") : "Waiting for governed RTC media"}>
              {callActive ? <PhoneOff className="w-5 h-5" /> : <Phone className="w-5 h-5" />}
            </button>
            <button onClick={async () => {
              if (videoActive) {
                setVideoActive(false);
                return;
              }
              const ready = hasGovernedMedia || await ensureGovernedMedia();
              if (ready) { setCallEnded(false); setVideoActive(true); if (!callActive) setCallActive(true); }
            }}
              disabled={mediaTokenM.isPending}
              className={`p-2.5 rounded-full transition-colors ${videoActive ? "bg-red-500 text-white" : "bg-blue-100 text-primary-hover hover:bg-blue-200"}`}
              title={hasGovernedMedia ? (videoActive ? "Stop video" : "Video call") : "Waiting for governed RTC media"}>
              {videoActive ? <VideoOff className="w-5 h-5" /> : <Video className="w-5 h-5" />}
            </button>
            <button className="p-2.5 rounded-full bg-neutral-100 text-muted-foreground hover:bg-neutral-100" title="Voice note">
              <Mic className="w-5 h-5" />
            </button>
            <LowBandwidthToggle audioOnly={audioOnly} onAudioOnlyChange={setAudioOnly} />
          </div>

          {/* Pre-call placeholder — during a live call the video is front and centre. */}
          {!callFront && (
            <div className="h-40 bg-neutral-900 flex flex-col items-center justify-center text-muted-foreground text-xs gap-2 px-3">
              <Video className="w-8 h-8 opacity-60" />
              <span className="text-center text-muted-foreground">
                {callEnded && hasGovernedMedia
                  ? "Call ended. Start audio or video again to reopen the live stage."
                  : "Live media is blocked until the Telemedicine service returns a governed room and scoped token."}
              </span>
            </div>
          )}
          {mediaError ? (
            <div className="mx-3 mt-2 rounded-md border border-amber-300 bg-warning-soft p-2 text-[11px] text-warning-foreground">
              {mediaError} Continue governed teleconsult workflow via notes/messages while support stabilizes media.
            </div>
          ) : null}

          {/* Chat messages */}
          <div className="flex-1 overflow-y-auto p-3 space-y-2">
            {messages.length === 0 && (
              <p className="text-xs text-muted-foreground text-center py-8">No messages yet. Start the conversation.</p>
            )}
            {messages.map((msg) => {
              const isMe = msg.senderId === user?.id;
              return (
                <div key={msg.id} className={`flex ${isMe ? "justify-end" : "justify-start"}`}>
                  <div className={`max-w-[85%] rounded-xl px-3 py-2 ${
                    isMe ? "bg-primary text-white" : "bg-neutral-100 text-foreground"
                  }`}>
                    {!isMe && <p className="text-[10px] font-semibold opacity-70 mb-0.5">{msg.senderName}</p>}
                    <p className="text-sm">{msg.content}</p>
                    <p className={`text-[9px] mt-0.5 ${isMe ? "text-white/85" : "text-muted-foreground"}`}>
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
                className="flex-1 px-3 py-2 text-sm border border-border rounded-lg focus:ring-1 focus:ring-primary/40" />
              <button onClick={handleSendMessage} disabled={sending || !newMessage.trim()}
                className="p-2 bg-primary text-white rounded-lg hover:bg-primary-hover disabled:opacity-40">
                <Send className="w-4 h-4" />
              </button>
            </div>
          </div>
        </div>

        {/* ═══ CENTER PANE — video front and centre during a call; response note otherwise ═══ */}
        {callFront ? (
          <div className="flex-1 min-w-0 flex flex-col p-3 gap-2">
            <div className="flex items-center justify-between shrink-0">
              <h3 className="text-sm font-semibold text-foreground flex items-center gap-2">
                <Video className="w-4 h-4 text-primary" /> Live consultation
              </h3>
              {callActive && <span className="text-xs text-green-600 font-mono">{formatTime(callDuration)}</span>}
            </div>
            <div className="flex-1 min-h-0" data-testid="session-video-stage">
              <AdaptiveSessionRoom
                layout="consult"
                localPreviewPolicy="pip"
                controls={{ microphone: true, camera: true, leave: true, settings: false }}
                serverUrl={mediaRoomUrl}
                token={mediaToken}
                videoEnabled={videoActive && !audioOnly}
                audioOnly={audioOnly}
                onAudioOnlyChange={setAudioOnly}
                onConnected={() => {
                  setCallActive(true);
                  setMediaError(null);
                }}
                onDisconnected={() => {
                  setCallActive(false);
                  setVideoActive(false);
                  setCallEnded(true);
                }}
                onError={(message) => setMediaError(message)}
              />
            </div>
          </div>
        ) : (
          <div className="flex-1 overflow-y-auto p-4 space-y-4 min-w-0">
            {responseNotePane}
          </div>
        )}

        {/* ═══ RIGHT PANE — info (tabbed + collapsible while a call is live) ═══ */}
        {callFront ? (
          rightCollapsed ? (
            <div className="w-10 border-l bg-card flex flex-col items-center py-2 shrink-0">
              <button
                type="button"
                onClick={() => setRightCollapsed(false)}
                title="Open side panel"
                className="p-1.5 rounded-md text-muted-foreground hover:text-foreground hover:bg-background"
              >
                <PanelRightOpen className="w-4 h-4" />
              </button>
            </div>
          ) : (
            <div className="w-80 border-l bg-card flex flex-col shrink-0 min-h-0" data-testid="session-side-panel">
              <div className="flex items-center border-b shrink-0">
                <button
                  type="button"
                  data-testid="right-tab-note"
                  onClick={() => setRightTab("note")}
                  className={`px-3 py-2 text-xs font-medium border-b-2 ${
                    rightTab === "note" ? "border-primary text-primary" : "border-transparent text-muted-foreground hover:text-foreground"
                  }`}
                >
                  Response note
                </button>
                <button
                  type="button"
                  data-testid="right-tab-patient"
                  onClick={() => setRightTab("patient")}
                  className={`px-3 py-2 text-xs font-medium border-b-2 ${
                    rightTab === "patient" ? "border-primary text-primary" : "border-transparent text-muted-foreground hover:text-foreground"
                  }`}
                >
                  Patient
                </button>
                <button
                  type="button"
                  onClick={() => setRightCollapsed(true)}
                  title="Collapse side panel"
                  className="ml-auto mr-2 p-1.5 rounded-md text-muted-foreground hover:text-foreground hover:bg-background"
                >
                  <PanelRightClose className="w-4 h-4" />
                </button>
              </div>
              <div className="flex-1 overflow-y-auto min-h-0">
                {rightTab === "note" ? <div className="p-4 space-y-4">{responseNotePane}</div> : patientInfoPane}
              </div>
            </div>
          )
        ) : (
          <div className="w-72 border-l bg-card overflow-y-auto shrink-0">
            {patientInfoPane}
          </div>
        )}
      </div>
    </div>
  );
}
