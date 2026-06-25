"use client";

import { useEffect, useRef, useState } from "react";
import { MessageSquare, Send, Phone, Video, Users, Radio, PhoneIncoming } from "lucide-react";
import { useAuthStore } from "@/hooks/useAuthStore";
import {
  useCommsInbox,
  useConversation,
  useMessages,
  useSendMessage,
  useMarkRead,
  useUpdatePresence,
  useStartCall,
  useCallActions,
  useMeetingActions,
  useCommsSummary,
  useIncomingCalls,
  meetingToCall,
  type CallResponse,
} from "@/hooks/queries/useComms";
import { useKhulumaRealtime, type IncomingCall } from "@/hooks/useKhulumaRealtime";
import { CommsCallModal } from "@/components/comms/CommsCallModal";

const PRESENCE_OPTIONS = ["ONLINE", "AWAY", "BUSY", "DND", "OFFLINE"] as const;

/**
 * The first-class Comms Hub surface: a live inbox, conversation detail with realtime send + read
 * receipts, a presence control, an unread badge, and 1:1 calls (incoming-call prompt + LiveKit
 * room). Backed entirely by real `/internal/v1/khuluma/**` BFF endpoints; realtime is an optional
 * enhancement (see {@link useKhulumaRealtime}) — the hub stays correct via refetch without it.
 */
