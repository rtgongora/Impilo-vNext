"use client";

import React, { useCallback, useEffect, useRef, useState } from "react";
import { vitoApi, type AuditEvent } from "@/lib/vitoApi";

/**
 * Audit Event Viewer — displays events from the VITO event_outbox table.
 * Supports auto-refresh and expandable payload inspection for debugging
 * the outbox-pattern event pipeline.
 */
export default function AuditEventViewerPage() {
  const [events, setEvents] = useState<AuditEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [autoRefresh, setAutoRefresh] = useState(false);
  const [expandedRow, setExpandedRow] = useState<number | null>(null);
  const [aggregateTypeFilter, setAggregateTypeFilter] = useState<string>("ALL");
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const loadEvents = useCallback(async () => {
    setLoading(true);
    try {
      const data = await vitoApi.getAuditEvents(0, 50);
      setEvents(data.items ?? []);
    } catch {
      setEvents([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadEvents();
  }, [loadEvents]);

  // Auto-refresh toggle
  useEffect(() => {
    if (autoRefresh) {
      intervalRef.current = setInterval(() => {
        loadEvents();
      }, 5000);
    } else {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
    }
    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
      }
    };
  }, [autoRefresh, loadEvents]);

  // Compute unique aggregate types for filter dropdown
  const aggregateTypes = Array.from(new Set(events.map((e) => e.aggregateType))).sort();

  const filteredEvents =
    aggregateTypeFilter === "ALL"
      ? events
      : events.filter((e) => e.aggregateType === aggregateTypeFilter);

  const formatPayload = (payload: string): string => {
    try {
      return JSON.stringify(JSON.parse(payload), null, 2);
    } catch {
      return payload;
    }
  };

  return (
    <div className="max-w-7xl mx-auto px-6 py-8">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-neutral-900">
          Audit Event Viewer
        </h1>
        <p className="text-sm text-neutral-500 mt-1">
          Inspect events from the VITO event_outbox for debugging and audit trails
        </p>
      </div>

      {/* Controls bar */}
      <div className="flex items-center gap-4 mb-4">
        {/* Aggregate type filter */}
        <div className="flex items-center gap-2">
          <label htmlFor="aggregate-filter" className="text-sm text-neutral-500">
            Aggregate Type:
          </label>
          <select
            id="aggregate-filter"
            value={aggregateTypeFilter}
            onChange={(e) => setAggregateTypeFilter(e.target.value)}
            className="px-3 py-2 text-sm border border-neutral-200 rounded-[8px] bg-card focus:outline-none focus:ring-2 focus:ring-brand-primary/20 focus:border-brand-primary"
          >
            <option value="ALL">All Types</option>
            {aggregateTypes.map((type) => (
              <option key={type} value={type}>
                {type}
              </option>
            ))}
          </select>
        </div>

        {/* Auto-refresh toggle */}
        <div className="flex items-center gap-2 ml-auto">
          <span className="text-sm text-neutral-500">Auto-refresh</span>
          <button
            onClick={() => setAutoRefresh(!autoRefresh)}
            className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${
              autoRefresh ? "bg-brand-primary" : "bg-neutral-200"
            }`}
          >
            <span
              className={`inline-block h-4 w-4 rounded-full bg-card transition-transform ${
                autoRefresh ? "translate-x-6" : "translate-x-1"
              }`}
            />
          </button>
          {autoRefresh && (
            <span className="text-xs text-neutral-400">every 5s</span>
          )}
        </div>

        {/* Manual refresh */}
        <button
          onClick={loadEvents}
          disabled={loading}
          className="px-4 py-2 text-sm font-medium text-neutral-700 bg-neutral-100 rounded-[8px] hover:bg-neutral-200 disabled:opacity-50"
        >
          Refresh
        </button>
      </div>

      {/* Events table */}
      <div className="bg-card rounded-[12px] shadow-subtle border border-neutral-100 overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-neutral-100 bg-neutral-50">
              <th className="px-4 py-3 text-left font-medium text-neutral-500">ID</th>
              <th className="px-4 py-3 text-left font-medium text-neutral-500">Aggregate Type</th>
              <th className="px-4 py-3 text-left font-medium text-neutral-500">Aggregate ID</th>
              <th className="px-4 py-3 text-left font-medium text-neutral-500">Event Type</th>
              <th className="px-4 py-3 text-left font-medium text-neutral-500">Published</th>
              <th className="px-4 py-3 text-left font-medium text-neutral-500">Created At</th>
            </tr>
          </thead>
          <tbody>
            {loading && events.length === 0 ? (
              <tr>
                <td colSpan={6} className="px-4 py-8 text-center text-neutral-500">Loading...</td>
              </tr>
            ) : filteredEvents.length === 0 ? (
              <tr>
                <td colSpan={6} className="px-4 py-8 text-center text-neutral-500">
                  {aggregateTypeFilter === "ALL"
                    ? "No audit events found"
                    : `No events for aggregate type "${aggregateTypeFilter}"`}
                </td>
              </tr>
            ) : (
              filteredEvents.map((event) => (
                <React.Fragment key={event.id}>
                  <tr
                    onClick={() => setExpandedRow(expandedRow === event.id ? null : event.id)}
                    className="border-b border-neutral-50 hover:bg-neutral-50 cursor-pointer"
                  >
                    <td className="px-4 py-3 font-mono text-xs">{event.id}</td>
                    <td className="px-4 py-3">
                      <span className="inline-flex px-2 py-0.5 rounded-full text-xs font-medium bg-info/10 text-info">
                        {event.aggregateType}
                      </span>
                    </td>
                    <td className="px-4 py-3 font-mono text-xs">{event.aggregateId}</td>
                    <td className="px-4 py-3 text-neutral-900">{event.eventType}</td>
                    <td className="px-4 py-3">
                      {event.publishedAt ? (
                        <span className="inline-flex px-2 py-0.5 rounded-full text-xs font-medium bg-success/10 text-success">
                          Yes
                        </span>
                      ) : (
                        <span className="inline-flex px-2 py-0.5 rounded-full text-xs font-medium bg-warning/10 text-warning">
                          Pending
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-neutral-500">
                      {new Date(event.createdAt).toLocaleString()}
                    </td>
                  </tr>
                  {expandedRow === event.id && (
                    <tr className="bg-neutral-50">
                      <td colSpan={6} className="px-4 py-3">
                        <div className="text-xs">
                          <div className="flex items-center justify-between mb-2">
                            <span className="text-neutral-500 font-medium">Event Payload</span>
                            {event.publishedAt && (
                              <span className="text-neutral-400">
                                Published: {new Date(event.publishedAt).toLocaleString()}
                              </span>
                            )}
                          </div>
                          <pre className="font-mono bg-card p-3 rounded-[8px] border border-neutral-200 overflow-x-auto max-h-64 overflow-y-auto whitespace-pre-wrap">
                            {formatPayload(event.payload)}
                          </pre>
                        </div>
                      </td>
                    </tr>
                  )}
                </React.Fragment>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
