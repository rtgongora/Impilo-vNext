"use client";

import { useEffect, useState } from "react";
import { MessagesSquare, Send } from "lucide-react";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useMessages, useSendMessage, useMeetingActions } from "@/hooks/queries/useComms";

/**
 * The Khuluma discussion for an Impilo Live event, rendered inline on the event surface — so the
 * Impilo Live experience and Khuluma are one product family, not parallel screens. Resolves the
 * conversation anchored to the event ({@code eventConversation}); if none exists yet, an opt-in
 * "Start discussion" attaches one idempotently ({@code fromEvent}). Reuses the Khuluma message hooks.
 */
function commsErrorMessage(error: unknown): string {
  const status = (error as { status?: number })?.status;
  if (status === 401 || status === 403) return "Sign in to join this discussion.";
  const msg = (error as { error?: { message?: string } })?.error?.message;
  return msg || "Couldn't reach the discussion. Please try again.";
}

export function EventDiscussionPanel({ eventId, title }: { eventId: string; title?: string }) {
  const user = useAuthStore((s) => s.user);
  const currentActorId = user?.healthId ?? user?.id ?? "";
  const meetingActions = useMeetingActions();

  const [conversationId, setConversationId] = useState<string | null | undefined>(undefined); // undefined = loading
  const [starting, setStarting] = useState(false);
  const [draft, setDraft] = useState("");
  const [actionError, setActionError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    meetingActions
      .eventConversation(eventId)
      .then((r) => {
        if (active) setConversationId(r?.conversationId ?? null);
      })
      .catch(() => {
        if (active) setConversationId(null); // 404 → no discussion yet
      });
    return () => {
      active = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [eventId]);

  const messages = useMessages(conversationId ?? null);
  const send = useSendMessage(conversationId ?? "");

  const startDiscussion = () => {
    setStarting(true);
    setActionError(null);
    meetingActions
      .fromEvent(eventId, [], title)
      .then((r) => setConversationId(r.conversationId))
      .catch((e) => setActionError(commsErrorMessage(e)))
      .finally(() => setStarting(false));
  };

  const handleSend = () => {
    const body = draft.trim();
    if (!body || !conversationId) return;
    setActionError(null);
    send.mutate(
      { body, clientMessageId: crypto.randomUUID() },
      {
        onSuccess: () => setDraft(""),
        onError: (e) => setActionError(commsErrorMessage(e)),
      },
    );
  };

  return (
    <section className="rounded-2xl border border-border bg-card p-4" data-testid="event-discussion">
      <h3 className="mb-3 flex items-center gap-2 text-sm font-semibold">
        <MessagesSquare className="h-4 w-4" /> Discussion
        <span className="text-xs font-normal text-muted-foreground">· Khuluma</span>
      </h3>

      {conversationId === undefined && <p className="text-sm text-muted-foreground">Loading…</p>}

      {conversationId === null && (
        <div className="space-y-2">
          <p className="text-xs text-muted-foreground">Start a conversation for this event with attendees and hosts.</p>
          <button
            type="button"
            onClick={startDiscussion}
            disabled={starting}
            className="rounded-lg border border-border px-3 py-2 text-sm hover:bg-accent disabled:opacity-50"
          >
            {starting ? "Starting…" : "Start discussion"}
          </button>
          {actionError && <p className="text-xs text-red-600">{actionError}</p>}
        </div>
      )}

      {conversationId && (
        <>
          <div className="mb-2 max-h-64 space-y-2 overflow-y-auto" data-testid="event-discussion-messages">
            {messages.data?.length === 0 && <p className="text-xs text-muted-foreground">No messages yet.</p>}
            {messages.data?.map((m) => {
              const mine = m.senderId === currentActorId;
              return (
                <div key={m.messageId} className={`flex ${mine ? "justify-end" : "justify-start"}`}>
                  <div
                    className={`max-w-[85%] rounded-lg px-3 py-1.5 text-sm ${
                      mine ? "bg-primary text-primary-foreground" : "bg-muted"
                    }`}
                  >
                    {!mine && <p className="text-[10px] font-medium opacity-70">{m.senderDisplayName ?? m.senderId}</p>}
                    <p className="whitespace-pre-wrap break-words">{m.body}</p>
                  </div>
                </div>
              );
            })}
          </div>
          <div className="flex items-end gap-2">
            <textarea
              aria-label="Discussion message"
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter" && !e.shiftKey) {
                  e.preventDefault();
                  handleSend();
                }
              }}
              rows={1}
              placeholder="Message…"
              className="flex-1 resize-none rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
            <button
              type="button"
              aria-label="Send"
              onClick={handleSend}
              disabled={!draft.trim() || send.isPending}
              className="inline-flex items-center gap-1 rounded-md bg-primary px-3 py-2 text-sm text-primary-foreground disabled:opacity-50"
            >
              <Send className="h-4 w-4" />
            </button>
          </div>
          {actionError && <p className="mt-1 text-xs text-red-600">{actionError}</p>}
        </>
      )}
    </section>
  );
}
