"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import { getDashboardStats, listTickets } from "@/lib/supportApi";
import type { DashboardStats, Ticket } from "@/types/support";

const STAT_CARDS: { key: keyof DashboardStats; label: string; color: string }[] = [
  { key: "openCount", label: "Open", color: "bg-info" },
  { key: "inProgressCount", label: "In Progress", color: "bg-warning" },
  { key: "resolvedCount", label: "Resolved", color: "bg-success" },
  { key: "closedCount", label: "Closed", color: "bg-neutral-500" },
  { key: "criticalCount", label: "Critical", color: "bg-danger" },
  { key: "highCount", label: "High Priority", color: "bg-brand-accent-red" },
  { key: "escalatedCount", label: "Escalated", color: "bg-brand-accent-yellow" },
];

function formatHours(h: number): string {
  if (h < 1) return `${Math.round(h * 60)}m`;
  if (h < 24) return `${h.toFixed(1)}h`;
  return `${(h / 24).toFixed(1)}d`;
}

export default function DashboardPage() {
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [recentTickets, setRecentTickets] = useState<Ticket[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function load() {
      try {
        const [statsData, ticketsData] = await Promise.all([
          getDashboardStats(),
          listTickets({ limit: 10 }),
        ]);
        setStats(statsData);
        setRecentTickets(ticketsData.items);
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to load dashboard");
      } finally {
        setLoading(false);
      }
    }
    load();
  }, []);

  if (loading) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="text-sm text-neutral-500">Loading dashboard...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-danger/10 border border-danger/20 rounded-lg p-4 text-sm text-danger">
        {error}
      </div>
    );
  }

  return (
    <div>
      <div className="mb-8">
        <h1 className="text-2xl font-semibold text-neutral-900">Support Dashboard</h1>
        <p className="text-sm text-neutral-500 mt-1">
          Helpdesk overview: ticket volumes, SLA status, and queue health
        </p>
      </div>

      {/* Stats grid */}
      {stats && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">
          {STAT_CARDS.map(({ key, label, color }) => (
            <div
              key={key}
              className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-5"
            >
              <div className="flex items-center gap-2 mb-2">
                <div className={`w-2.5 h-2.5 rounded-full ${color}`} />
                <span className="text-xs font-medium text-neutral-500 uppercase tracking-wide">
                  {label}
                </span>
              </div>
              <p className="text-2xl font-semibold text-neutral-900">{stats[key]}</p>
            </div>
          ))}
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-5">
            <div className="flex items-center gap-2 mb-2">
              <div className="w-2.5 h-2.5 rounded-full bg-brand-primary" />
              <span className="text-xs font-medium text-neutral-500 uppercase tracking-wide">
                Avg Resolution
              </span>
            </div>
            <p className="text-2xl font-semibold text-neutral-900">
              {formatHours(stats.avgResolutionHours)}
            </p>
          </div>
        </div>
      )}

      {/* Quick actions */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
        <Link href="/tickets?status=OPEN" className="block">
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6 hover:border-brand-primary/30 transition-colors">
            <h3 className="text-sm font-medium text-neutral-500">Queue</h3>
            <p className="text-xl font-semibold text-neutral-900 mt-2">Open Tickets</p>
            <p className="text-xs text-neutral-500 mt-1">View and triage unassigned tickets</p>
          </div>
        </Link>
        <Link href="/incidents" className="block">
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6 hover:border-danger/30 transition-colors">
            <h3 className="text-sm font-medium text-neutral-500">Ops</h3>
            <p className="text-xl font-semibold text-neutral-900 mt-2">Incidents</p>
            <p className="text-xs text-neutral-500 mt-1">Active incidents and escalation queue</p>
          </div>
        </Link>
        <Link href="/knowledge" className="block">
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6 hover:border-info/30 transition-colors">
            <h3 className="text-sm font-medium text-neutral-500">Reference</h3>
            <p className="text-xl font-semibold text-neutral-900 mt-2">Knowledge Base</p>
            <p className="text-xs text-neutral-500 mt-1">Articles, runbooks, and troubleshooting guides</p>
          </div>
        </Link>
      </div>

      {/* Recent tickets table */}
      <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-semibold text-neutral-900">Recent Tickets</h2>
          <Link
            href="/tickets"
            className="text-sm text-brand-primary font-medium hover:underline"
          >
            View all
          </Link>
        </div>

        {recentTickets.length === 0 ? (
          <p className="text-sm text-neutral-500 py-8 text-center">No tickets yet</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-neutral-100">
                  <th className="text-left py-3 px-2 text-neutral-500 font-medium">Title</th>
                  <th className="text-left py-3 px-2 text-neutral-500 font-medium">Status</th>
                  <th className="text-left py-3 px-2 text-neutral-500 font-medium">Priority</th>
                  <th className="text-left py-3 px-2 text-neutral-500 font-medium">Category</th>
                  <th className="text-left py-3 px-2 text-neutral-500 font-medium">Assignee</th>
                  <th className="text-left py-3 px-2 text-neutral-500 font-medium">Updated</th>
                </tr>
              </thead>
              <tbody>
                {recentTickets.map((t) => (
                  <tr key={t.ticketId} className="border-b border-neutral-50 hover:bg-neutral-50/50">
                    <td className="py-3 px-2">
                      <Link href={`/tickets/${t.ticketId}`} className="text-brand-primary font-medium hover:underline">
                        {t.title}
                      </Link>
                    </td>
                    <td className="py-3 px-2">
                      <StatusBadge status={t.status} />
                    </td>
                    <td className="py-3 px-2">
                      <PriorityBadge priority={t.priority} />
                    </td>
                    <td className="py-3 px-2 text-neutral-600">{t.category}</td>
                    <td className="py-3 px-2 text-neutral-600">{t.assigneeRef ?? "Unassigned"}</td>
                    <td className="py-3 px-2 text-neutral-500 text-xs">
                      {new Date(t.updatedAt).toLocaleDateString()}
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

function StatusBadge({ status }: { status: string }) {
  const styles: Record<string, string> = {
    OPEN: "bg-info/10 text-info",
    IN_PROGRESS: "bg-warning/10 text-warning",
    WAITING: "bg-neutral-100 text-neutral-600",
    RESOLVED: "bg-success/10 text-success",
    CLOSED: "bg-neutral-100 text-neutral-500",
  };
  return (
    <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${styles[status] ?? styles.OPEN}`}>
      {status.replace("_", " ")}
    </span>
  );
}

function PriorityBadge({ priority }: { priority: string }) {
  const styles: Record<string, string> = {
    LOW: "bg-neutral-100 text-neutral-600",
    MEDIUM: "bg-info/10 text-info",
    HIGH: "bg-warning/10 text-warning",
    CRITICAL: "bg-danger/10 text-danger",
  };
  return (
    <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${styles[priority] ?? styles.MEDIUM}`}>
      {priority}
    </span>
  );
}
