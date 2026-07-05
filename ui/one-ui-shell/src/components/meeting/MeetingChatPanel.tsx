"use client";

import { useEffect, useRef, useState } from "react";
import { Send } from "lucide-react";
import { useMessages, useSendMessage } from "@/hooks/queries/useComms";

/**
 * In-meeting chat — the SAME Khuluma conversation thread as the Comms Hub (template chat
 * persistence = CONVERSATION), filtered to plain TEXT messages (AGENDA/NOTE render in the
 * Notes panel). Polling keeps it live without the realtime socket.
 */
export function MeetingChatPanel({
  conversationId,
  currentActorId,
}: {
  conversationId: string;
  currentActorId: string;
}) {
  const messages = useMessages(conversationId);
  const send = useSendMessage(conversationId);
  const [draft, setDraft] = useState("");
  const endRef = useRef<HTMLDivElement | null>(null);

  const chat = (messages.data ?? []).filter((m) => m.contentType === "TEXT" || m.contentType === "SYSTEM");

  useEffect(() => {
    // scrollIntoView is absent in jsdom — optional-call defensively.
    endRef.current?.scrollIntoView?.({ behavior: "smooth" });
  }, [chat.length]);

  const handleSend = () => {
    const body = draft.trim();
    if (!body) return;
    send.mutate({ body, clientMessageId: crypto.randomUUID() });
    setDraft("");
  };

  return (
    <div className="flex h-full flex-col" data-testid="meeting-chat-panel">
      <div className="flex-1 space-y-1.5 overflow-y-auto p-3">
        {chat.length === 0 && <p className="text-xs text-neutral-500">No messages yet.</p>}
        {chat.map((m) => (
          <div
            key={m.messageId}
            className={`max-w-[85%] rounded px-2 py-1.5 text-xs ${
              m.senderId === currentActorId ? "ml-auto bg-emerald-900/60" : "bg-neutral-800"
            }`}
          >
            <p className="mb-0.5 text-[10px] text-neutral-400">{m.senderDisplayName ?? m.senderId}</p>
            <p className="whitespace-pre-wrap break-words">{m.body}</p>
          </div>
        ))}
        <div ref={endRef} />
      </div>
      <div className="flex gap-1 border-t border-neutral-800 p-2">
        <input
          aria-label="Chat message"
          data-testid="meeting-chat-input"
          className="min-w-0 flex-1 rounded border border-neutral-700 bg-neutral-900 px-2 py-1.5 text-xs"
          placeholder="Message everyone…"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && handleSend()}
        />
        <button
          type="button"
          aria-label="Send message"
          data-testid="meeting-chat-send"
          className="rounded bg-emerald-600 px-2.5 text-white hover:bg-emerald-500"
          onClick={handleSend}
        >
          <Send className="h-3.5 w-3.5" />
        </button>
      </div>
    </div>
  );
}
