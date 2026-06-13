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
  FLAGGED: "bg-red-100 text-danger",
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
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to administration
          </Link>
        </div>

        {error ? (
          <div className="bg-card rounded-lg border border-danger/28 p-12 text-center">
            <AlertCircle className="w-10 h-10 text-red-300 mx-auto mb-3" />
            <p className="text-red-600 text-sm">Failed to load break glass events</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading break glass events...</span>
          </div>
        ) : events.length === 0 ? (
          <div className="bg-card rounded-lg border border-border p-12 text-center">
            <AlertTriangle className="w-10 h-10 text-muted-foreground mx-auto mb-3" />
            <p className="text-muted-foreground text-sm">No break glass events</p>
          </div>
        ) : (
          <div className="bg-card rounded-lg border border-border overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-background">
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">When</th>
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">Actor</th>
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">Reason</th>
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">Resource</th>
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">Review</th>
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {events.map((event) => {
                  const reviewStatus =
                    readAttr(event, "reviewStatus", "review_status") || "PENDING_REVIEW";
                  const reviewStyle =
                    REVIEW_STYLES[reviewStatus] ?? "bg-neutral-100 text-muted-foreground";
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
                      className={`hover:bg-background transition-colors align-top ${
                        isHighlighted ? "bg-yellow-50 ring-2 ring-inset ring-yellow-300" : ""
                      }`}
                      data-testid={isHighlighted ? "break-glass-highlighted-row" : undefined}
                    >
                      <td className="px-4 py-3 text-muted-foreground whitespace-nowrap">
                        {grantedAt ? new Date(grantedAt).toLocaleString() : "—"}
                      </td>
                      <td className="px-4 py-3 font-medium text-foreground">
                        {readAttr(event, "actorName", "actor_name") ||
                          readAttr(event, "actorId", "actor_id")}
                      </td>
                      <td className="px-4 py-3 text-muted-foreground max-w-xs">
                        {readAttr(event, "reason")}
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">
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
                              className="w-full rounded border border-border px-2 py-1 text-xs"
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
                          <span className="text-xs text-muted-foreground">Reviewed</span>
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
