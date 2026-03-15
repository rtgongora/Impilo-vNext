/**
 * LocalQueueScreen — View and manage the offline operation queue.
 */

import React, { useState, useCallback } from "react";
import { Screen, Header, Card, CardBody, Button, Badge, EmptyState } from "@impilo/mobile-design-system";
import { useSyncEngine } from "@impilo/mobile-offline";
import type { SyncQueueItem, SyncItemStatus } from "../../types";

const STATUS_COLORS: Record<SyncItemStatus, string> = {
  PENDING: "secondary",
  SYNCING: "primary",
  SYNCED: "primary",
  FAILED: "destructive",
  CONFLICT: "destructive",
};

export function LocalQueueScreen() {
  const { queue, pendingCount, retryFailed, triggerSync } = useSyncEngine();
  const [filter, setFilter] = useState<SyncItemStatus | "ALL">("ALL");

  const items: SyncQueueItem[] = (queue ?? []).map((op: { id: string; type: string; resourceType: string; resourceId: string; payload: Record<string, unknown>; status: string; attempts: number; lastAttemptAt?: string; error?: string; createdAt: string }) => ({
    id: op.id,
    operationType: op.type as SyncQueueItem["operationType"],
    resourceType: op.resourceType,
    resourceId: op.resourceId,
    payload: op.payload,
    status: op.status as SyncItemStatus,
    attemptCount: op.attempts,
    lastAttemptAt: op.lastAttemptAt,
    errorMessage: op.error,
    createdAt: op.createdAt,
  }));

  const filtered = filter === "ALL" ? items : items.filter((i) => i.status === filter);

  return React.createElement(
    Screen, null,
    React.createElement(Header, { title: "Sync Queue" }),
    React.createElement("div", { "data-testid": "local-queue-screen", style: { padding: "16px" } },

      // Filter tabs
      React.createElement("div", { style: { display: "flex", gap: "4px", marginBottom: "16px", flexWrap: "wrap" } },
        (["ALL", "PENDING", "SYNCING", "SYNCED", "FAILED", "CONFLICT"] as const).map((s) =>
          React.createElement(Button, {
            key: s,
            title: s === "ALL" ? `All (${items.length})` : `${s} (${items.filter((i) => i.status === s).length})`,
            variant: filter === s ? "primary" : "outline",
            size: "sm",
            onPress: () => setFilter(s),
            testID: `filter-${s.toLowerCase()}`,
          })
        )
      ),

      // Actions
      React.createElement("div", { style: { display: "flex", gap: "8px", marginBottom: "12px" } },
        React.createElement(Button, { title: "Sync All", variant: "primary", onPress: triggerSync, testID: "sync-all-btn" }),
        React.createElement(Button, { title: "Retry Failed", variant: "outline", onPress: retryFailed, testID: "retry-failed-btn" })
      ),

      // Queue items
      filtered.length === 0
        ? React.createElement(EmptyState, { title: "Queue empty", message: "No operations in queue" })
        : filtered.map((item) =>
            React.createElement(Card, { key: item.id },
              React.createElement(CardBody, null,
                React.createElement("div", { "data-testid": `queue-item-${item.id}` },
                  React.createElement("div", { style: { display: "flex", justifyContent: "space-between" } },
                    React.createElement("strong", null, `${item.operationType} ${item.resourceType}`),
                    React.createElement(Badge, { variant: STATUS_COLORS[item.status] as "primary" | "destructive" | "secondary", children: item.status })
                  ),
                  React.createElement("p", { style: { fontSize: "12px", color: "#6B7280" } }, `Resource: ${item.resourceId}`),
                  React.createElement("p", { style: { fontSize: "12px", color: "#9CA3AF" } },
                    `Attempts: ${item.attemptCount} | Created: ${new Date(item.createdAt).toLocaleTimeString()}`
                  ),
                  item.errorMessage
                    ? React.createElement("p", { style: { fontSize: "12px", color: "#DC2626", marginTop: "4px" } }, item.errorMessage)
                    : null
                )
              )
            )
          )
    )
  );
}
