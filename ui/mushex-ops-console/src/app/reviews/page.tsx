"use client";

import { useState, useEffect, useCallback } from "react";
import {
  mushexApi,
  type OpsReview,
  type ReviewStatus,
  type PagedResponse,
} from "@/lib/mushexApi";

const STATUS_BADGE: Record<ReviewStatus, string> = {
  PENDING: "badge-pending",
  IN_REVIEW: "badge-in-review",
  APPROVED: "badge-approved",
  REJECTED: "badge-rejected",
};

export default function ReviewsPage() {
  const [reviews, setReviews] = useState<OpsReview[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [statusFilter, setStatusFilter] = useState<ReviewStatus | "">("");
  const [actionLoading, setActionLoading] = useState<string | null>(null);

  const loadReviews = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const params: { page: number; size: number; status?: ReviewStatus } = {
        page,
        size: 20,
      };
      if (statusFilter) {
        params.status = statusFilter;
      }
      const data: PagedResponse<OpsReview> = await mushexApi.listReviews(params);
      setReviews(data.items);
      setTotalPages(data.totalPages);
      setTotalElements(data.totalElements);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load reviews");
    } finally {
      setLoading(false);
    }
  }, [page, statusFilter]);

  useEffect(() => {
    loadReviews();
  }, [loadReviews]);

  const handleApprove = useCallback(async (id: string) => {
    setActionLoading(id);
    setError(null);
    setSuccessMessage(null);
    try {
      await mushexApi.approveReview(id, "Approved via Ops Console");
      setSuccessMessage(`Review ${id} approved.`);
      await loadReviews();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to approve review");
    } finally {
      setActionLoading(null);
    }
  }, [loadReviews]);

  const handleReject = useCallback(async (id: string) => {
    setActionLoading(id);
    setError(null);
    setSuccessMessage(null);
    try {
      await mushexApi.rejectReview(id, "Rejected via Ops Console");
      setSuccessMessage(`Review ${id} rejected.`);
      await loadReviews();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to reject review");
    } finally {
      setActionLoading(null);
    }
  }, [loadReviews]);

  return (
    <div className="p-8">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-semibold text-neutral-900">Review Queue</h1>
          <p className="text-sm text-neutral-500 mt-1">
            Review queued items and approve or reject them.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <label className="text-xs font-medium text-neutral-600">Status:</label>
          <select
            value={statusFilter}
            onChange={(e) => {
              setStatusFilter(e.target.value as ReviewStatus | "");
              setPage(0);
            }}
            className="select-field w-40"
          >
            <option value="">All</option>
            <option value="PENDING">Pending</option>
            <option value="IN_REVIEW">In Review</option>
            <option value="APPROVED">Approved</option>
            <option value="REJECTED">Rejected</option>
          </select>
        </div>
      </div>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-danger-soft border border-danger/28 text-sm text-red-800">
          {error}
        </div>
      )}

      {successMessage && (
        <div className="mb-4 p-3 rounded-lg bg-success-soft border border-success/25 text-sm text-primary-hover">
          {successMessage}
          <button
            onClick={() => setSuccessMessage(null)}
            className="ml-2 text-primary hover:text-primary-hover font-medium"
          >
            Dismiss
          </button>
        </div>
      )}

      {loading ? (
        <div className="p-8 text-center text-neutral-500 text-sm">Loading...</div>
      ) : (
        <div className="card overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-neutral-200 bg-neutral-50">
                <th className="text-left px-4 py-3 font-medium text-neutral-600">ID</th>
                <th className="text-left px-4 py-3 font-medium text-neutral-600">Queue</th>
                <th className="text-left px-4 py-3 font-medium text-neutral-600">Entity</th>
                <th className="text-left px-4 py-3 font-medium text-neutral-600">Status</th>
                <th className="text-left px-4 py-3 font-medium text-neutral-600">Assigned To</th>
                <th className="text-left px-4 py-3 font-medium text-neutral-600">Notes</th>
                <th className="text-left px-4 py-3 font-medium text-neutral-600">Created</th>
                <th className="text-left px-4 py-3 font-medium text-neutral-600">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-neutral-100">
              {reviews.map((review) => (
                <tr key={review.id} className="hover:bg-neutral-50 transition-colors">
                  <td className="px-4 py-3 font-mono text-xs font-medium text-neutral-900">
                    {review.id}
                  </td>
                  <td className="px-4 py-3 text-xs">
                    <span className="badge bg-blue-100 text-blue-800">
                      {review.queueType}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <div className="text-xs">
                      <span className="text-neutral-500">{review.entityType}:</span>{" "}
                      <span className="font-mono">{review.entityId}</span>
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    <span className={STATUS_BADGE[review.status]}>
                      {review.status.replace(/_/g, " ")}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-xs text-neutral-500">
                    {review.assignedTo || "Unassigned"}
                  </td>
                  <td className="px-4 py-3 text-xs text-neutral-500 max-w-xs truncate">
                    {review.notes || "---"}
                  </td>
                  <td className="px-4 py-3 text-xs text-neutral-500">
                    {new Date(review.createdAt).toLocaleString()}
                  </td>
                  <td className="px-4 py-3">
                    {["PENDING", "IN_REVIEW"].includes(review.status) && (
                      <div className="flex gap-1">
                        <button
                          onClick={() => handleApprove(review.id)}
                          disabled={actionLoading === review.id}
                          className="px-2 py-1 rounded text-xs font-medium text-primary-hover bg-success-soft hover:bg-emerald-100 transition-colors disabled:opacity-50"
                        >
                          Approve
                        </button>
                        <button
                          onClick={() => handleReject(review.id)}
                          disabled={actionLoading === review.id}
                          className="px-2 py-1 rounded text-xs font-medium text-danger bg-danger-soft hover:bg-red-100 transition-colors disabled:opacity-50"
                        >
                          Reject
                        </button>
                      </div>
                    )}
                    {review.resolvedAt && (
                      <span className="text-xs text-neutral-400">
                        {new Date(review.resolvedAt).toLocaleDateString()}
                      </span>
                    )}
                  </td>
                </tr>
              ))}
              {reviews.length === 0 && (
                <tr>
                  <td colSpan={8} className="px-4 py-12 text-center text-neutral-500">
                    No reviews found
                    {statusFilter ? ` with status ${statusFilter}` : ""}.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between mt-4">
          <span className="text-xs text-neutral-500">
            {totalElements} review{totalElements !== 1 ? "s" : ""} total
          </span>
          <div className="flex gap-2">
            <button
              onClick={() => setPage(Math.max(0, page - 1))}
              disabled={page === 0}
              className="btn-secondary text-xs"
            >
              Previous
            </button>
            <span className="text-xs text-neutral-600 self-center">
              Page {page + 1} of {totalPages}
            </span>
            <button
              onClick={() => setPage(Math.min(totalPages - 1, page + 1))}
              disabled={page >= totalPages - 1}
              className="btn-secondary text-xs"
            >
              Next
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
