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
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to administration
          </Link>
        </div>

        <div className="mb-4 rounded-lg border border-gray-200 bg-white p-3">
          <p className="text-xs text-gray-500">
            Optional aggregate filters (Phase 8D): scope audit rows by aggregate type/id.
          </p>
          <div className="mt-2 flex flex-wrap items-end gap-2">
            <label className="text-xs text-gray-600">
              Aggregate type
              <input
                value={draftAggregateType}
                onChange={(event) => setDraftAggregateType(event.target.value)}
                className="mt-1 block rounded-lg border border-gray-300 px-2 py-1.5 text-sm"
                placeholder="PAYMENT_INTENT"
              />
            </label>
            <label className="text-xs text-gray-600">
              Aggregate id
              <input
                value={draftAggregateId}
                onChange={(event) => setDraftAggregateId(event.target.value)}
                className="mt-1 block rounded-lg border border-gray-300 px-2 py-1.5 text-sm"
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
              className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm hover:bg-gray-50"
            >
              Apply
            </button>
          </div>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading audit entries...</span>
          </div>
        ) : entries.length === 0 ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <FileSearch className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">No audit entries found</p>
          </div>
        ) : (
          <>
            <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b bg-gray-50">
                    <th className="text-left px-4 py-3 font-medium text-gray-600">
                      Timestamp
                    </th>
                    <th className="text-left px-4 py-3 font-medium text-gray-600">Actor</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-600">Action</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-600">
                      Resource Type
                    </th>
                    <th className="text-left px-4 py-3 font-medium text-gray-600">
                      Resource ID
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {entries.map((entry) => (
                    <tr key={entry.id} className="hover:bg-gray-50 transition-colors">
                      <td className="px-4 py-3 text-gray-500 whitespace-nowrap">
                        {new Date(entry.attributes.timestamp).toLocaleString()}
                      </td>
                      <td className="px-4 py-3">
                        <div>
                          <span className="text-gray-900">{entry.attributes.actorId}</span>
                          <span className="ml-1 text-xs text-gray-400">
                            ({entry.attributes.actorType})
                          </span>
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        <span className="inline-block px-2 py-0.5 text-xs rounded-full bg-impilo-100 text-impilo-600">
                          {entry.attributes.action}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        {entry.attributes.resourceType}
                      </td>
                      <td className="px-4 py-3">
                        <Link
                          href={`/admin/audit/${entry.id}`}
                          className="text-impilo-500 hover:text-impilo-700 font-mono text-xs"
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
              <p className="text-sm text-gray-500">
                Page {page + 1} of {totalPages}
              </p>
              <div className="flex gap-2">
                <button
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  disabled={page === 0}
                  className="inline-flex items-center gap-1 px-3 py-1.5 text-sm border border-gray-300 rounded-lg hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                >
                  <ChevronLeft className="w-4 h-4" />
                  Previous
                </button>
                <button
                  onClick={() => setPage((p) => p + 1)}
                  disabled={page + 1 >= totalPages}
                  className="inline-flex items-center gap-1 px-3 py-1.5 text-sm border border-gray-300 rounded-lg hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
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
