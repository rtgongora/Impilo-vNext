"use client";

/**
 * Break Glass Log — Emergency access log and review table.
 * Route: /admin/break-glass | pageTitle: "Break Glass Log"
 */

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { ArrowLeft, Loader2, AlertTriangle, AlertCircle, CheckCircle2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useBreakGlassReviews, useReviewBreakGlass, type BreakGlassReviewResource } from "@/hooks/queries/useTrustAdmin";

const REVIEW_STYLES: Record<string, string> = {
  PENDING_REVIEW: "bg-yellow-100 text-yellow-700",
  PENDING: "bg-yellow-100 text-yellow-700",
  REVIEWED: "bg-green-100 text-green-700",
  APPROVED: "bg-green-100 text-green-700",
  FLAGGED: "bg-red-100 text-red-700",
  ESCALATED: "bg-orange-100 text-orange-700",
};

function readAttr(
  event: BreakGlassReviewResource,
  key: string,
  alt?: string,
): string {
  const record = event as BreakGlassReviewResource & Record<string, unknown>;
  const attrs = record.attributes ?? {};
  const direct = record[key] ?? attrs[key];
  if (direct != null && String(direct) !== "") return String(direct);
  if (alt) {
    const altVal = record[alt] ?? attrs[alt];
    if (altVal != null && String(altVal) !== "") return String(altVal);
  }
  return "";
}

export default function BreakGlassPage() {
  const searchParams = useSearchParams();
  const highlightRequestId = searchParams.get("requestId")?.trim() ?? "";
  const { data, isLoading, error } = useBreakGlassReviews();
  const reviewBreakGlass = useReviewBreakGlass();
  const [reviewNotes, setReviewNotes] = useState<Record<string, string>>({});

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
                  <th className="text-left px-4 py-3 font-medium text-gray-600">When</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Actor</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Reason</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Resource</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Review</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {events.map((event) => {
                  const reviewStatus =
                    readAttr(event, "reviewStatus", "review_status") || "PENDING_REVIEW";
                  const reviewStyle =
                    REVIEW_STYLES[reviewStatus] ?? "bg-gray-100 text-gray-600";
                  const grantedAt =
                    readAttr(event, "grantedAt", "granted_at") ||
                    readAttr(event, "timestamp");
                  const isPending =
                    reviewStatus.toUpperCase().includes("PENDING") ||
                    reviewStatus === "PENDING_REVIEW";

                  const isHighlighted = highlightRequestId !== "" && event.id === highlightRequestId;

                  return (
                    <tr
                      key={event.id}
                      className={`hover:bg-gray-50 transition-colors align-top ${
                        isHighlighted ? "bg-yellow-50 ring-2 ring-inset ring-yellow-300" : ""
                      }`}
                      data-testid={isHighlighted ? "break-glass-highlighted-row" : undefined}
                    >
                      <td className="px-4 py-3 text-gray-500 whitespace-nowrap">
                        {grantedAt ? new Date(grantedAt).toLocaleString() : "—"}
                      </td>
                      <td className="px-4 py-3 font-medium text-gray-900">
                        {readAttr(event, "actorName", "actor_name") ||
                          readAttr(event, "actorId", "actor_id")}
                      </td>
                      <td className="px-4 py-3 text-gray-600 max-w-xs">
                        {readAttr(event, "reason")}
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        {readAttr(event, "resourceId", "resource_id") ||
                          readAttr(event, "patientId", "patient_id") ||
                          "—"}
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={`inline-block px-2 py-0.5 text-xs rounded-full ${reviewStyle}`}
                        >
                          {reviewStatus}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        {isPending ? (
                          <div className="space-y-2 min-w-[12rem]">
                            <input
                              value={reviewNotes[event.id] ?? ""}
                              onChange={(e) =>
                                setReviewNotes((prev) => ({
                                  ...prev,
                                  [event.id]: e.target.value,
                                }))
                              }
                              placeholder="Review notes (optional)"
                              className="w-full rounded border border-gray-200 px-2 py-1 text-xs"
                            />
                            <div className="flex gap-1">
                              <button
                                type="button"
                                disabled={reviewBreakGlass.isPending}
                                onClick={() =>
                                  reviewBreakGlass.mutate({
                                    id: event.id,
                                    decision: "APPROVED",
                                    notes: reviewNotes[event.id] ?? "",
                                  })
                                }
                                className="inline-flex items-center gap-1 rounded bg-green-600 px-2 py-1 text-[11px] font-medium text-white hover:bg-green-700 disabled:opacity-50"
                              >
                                <CheckCircle2 className="h-3 w-3" />
                                Approve
                              </button>
                              <button
                                type="button"
                                disabled={reviewBreakGlass.isPending}
                                onClick={() =>
                                  reviewBreakGlass.mutate({
                                    id: event.id,
                                    decision: "FLAGGED",
                                    notes: reviewNotes[event.id] ?? "",
                                  })
                                }
                                className="rounded bg-red-600 px-2 py-1 text-[11px] font-medium text-white hover:bg-red-700 disabled:opacity-50"
                              >
                                Flag
                              </button>
                            </div>
                          </div>
                        ) : (
                          <span className="text-xs text-gray-400">Reviewed</span>
                        )}
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
