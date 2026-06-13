"use client";

/**
 * Audit Trail — Audit log table with timestamps, actors, actions, and resources.
 * Route: /admin/audit | pageTitle: "Audit Trail"
 */

import { useState } from "react";
import Link from "next/link";
import { ArrowLeft, Loader2, FileSearch, ChevronLeft, ChevronRight } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuditLog } from "@/hooks/queries/useAudit";

export default function AuditTrailPage() {
  const [page, setPage] = useState(0);
  const [draftAggregateType, setDraftAggregateType] = useState("");
  const [draftAggregateId, setDraftAggregateId] = useState("");
  const [aggregateType, setAggregateType] = useState("");
  const [aggregateId, setAggregateId] = useState("");
  const { data, isLoading } = useAuditLog(page, { aggregateType, aggregateId });

  const entries = data?.data ?? [];
  const totalPages = data?.meta?.page?.total_pages ?? 1;

  return (
    <AppLayout>
      <PageShell
        title="Audit Trail"
        subtitle="Complete audit log of system actions and events"
      >
        <div className="mb-4">
          <Link
            href="/admin"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to administration
          </Link>
        </div>

        <div className="mb-4 rounded-lg border border-border bg-card p-3">
          <p className="text-xs text-muted-foreground">
            Optional aggregate filters (Phase 8D): scope audit rows by aggregate type/id.
          </p>
          <div className="mt-2 flex flex-wrap items-end gap-2">
            <label className="text-xs text-muted-foreground">
              Aggregate type
              <input
                value={draftAggregateType}
                onChange={(event) => setDraftAggregateType(event.target.value)}
                className="mt-1 block rounded-lg border border-border px-2 py-1.5 text-sm"
                placeholder="PAYMENT_INTENT"
              />
            </label>
            <label className="text-xs text-muted-foreground">
              Aggregate id
              <input
                value={draftAggregateId}
                onChange={(event) => setDraftAggregateId(event.target.value)}
                className="mt-1 block rounded-lg border border-border px-2 py-1.5 text-sm"
                placeholder="PI-123"
              />
            </label>
            <button
              type="button"
              onClick={() => {
                setPage(0);
                setAggregateType(draftAggregateType.trim());
                setAggregateId(draftAggregateId.trim());
              }}
              className="rounded-lg border border-border px-3 py-1.5 text-sm hover:bg-background"
            >
              Apply
            </button>
          </div>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading audit entries...</span>
          </div>
        ) : entries.length === 0 ? (
          <div className="bg-card rounded-lg border border-border p-12 text-center">
            <FileSearch className="w-10 h-10 text-muted-foreground mx-auto mb-3" />
            <p className="text-muted-foreground text-sm">No audit entries found</p>
          </div>
        ) : (
          <>
            <div className="bg-card rounded-lg border border-border overflow-hidden">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b bg-background">
                    <th className="text-left px-4 py-3 font-medium text-muted-foreground">
                      Timestamp
                    </th>
                    <th className="text-left px-4 py-3 font-medium text-muted-foreground">Actor</th>
                    <th className="text-left px-4 py-3 font-medium text-muted-foreground">Action</th>
                    <th className="text-left px-4 py-3 font-medium text-muted-foreground">
                      Resource Type
                    </th>
                    <th className="text-left px-4 py-3 font-medium text-muted-foreground">
                      Resource ID
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {entries.map((entry) => (
                    <tr key={entry.id} className="hover:bg-background transition-colors">
                      <td className="px-4 py-3 text-muted-foreground whitespace-nowrap">
                        {new Date(entry.attributes.timestamp).toLocaleString()}
                      </td>
                      <td className="px-4 py-3">
                        <div>
                          <span className="text-foreground">{entry.attributes.actorId}</span>
                          <span className="ml-1 text-xs text-muted-foreground">
                            ({entry.attributes.actorType})
                          </span>
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        <span className="inline-block px-2 py-0.5 text-xs rounded-full bg-primary-soft text-primary">
                          {entry.attributes.action}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">
                        {entry.attributes.resourceType}
                      </td>
                      <td className="px-4 py-3">
                        <Link
                          href={`/admin/audit/${entry.id}`}
                          className="text-primary hover:text-primary-hover font-mono text-xs"
                        >
                          {entry.attributes.resourceId}
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Pagination */}
            <div className="mt-4 flex items-center justify-between">
              <p className="text-sm text-muted-foreground">
                Page {page + 1} of {totalPages}
              </p>
              <div className="flex gap-2">
                <button
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  disabled={page === 0}
                  className="inline-flex items-center gap-1 px-3 py-1.5 text-sm border border-border rounded-lg hover:bg-background disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                >
                  <ChevronLeft className="w-4 h-4" />
                  Previous
                </button>
                <button
                  onClick={() => setPage((p) => p + 1)}
                  disabled={page + 1 >= totalPages}
                  className="inline-flex items-center gap-1 px-3 py-1.5 text-sm border border-border rounded-lg hover:bg-background disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                >
                  Next
                  <ChevronRight className="w-4 h-4" />
                </button>
              </div>
            </div>
          </>
        )}
      </PageShell>
    </AppLayout>
  );
}
