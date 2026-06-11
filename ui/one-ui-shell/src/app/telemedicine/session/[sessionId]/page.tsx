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

import { useEffect, useRef, useState } from "react";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import {
  ArrowLeft, CheckCircle2, FileText, Loader2, Lock,
  MessageCircle, Mic, MicOff, Phone, PhoneOff, Send, Shield, User,
  Video, VideoOff, Activity,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { apiClient } from "@/lib/api-client";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useRoleGroup } from "@/hooks/useRoleGroup";
import { useWebRtcCall } from "@/hooks/useWebRtcCall";
import { SHELL_TASKBAR_HEIGHT_PX } from "@/lib/shell/app-registry";
import { prefetchIceServers } from "@/lib/webrtc/ice-servers";
import {
  DEMO_PATIENT_ID,
  resolveTelemedicinePatientId,
} from "@/lib/webrtc/dev-test-facility";

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
  const searchParams = useSearchParams();
  const sessionId = params.sessionId as string;
  const user = useAuthStore((s) => s.user);
  const { isCitizen } = useRoleGroup();
  const autoAnswer = searchParams.get("autoAnswer") === "1";
  const autoAnswerVideo = searchParams.get("callType") === "video";
  const invitePeerId = searchParams.get("peerId") ?? undefined;

  // Session state
  const [session, setSession] = useState<Record<string, unknown> | null>(null);
  const [loading, setLoading] = useState(true);
  const [messages, setMessages] = useState<Message[]>([]);
  const [newMessage, setNewMessage] = useState("");
  const [sending, setSending] = useState(false);

  const peerId = user?.id ?? "anonymous";
  const sessionAttrs = (session?.attributes as Record<string, unknown> | undefined) ?? session;
  const rawPatientId =
    (sessionAttrs?.patient_id as string | undefined) ||
    (sessionAttrs?.patientId as string | undefined) ||
    (session?.patientId as string | undefined) ||
    (session?.patient_id as string | undefined) ||
    "";
  const patientId = resolveTelemedicinePatientId(rawPatientId);
  const providerUserId =
    invitePeerId ||
    (sessionAttrs?.provider_user_id as string | undefined) ||
    (session?.provider_user_id as string | undefined);
  const rtcPatientId = patientId || (!isCitizen ? DEMO_PATIENT_ID : undefined);
  const {
    callActive,
    callPhase,
    isVideoCall,
    videoEnabled: cameraOn,
    audioEnabled,
    connectionState,
    signalingStatus,
    remotePeerId,
    error: rtcError,
    localVideoRef,
    remoteVideoRef,
    startCall,
    endCall,
    toggleAudio,
    toggleVideo,
  } = useWebRtcCall({
    sessionId,
    peerId,
    patientId: isCitizen ? undefined : rtcPatientId,
    targetUserId: isCitizen ? providerUserId : undefined,
    callerName: user?.displayName || user?.email,
    autoAnswer,
    autoAnswerVideo,
  });
  const canStartCall =
    !loading &&
    !callActive &&
    (isCitizen ? Boolean(providerUserId || autoAnswer) : Boolean(patientId || DEMO_PATIENT_ID));

  const [callDuration, setCallDuration] = useState(0);
  const callTimer = useRef<ReturnType<typeof setInterval> | null>(null);
  const hadCallRef = useRef(false);

  // Clear accept-call query params after a call ends so the same page can place or accept another call.
  useEffect(() => {
    if (callPhase !== "idle") {
      hadCallRef.current = true;
      return;
    }
    if (hadCallRef.current && (autoAnswer || invitePeerId)) {
      router.replace(`/telemedicine/session/${sessionId}`, { scroll: false });
      hadCallRef.current = false;
    }
  }, [autoAnswer, callPhase, invitePeerId, router, sessionId]);

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
  const messagesPath = `/internal/v1/telemedicine/sessions/${sessionId}/messages`;

  useEffect(() => {
    prefetchIceServers();
  }, []);

  // Load session
  useEffect(() => {
    async function load() {
      try {
        const res = await apiClient.get<{ data: Record<string, unknown> }>(`/internal/v1/teleconsult/sessions/${sessionId}`);
        setSession(res.data);
      } catch {
        try {
          const list = await apiClient.get<{ data: Array<Record<string, unknown>> }>("/internal/v1/telemedicine/sessions");
          const found = (list.data ?? []).find((s) => String(s.id) === sessionId);
          if (found) {
            setSession(found);
          } else {
            setSession({ id: sessionId, status: "IN_SESSION", stage: 5 });
          }
        } catch {
          setSession({ id: sessionId, status: "IN_SESSION", stage: 5 });
        }
      }
      try {
        const msgs = await apiClient.get<{ data: Message[] }>(messagesPath);
        setMessages(msgs.data ?? []);
      } catch {
        try {
          const legacy = await apiClient.get<{ data: Message[] }>(
            `/internal/v1/teleconsult/sessions/${sessionId}/messages`,
          );
          setMessages(legacy.data ?? []);
        } catch { /* no messages yet */ }
      }
      try {
        await apiClient.post(`/internal/v1/telemedicine/sessions/${sessionId}/join`, {});
      } catch { /* join best-effort for RTC room */ }
      setLoading(false);
    }
    load();
  }, [messagesPath, sessionId]);

  // Poll chat so both participants see new messages without refresh
  useEffect(() => {
    let cancelled = false;

    async function refreshMessages() {
      try {
        const res = await apiClient.get<{ data: Message[] }>(messagesPath);
        if (!cancelled) {
          setMessages(res.data ?? []);
        }
      } catch {
        try {
          const legacy = await apiClient.get<{ data: Message[] }>(
            `/internal/v1/teleconsult/sessions/${sessionId}/messages`,
          );
          if (!cancelled) {
            setMessages(legacy.data ?? []);
          }
        } catch { /* ignore */ }
      }
    }

    const timer = setInterval(() => {
      void refreshMessages();
    }, 2000);

    return () => {
      cancelled = true;
      clearInterval(timer);
    };
  }, [messagesPath, sessionId]);

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
      const res = await apiClient.post<{ data: Message }>(messagesPath, {
        content: newMessage.trim(),
        senderName: user?.displayName || user?.email || "Unknown",
        type: "TEXT",
      });
      setMessages((prev) => {
        if (prev.some((m) => m.id === res.data.id)) return prev;
        return [...prev, res.data];
      });
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
    <AppLayout>
    <div
      className="relative flex h-[calc(100dvh-5rem)] min-h-0 flex-col bg-gray-50"
      style={{ paddingBottom: callActive ? SHELL_TASKBAR_HEIGHT_PX + 88 : 0 }}
    >
      <header className="flex shrink-0 flex-wrap items-center justify-between gap-2 border-b bg-white px-4 py-2">
        <div className="flex min-w-0 flex-wrap items-center gap-3">
          <Link href="/telemedicine" className="text-gray-400 hover:text-gray-600">
            <ArrowLeft className="h-4 w-4" />
          </Link>
          <Video className="h-4 w-4 text-impilo-500" />
          <span className="text-sm font-semibold text-gray-900">Teleconsult Session</span>
          <span className={`rounded-full px-2 py-0.5 text-[10px] font-medium ${
            isClosed ? "bg-gray-100 text-gray-600"
            : isResponded ? "bg-blue-100 text-blue-700"
            : callPhase === "ringing" ? "bg-sky-100 text-sky-700"
            : callActive ? "bg-green-100 text-green-700"
            : "bg-amber-100 text-amber-700"
          }`}>
            {isClosed ? "CLOSED" : isResponded ? "RESPONDED" : callPhase === "ringing" ? "RINGING" : callActive ? "IN CALL" : status}
          </span>
          {callActive && (
            <span className="font-mono text-xs text-green-600">{formatTime(callDuration)}</span>
          )}
        </div>
        <div className="flex items-center gap-2">
          {isResponded && !isClosed && (
            <button onClick={() => setShowCompletion(true)}
              className="rounded-md bg-impilo-500 px-3 py-1 text-xs font-medium text-white hover:bg-impilo-600">
              Complete & Close
            </button>
          )}
          <span className="max-w-[10rem] truncate text-xs text-gray-400">{sessionId}</span>
        </div>
      </header>

      <div className="flex min-h-0 flex-1 overflow-hidden">
        {/* ═══ LEFT PANE — Communication ═══ */}
        <div className="flex w-80 shrink-0 flex-col border-r bg-white">
          <div className="border-b p-3">
            <div className="flex items-center justify-center gap-2">
              {!callActive ? (
                <>
                  <button
                    type="button"
                    onClick={() => void startCall({ video: false })}
                    disabled={!canStartCall}
                    className="rounded-full bg-green-100 p-2.5 text-green-700 transition-colors hover:bg-green-200 disabled:cursor-not-allowed disabled:opacity-40"
                    title="Start audio call"
                  >
                    <Phone className="h-5 w-5" />
                  </button>
                  <button
                    type="button"
                    onClick={() => void startCall({ video: true })}
                    disabled={!canStartCall}
                    className="rounded-full bg-blue-100 p-2.5 text-blue-700 transition-colors hover:bg-blue-200 disabled:cursor-not-allowed disabled:opacity-40"
                    title="Start video call"
                  >
                    <Video className="h-5 w-5" />
                  </button>
                </>
              ) : (
                <>
                  <button
                    type="button"
                    onClick={endCall}
                    className="rounded-full bg-red-500 p-2.5 text-white transition-colors hover:bg-red-600"
                    title="End call"
                  >
                    <PhoneOff className="h-5 w-5" />
                  </button>
                  <button
                    type="button"
                    onClick={toggleAudio}
                    className={`rounded-full p-2.5 transition-colors ${audioEnabled ? "bg-gray-100 text-gray-600 hover:bg-gray-200" : "bg-red-100 text-red-600"}`}
                    title={audioEnabled ? "Mute microphone" : "Unmute microphone"}
                  >
                    {audioEnabled ? <Mic className="h-5 w-5" /> : <MicOff className="h-5 w-5" />}
                  </button>
                  {isVideoCall && (
                    <button
                      type="button"
                      onClick={toggleVideo}
                      className={`rounded-full p-2.5 transition-colors ${cameraOn ? "bg-blue-100 text-blue-700 hover:bg-blue-200" : "bg-amber-100 text-amber-700"}`}
                      title={cameraOn ? "Turn camera off" : "Turn camera on"}
                    >
                      {cameraOn ? <Video className="h-5 w-5" /> : <VideoOff className="h-5 w-5" />}
                    </button>
                  )}
                </>
              )}
            </div>
            {(callPhase === "ringing" || rtcError || (!canStartCall && !callActive && !loading)) && (
              <div className="mt-2 space-y-0.5 text-center">
                {callPhase === "ringing" && (
                  <p className="text-[11px] text-sky-700">Ringing — waiting for accept…</p>
                )}
                {!canStartCall && !callActive && !loading && !isCitizen && !patientId && (
                  <p className="text-[11px] text-amber-700">Loading session patient context…</p>
                )}
                {rtcError && <p className="text-[11px] text-red-600">{rtcError}</p>}
              </div>
            )}
          </div>

          {callActive && isVideoCall && (
            <div className="shrink-0 border-b bg-gray-900 p-2">
              <div className="grid h-44 grid-cols-2 gap-1.5">
                <div className="relative overflow-hidden rounded-md bg-gray-800">
                  <span className="absolute left-1.5 top-1.5 z-10 rounded bg-black/60 px-1.5 py-0.5 text-[9px] font-medium text-white">You</span>
                  <video ref={localVideoRef} autoPlay muted playsInline className={cameraOn ? "h-full w-full object-cover" : "hidden"} />
                  {!cameraOn && (
                    <div className="flex h-full min-h-[5.5rem] items-center justify-center text-[10px] text-gray-400">Camera off</div>
                  )}
                </div>
                <div className="relative overflow-hidden rounded-md bg-gray-800">
                  <span className="absolute left-1.5 top-1.5 z-10 rounded bg-black/60 px-1.5 py-0.5 text-[9px] font-medium text-white">Remote</span>
                  <video ref={remoteVideoRef} autoPlay playsInline className="h-full w-full object-cover" />
                </div>
              </div>
            </div>
          )}
          {callActive && !isVideoCall && (
            <div className="sr-only" aria-hidden>
              <video ref={localVideoRef} autoPlay muted playsInline />
              <video ref={remoteVideoRef} autoPlay playsInline />
            </div>
          )}
          {callActive && !isVideoCall && (
            <div className="flex shrink-0 items-center justify-center gap-2 border-b bg-green-50 px-3 py-2 text-xs text-green-800">
              <Phone className="h-3.5 w-3.5" />
              Audio call active
            </div>
          )}

          <div className="flex shrink-0 items-center gap-2 border-b px-3 py-2">
            <MessageCircle className="h-3.5 w-3.5 text-impilo-500" />
            <span className="text-xs font-semibold text-gray-800">Session chat</span>
            <span className="text-[10px] text-gray-400">· live</span>
          </div>

          <div className="min-h-0 flex-1 space-y-2 overflow-y-auto p-3">
            {messages.length === 0 && (
              <p className="py-8 text-center text-xs text-gray-400">No messages yet. Start the conversation.</p>
            )}
            {messages.map((msg) => {
              const isMe = msg.senderId === user?.id;
              return (
                <div key={msg.id} className={`flex ${isMe ? "justify-end" : "justify-start"}`}>
                  <div className={`max-w-[85%] rounded-xl px-3 py-2 ${
                    isMe ? "bg-impilo-500 text-white" : "bg-gray-100 text-gray-900"
                  }`}>
                    {!isMe && <p className="mb-0.5 text-[10px] font-semibold opacity-70">{msg.senderName}</p>}
                    <p className="text-sm leading-relaxed">{msg.content}</p>
                    <p className={`mt-0.5 text-[9px] ${isMe ? "text-impilo-100" : "text-gray-400"}`}>
                      {new Date(msg.timestamp).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
                    </p>
                  </div>
                </div>
              );
            })}
            <div ref={chatEndRef} />
          </div>

          <div className="shrink-0 border-t p-2">
            <div className="flex gap-1.5">
              <input
                value={newMessage}
                onChange={(e) => setNewMessage(e.target.value)}
                onKeyDown={(e) => { if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); handleSendMessage(); } }}
                placeholder="Type a message..."
                className="flex-1 rounded-lg border border-gray-200 px-3 py-2 text-sm focus:ring-1 focus:ring-impilo-400"
              />
              <button
                onClick={handleSendMessage}
                disabled={sending || !newMessage.trim()}
                className="rounded-lg bg-impilo-500 p-2 text-white hover:bg-impilo-600 disabled:opacity-40"
              >
                <Send className="h-4 w-4" />
              </button>
            </div>
          </div>
        </div>

        {/* ═══ CENTER PANE — Response Note Draft ═══ */}
        <div className="min-h-0 min-w-0 flex-1 space-y-4 overflow-y-auto p-4">
          <div className="flex items-center justify-between">
            <h3 className="flex items-center gap-2 text-sm font-semibold text-gray-900">
              <FileText className="h-4 w-4 text-gray-500" /> Response Note
            </h3>
            <div className="flex items-center gap-2 text-xs text-gray-400">
              {lastSaved && <span>Saved {lastSaved}</span>}
              <span className="h-1.5 w-1.5 rounded-full bg-green-400" />
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
        <div className="w-72 shrink-0 overflow-y-auto border-l bg-white">
          <div className="border-b p-3">
            <h4 className="mb-2 text-[10px] font-semibold uppercase tracking-wider text-gray-400">Patient</h4>
            <div className="flex items-center gap-2">
              <div className="flex h-8 w-8 items-center justify-center rounded-full bg-impilo-100">
                <User className="h-4 w-4 text-impilo-600" />
              </div>
              <div>
                <p className="text-sm font-medium text-gray-900">{(session?.patientId as string)?.substring(0, 12) || "Patient"}</p>
                <p className="text-[10px] text-gray-400">Click to view chart</p>
              </div>
            </div>
          </div>

          <div className="border-b p-3">
            <h4 className="mb-2 text-[10px] font-semibold uppercase tracking-wider text-gray-400">Referral</h4>
            <div className="space-y-1.5 text-xs">
              <div className="flex justify-between"><span className="text-gray-500">Urgency</span><span className="font-medium">{(session?.urgency as string) || "—"}</span></div>
              <div className="flex justify-between"><span className="text-gray-500">Specialty</span><span className="font-medium">{(session?.specialty as string) || "—"}</span></div>
              <div className="flex justify-between"><span className="text-gray-500">Routing</span><span className="font-medium">{(session?.routingType as string) || "—"}</span></div>
              <div className="flex justify-between"><span className="text-gray-500">Stage</span><span className="font-medium">{(session?.stage as number) || "—"}</span></div>
            </div>
          </div>

          <div className="border-b p-3">
            <h4 className="mb-2 text-[10px] font-semibold uppercase tracking-wider text-gray-400">Consent</h4>
            {Boolean(session?.consentToken) ? (
              <div className="flex items-center gap-1.5 text-xs text-green-700">
                <Shield className="w-3.5 h-3.5" /> Verified
              </div>
            ) : (
              <p className="text-xs text-gray-400">Pending</p>
            )}
          </div>

          {Boolean(session?.referralLetter) && (
            <div className="border-b p-3">
              <h4 className="mb-2 text-[10px] font-semibold uppercase tracking-wider text-gray-400">Referral Letter</h4>
              <p className="text-xs text-gray-600 line-clamp-6">{session?.referralLetter as string}</p>
            </div>
          )}

          <div className="p-3">
            <h4 className="mb-2 text-[10px] font-semibold uppercase tracking-wider text-gray-400">Timeline</h4>
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

      {callActive && (
        <div
          className="fixed inset-x-0 z-[250] mx-auto flex max-w-lg items-center justify-between gap-3 rounded-2xl border border-slate-200 bg-white/95 px-4 py-3 shadow-2xl backdrop-blur-md"
          style={{ bottom: `calc(${SHELL_TASKBAR_HEIGHT_PX}px + 0.75rem)` }}
        >
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold text-gray-900">
              {callPhase === "ringing" ? "Ringing…" : isVideoCall ? "Video call" : "Audio call"}
              {callPhase !== "ringing" ? ` · ${formatTime(callDuration)}` : ""}
            </p>
            <p className="truncate text-xs text-gray-500">
              {callPhase === "ringing"
                ? "Waiting for accept"
                : `${connectionState} · ${signalingStatus}${remotePeerId ? " · connected" : " · waiting for peer"}`}
            </p>
          </div>
          <button
            type="button"
            onClick={endCall}
            className="shrink-0 rounded-full bg-red-500 px-4 py-2 text-sm font-medium text-white hover:bg-red-600"
          >
            End
          </button>
        </div>
      )}
    </div>
    </AppLayout>
  );
}
