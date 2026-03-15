"use client";

import React, { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useSearchParams, useRouter } from "next/navigation";
import { listTickets } from "@/lib/supportApi";
import { StatusBadge, PriorityBadge, EscalationBadge } from "@/components/StatusBadge";
import type { Ticket, TicketFilters, TicketStatus, TicketPriority, TicketCategory } from "@/types/support";

const STATUS_OPTIONS: TicketStatus[] = ["OPEN", "IN_PROGRESS", "WAITING", "RESOLVED", "CLOSED"];
const PRIORITY_OPTIONS: TicketPriority[] = ["LOW", "MEDIUM", "HIGH", "CRITICAL"];
const CATEGORY_OPTIONS: TicketCategory[] = ["GENERAL", "INCIDENT", "BUG", "FEATURE_REQUEST", "ACCESS", "CONFIGURATION", "DATA"];

export default function TicketsListPage() {
  const searchParams = useSearchParams();
  const router = useRouter();

  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [statusFilter, setStatusFilter] = useState<TicketStatus | "">(
    (searchParams.get("status") as TicketStatus) || ""
  );
  const [priorityFilter, setPriorityFilter] = useState<TicketPriority | "">(
    (searchParams.get("priority") as TicketPriority) || ""
  );
  const [categoryFilter, setCategoryFilter] = useState<TicketCategory | "">(
    (searchParams.get("category") as TicketCategory) || ""
  );
  const [page, setPage] = useState(0);

  const fetchTickets = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const filters: TicketFilters = { cursor: page, limit: 20 };
      if (statusFilter) filters.status = statusFilter;
      if (priorityFilter) filters.priority = priorityFilter;
      if (categoryFilter) filters.category = categoryFilter;
      const data = await listTickets(filters);
      setTickets(data.items);
      setTotalElements(data.total_elements);
      setHasMore(data.has_more);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load tickets");
    } finally {
      setLoading(false);
    }
  }, [statusFilter, priorityFilter, categoryFilter, page]);

  useEffect(() => { fetchTickets(); }, [fetchTickets]);

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-semibold text-neutral-900">Tickets</h1>
          <p className="text-sm text-neutral-500 mt-1">
            {totalElements} total ticket{totalElements !== 1 ? "s" : ""}
          </p>
        </div>
        <Link
          href="/tickets/create"
          className="px-4 py-2 bg-brand-primary text-white rounded-lg text-sm font-medium hover:bg-brand-primary/90"
        >
          Create Ticket
        </Link>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap gap-3 mb-6">
        <select
          value={statusFilter}
          onChange={(e) => { setStatusFilter(e.target.value as TicketStatus | ""); setPage(0); }}
          className="px-3 py-2 border border-neutral-200 rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-brand-primary/30"
        >
          <option value="">All Statuses</option>
          {STATUS_OPTIONS.map((s) => (
            <option key={s} value={s}>{s.replace("_", " ")}</option>
          ))}
        </select>
        <select
          value={priorityFilter}
          onChange={(e) => { setPriorityFilter(e.target.value as TicketPriority | ""); setPage(0); }}
          className="px-3 py-2 border border-neutral-200 rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-brand-primary/30"
        >
          <option value="">All Priorities</option>
          {PRIORITY_OPTIONS.map((p) => (
            <option key={p} value={p}>{p}</option>
          ))}
        </select>
        <select
          value={categoryFilter}
          onChange={(e) => { setCategoryFilter(e.target.value as TicketCategory | ""); setPage(0); }}
          className="px-3 py-2 border border-neutral-200 rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-brand-primary/30"
        >
          <option value="">All Categories</option>
          {CATEGORY_OPTIONS.map((c) => (
            <option key={c} value={c}>{c.replace("_", " ")}</option>
          ))}
        </select>
        {(statusFilter || priorityFilter || categoryFilter) && (
          <button
            onClick={() => { setStatusFilter(""); setPriorityFilter(""); setCategoryFilter(""); setPage(0); }}
            className="px-3 py-2 text-sm text-neutral-600 hover:text-neutral-900"
          >
            Clear filters
          </button>
        )}
      </div>

      {error && (
        <div className="bg-danger/10 border border-danger/20 rounded-lg p-4 text-sm text-danger mb-4">
          {error}
        </div>
      )}

      {/* Table */}
      <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100">
        {loading ? (
          <div className="p-8 text-center text-sm text-neutral-500">Loading tickets...</div>
        ) : tickets.length === 0 ? (
          <div className="p-8 text-center text-sm text-neutral-500">No tickets match your filters</div>
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-neutral-100">
                    <th className="text-left py-3 px-4 text-neutral-500 font-medium">Title</th>
                    <th className="text-left py-3 px-4 text-neutral-500 font-medium">Status</th>
                    <th className="text-left py-3 px-4 text-neutral-500 font-medium">Priority</th>
                    <th className="text-left py-3 px-4 text-neutral-500 font-medium">Category</th>
                    <th className="text-left py-3 px-4 text-neutral-500 font-medium">Assignee</th>
                    <th className="text-left py-3 px-4 text-neutral-500 font-medium">Escalation</th>
                    <th className="text-left py-3 px-4 text-neutral-500 font-medium">Reporter</th>
                    <th className="text-left py-3 px-4 text-neutral-500 font-medium">Updated</th>
                  </tr>
                </thead>
                <tbody>
                  {tickets.map((t) => (
                    <tr key={t.ticketId} className="border-b border-neutral-50 hover:bg-neutral-50/50">
                      <td className="py-3 px-4">
                        <Link href={`/tickets/${t.ticketId}`} className="text-brand-primary font-medium hover:underline">
                          {t.title}
                        </Link>
                        {t.correlationId && (
                          <p className="text-xs text-neutral-400 font-mono mt-0.5">{t.correlationId.slice(0, 8)}...</p>
                        )}
                      </td>
                      <td className="py-3 px-4"><StatusBadge status={t.status} /></td>
                      <td className="py-3 px-4"><PriorityBadge priority={t.priority} /></td>
                      <td className="py-3 px-4 text-neutral-600">{t.category}</td>
                      <td className="py-3 px-4 text-neutral-600">{t.assigneeRef ?? "Unassigned"}</td>
                      <td className="py-3 px-4"><EscalationBadge level={t.escalationLevel} /></td>
                      <td className="py-3 px-4 text-neutral-600 text-xs">{t.reporterRef}</td>
                      <td className="py-3 px-4 text-neutral-500 text-xs">
                        {new Date(t.updatedAt).toLocaleDateString()}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Pagination */}
            <div className="flex items-center justify-between px-4 py-3 border-t border-neutral-100">
              <button
                onClick={() => setPage(Math.max(0, page - 1))}
                disabled={page === 0}
                className="px-3 py-1.5 text-sm rounded-lg border border-neutral-200 disabled:opacity-40"
              >
                Previous
              </button>
              <span className="text-sm text-neutral-500">Page {page + 1}</span>
              <button
                onClick={() => hasMore && setPage(page + 1)}
                disabled={!hasMore}
                className="px-3 py-1.5 text-sm rounded-lg border border-neutral-200 disabled:opacity-40"
              >
                Next
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
