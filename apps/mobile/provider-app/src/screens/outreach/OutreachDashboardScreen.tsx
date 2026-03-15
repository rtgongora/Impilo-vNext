/**
 * OutreachDashboardScreen — Outreach mode home with daily stats and GPS tracking.
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
  ErrorState,
} from "@impilo/mobile-design-system";
import { useAppStore } from "../../stores/appStore";
import { getHouseholds } from "../../services/householdService";
import { getMyTasks } from "../../services/taskService";
import { useSyncEngine } from "@impilo/mobile-offline";
import type { Household, Task } from "../../types";

export function OutreachDashboardScreen() {
  const { facilityId, isOnline } = useAppStore();
  const { status: syncStatus, pendingCount } = useSyncEngine();
  const [households, setHouseholds] = useState<Household[]>([]);
  const [tasks, setTasks] = useState<Task[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    if (!facilityId) return;
    setLoading(true);
    setError(null);
    try {
      const [hhResult, taskResult] = await Promise.all([
        getHouseholds(facilityId, 0, 100),
        getMyTasks(undefined, 0, 50),
      ]);
      setHouseholds(hhResult.households);
      setTasks(taskResult.tasks.filter((t) => t.taskType === "OUTREACH" || t.taskType === "COMMUNITY_VISIT"));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load outreach data");
    } finally {
      setLoading(false);
    }
  }, [facilityId]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  if (loading) {
    return React.createElement(Screen, null,
      React.createElement(Header, { title: "Outreach" }),
      React.createElement(LoadingSpinner, { size: "lg" })
    );
  }

  if (error) {
    return React.createElement(Screen, null,
      React.createElement(Header, { title: "Outreach" }),
      React.createElement(ErrorState, { title: "Error", message: error, onRetry: loadData })
    );
  }

  const overdueVisits = households.filter(
    (h) => h.nextVisitDate && new Date(h.nextVisitDate) < new Date()
  );
  const highRisk = households.filter((h) => h.riskCategory === "HIGH");
  const todayTasks = tasks.filter((t) => t.status === "PENDING" || t.status === "IN_PROGRESS");

  return React.createElement(
    Screen,
    null,
    React.createElement(Header, { title: "Outreach Dashboard" }),
    React.createElement(
      "div",
      { "data-testid": "outreach-dashboard", style: { padding: "16px" } },

      // Sync status
      !isOnline || pendingCount > 0
        ? React.createElement(
            Card,
            null,
            React.createElement(CardBody, null,
              React.createElement(
                "div",
                { style: { display: "flex", justifyContent: "space-between", alignItems: "center" } },
                React.createElement(Badge, {
                  variant: isOnline ? "secondary" : "destructive",
                  children: isOnline ? "Online" : "Offline",
                }),
                pendingCount > 0
                  ? React.createElement("span", { style: { fontSize: "14px" } }, `${pendingCount} items pending sync`)
                  : null
              )
            )
          )
        : null,

      // Summary tiles
      React.createElement(
        "div",
        { style: { display: "grid", gridTemplateColumns: "1fr 1fr", gap: "12px", marginTop: "12px" } },
        React.createElement(Card, null,
          React.createElement(CardBody, null,
            React.createElement("div", { style: { textAlign: "center" } },
              React.createElement("div", { style: { fontSize: "24px", fontWeight: "700" } }, String(households.length)),
              React.createElement("div", { style: { fontSize: "12px", color: "#6B7280" } }, "Households")
            )
          )
        ),
        React.createElement(Card, null,
          React.createElement(CardBody, null,
            React.createElement("div", { style: { textAlign: "center" } },
              React.createElement("div", { style: { fontSize: "24px", fontWeight: "700" } }, String(todayTasks.length)),
              React.createElement("div", { style: { fontSize: "12px", color: "#6B7280" } }, "Today's Tasks")
            )
          )
        ),
        React.createElement(Card, null,
          React.createElement(CardBody, null,
            React.createElement("div", { style: { textAlign: "center" } },
              React.createElement("div", {
                style: { fontSize: "24px", fontWeight: "700", color: overdueVisits.length > 0 ? "#DC2626" : "#111827" },
              }, String(overdueVisits.length)),
              React.createElement("div", { style: { fontSize: "12px", color: "#6B7280" } }, "Overdue Visits")
            )
          )
        ),
        React.createElement(Card, null,
          React.createElement(CardBody, null,
            React.createElement("div", { style: { textAlign: "center" } },
              React.createElement("div", {
                style: { fontSize: "24px", fontWeight: "700", color: highRisk.length > 0 ? "#F59E0B" : "#111827" },
              }, String(highRisk.length)),
              React.createElement("div", { style: { fontSize: "12px", color: "#6B7280" } }, "High Risk")
            )
          )
        )
      ),

      // Today's schedule
      React.createElement(CardHeader, { children: "Today's Schedule" }),
      todayTasks.length === 0
        ? React.createElement("p", { style: { color: "#6B7280", padding: "8px" } }, "No outreach tasks for today")
        : todayTasks.map((task) =>
            React.createElement(
              Card,
              { key: task.id },
              React.createElement(CardBody, null,
                React.createElement("div", { "data-testid": `outreach-task-${task.id}` },
                  React.createElement("strong", null, task.title),
                  task.patientName
                    ? React.createElement("p", { style: { fontSize: "14px", color: "#6B7280" } }, task.patientName)
                    : null,
                  React.createElement(Badge, { variant: "outline", children: task.priority })
                )
              )
            )
          ),

      React.createElement("div", { style: { marginTop: "16px" } },
        React.createElement(Button, { title: "Refresh", variant: "outline", onPress: loadData, testID: "refresh-outreach" })
      )
    )
  );
}
