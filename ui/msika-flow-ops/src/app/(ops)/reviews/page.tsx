"use client";
import { useState, useEffect, useCallback } from "react";
import { opsApi, type Review } from "@/lib/opsApi";

export default function ReviewsPage() {
  const [reviews, setReviews] = useState<Review[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState(false);
  const [rejectingId, setRejectingId] = useState<string | null>(null);
  const [rejectNotes, setRejectNotes] = useState("");

  const loadReviews = useCallback(async () => {
    try {
      setLoading(true);
      const data = await opsApi.getPendingReviews();
      setReviews(data.content || []);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load reviews");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadReviews();
  }, [loadReviews]);

  const handleApprove = async (id: string) => {
    setActionLoading(true);
    setError(null);
    try {
      await opsApi.approveReview(id);
      await loadReviews();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to approve");
    } finally {
      setActionLoading(false);
    }
  };

  const handleReject = async () => {
    if (!rejectingId) return;
    setActionLoading(true);
    setError(null);
    try {
      await opsApi.rejectReview(rejectingId, rejectNotes);
      setRejectingId(null);
      setRejectNotes("");
      await loadReviews();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to reject");
    } finally {
      setActionLoading(false);
    }
  };

  if (loading)
    return (
      <div className="p-8">
        <div className="animate-pulse space-y-4">
          <div className="h-8 bg-neutral-200 rounded w-1/3" />
        </div>
      </div>
    );

  return (
    <div className="p-8">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-semibold">Pending Reviews</h1>
          <p className="text-sm text-neutral-500 mt-1">
            Approve or reject vendor applications and escalated items.
          </p>
        </div>
        <button onClick={loadReviews} className="btn-secondary">
          Refresh
        </button>
      </div>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-danger-soft border border-danger/28 text-sm text-red-800">
          {error}
        </div>
      )}

      {rejectingId && (
        <div className="card p-6 mb-6 space-y-4">
          <h2 className="text-lg font-semibold">Reject Review</h2>
          <textarea
            value={rejectNotes}
            onChange={(e) => setRejectNotes(e.target.value)}
            placeholder="Reason for rejection..."
            rows={3}
            className="input-field"
          />
          <div className="flex gap-2">
            <button onClick={handleReject} disabled={actionLoading} className="btn-danger">
              {actionLoading ? "Rejecting..." : "Reject"}
            </button>
            <button
              onClick={() => {
                setRejectingId(null);
                setRejectNotes("");
              }}
              className="btn-secondary"
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      <div className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-neutral-200 bg-neutral-50">
              <th className="text-left px-4 py-3 font-medium text-neutral-600">ID</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Entity Type</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Entity ID</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Status</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Created</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-neutral-100">
            {reviews.map((review) => (
              <tr key={review.id} className="hover:bg-neutral-50">
                <td className="px-4 py-3 font-mono text-xs">{review.id}</td>
                <td className="px-4 py-3">{review.entityType}</td>
                <td className="px-4 py-3 font-mono text-xs">{review.entityId}</td>
                <td className="px-4 py-3">
                  <span className="badge-pending">{review.status}</span>
                </td>
                <td className="px-4 py-3 text-xs text-neutral-500">
                  {new Date(review.createdAt).toLocaleString()}
                </td>
                <td className="px-4 py-3">
                  <div className="flex gap-2">
                    <button
                      onClick={() => handleApprove(review.id)}
                      disabled={actionLoading}
                      className="text-xs text-primary hover:text-primary-hover font-medium"
                    >
                      Approve
                    </button>
                    <button
                      onClick={() => setRejectingId(review.id)}
                      disabled={actionLoading}
                      className="text-xs text-red-600 hover:text-red-800 font-medium"
                    >
                      Reject
                    </button>
                  </div>
                </td>
              </tr>
            ))}
            {reviews.length === 0 && (
              <tr>
                <td colSpan={6} className="px-4 py-12 text-center text-neutral-500">
                  No pending reviews.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
