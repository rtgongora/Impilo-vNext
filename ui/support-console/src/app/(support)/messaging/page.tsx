"use client";

import React, { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { listTickets, listMessages, sendMessage } from "@/lib/supportApi";
import { StatusBadge } from "@/components/StatusBadge";
import { useSupportSession } from "@/stores/sessionStore";
import type { Ticket, SupportMessage } from "@/types/support";

export default function MessagingPage() {
  const session = useSupportSession((s) => s.session);
  const actorId = session?.actorId ?? "support-agent";

  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [selectedTicketId, setSelectedTicketId] = useState<string | null>(null);
  const [messages, setMessages] = useState<SupportMessage[]>([]);
  const [loading, setLoading] = useState(true);
  const [messagesLoading, setMessagesLoading] = useState(false);
  const [messageBody, setMessageBody] = useState("");
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadTickets = useCallback(async () => {
    setLoading(true);
    try {
      const data = await listTickets({ limit: 50 });
      const activeTickets = data.items.filter(
        (t) => t.status !== "CLOSED" && t.status !== "RESOLVED"
      );
      setTickets(activeTickets);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load tickets");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadTickets(); }, [loadTickets]);

  const loadMessages = useCallback(async (ticketId: string) => {
    setMessagesLoading(true);
    try {
      const data = await listMessages(ticketId);
      setMessages(data.items);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load messages");
    } finally {
      setMessagesLoading(false);
    }
  }, []);

  useEffect(() => {
    if (selectedTicketId) loadMessages(selectedTicketId);
  }, [selectedTicketId, loadMessages]);

  const handleSend = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!messageBody.trim() || !selectedTicketId) return;
    setSending(true);
    try {
      const msg = await sendMessage(selectedTicketId, actorId, "AGENT", messageBody.trim());
      setMessages((prev) => [...prev, msg]);
      setMessageBody("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to send message");
    } finally {
      setSending(false);
    }
  };

  const selectedTicket = tickets.find((t) => t.ticketId === selectedTicketId);

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-neutral-900">Support Messaging</h1>
        <p className="text-sm text-neutral-500 mt-1">
          Conversation threads linked to active tickets
        </p>
      </div>

      {error && (
        <div className="bg-danger/10 border border-danger/20 rounded-lg p-4 text-sm text-danger mb-4">
          {error}
          <button onClick={() => setError(null)} className="ml-2 underline">Dismiss</button>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-0 bg-card rounded-[12px] shadow-subtle border border-neutral-100 min-h-[600px]">
        {/* Ticket conversation list */}
        <div className="lg:col-span-1 border-r border-neutral-100">
          <div className="p-3 border-b border-neutral-100">
            <p className="text-xs font-medium text-neutral-500 uppercase tracking-wide">
              Active Tickets ({tickets.length})
            </p>
          </div>
          {loading ? (
            <div className="p-6 text-center text-sm text-neutral-500">Loading...</div>
          ) : tickets.length === 0 ? (
            <div className="p-6 text-center text-sm text-neutral-500">No active tickets</div>
          ) : (
            <div className="divide-y divide-neutral-50 max-h-[550px] overflow-y-auto">
              {tickets.map((t) => (
                <button
                  key={t.ticketId}
                  onClick={() => setSelectedTicketId(t.ticketId)}
                  className={`w-full text-left p-3 hover:bg-neutral-50/50 transition-colors ${
                    selectedTicketId === t.ticketId ? "bg-brand-primary/5 border-l-2 border-brand-primary" : ""
                  }`}
                >
                  <div className="flex items-center gap-2 mb-0.5">
                    <StatusBadge status={t.status} />
                    <span className="text-xs text-neutral-400">{t.reporterRef}</span>
                  </div>
                  <p className="text-sm font-medium text-neutral-900 line-clamp-1">{t.title}</p>
                  <p className="text-xs text-neutral-500 mt-0.5">
                    {t.assigneeRef ? `Assigned: ${t.assigneeRef}` : "Unassigned"}
                  </p>
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Message thread */}
        <div className="lg:col-span-2 flex flex-col">
          {selectedTicket ? (
            <>
              {/* Thread header */}
              <div className="p-4 border-b border-neutral-100 flex items-center justify-between">
                <div>
                  <p className="text-sm font-semibold text-neutral-900">{selectedTicket.title}</p>
                  <p className="text-xs text-neutral-500">
                    {selectedTicket.reporterRef} &middot; {selectedTicket.category} &middot;{" "}
                    <Link href={`/tickets/${selectedTicket.ticketId}`} className="text-brand-primary hover:underline">
                      View ticket
                    </Link>
                  </p>
                </div>
                <StatusBadge status={selectedTicket.status} />
              </div>

              {/* Messages */}
              <div className="flex-1 p-4 overflow-y-auto max-h-[420px] space-y-3">
                {messagesLoading ? (
                  <p className="text-sm text-neutral-500 text-center py-8">Loading messages...</p>
                ) : messages.length === 0 ? (
                  <p className="text-sm text-neutral-500 text-center py-8">
                    No messages yet. Start the conversation.
                  </p>
                ) : (
                  messages.map((m) => (
                    <div
                      key={m.messageId}
                      className={`max-w-[80%] p-3 rounded-lg text-sm ${
                        m.senderType === "AGENT"
                          ? "bg-brand-primary/5 border border-brand-primary/10 ml-auto"
                          : m.senderType === "SYSTEM"
                          ? "bg-neutral-50 text-neutral-500 italic mx-auto text-center"
                          : "bg-card border border-neutral-200"
                      }`}
                    >
                      <div className="flex items-center gap-2 mb-1">
                        <span className="text-xs font-medium">{m.senderRef}</span>
                        <span className="text-xs text-neutral-400">
                          {new Date(m.createdAt).toLocaleTimeString()}
                        </span>
                      </div>
                      <p className="whitespace-pre-wrap">{m.body}</p>
                    </div>
                  ))
                )}
              </div>

              {/* Message input */}
              <div className="p-4 border-t border-neutral-100">
                <form onSubmit={handleSend} className="flex gap-3">
                  <input
                    type="text"
                    value={messageBody}
                    onChange={(e) => setMessageBody(e.target.value)}
                    placeholder="Type a message..."
                    className="flex-1 px-4 py-2 border border-neutral-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/30"
                  />
                  <button
                    type="submit"
                    disabled={!messageBody.trim() || sending}
                    className="px-4 py-2 bg-brand-primary text-white rounded-lg text-sm font-medium hover:bg-brand-primary/90 disabled:opacity-50"
                  >
                    {sending ? "..." : "Send"}
                  </button>
                </form>
              </div>
            </>
          ) : (
            <div className="flex-1 flex items-center justify-center text-sm text-neutral-500">
              Select a ticket to view its conversation thread
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
