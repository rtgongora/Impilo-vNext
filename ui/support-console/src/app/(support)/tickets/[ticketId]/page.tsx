"use client";

import React, { useCallback, useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import {
  getTicket,
  updateTicket,
  listComments,
  addComment,
  listAssignments,
  assignTicket,
  escalateTicket,
  listMessages,
  sendMessage,
} from "@/lib/supportApi";
import { StatusBadge, PriorityBadge, EscalationBadge } from "@/components/StatusBadge";
import { useSupportSession } from "@/stores/sessionStore";
import type {
  Ticket,
  TicketComment,
  TicketAssignment,
  SupportMessage,
  TicketStatus,
  TicketPriority,
} from "@/types/support";

const STATUS_TRANSITIONS: Record<string, TicketStatus[]> = {
  OPEN: ["IN_PROGRESS", "WAITING", "CLOSED"],
  IN_PROGRESS: ["WAITING", "RESOLVED", "CLOSED"],
  WAITING: ["IN_PROGRESS", "RESOLVED", "CLOSED"],
  RESOLVED: ["CLOSED", "OPEN"],
  CLOSED: ["OPEN"],
};

export default function TicketDetailPage() {
  const params = useParams();
  const router = useRouter();
  const ticketId = params.ticketId as string;
  const session = useSupportSession((s) => s.session);

  const [ticket, setTicket] = useState<Ticket | null>(null);
  const [comments, setComments] = useState<TicketComment[]>([]);
  const [assignments, setAssignments] = useState<TicketAssignment[]>([]);
  const [messages, setMessages] = useState<SupportMessage[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Form state
  const [commentBody, setCommentBody] = useState("");
  const [messageBody, setMessageBody] = useState("");
  const [assigneeRef, setAssigneeRef] = useState("");
  const [resolution, setResolution] = useState("");
  const [submitting, setSubmitting] = useState<string | null>(null);

  const actorId = session?.actorId ?? "support-agent";

  const loadTicket = useCallback(async () => {
    try {
      const [t, c, a, m] = await Promise.all([
        getTicket(ticketId),
        listComments(ticketId),
        listAssignments(ticketId),
        listMessages(ticketId),
      ]);
      setTicket(t);
      setComments(c.items);
      setAssignments(a.items);
      setMessages(m.items);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load ticket");
    } finally {
      setLoading(false);
    }
  }, [ticketId]);

  useEffect(() => { loadTicket(); }, [loadTicket]);

  const handleStatusChange = async (newStatus: TicketStatus) => {
    setSubmitting("status");
    try {
      const params: { status: TicketStatus; resolution?: string } = { status: newStatus };
      if ((newStatus === "RESOLVED" || newStatus === "CLOSED") && resolution.trim()) {
        params.resolution = resolution.trim();
      }
      const updated = await updateTicket(ticketId, params);
      setTicket(updated);
      setResolution("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to update status");
    } finally {
      setSubmitting(null);
    }
  };

  const handleAddComment = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!commentBody.trim()) return;
    setSubmitting("comment");
    try {
      const comment = await addComment(ticketId, actorId, commentBody.trim());
      setComments((prev) => [...prev, comment]);
      setCommentBody("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to add comment");
    } finally {
      setSubmitting(null);
    }
  };

  const handleAssign = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!assigneeRef.trim()) return;
    setSubmitting("assign");
    try {
      const assignment = await assignTicket(ticketId, assigneeRef.trim(), actorId);
      setAssignments((prev) => [assignment, ...prev]);
      setTicket((prev) => prev ? { ...prev, assigneeRef: assigneeRef.trim() } : null);
      setAssigneeRef("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to assign ticket");
    } finally {
      setSubmitting(null);
    }
  };

  const handleEscalate = async () => {
    if (!ticket) return;
    setSubmitting("escalate");
    try {
      const updated = await escalateTicket(ticketId, actorId, ticket.escalationLevel + 1);
      setTicket(updated);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to escalate ticket");
    } finally {
      setSubmitting(null);
    }
  };

  const handleSendMessage = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!messageBody.trim()) return;
    setSubmitting("message");
    try {
      const msg = await sendMessage(ticketId, actorId, "AGENT", messageBody.trim());
      setMessages((prev) => [...prev, msg]);
      setMessageBody("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to send message");
    } finally {
      setSubmitting(null);
    }
  };

  if (loading) {
    return <div className="py-20 text-center text-sm text-neutral-500">Loading ticket...</div>;
  }

  if (!ticket) {
    return (
      <div className="py-20 text-center">
        <p className="text-sm text-neutral-500">Ticket not found</p>
        <Link href="/tickets" className="text-brand-primary text-sm mt-2 inline-block">Back to tickets</Link>
      </div>
    );
  }

  const allowedTransitions = STATUS_TRANSITIONS[ticket.status] ?? [];

  return (
    <div>
      {/* Breadcrumb */}
      <div className="flex items-center gap-2 text-sm text-neutral-500 mb-4">
        <Link href="/tickets" className="hover:text-brand-primary">Tickets</Link>
        <span>/</span>
        <span className="text-neutral-900 font-medium">{ticket.title}</span>
      </div>

      {error && (
        <div className="bg-danger/10 border border-danger/20 rounded-lg p-4 text-sm text-danger mb-4">
          {error}
          <button onClick={() => setError(null)} className="ml-2 underline">Dismiss</button>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Main content */}
        <div className="lg:col-span-2 space-y-6">
          {/* Ticket info */}
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6">
            <div className="flex items-start justify-between mb-4">
              <h1 className="text-xl font-semibold text-neutral-900">{ticket.title}</h1>
              <div className="flex items-center gap-2">
                <StatusBadge status={ticket.status} />
                <PriorityBadge priority={ticket.priority} />
                <EscalationBadge level={ticket.escalationLevel} />
              </div>
            </div>
            <div className="prose prose-sm max-w-none text-neutral-700 whitespace-pre-wrap">
              {ticket.description}
            </div>
            {ticket.resolution && (
              <div className="mt-4 p-3 bg-success/5 border border-success/20 rounded-lg">
                <p className="text-xs font-medium text-success mb-1">Resolution</p>
                <p className="text-sm text-neutral-700">{ticket.resolution}</p>
              </div>
            )}
          </div>

          {/* Comments section */}
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6">
            <h2 className="text-lg font-semibold text-neutral-900 mb-4">
              Comments ({comments.length})
            </h2>
            <div className="space-y-4 mb-4">
              {comments.length === 0 && (
                <p className="text-sm text-neutral-500">No comments yet</p>
              )}
              {comments.map((c) => (
                <div key={c.commentId} className="border-l-2 border-neutral-200 pl-4">
                  <div className="flex items-center gap-2 mb-1">
                    <span className="text-sm font-medium text-neutral-900">{c.authorRef}</span>
                    <span className="text-xs text-neutral-400">
                      {new Date(c.createdAt).toLocaleString()}
                    </span>
                  </div>
                  <p className="text-sm text-neutral-700 whitespace-pre-wrap">{c.body}</p>
                </div>
              ))}
            </div>
            <form onSubmit={handleAddComment} className="flex gap-3">
              <input
                type="text"
                value={commentBody}
                onChange={(e) => setCommentBody(e.target.value)}
                placeholder="Add a comment..."
                className="flex-1 px-4 py-2 border border-neutral-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/30"
              />
              <button
                type="submit"
                disabled={!commentBody.trim() || submitting === "comment"}
                className="px-4 py-2 bg-brand-primary text-white rounded-lg text-sm font-medium hover:bg-brand-primary/90 disabled:opacity-50"
              >
                {submitting === "comment" ? "Adding..." : "Comment"}
              </button>
            </form>
          </div>

          {/* Messages / conversation thread */}
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6">
            <h2 className="text-lg font-semibold text-neutral-900 mb-4">
              Conversation ({messages.length})
            </h2>
            <div className="space-y-3 mb-4 max-h-96 overflow-y-auto">
              {messages.length === 0 && (
                <p className="text-sm text-neutral-500">No messages yet. Start a conversation with the requester.</p>
              )}
              {messages.map((m) => (
                <div
                  key={m.messageId}
                  className={`p-3 rounded-lg text-sm ${
                    m.senderType === "AGENT"
                      ? "bg-brand-primary/5 border border-brand-primary/10 ml-8"
                      : m.senderType === "SYSTEM"
                      ? "bg-neutral-50 border border-neutral-100 text-neutral-500 italic"
                      : "bg-white border border-neutral-200 mr-8"
                  }`}
                >
                  <div className="flex items-center gap-2 mb-1">
                    <span className="font-medium text-neutral-900 text-xs">{m.senderRef}</span>
                    <span className={`text-xs px-1.5 py-0.5 rounded-full ${
                      m.senderType === "AGENT" ? "bg-brand-primary/10 text-brand-primary" :
                      m.senderType === "SYSTEM" ? "bg-neutral-100 text-neutral-500" :
                      "bg-info/10 text-info"
                    }`}>
                      {m.senderType}
                    </span>
                    <span className="text-xs text-neutral-400">{new Date(m.createdAt).toLocaleString()}</span>
                  </div>
                  <p className="text-neutral-700 whitespace-pre-wrap">{m.body}</p>
                </div>
              ))}
            </div>
            <form onSubmit={handleSendMessage} className="flex gap-3">
              <input
                type="text"
                value={messageBody}
                onChange={(e) => setMessageBody(e.target.value)}
                placeholder="Type a message..."
                className="flex-1 px-4 py-2 border border-neutral-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/30"
              />
              <button
                type="submit"
                disabled={!messageBody.trim() || submitting === "message"}
                className="px-4 py-2 bg-brand-primary text-white rounded-lg text-sm font-medium hover:bg-brand-primary/90 disabled:opacity-50"
              >
                {submitting === "message" ? "Sending..." : "Send"}
              </button>
            </form>
          </div>
        </div>

        {/* Sidebar */}
        <div className="space-y-6">
          {/* Metadata */}
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-5">
            <h3 className="text-sm font-semibold text-neutral-900 mb-3">Details</h3>
            <dl className="space-y-2.5 text-sm">
              <div className="flex justify-between">
                <dt className="text-neutral-500">Category</dt>
                <dd className="text-neutral-900 font-medium">{ticket.category}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-neutral-500">Reporter</dt>
                <dd className="text-neutral-900">{ticket.reporterRef}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-neutral-500">Assignee</dt>
                <dd className="text-neutral-900">{ticket.assigneeRef ?? "Unassigned"}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-neutral-500">Facility</dt>
                <dd className="text-neutral-900">{ticket.facilityRef ?? "-"}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-neutral-500">Created</dt>
                <dd className="text-neutral-900 text-xs">{new Date(ticket.createdAt).toLocaleString()}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-neutral-500">Updated</dt>
                <dd className="text-neutral-900 text-xs">{new Date(ticket.updatedAt).toLocaleString()}</dd>
              </div>
              {ticket.resolvedAt && (
                <div className="flex justify-between">
                  <dt className="text-neutral-500">Resolved</dt>
                  <dd className="text-neutral-900 text-xs">{new Date(ticket.resolvedAt).toLocaleString()}</dd>
                </div>
              )}
              {ticket.correlationId && (
                <div className="flex justify-between">
                  <dt className="text-neutral-500">Correlation</dt>
                  <dd className="text-neutral-900 font-mono text-xs">{ticket.correlationId.slice(0, 12)}...</dd>
                </div>
              )}
              {ticket.requestId && (
                <div className="flex justify-between">
                  <dt className="text-neutral-500">Request ID</dt>
                  <dd className="text-neutral-900 font-mono text-xs">{ticket.requestId.slice(0, 12)}...</dd>
                </div>
              )}
              <div className="flex justify-between">
                <dt className="text-neutral-500">Version</dt>
                <dd className="text-neutral-900">v{ticket.version}</dd>
              </div>
            </dl>
          </div>

          {/* Status transition */}
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-5">
            <h3 className="text-sm font-semibold text-neutral-900 mb-3">Update Status</h3>
            {(ticket.status === "IN_PROGRESS" || ticket.status === "WAITING") && (
              <div className="mb-3">
                <input
                  type="text"
                  value={resolution}
                  onChange={(e) => setResolution(e.target.value)}
                  placeholder="Resolution notes (for resolve/close)"
                  className="w-full px-3 py-2 border border-neutral-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/30"
                />
              </div>
            )}
            <div className="flex flex-wrap gap-2">
              {allowedTransitions.map((s) => (
                <button
                  key={s}
                  onClick={() => handleStatusChange(s)}
                  disabled={submitting === "status"}
                  className={`px-3 py-1.5 rounded-lg text-xs font-medium border transition-colors ${
                    s === "RESOLVED" ? "border-success text-success hover:bg-success/10" :
                    s === "CLOSED" ? "border-neutral-300 text-neutral-600 hover:bg-neutral-100" :
                    s === "IN_PROGRESS" ? "border-warning text-warning hover:bg-warning/10" :
                    "border-neutral-200 text-neutral-600 hover:bg-neutral-50"
                  }`}
                >
                  {s.replace("_", " ")}
                </button>
              ))}
            </div>
          </div>

          {/* Assignment */}
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-5">
            <h3 className="text-sm font-semibold text-neutral-900 mb-3">Assign / Reassign</h3>
            <form onSubmit={handleAssign} className="flex gap-2">
              <input
                type="text"
                value={assigneeRef}
                onChange={(e) => setAssigneeRef(e.target.value)}
                placeholder="Agent ref..."
                className="flex-1 px-3 py-2 border border-neutral-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/30"
              />
              <button
                type="submit"
                disabled={!assigneeRef.trim() || submitting === "assign"}
                className="px-3 py-2 bg-brand-primary text-white rounded-lg text-xs font-medium hover:bg-brand-primary/90 disabled:opacity-50"
              >
                Assign
              </button>
            </form>
            {assignments.length > 0 && (
              <div className="mt-3 space-y-2">
                <p className="text-xs font-medium text-neutral-500">History</p>
                {assignments.slice(0, 5).map((a) => (
                  <div key={a.assignmentId} className="text-xs text-neutral-600">
                    <span className="font-medium">{a.assigneeRef}</span>
                    {" "}assigned by {a.assignedBy}{" "}
                    <span className="text-neutral-400">{new Date(a.assignedAt).toLocaleString()}</span>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Escalation */}
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-5">
            <h3 className="text-sm font-semibold text-neutral-900 mb-3">Escalation</h3>
            <p className="text-sm text-neutral-600 mb-2">
              Current level: <span className="font-semibold">{ticket.escalationLevel}</span>
              {ticket.escalatedBy && (
                <span className="text-xs text-neutral-400"> (by {ticket.escalatedBy})</span>
              )}
            </p>
            {ticket.slaBreachedAt && (
              <p className="text-xs text-danger mb-2">
                SLA breached: {new Date(ticket.slaBreachedAt).toLocaleString()}
              </p>
            )}
            <button
              onClick={handleEscalate}
              disabled={submitting === "escalate" || ticket.status === "RESOLVED" || ticket.status === "CLOSED"}
              className="w-full px-3 py-2 bg-warning/10 border border-warning/30 text-warning rounded-lg text-sm font-medium hover:bg-warning/20 disabled:opacity-50"
            >
              {submitting === "escalate" ? "Escalating..." : `Escalate to L${ticket.escalationLevel + 1}`}
            </button>
          </div>

          {/* Diagnostics linkage */}
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-5">
            <h3 className="text-sm font-semibold text-neutral-900 mb-3">Diagnostics</h3>
            {ticket.correlationId ? (
              <div className="space-y-2">
                <p className="text-xs text-neutral-500">Linked trace:</p>
                <code className="block text-xs font-mono bg-neutral-50 p-2 rounded break-all">
                  {ticket.correlationId}
                </code>
                <p className="text-xs text-neutral-500">
                  Use this correlation ID to search observability dashboards (Grafana, Loki, Tempo)
                  for related logs, traces, and metrics.
                </p>
              </div>
            ) : (
              <p className="text-sm text-neutral-500">No correlation ID linked</p>
            )}
            {ticket.requestId && (
              <div className="mt-3">
                <p className="text-xs text-neutral-500">Request ID:</p>
                <code className="block text-xs font-mono bg-neutral-50 p-2 rounded break-all mt-1">
                  {ticket.requestId}
                </code>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
