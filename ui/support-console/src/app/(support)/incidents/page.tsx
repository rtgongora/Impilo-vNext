"use client";

import React, { useEffect, useState, useCallback } from "react";
import Link from "next/link";
import { listTickets } from "@/lib/supportApi";
import { StatusBadge, PriorityBadge, EscalationBadge } from "@/components/StatusBadge";
import type { Ticket } from "@/types/support";

export default function IncidentsPage() {
  const [incidents, setIncidents] = useState<Ticket[]>([]);
  const [escalated, setEscalated] = useState<Ticket[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<"incidents" | "escalated">("incidents");

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [incidentData, allTickets] = await Promise.all([
        listTickets({ category: "INCIDENT", limit: 50 }),
        listTickets({ limit: 100 }),
      ]);
      setIncidents(incidentData.items);
      setEscalated(allTickets.items.filter((t) => t.escalationLevel > 0));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load incidents");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const activeList = activeTab === "incidents" ? incidents : escalated;

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-neutral-900">Incident & Escalation Queue</h1>
        <p className="text-sm text-neutral-500 mt-1">
          Active incidents, escalated tickets, and service disruption tracking
        </p>
      </div>

      {/* Runbook links */}
      <div className="bg-info/5 border border-info/20 rounded-lg p-4 mb-6">
        <p className="text-sm font-medium text-info mb-2">Runbook References</p>
        <div className="flex flex-wrap gap-3 text-xs">
          <span className="px-2 py-1 bg-card rounded border border-info/20 text-neutral-700">
            Ring-0 Failover: docs/dr/runbooks/ring0-failover.md
          </span>
          <span className="px-2 py-1 bg-card rounded border border-info/20 text-neutral-700">
            DB Restore: docs/dr/runbooks/db-restore.md
          </span>
          <span className="px-2 py-1 bg-card rounded border border-info/20 text-neutral-700">
            Kafka Recovery: docs/dr/runbooks/kafka-recovery.md
          </span>
          <span className="px-2 py-1 bg-card rounded border border-info/20 text-neutral-700">
            Partial Recovery: docs/dr/runbooks/partial-platform-recovery.md
          </span>
        </div>
      </div>

      {error && (
        <div className="bg-danger/10 border border-danger/20 rounded-lg p-4 text-sm text-danger mb-4">
          {error}
        </div>
      )}

      {/* Tabs */}
      <div className="flex gap-1 mb-4">
        <button
          onClick={() => setActiveTab("incidents")}
          className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
            activeTab === "incidents"
              ? "bg-danger/10 text-danger"
              : "text-neutral-600 hover:bg-neutral-100"
          }`}
        >
          Incidents ({incidents.length})
        </button>
        <button
          onClick={() => setActiveTab("escalated")}
          className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
            activeTab === "escalated"
              ? "bg-warning/10 text-warning"
              : "text-neutral-600 hover:bg-neutral-100"
          }`}
        >
          Escalated ({escalated.length})
        </button>
      </div>

      {/* Summary cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
        <div className="bg-card rounded-[12px] shadow-subtle border border-neutral-100 p-4">
          <p className="text-xs text-neutral-500 uppercase tracking-wide">Active Incidents</p>
          <p className="text-2xl font-semibold text-danger mt-1">
            {incidents.filter((i) => i.status === "OPEN" || i.status === "IN_PROGRESS").length}
          </p>
        </div>
        <div className="bg-card rounded-[12px] shadow-subtle border border-neutral-100 p-4">
          <p className="text-xs text-neutral-500 uppercase tracking-wide">Critical</p>
          <p className="text-2xl font-semibold text-danger mt-1">
            {incidents.filter((i) => i.priority === "CRITICAL").length}
          </p>
        </div>
        <div className="bg-card rounded-[12px] shadow-subtle border border-neutral-100 p-4">
          <p className="text-xs text-neutral-500 uppercase tracking-wide">Escalated L2+</p>
          <p className="text-2xl font-semibold text-warning mt-1">
            {escalated.filter((t) => t.escalationLevel >= 2).length}
          </p>
        </div>
        <div className="bg-card rounded-[12px] shadow-subtle border border-neutral-100 p-4">
          <p className="text-xs text-neutral-500 uppercase tracking-wide">Resolved Today</p>
          <p className="text-2xl font-semibold text-success mt-1">
            {incidents.filter((i) => i.status === "RESOLVED" && i.resolvedAt &&
              new Date(i.resolvedAt).toDateString() === new Date().toDateString()).length}
          </p>
        </div>
      </div>

      {/* Ticket list */}
      <div className="bg-card rounded-[12px] shadow-subtle border border-neutral-100">
        {loading ? (
          <div className="p-8 text-center text-sm text-neutral-500">Loading...</div>
        ) : activeList.length === 0 ? (
          <div className="p-8 text-center text-sm text-neutral-500">
            {activeTab === "incidents" ? "No incidents" : "No escalated tickets"}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-neutral-100">
                  <th className="text-left py-3 px-4 text-neutral-500 font-medium">Title</th>
                  <th className="text-left py-3 px-4 text-neutral-500 font-medium">Status</th>
                  <th className="text-left py-3 px-4 text-neutral-500 font-medium">Priority</th>
                  <th className="text-left py-3 px-4 text-neutral-500 font-medium">Escalation</th>
                  <th className="text-left py-3 px-4 text-neutral-500 font-medium">Assignee</th>
                  <th className="text-left py-3 px-4 text-neutral-500 font-medium">Facility</th>
                  <th className="text-left py-3 px-4 text-neutral-500 font-medium">Created</th>
                </tr>
              </thead>
              <tbody>
                {activeList.map((t) => (
                  <tr key={t.ticketId} className="border-b border-neutral-50 hover:bg-neutral-50/50">
                    <td className="py-3 px-4">
                      <Link href={`/tickets/${t.ticketId}`} className="text-brand-primary font-medium hover:underline">
                        {t.title}
                      </Link>
                      {t.correlationId && (
                        <p className="text-xs text-neutral-400 font-mono mt-0.5">
                          corr: {t.correlationId.slice(0, 8)}...
                        </p>
                      )}
                    </td>
                    <td className="py-3 px-4"><StatusBadge status={t.status} /></td>
                    <td className="py-3 px-4"><PriorityBadge priority={t.priority} /></td>
                    <td className="py-3 px-4"><EscalationBadge level={t.escalationLevel} /></td>
                    <td className="py-3 px-4 text-neutral-600">{t.assigneeRef ?? "Unassigned"}</td>
                    <td className="py-3 px-4 text-neutral-600 text-xs">{t.facilityRef ?? "-"}</td>
                    <td className="py-3 px-4 text-neutral-500 text-xs">
                      {new Date(t.createdAt).toLocaleString()}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
