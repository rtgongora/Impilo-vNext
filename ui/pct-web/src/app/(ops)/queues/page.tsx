"use client";

import { useState, useEffect, useCallback } from "react";
import {
  pctApi,
  type Queue,
  type QueueItem,
  type QueueItemStatus,
} from "@/lib/pctApi";
import { useSessionStore } from "@/stores/sessionStore";

const STATUS_COLORS: Record<QueueItemStatus, string> = {
  WAITING: "bg-yellow-100 text-yellow-800",
  CALLED: "bg-blue-100 text-blue-800",
  IN_SERVICE: "bg-emerald-100 text-primary-hover",
  COMPLETED: "bg-neutral-100 text-neutral-600",
  NO_SHOW: "bg-red-100 text-red-800",
  TRANSFERRED: "bg-purple-100 text-purple-800",
  CANCELLED: "bg-neutral-100 text-neutral-400",
};

const ACUITY_BADGE: Record<number, string> = {
  1: "badge-triage-1",
  2: "badge-triage-2",
  3: "badge-triage-3",
  4: "badge-triage-4",
  5: "badge-triage-5",
};

export default function QueuesPage() {
  const { facilityId, workspaceId } = useSessionStore();

  const [queues, setQueues] = useState<Queue[]>([]);
  const [selectedQueueId, setSelectedQueueId] = useState<string | null>(null);
  const [queueItems, setQueueItems] = useState<QueueItem[]>([]);
  const [calledPatient, setCalledPatient] = useState<QueueItem | null>(null);

  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Escalate dialog state — reason is mandatory, target queue optional.
  const [escalateItem, setEscalateItem] = useState<QueueItem | null>(null);
  const [escalateReason, setEscalateReason] = useState("");
  const [escalateTargetQueueId, setEscalateTargetQueueId] = useState("");
  const [escalateError, setEscalateError] = useState<string | null>(null);

  const loadQueues = useCallback(async () => {
    try {
      setLoading(true);
      const data = await pctApi.getQueues(facilityId, workspaceId);
      setQueues(data);
      if (data.length > 0 && !selectedQueueId) {
        setSelectedQueueId(data[0].id);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load queues");
    } finally {
      setLoading(false);
    }
  }, [facilityId, workspaceId, selectedQueueId]);

  const loadQueueItems = useCallback(async () => {
    if (!selectedQueueId) return;
    try {
      const items = await pctApi.getQueueItems(selectedQueueId);
      setQueueItems(items);

      // Find currently called patient
      const called = items.find((i) => i.status === "CALLED");
      setCalledPatient(called || null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load queue items");
    }
  }, [selectedQueueId]);

  useEffect(() => {
    loadQueues();
  }, [loadQueues]);

  useEffect(() => {
    loadQueueItems();
  }, [loadQueueItems]);

  // Auto-refresh queue items every 10 seconds
  useEffect(() => {
    if (!selectedQueueId) return;
    const interval = setInterval(() => {
      loadQueueItems();
    }, 10000);
    return () => clearInterval(interval);
  }, [selectedQueueId, loadQueueItems]);

  const handleCallNext = async (queueId: string) => {
    setActionLoading(true);
    setError(null);
    setSuccessMessage(null);

    try {
      const item = await pctApi.callNext(queueId);
      setCalledPatient(item);
      setSuccessMessage(`Called patient: Token #${item.tokenNumber}`);
      await loadQueueItems();
      await loadQueues();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to call next patient");
    } finally {
      setActionLoading(false);
    }
  };

  const handleUpdateStatus = async (itemId: string, status: QueueItemStatus) => {
    setActionLoading(true);
    setError(null);

    try {
      await pctApi.updateQueueItemStatus(itemId, status);
      await loadQueueItems();
      await loadQueues();

      if (status === "IN_SERVICE") {
        setSuccessMessage("Patient is now in service.");
      } else if (status === "COMPLETED") {
        setSuccessMessage("Patient marked as completed.");
        setCalledPatient(null);
      } else if (status === "NO_SHOW") {
        setSuccessMessage("Patient marked as no-show.");
        setCalledPatient(null);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to update status");
    } finally {
      setActionLoading(false);
    }
  };

  const openEscalate = (item: QueueItem) => {
    setEscalateReason("");
    setEscalateTargetQueueId("");
    setEscalateError(null);
    setEscalateItem(item);
  };

  const handleEscalate = async () => {
    if (!escalateItem || !escalateReason.trim()) return;
    setActionLoading(true);
    setEscalateError(null);
    setError(null);
    setSuccessMessage(null);

    try {
      await pctApi.escalateQueueItem(escalateItem.id, {
        reason: escalateReason.trim(),
        ...(escalateTargetQueueId ? { targetQueueId: escalateTargetQueueId } : {}),
      });
      setSuccessMessage(
        `Escalated token #${escalateItem.tokenNumber}${
          escalateTargetQueueId
            ? ` to ${queues.find((q) => q.id === escalateTargetQueueId)?.name ?? "target queue"}`
            : ""
        }.`,
      );
      setEscalateItem(null);
      setEscalateReason("");
      setEscalateTargetQueueId("");
      await loadQueueItems();
      await loadQueues();
    } catch (err) {
      setEscalateError(err instanceof Error ? err.message : "Failed to escalate queue item");
    } finally {
      setActionLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="p-8">
        <div className="animate-pulse space-y-4">
          <div className="h-8 bg-neutral-200 rounded w-1/3" />
          <div className="grid grid-cols-3 gap-4">
            {[1, 2, 3].map((i) => (
              <div key={i} className="card p-4 space-y-2">
                <div className="h-5 bg-neutral-100 rounded w-2/3" />
                <div className="h-8 bg-neutral-100 rounded w-1/3" />
              </div>
            ))}
          </div>
        </div>
      </div>
    );
  }

  const selectedQueue = queues.find((q) => q.id === selectedQueueId);

  return (
    <div className="p-8">
      <h1 className="text-2xl font-semibold text-neutral-900">Queue Management</h1>
      <p className="text-sm text-neutral-500 mt-1 mb-6">
        Manage patient queues, call next patients, and track service status.
      </p>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-danger-soft border border-danger/28 text-sm text-red-800">
          {error}
        </div>
      )}

      {successMessage && (
        <div className="mb-4 p-3 rounded-lg bg-success-soft border border-success/25 text-sm text-primary-hover">
          {successMessage}
        </div>
      )}

      {/* Queue cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 mb-8">
        {queues.map((queue) => (
          <button
            key={queue.id}
            onClick={() => setSelectedQueueId(queue.id)}
            className={`card p-4 text-left transition-all ${
              selectedQueueId === queue.id
                ? "ring-2 ring-brand-primary border-brand-primary"
                : "hover:border-neutral-300"
            }`}
          >
            <div className="flex items-center justify-between mb-2">
              <h3 className="text-sm font-semibold text-neutral-900 truncate">
                {queue.name}
              </h3>
              {!queue.active && (
                <span className="badge bg-neutral-100 text-neutral-500">Inactive</span>
              )}
            </div>
            <div className="flex items-baseline gap-4">
              <div>
                <span className="text-2xl font-bold text-brand-primary">
                  {queue.waitingCount}
                </span>
                <span className="text-xs text-neutral-500 ml-1">waiting</span>
              </div>
              <div>
                <span className="text-lg font-semibold text-primary">
                  {queue.inServiceCount}
                </span>
                <span className="text-xs text-neutral-500 ml-1">in service</span>
              </div>
            </div>
            <p className="text-xs text-neutral-400 mt-2">
              Avg wait: {queue.averageWaitMinutes} min
            </p>
          </button>
        ))}

        {queues.length === 0 && (
          <div className="col-span-full text-center py-12 text-neutral-500">
            No queues available for this workspace.
          </div>
        )}
      </div>

      {/* Selected queue detail */}
      {selectedQueue && (
        <div className="space-y-6">
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-semibold text-neutral-900">
              {selectedQueue.name}
            </h2>
            <button
              onClick={() => handleCallNext(selectedQueue.id)}
              disabled={actionLoading || selectedQueue.waitingCount === 0}
              className="btn-primary"
            >
              {actionLoading ? "Calling..." : "Call Next"}
            </button>
          </div>

          {/* Currently called patient */}
          {calledPatient && (
            <div className="card p-5 border-l-4 border-l-blue-500">
              <div className="flex items-center justify-between mb-3">
                <div>
                  <h3 className="text-sm font-semibold text-neutral-900">
                    Currently Called
                  </h3>
                  <p className="text-xs text-neutral-500">
                    Patient CPID: {calledPatient.patientCpid}
                  </p>
                </div>
                <span className="text-3xl font-bold text-brand-primary font-mono">
                  #{calledPatient.tokenNumber}
                </span>
              </div>
              <div className="flex items-center gap-3 text-sm">
                {calledPatient.acuity && (
                  <span className={ACUITY_BADGE[calledPatient.acuity]}>
                    Acuity {calledPatient.acuity}
                  </span>
                )}
                <span className="text-neutral-500">
                  Priority: {calledPatient.priority}
                </span>
                {calledPatient.calledAt && (
                  <span className="text-neutral-400 text-xs">
                    Called at: {new Date(calledPatient.calledAt).toLocaleTimeString()}
                  </span>
                )}
              </div>
              <div className="flex gap-2 mt-4">
                <button
                  onClick={() => handleUpdateStatus(calledPatient.id, "IN_SERVICE")}
                  disabled={actionLoading}
                  className="btn-primary text-xs"
                >
                  Start Service
                </button>
                <button
                  onClick={() => handleUpdateStatus(calledPatient.id, "NO_SHOW")}
                  disabled={actionLoading}
                  className="btn-danger text-xs"
                >
                  No Show
                </button>
              </div>
            </div>
          )}

          {/* Queue items table */}
          <div className="card overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-neutral-200 bg-neutral-50">
                  <th className="text-left px-4 py-3 font-medium text-neutral-600">Token</th>
                  <th className="text-left px-4 py-3 font-medium text-neutral-600">Patient CPID</th>
                  <th className="text-left px-4 py-3 font-medium text-neutral-600">Acuity</th>
                  <th className="text-left px-4 py-3 font-medium text-neutral-600">Priority</th>
                  <th className="text-left px-4 py-3 font-medium text-neutral-600">Status</th>
                  <th className="text-left px-4 py-3 font-medium text-neutral-600">Enqueued</th>
                  <th className="text-left px-4 py-3 font-medium text-neutral-600">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-neutral-100">
                {queueItems.map((item) => (
                  <tr key={item.id} className="hover:bg-neutral-50 transition-colors">
                    <td className="px-4 py-3 font-mono font-bold text-brand-primary">
                      #{item.tokenNumber}
                    </td>
                    <td className="px-4 py-3 text-neutral-900">
                      {item.patientCpid}
                    </td>
                    <td className="px-4 py-3">
                      {item.acuity ? (
                        <span className={ACUITY_BADGE[item.acuity]}>
                          {item.acuity}
                        </span>
                      ) : (
                        <span className="text-neutral-400">--</span>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <span
                        className={`badge ${
                          item.priority === "URGENT"
                            ? "bg-red-100 text-red-800"
                            : item.priority === "HIGH"
                              ? "bg-orange-100 text-orange-800"
                              : "bg-neutral-100 text-neutral-600"
                        }`}
                      >
                        {item.priority}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex flex-wrap items-center gap-1.5">
                        <span className={`badge ${STATUS_COLORS[item.status]}`}>
                          {item.status}
                        </span>
                        {item.escalatedAt && (
                          <span
                            className="badge bg-red-100 text-red-800"
                            title={
                              item.escalationReason
                                ? `Escalated: ${item.escalationReason}`
                                : "Escalated"
                            }
                          >
                            Escalated {new Date(item.escalatedAt).toLocaleTimeString()}
                          </span>
                        )}
                      </div>
                    </td>
                    <td className="px-4 py-3 text-neutral-500 text-xs">
                      {new Date(item.enqueuedAt).toLocaleTimeString()}
                    </td>
                    <td className="px-4 py-3">
                      {item.status === "IN_SERVICE" && (
                        <button
                          onClick={() => handleUpdateStatus(item.id, "COMPLETED")}
                          disabled={actionLoading}
                          className="text-xs text-primary hover:text-primary-hover font-medium"
                        >
                          Complete
                        </button>
                      )}
                      {item.status === "WAITING" && (
                        <button
                          onClick={() => handleUpdateStatus(item.id, "CANCELLED")}
                          disabled={actionLoading}
                          className="text-xs text-neutral-500 hover:text-red-600 font-medium"
                        >
                          Cancel
                        </button>
                      )}
                      {(item.status === "WAITING" ||
                        item.status === "CALLED" ||
                        item.status === "IN_SERVICE") && (
                        <button
                          onClick={() => openEscalate(item)}
                          disabled={actionLoading}
                          className="ml-2 text-xs text-orange-600 hover:text-orange-700 font-medium"
                          title="Escalate this queue item (reason required)"
                        >
                          Escalate
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
                {queueItems.length === 0 && (
                  <tr>
                    <td
                      colSpan={7}
                      className="px-4 py-12 text-center text-neutral-500"
                    >
                      No patients in this queue.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Escalate dialog — mandatory reason, optional target queue */}
      {escalateItem && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
          role="dialog"
          aria-modal="true"
          aria-label="Escalate queue item"
        >
          <div className="card w-full max-w-md p-5">
            <h3 className="text-sm font-semibold text-neutral-900">
              Escalate token #{escalateItem.tokenNumber}
            </h3>
            <p className="mt-1 text-xs text-neutral-500">
              Escalation bumps urgency to the top of the scale and records who escalated and
              why. Optionally move the patient to another queue at the same time.
            </p>

            <label
              className="mt-4 block text-xs font-medium text-neutral-600"
              htmlFor="escalate-reason"
            >
              Reason (required)
            </label>
            <textarea
              id="escalate-reason"
              value={escalateReason}
              onChange={(e) => setEscalateReason(e.target.value)}
              rows={3}
              placeholder="e.g. Deteriorating vitals observed in the waiting area"
              className="mt-1 w-full rounded-lg border border-neutral-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/40"
              autoFocus
            />

            <label
              className="mt-3 block text-xs font-medium text-neutral-600"
              htmlFor="escalate-target-queue"
            >
              Move to queue (optional)
            </label>
            <select
              id="escalate-target-queue"
              value={escalateTargetQueueId}
              onChange={(e) => setEscalateTargetQueueId(e.target.value)}
              className="mt-1 w-full rounded-lg border border-neutral-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/40"
            >
              <option value="">Keep in current queue</option>
              {queues
                .filter((q) => q.id !== escalateItem.queueId)
                .map((q) => (
                  <option key={q.id} value={q.id}>
                    {q.name}
                  </option>
                ))}
            </select>

            {escalateError && (
              <p className="mt-3 rounded-lg bg-danger-soft border border-danger/28 px-3 py-2 text-xs text-red-800">
                {escalateError}
              </p>
            )}

            <div className="mt-4 flex justify-end gap-2">
              <button
                onClick={() => {
                  setEscalateItem(null);
                  setEscalateReason("");
                  setEscalateTargetQueueId("");
                  setEscalateError(null);
                }}
                disabled={actionLoading}
                className="rounded-lg border border-neutral-300 px-4 py-2 text-xs font-medium text-neutral-700 hover:bg-neutral-50 disabled:opacity-50"
              >
                Cancel
              </button>
              <button
                onClick={handleEscalate}
                disabled={actionLoading || !escalateReason.trim()}
                className="btn-primary text-xs"
              >
                {actionLoading ? "Escalating..." : "Escalate"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
