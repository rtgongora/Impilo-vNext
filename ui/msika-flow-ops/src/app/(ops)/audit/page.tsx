"use client";

import { useState, useEffect, useCallback } from "react";
import { opsApi, type AuditEvent, type PagedResponse } from "@/lib/opsApi";

const EVENT_STYLES: Record<string, string> = {
  ORDER_CREATED: "bg-blue-100 text-primary-hover",
  ORDER_VALIDATED: "bg-indigo-100 text-primary-hover",
  ORDER_PRICED: "bg-purple-100 text-warning-foreground",
  ORDER_CANCELLED: "bg-red-100 text-danger",
  ORDER_COMPLETED: "bg-emerald-100 text-primary-hover",
  REVIEW_APPROVED: "bg-green-100 text-green-700",
  REVIEW_REJECTED: "bg-red-100 text-danger",
  VENDOR_SUSPENDED: "bg-amber-100 text-warning-foreground",
  VENDOR_REINSTATED: "bg-teal-100 text-teal-700",
};

export default function AuditPage() {
  const [events, setEvents] = useState<AuditEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [eventTypeFilter, setEventTypeFilter] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const loadEvents = useCallback(async (p = 0) => {
    setLoading(true);
    setError(null);
    try {
      const data: PagedResponse<AuditEvent> = await opsApi.listAuditEvents({
        eventType: eventTypeFilter || undefined,
        page: p,
        size: 50,
      });
      setEvents(data.content);
      setTotalPages(data.totalPages);
      setTotalElements(data.totalElements);
      setPage(p);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load audit events");
    } finally {
      setLoading(false);
    }
  }, [eventTypeFilter]);

  useEffect(() => {
    loadEvents(0);
  }, [loadEvents]);

  const togglePayload = (id: string) => {
    setExpandedId(expandedId === id ? null : id);
  };

  const formatPayload = (payload: string): string => {
    try {
      return JSON.stringify(JSON.parse(payload), null, 2);
    } catch {
      return payload;
    }
  };

  return (
    <div className="p-8">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-semibold">Audit Log</h1>
          <p className="text-sm text-neutral-500 mt-1">
            Non-repudiation audit trail for all MSIKA Flow actions.
            {totalElements > 0 && (
              <span className="ml-2 text-neutral-400">({totalElements} events)</span>
            )}
          </p>
        </div>
        <button onClick={() => loadEvents(page)} className="btn-secondary">
          Refresh
        </button>
      </div>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-danger-soft border border-danger/28 text-sm text-red-800">
          {error}
        </div>
      )}

      <div className="flex gap-3 mb-6">
        <select
          value={eventTypeFilter}
          onChange={(e) => setEventTypeFilter(e.target.value)}
          className="input-field w-56"
        >
          <option value="">All Event Types</option>
          <option value="ORDER_CREATED">Order Created</option>
          <option value="ORDER_VALIDATED">Order Validated</option>
          <option value="ORDER_PRICED">Order Priced</option>
          <option value="ORDER_COMPLETED">Order Completed</option>
          <option value="ORDER_CANCELLED">Order Cancelled</option>
          <option value="REVIEW_APPROVED">Review Approved</option>
          <option value="REVIEW_REJECTED">Review Rejected</option>
          <option value="VENDOR_SUSPENDED">Vendor Suspended</option>
          <option value="VENDOR_REINSTATED">Vendor Reinstated</option>
        </select>
      </div>

      <div className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-neutral-200 bg-neutral-50">
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Event Type</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Entity</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Actor</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Tenant</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Timestamp</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Payload</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-neutral-100">
            {loading ? (
              <tr>
                <td colSpan={6} className="px-4 py-12 text-center text-neutral-500">
                  <div className="animate-pulse">Loading audit events...</div>
                </td>
              </tr>
            ) : events.length === 0 ? (
              <tr>
                <td colSpan={6} className="px-4 py-12 text-center text-neutral-500">
                  No audit events found.
                </td>
              </tr>
            ) : (
              events.map((event) => (
                <tr key={event.id} className="hover:bg-neutral-50">
                  <td className="px-4 py-3">
                    <span className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${EVENT_STYLES[event.eventType] ?? "bg-neutral-100 text-neutral-700"}`}>
                      {event.eventType}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <span className="text-xs text-neutral-500">{event.entityType}</span>
                    <span className="font-mono text-xs ml-1">{event.entityId.substring(0, 12)}...</span>
                  </td>
                  <td className="px-4 py-3 font-mono text-xs">{event.actorId}</td>
                  <td className="px-4 py-3 font-mono text-xs">{event.tenantId}</td>
                  <td className="px-4 py-3 text-xs text-neutral-500">
                    {new Date(event.createdAt).toLocaleString()}
                  </td>
                  <td className="px-4 py-3">
                    <button
                      onClick={() => togglePayload(event.id)}
                      className="text-xs text-brand-primary hover:underline font-medium"
                    >
                      {expandedId === event.id ? "Hide" : "View"}
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {expandedId && (
        <div className="mt-4 card p-4">
          <h3 className="text-sm font-semibold mb-2">Event Payload</h3>
          <pre className="text-xs bg-neutral-50 rounded-lg p-4 overflow-x-auto max-h-64">
            {formatPayload(events.find((e) => e.id === expandedId)?.payload ?? "{}")}
          </pre>
        </div>
      )}

      {totalPages > 1 && (
        <div className="flex items-center justify-center gap-2 mt-4">
          <button
            onClick={() => loadEvents(page - 1)}
            disabled={page === 0 || loading}
            className="btn-secondary text-xs px-3 py-1 disabled:opacity-50"
          >
            Previous
          </button>
          <span className="text-sm text-neutral-500">
            Page {page + 1} of {totalPages}
          </span>
          <button
            onClick={() => loadEvents(page + 1)}
            disabled={page >= totalPages - 1 || loading}
            className="btn-secondary text-xs px-3 py-1 disabled:opacity-50"
          >
            Next
          </button>
        </div>
      )}
    </div>
  );
}
