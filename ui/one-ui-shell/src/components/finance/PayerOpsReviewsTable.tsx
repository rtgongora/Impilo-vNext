"use client";

import { DomainCollectionTable } from "@/components/common/DomainCollectionTable";

type ReviewRow = {
  id: string;
  status: string;
  intentId: string;
  reason: string;
  createdAt: string;
};

function normalizeReviews(payload: unknown): ReviewRow[] {
  const raw = Array.isArray(payload)
    ? payload
    : payload && typeof payload === "object"
      ? (Array.isArray((payload as { data?: unknown }).data)
          ? (payload as { data: unknown[] }).data
          : Array.isArray((payload as { items?: unknown }).items)
            ? (payload as { items: unknown[] }).items
            : Array.isArray((payload as { reviews?: unknown }).reviews)
              ? (payload as { reviews: unknown[] }).reviews
              : [])
      : [];

  return raw.map((row, index) => {
    const r = row as Record<string, unknown>;
    const attrs = (r.attributes as Record<string, unknown> | undefined) ?? r;
    return {
      id: String(r.id ?? attrs.reviewId ?? index),
      status: String(attrs.status ?? attrs.reviewStatus ?? "PENDING"),
      intentId: String(attrs.intentId ?? attrs.paymentIntentId ?? "—"),
      reason: String(attrs.reason ?? attrs.note ?? "—"),
      createdAt: String(attrs.createdAt ?? attrs.created_at ?? "—"),
    };
  });
}

interface PayerOpsReviewsTableProps {
  data: unknown;
  isLoading?: boolean;
  error?: Error | null;
  onApprove?: (reviewId: string) => void;
  onReject?: (reviewId: string) => void;
  pendingReviewId?: string;
}

export function PayerOpsReviewsTable({
  data,
  isLoading,
  error,
  onApprove,
  onReject,
  pendingReviewId,
}: PayerOpsReviewsTableProps) {
  const rows = normalizeReviews(data);

  return (
    <DomainCollectionTable<ReviewRow>
      rows={rows}
      isLoading={isLoading}
      error={error}
      emptyTitle="No ops reviews in queue"
      emptyHint="Arm the reviews feed to load payer-ops adjudication rows."
      rowKey={(row) => row.id}
      columns={[
        { key: "id", header: "Review", render: (row) => <span className="font-mono text-xs">{row.id}</span> },
        { key: "status", header: "Status", render: (row) => row.status },
        { key: "intent", header: "Intent", render: (row) => row.intentId },
        { key: "reason", header: "Reason", render: (row) => row.reason },
        { key: "created", header: "Created", render: (row) => row.createdAt },
        {
          key: "actions",
          header: "Actions",
          render: (row) =>
            row.status.toUpperCase() === "PENDING" ? (
              <div className="flex gap-1">
                <button
                  type="button"
                  disabled={pendingReviewId === row.id}
                  className="rounded bg-emerald-700 px-2 py-1 text-xs text-white disabled:opacity-50"
                  onClick={() => onApprove?.(row.id)}
                >
                  Approve
                </button>
                <button
                  type="button"
                  disabled={pendingReviewId === row.id}
                  className="rounded border border-danger/28 px-2 py-1 text-xs text-red-800 disabled:opacity-50"
                  onClick={() => onReject?.(row.id)}
                >
                  Reject
                </button>
              </div>
            ) : (
              <span className="text-xs text-muted-foreground">—</span>
            ),
        },
      ]}
    />
  );
}