export function CommsHub({ persona }: { persona: "work" | "life" }) {
  const user = useAuthStore((state) => state.user);
  const currentActorId = user?.healthId ?? user?.id ?? "";

  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [draft, setDraft] = useState("");
  const [activeCall, setActiveCall] = useState<{ call: CallResponse; direction: "incoming" | "outgoing" } | null>(null);
  const [incoming, setIncoming] = useState<IncomingCall | null>(null);

  const inbox = useCommsInbox();
  const summary = useCommsSummary();
  const detail = useConversation(selectedId);
  const messages = useMessages(selectedId);
  const sendMessage = useSendMessage(selectedId ?? "");
  const markRead = useMarkRead(selectedId ?? "");
  const updatePresence = useUpdatePresence();
  const startCall = useStartCall();
  const callActions = useCallActions();
  const meetingActions = useMeetingActions();

  useKhulumaRealtime({ enabled: !!user, onIncomingCall: setIncoming });

  // Secure poll fallback for ringing (works without the realtime socket) — surfaces the prompt.
  const incomingPoll = useIncomingCalls(!!user && !activeCall);
  useEffect(() => {
    const ringing = incomingPoll.data?.[0];
    if (ringing && !activeCall && (!incoming || incoming.callId !== ringing.callId)) {
      setIncoming({
        callId: ringing.callId,
        conversationId: ringing.conversationId,
        initiatedBy: ringing.initiatedBy,
        callType: ringing.callType,
      });
    }
  }, [incomingPoll.data, activeCall, incoming]);

  // Mark the newest peer message read once a conversation's messages are visible.
  const lastMarkedRef = useRef<string | null>(null);
  useEffect(() => {
    const list = messages.data;
    if (!selectedId || !list || list.length === 0) return;
    const newest = list[list.length - 1];
    if (newest.senderId !== currentActorId && lastMarkedRef.current !== newest.messageId) {
      lastMarkedRef.current = newest.messageId;
      markRead.mutate(newest.messageId);
    }
  }, [messages.data, selectedId, currentActorId, markRead]);

  const handleSend = () => {
    const body = draft.trim();
    if (!body || !selectedId) return;
    sendMessage.mutate({ body, clientMessageId: crypto.randomUUID() });
    setDraft("");
  };

  const handleStartCall = (callType: "AUDIO" | "VIDEO") => {
    if (!selectedId || !detail.data) return;
    const callees = detail.data.participants
      .filter((p) => p.active && p.actorId !== currentActorId)
      .map((p) => ({ actorId: p.actorId, actorType: p.actorType, displayName: p.displayName ?? undefined }));
    startCall.mutate(
      { conversationId: selectedId, callType, displayName: user?.healthId ?? undefined, callees },
      { onSuccess: (call) => setActiveCall({ call, direction: "outgoing" }) },
    );
  };

  const handleStartMeeting = () => {
    if (!selectedId || !detail.data) return;
    const participants = detail.data.participants
      .filter((p) => p.active && p.actorId !== currentActorId)
      .map((p) => ({ actorId: p.actorId, actorType: p.actorType, displayName: p.displayName ?? undefined }));
    meetingActions
      .create({ title: detail.data.title ?? "Meeting", participants })
      .then((meeting) => meetingActions.join(meeting.conversationId))
      .then((joined) => setActiveCall({ call: meetingToCall(joined), direction: "outgoing" }));
  };

  const acceptIncoming = async () => {
    if (!incoming) return;
    const call = await callActions.accept(incoming.callId);
    setActiveCall({ call, direction: "incoming" });
    setIncoming(null);
  };

  const declineIncoming = async () => {
    if (!incoming) return;
    await callActions.decline(incoming.callId);
    setIncoming(null);
  };

  const endActiveCall = async () => {
    if (activeCall) {
      await callActions.end(activeCall.call.callId).catch(() => undefined);
      setActiveCall(null);
    }
  };

  const unread = summary.data?.messages?.unreadCount ?? 0;
  // A meeting conversation is an Impilo Live event — offer to open the rich Live experience.
  const liveEventId = detail.data?.links?.find((l) => l.objectType === "LIVE_EVENT")?.objectId;

  return (
    <div className="flex flex-col gap-3" data-testid="comms-hub" data-persona={persona}>
      {/* Header: presence + unread badge */}
      <div className="flex items-center justify-between rounded-md border border-border bg-card px-3 py-2">
        <div className="flex items-center gap-2 text-sm font-medium">
          <MessageSquare className="h-4 w-4" />
          <span>{persona === "work" ? "Work conversations" : "My messages"}</span>
          {unread > 0 && (
            <span
              className="ml-1 rounded-full bg-primary px-2 py-0.5 text-xs text-primary-foreground"
              data-testid="unread-badge"
            >
              {unread}
            </span>
          )}
        </div>
        <label className="flex items-center gap-2 text-xs text-muted-foreground">
          Presence
          <select
            aria-label="Presence status"
            className="rounded-md border border-border bg-background px-2 py-1 text-xs"
            defaultValue="ONLINE"
            onChange={(e) => updatePresence.mutate({ status: e.target.value })}
          >
            {PRESENCE_OPTIONS.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </label>
      </div>

      <div className="grid grid-cols-1 gap-3 md:grid-cols-[280px_1fr]">
        {/* Inbox */}
        <aside className="rounded-md border border-border bg-card" aria-label="Conversation inbox">
          {inbox.isLoading && <p className="p-3 text-sm text-muted-foreground">Loading inbox…</p>}
          {inbox.isError && <p className="p-3 text-sm text-destructive">Could not load conversations.</p>}
          {inbox.data && inbox.data.length === 0 && (
            <p className="p-3 text-sm text-muted-foreground">No conversations yet.</p>
          )}
          <ul className="divide-y divide-border">
            {inbox.data?.map((c) => (
              <li key={c.conversationId}>
                <button
                  type="button"
                  onClick={() => setSelectedId(c.conversationId)}
                  className={`flex w-full flex-col items-start gap-0.5 px-3 py-2 text-left hover:bg-accent ${
                    selectedId === c.conversationId ? "bg-accent" : ""
                  }`}
                >
                  <span className="flex w-full items-center justify-between text-sm font-medium">
                    <span className="truncate">{c.title ?? c.type}</span>
                    {c.unreadCount > 0 && (
                      <span className="ml-2 rounded-full bg-primary px-1.5 text-xs text-primary-foreground">
                        {c.unreadCount}
                      </span>
                    )}
                  </span>
                  <span className="truncate text-xs text-muted-foreground">{c.lastMessagePreview ?? "—"}</span>
                </button>
              </li>
            ))}
          </ul>
        </aside>

        {/* Conversation detail */}
        <section className="flex min-h-[360px] flex-col rounded-md border border-border bg-card" aria-label="Conversation">
          {!selectedId && (
            <div className="flex flex-1 items-center justify-center text-sm text-muted-foreground">
              Select a conversation to start messaging.
            </div>
          )}
          {selectedId && (
            <>
              <div className="flex items-center justify-between border-b border-border px-3 py-2">
                <span className="text-sm font-medium">{detail.data?.title ?? detail.data?.type ?? "Conversation"}</span>
                <div className="flex items-center gap-2">
                  {liveEventId && (
                    <a
                      href={`/live/event/${liveEventId}`}
                      className="inline-flex items-center gap-1 rounded-md border border-border px-2 py-1 text-xs hover:bg-accent"
                    >
                      <Radio className="h-3.5 w-3.5" /> Open in Impilo Live
                    </a>
                  )}
                  <button
                    type="button"
                    aria-label="Start audio call"
                    onClick={() => handleStartCall("AUDIO")}
                    className="inline-flex items-center gap-1 rounded-md border border-border px-2 py-1 text-xs hover:bg-accent"
                  >
                    <Phone className="h-3.5 w-3.5" /> Call
                  </button>
                  <button
                    type="button"
                    aria-label="Start video call"
                    onClick={() => handleStartCall("VIDEO")}
                    className="inline-flex items-center gap-1 rounded-md border border-border px-2 py-1 text-xs hover:bg-accent"
                  >
                    <Video className="h-3.5 w-3.5" /> Video
                  </button>
                  <button
                    type="button"
                    aria-label="Start meeting"
                    onClick={handleStartMeeting}
                    className="inline-flex items-center gap-1 rounded-md border border-border px-2 py-1 text-xs hover:bg-accent"
                  >
                    <Users className="h-3.5 w-3.5" /> Meet
                  </button>
                </div>
              </div>

              <div className="flex-1 space-y-2 overflow-y-auto p-3" data-testid="message-list">
                {messages.isLoading && <p className="text-sm text-muted-foreground">Loading messages…</p>}
                {messages.data?.map((m) => {
                  const mine = m.senderId === currentActorId;
                  return (
                    <div key={m.messageId} className={`flex ${mine ? "justify-end" : "justify-start"}`}>
                      <div
                        className={`max-w-[80%] rounded-lg px-3 py-1.5 text-sm ${
                          mine ? "bg-primary text-primary-foreground" : "bg-muted"
                        }`}
                      >
                        {!mine && (
                          <p className="text-[10px] font-medium opacity-70">{m.senderDisplayName ?? m.senderId}</p>
                        )}
                        <p className="whitespace-pre-wrap break-words">{m.body}</p>
                      </div>
                    </div>
                  );
                })}
              </div>

              <div className="flex items-end gap-2 border-t border-border p-2">
                <textarea
                  aria-label="Message"
                  value={draft}
                  onChange={(e) => setDraft(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" && !e.shiftKey) {
                      e.preventDefault();
                      handleSend();
                    }
                  }}
                  rows={2}
                  placeholder="Type a message…"
                  className="flex-1 resize-none rounded-md border border-border bg-background px-3 py-2 text-sm"
                />
                <button
                  type="button"
                  onClick={handleSend}
                  disabled={!draft.trim() || sendMessage.isPending}
                  className="inline-flex items-center gap-1 rounded-md bg-primary px-3 py-2 text-sm text-primary-foreground disabled:opacity-50"
                >
                  <Send className="h-4 w-4" /> Send
                </button>
              </div>
            </>
          )}
        </section>
      </div>

      {/* Incoming call prompt */}
      {incoming && !activeCall && (
        <div
          className="fixed bottom-4 right-4 z-50 flex items-center gap-3 rounded-lg border border-border bg-card p-3 shadow-lg"
          role="dialog"
          aria-label="Incoming call"
        >
          <PhoneIncoming className="h-5 w-5 text-emerald-500" />
          <div className="text-sm">
            <p className="font-medium">Incoming {incoming.callType.toLowerCase()} call</p>
            <p className="text-xs text-muted-foreground">from {incoming.initiatedBy}</p>
          </div>
          <button type="button" onClick={acceptIncoming} className="rounded-md bg-emerald-600 px-3 py-1 text-sm text-white">
            Accept
          </button>
          <button type="button" onClick={declineIncoming} className="rounded-md bg-red-600 px-3 py-1 text-sm text-white">
            Decline
          </button>
        </div>
      )}

      {/* Active call */}
      {activeCall && <CommsCallModal call={activeCall.call} direction={activeCall.direction} onEnd={endActiveCall} />}
    </div>
  );
}
