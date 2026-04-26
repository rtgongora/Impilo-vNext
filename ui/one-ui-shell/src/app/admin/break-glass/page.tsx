"use client";

/**
 * Break Glass Log — Emergency access log and review table.
 * Route: /admin/break-glass | pageTitle: "Break Glass Log"
 */

import Link from "next/link";
import { ArrowLeft, Loader2, AlertTriangle, AlertCircle } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useBreakGlassReviews } from "@/hooks/queries/useTrustAdmin";

const REVIEW_STYLES: Record<string, string> = {
  PENDING_REVIEW: "bg-yellow-100 text-yellow-700",
  REVIEWED: "bg-green-100 text-green-700",
  FLAGGED: "bg-red-100 text-red-700",
};

export default function BreakGlassPage() {
  const { data, isLoading, error } = useBreakGlassReviews();

  const events = data?.data ?? [];

  return (
    <AppLayout>
      <PageShell
        title="Break Glass Log"
        subtitle="Emergency access log and override management"
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

        {error ? (
          <div className="bg-white rounded-lg border border-red-200 p-12 text-center">
            <AlertCircle className="w-10 h-10 text-red-300 mx-auto mb-3" />
            <p className="text-red-600 text-sm">Failed to load break glass events</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading break glass events...</span>
          </div>
        ) : events.length === 0 ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <AlertTriangle className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">No break glass events</p>
          </div>
        ) : (
          <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-gray-50">
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Timestamp</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">User</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Reason</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Patient Accessed</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Duration</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Review Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {events.map((event) => {
                  const reviewStyle =
                    REVIEW_STYLES[event.attributes.reviewStatus] ?? "bg-gray-100 text-gray-600";
                  return (
                    <tr key={event.id} className="hover:bg-gray-50 transition-colors">
                      <td className="px-4 py-3 text-gray-500 whitespace-nowrap">
                        {new Date(event.attributes.timestamp).toLocaleString()}
                      </td>
                      <td className="px-4 py-3 font-medium text-gray-900">
                        {event.attributes.actorName ?? event.attributes.actorId}
                      </td>
                      <td className="px-4 py-3 text-gray-600 max-w-xs truncate">
                        {event.attributes.reason}
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        {event.attributes.patientId}
                      </td>
                      <td className="px-4 py-3 text-gray-500">
                        {event.attributes.duration}
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={`inline-block px-2 py-0.5 text-xs rounded-full ${reviewStyle}`}
                        >
                          {event.attributes.reviewStatus}
                        </span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
