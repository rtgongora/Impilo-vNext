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
  IN_SERVICE: "bg-emerald-100 text-emerald-800",
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
        <div className="mb-4 p-3 rounded-lg bg-red-50 border border-red-200 text-sm text-red-800">
          {error}
        </div>
      )}

      {successMessage && (
        <div className="mb-4 p-3 rounded-lg bg-emerald-50 border border-emerald-200 text-sm text-emerald-800">
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
                <span className="text-lg font-semibold text-emerald-600">
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
                      <span className={`badge ${STATUS_COLORS[item.status]}`}>
                        {item.status}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-neutral-500 text-xs">
                      {new Date(item.enqueuedAt).toLocaleTimeString()}
                    </td>
                    <td className="px-4 py-3">
                      {item.status === "IN_SERVICE" && (
                        <button
                          onClick={() => handleUpdateStatus(item.id, "COMPLETED")}
                          disabled={actionLoading}
                          className="text-xs text-emerald-600 hover:text-emerald-800 font-medium"
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
    </div>
  );
}
