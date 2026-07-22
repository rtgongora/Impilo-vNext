"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { MessageSquare, Send, Phone, Video, Users, Radio, PhoneIncoming, Plus, X } from "lucide-react";
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
  useCreateConversation,
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
export function CommsHub({ persona, defaultCompose }: { persona: "work" | "life"; defaultCompose?: boolean }) {
  const router = useRouter();
  const user = useAuthStore((state) => state.user);
  const currentActorId = user?.healthId ?? user?.id ?? "";

  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [draft, setDraft] = useState("");
  const [activeCall, setActiveCall] = useState<{ call: CallResponse; direction: "incoming" | "outgoing" } | null>(null);
  const [incoming, setIncoming] = useState<IncomingCall | null>(null);

  // "New conversation" composer. Chat respects the no-public-people-search
  // boundary: you start a conversation with someone whose identifier you
  // already have (Health ID / Provider ID), or add several for a group.
  const [composeOpen, setComposeOpen] = useState(defaultCompose ?? false);
  const [composeTitle, setComposeTitle] = useState("");
  const [recipientId, setRecipientId] = useState("");
  const [recipientName, setRecipientName] = useState("");
  const [recipients, setRecipients] = useState<Array<{ actorId: string; displayName: string }>>([]);
  const [composeError, setComposeError] = useState<string | null>(null);
  const createConversation = useCreateConversation();

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

  function addRecipient() {
    const id = recipientId.trim();
    if (!id || recipients.some((r) => r.actorId === id)) return;
    setRecipients((prev) => [...prev, { actorId: id, displayName: recipientName.trim() || id }]);
    setRecipientId("");
    setRecipientName("");
  }

  function resetCompose() {
    setComposeOpen(false);
    setComposeTitle("");
    setRecipientId("");
    setRecipientName("");
    setRecipients([]);
    setComposeError(null);
  }

  async function handleCreateConversation() {
    setComposeError(null);
    // allow a single typed-but-not-yet-added recipient
    const pending = recipientId.trim();
    const all = pending && !recipients.some((r) => r.actorId === pending)
      ? [...recipients, { actorId: pending, displayName: recipientName.trim() || pending }]
      : recipients;
    if (all.length === 0) {
      setComposeError("Add at least one recipient by their Health ID or Provider ID.");
      return;
    }
    try {
      const res = (await createConversation.mutateAsync({
        type: all.length > 1 ? "GROUP" : "DIRECT",
        title: composeTitle.trim() || (all.length > 1 ? "Group conversation" : all[0].displayName),
        participants: all.map((r) => ({ actorId: r.actorId, actorType: "CITIZEN", displayName: r.displayName, role: "MEMBER" })),
      })) as { conversationId?: string; id?: string };
      const newId = res?.conversationId ?? res?.id;
      if (!newId) {
        setComposeError("The conversation was not created — the service returned no id. Please retry.");
        return;
      }
      resetCompose();
      setSelectedId(newId);
    } catch (e) {
      const err = e as { status?: number; error?: { message?: string } };
      setComposeError(err?.error?.message ?? "Could not start the conversation. Check the recipient identifier and try again.");
    }
  }

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

  // Meetings open the full /meet room (W5): grid stage, KNOCK lobby, notes, reactions.
  // A MEETING conversation navigates straight there; other types spin a meeting up first.
  const handleStartMeeting = () => {
    if (!selectedId || !detail.data) return;
    if (detail.data.type === "MEETING") {
      router.push(`/meet/${selectedId}`);
      return;
    }
    const participants = detail.data.participants
      .filter((p) => p.active && p.actorId !== currentActorId)
      .map((p) => ({ actorId: p.actorId, actorType: p.actorType, displayName: p.displayName ?? undefined }));
    meetingActions
      .create({ title: detail.data.title ?? "Meeting", participants })
      .then((meeting) => router.push(`/meet/${meeting.conversationId}`));
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
          <div className="flex items-center justify-between border-b border-border px-3 py-2">
            <span className="text-xs font-medium text-muted-foreground">Conversations</span>
            <button
              type="button"
              onClick={() => setComposeOpen(true)}
              data-testid="comms-new-conversation"
              className="inline-flex items-center gap-1 rounded-md bg-primary px-2 py-1 text-xs text-primary-foreground"
            >
              <Plus className="h-3.5 w-3.5" /> New
            </button>
          </div>
          {inbox.isLoading && <p className="p-3 text-sm text-muted-foreground">Loading inbox…</p>}
          {inbox.isError && <p className="p-3 text-sm text-destructive">Could not load conversations.</p>}
          {inbox.data && inbox.data.length === 0 && (
            <p className="p-3 text-sm text-muted-foreground">No conversations yet. Tap <span className="font-medium">New</span> to start one.</p>
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

              {sendMessage.isError && (
                <p className="border-t border-border px-3 py-1 text-xs text-destructive" data-testid="comms-send-error">
                  Message failed to send. Please retry.
                </p>
              )}
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

      {/* New conversation composer */}
      {composeOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" role="dialog" aria-label="New conversation">
          <div className="w-full max-w-md rounded-lg border border-border bg-card p-4 shadow-xl" data-testid="comms-new-dialog">
            <div className="mb-3 flex items-center justify-between">
              <h2 className="text-sm font-semibold">New conversation</h2>
              <button type="button" aria-label="Close" onClick={resetCompose} className="rounded p-1 hover:bg-accent">
                <X className="h-4 w-4" />
              </button>
            </div>
            <label className="block text-xs text-muted-foreground">
              Title (optional)
              <input
                value={composeTitle}
                onChange={(e) => setComposeTitle(e.target.value)}
                className="mt-1 w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm"
                placeholder="e.g. Care coordination"
              />
            </label>
            <div className="mt-3 grid grid-cols-[1fr_1fr_auto] items-end gap-2">
              <label className="block text-xs text-muted-foreground">
                Recipient ID
                <input
                  value={recipientId}
                  onChange={(e) => setRecipientId(e.target.value)}
                  data-testid="comms-recipient-id"
                  className="mt-1 w-full rounded-md border border-border bg-background px-2 py-1.5 font-mono text-xs"
                  placeholder="Health ID / Provider ID"
                />
              </label>
              <label className="block text-xs text-muted-foreground">
                Name (optional)
                <input
                  value={recipientName}
                  onChange={(e) => setRecipientName(e.target.value)}
                  className="mt-1 w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm"
                  placeholder="Display name"
                />
              </label>
              <button type="button" onClick={addRecipient} className="rounded-md border border-border px-2 py-1.5 text-xs hover:bg-accent">
                Add
              </button>
            </div>
            {recipients.length > 0 && (
              <ul className="mt-2 flex flex-wrap gap-1.5">
                {recipients.map((r) => (
                  <li key={r.actorId} className="inline-flex items-center gap-1 rounded-full bg-muted px-2 py-0.5 text-xs">
                    {r.displayName}
                    <button type="button" aria-label={`Remove ${r.displayName}`} onClick={() => setRecipients((prev) => prev.filter((x) => x.actorId !== r.actorId))}>
                      <X className="h-3 w-3" />
                    </button>
                  </li>
                ))}
              </ul>
            )}
            {composeError && (
              <p className="mt-3 rounded-md border border-warning/35 bg-warning-soft p-2 text-xs text-warning-foreground" data-testid="comms-new-error">
                {composeError}
              </p>
            )}
            <div className="mt-4 flex justify-end gap-2">
              <button type="button" onClick={resetCompose} className="rounded-md border border-border px-3 py-1.5 text-sm hover:bg-accent">
                Cancel
              </button>
              <button
                type="button"
                onClick={handleCreateConversation}
                disabled={createConversation.isPending}
                data-testid="comms-create-conversation"
                className="rounded-md bg-primary px-3 py-1.5 text-sm text-primary-foreground disabled:opacity-50"
              >
                {createConversation.isPending ? "Starting…" : "Start conversation"}
              </button>
            </div>
          </div>
        </div>
      )}

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
