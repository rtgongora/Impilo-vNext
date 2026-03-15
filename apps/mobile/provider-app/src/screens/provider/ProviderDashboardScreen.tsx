/**
 * ProviderDashboardScreen — Worklist/task queue for the provider.
 *
 * Shows assigned tasks, today's encounters, overdue items, and quick actions.
 */

import React, { useState, useEffect, useCallback } from "react";
import {
  Screen,
  Header,
  Card,
  CardBody,
  CardHeader,
  Button,
  Badge,
  LoadingSpinner,
  EmptyState,
  ErrorState,
} from "@impilo/mobile-design-system";
import { useAppStore } from "../../stores/appStore";
import { getMyTasks } from "../../services/taskService";
import { listEncounters } from "../../services/encounterService";
import type { Task, Encounter } from "../../types";

const PRIORITY_VARIANT: Record<string, string> = {
  URGENT: "destructive",
  HIGH: "warning",
  MEDIUM: "secondary",
  LOW: "outline",
};

export function ProviderDashboardScreen() {
  const { facilityId } = useAppStore();
  const [tasks, setTasks] = useState<Task[]>([]);
  const [openEncounters, setOpenEncounters] = useState<Encounter[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedTaskId, setSelectedTaskId] = useState<string | null>(null);

  const loadDashboard = useCallback(async () => {
    if (!facilityId) return;
    setLoading(true);
    setError(null);
    try {
      const [taskResult, encounterResult] = await Promise.all([
        getMyTasks(undefined, 0, 50),
        listEncounters(undefined, 0, 20),
      ]);
      setTasks(taskResult.tasks);
      setOpenEncounters(
        encounterResult.encounters.filter((e) => e.status === "IN_PROGRESS")
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load dashboard");
    } finally {
      setLoading(false);
    }
  }, [facilityId]);

  useEffect(() => {
    loadDashboard();
  }, [loadDashboard]);

  if (loading) {
    return React.createElement(Screen, null,
      React.createElement(Header, { title: "Worklist" }),
      React.createElement(LoadingSpinner, { size: "lg" })
    );
  }

  if (error) {
    return React.createElement(Screen, null,
      React.createElement(Header, { title: "Worklist" }),
      React.createElement(ErrorState, { title: "Error", message: error, onRetry: loadDashboard })
    );
  }

  const pendingTasks = tasks.filter((t) => t.status === "PENDING" || t.status === "IN_PROGRESS");
  const overdueTasks = tasks.filter((t) => t.status === "OVERDUE");

  return React.createElement(
    Screen,
    null,
    React.createElement(Header, { title: "Worklist" }),
    React.createElement(
      "div",
      { "data-testid": "provider-dashboard", style: { padding: "16px" } },

      // Summary tiles
      React.createElement(
        "div",
        { style: { display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: "12px", marginBottom: "16px" } },
        React.createElement(Card, null,
          React.createElement(CardBody, null,
            React.createElement("div", { style: { textAlign: "center" } },
              React.createElement("div", { style: { fontSize: "24px", fontWeight: "700" } }, String(pendingTasks.length)),
              React.createElement("div", { style: { fontSize: "12px", color: "#6B7280" } }, "Pending Tasks")
            )
          )
        ),
        React.createElement(Card, null,
          React.createElement(CardBody, null,
            React.createElement("div", { style: { textAlign: "center" } },
              React.createElement("div", { style: { fontSize: "24px", fontWeight: "700" } }, String(openEncounters.length)),
              React.createElement("div", { style: { fontSize: "12px", color: "#6B7280" } }, "Open Visits")
            )
          )
        ),
        React.createElement(Card, null,
          React.createElement(CardBody, null,
            React.createElement("div", { style: { textAlign: "center" } },
              React.createElement("div", {
                style: { fontSize: "24px", fontWeight: "700", color: overdueTasks.length > 0 ? "#DC2626" : "#111827" },
              }, String(overdueTasks.length)),
              React.createElement("div", { style: { fontSize: "12px", color: "#6B7280" } }, "Overdue")
            )
          )
        )
      ),

      // Task list
      React.createElement(CardHeader, null, "My Tasks"),
      pendingTasks.length === 0
        ? React.createElement(EmptyState, { title: "No pending tasks", message: "All caught up" })
        : pendingTasks.map((task) =>
            React.createElement(
              Card,
              { key: task.id },
              React.createElement(
                CardBody,
                null,
                React.createElement(
                  "div",
                  {
                    "data-testid": `task-${task.id}`,
                    style: { cursor: "pointer" },
                    onClick: () => setSelectedTaskId(task.id === selectedTaskId ? null : task.id),
                  },
                  React.createElement(
                    "div",
                    { style: { display: "flex", justifyContent: "space-between", alignItems: "center" } },
                    React.createElement("strong", null, task.title),
                    React.createElement(Badge, {
                      variant: PRIORITY_VARIANT[task.priority] as "destructive" | "secondary" | "outline",
                      children: task.priority,
                    })
                  ),
                  task.patientName
                    ? React.createElement("p", { style: { fontSize: "14px", color: "#6B7280", margin: "4px 0" } },
                        `Patient: ${task.patientName}`
                      )
                    : null,
                  React.createElement(
                    "div",
                    { style: { display: "flex", gap: "8px", marginTop: "4px" } },
                    React.createElement(Badge, { variant: "outline", children: task.taskType }),
                    task.dueAt
                      ? React.createElement("span", { style: { fontSize: "12px", color: "#9CA3AF" } },
                          `Due: ${new Date(task.dueAt).toLocaleDateString()}`
                        )
                      : null
                  )
                )
              )
            )
          ),

      // Open encounters section
      openEncounters.length > 0
        ? React.createElement(
            React.Fragment,
            null,
            React.createElement(CardHeader, { children: "Open Encounters" }),
            openEncounters.map((enc) =>
              React.createElement(
                Card,
                { key: enc.id },
                React.createElement(
                  CardBody,
                  null,
                  React.createElement(
                    "div",
                    { "data-testid": `encounter-${enc.id}` },
                    React.createElement("strong", null, `${enc.encounterType} Visit`),
                    React.createElement(
                      "p",
                      { style: { fontSize: "14px", color: "#6B7280" } },
                      `Started: ${new Date(enc.startedAt).toLocaleTimeString()}`
                    ),
                    enc.chiefComplaint
                      ? React.createElement(
                          "p",
                          { style: { fontSize: "14px" } },
                          `CC: ${enc.chiefComplaint}`
                        )
                      : null
                  )
                )
              )
            )
          )
        : null,

      React.createElement(
        "div",
        { style: { marginTop: "16px" } },
        React.createElement(Button, {
          title: "Refresh",
          variant: "outline",
          onPress: loadDashboard,
          testID: "refresh-dashboard",
        })
      )
    )
  );
}
