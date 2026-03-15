/**
 * SupervisorDashboardScreen — Facility operations dashboard with KPI tiles.
 */

import React, { useState, useEffect, useCallback } from "react";
import { Screen, Header, Card, CardBody, CardHeader, Button, Badge, LoadingSpinner, ErrorState } from "@impilo/mobile-design-system";
import { getFacilityMetrics } from "../../services/supportService";
import { useAppStore } from "../../stores/appStore";
import type { FacilityMetrics } from "../../types";

export function SupervisorDashboardScreen() {
  const { facilityId } = useAppStore();
  const [metrics, setMetrics] = useState<FacilityMetrics | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadMetrics = useCallback(async () => {
    if (!facilityId) return;
    setLoading(true);
    setError(null);
    try {
      const m = await getFacilityMetrics(facilityId);
      setMetrics(m);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load metrics");
    } finally {
      setLoading(false);
    }
  }, [facilityId]);

  useEffect(() => { loadMetrics(); }, [loadMetrics]);

  if (loading) return React.createElement(Screen, null, React.createElement(Header, { title: "Dashboard" }), React.createElement(LoadingSpinner, { size: "lg" }));
  if (error) return React.createElement(Screen, null, React.createElement(Header, { title: "Dashboard" }), React.createElement(ErrorState, { title: "Error", message: error, onRetry: loadMetrics }));
  if (!metrics) return null;

  const tiles = [
    { label: "Patients Seen", value: metrics.patientsSeenToday, color: "#3B82F6" },
    { label: "Open Encounters", value: metrics.encountersOpen, color: "#F59E0B" },
    { label: "Closed Encounters", value: metrics.encountersClosed, color: "#10B981" },
    { label: "Avg Wait (min)", value: metrics.averageWaitTimeMinutes, color: "#6366F1" },
    { label: "Pending Tasks", value: metrics.pendingTasks, color: "#8B5CF6" },
    { label: "Overdue Escalations", value: metrics.overdueEscalations, color: metrics.overdueEscalations > 0 ? "#DC2626" : "#6B7280" },
    { label: "Stock Alerts", value: metrics.stockAlerts, color: metrics.stockAlerts > 0 ? "#DC2626" : "#6B7280" },
  ];

  return React.createElement(
    Screen, null,
    React.createElement(Header, { title: `${metrics.facilityName} — Dashboard` }),
    React.createElement("div", { "data-testid": "supervisor-dashboard", style: { padding: "16px" } },
      React.createElement("p", { style: { fontSize: "14px", color: "#6B7280", marginBottom: "12px" } }, `Date: ${metrics.date}`),
      React.createElement("div", { style: { display: "grid", gridTemplateColumns: "1fr 1fr", gap: "12px" } },
        tiles.map((tile) =>
          React.createElement(Card, { key: tile.label },
            React.createElement(CardBody, null,
              React.createElement("div", { style: { textAlign: "center" } },
                React.createElement("div", { style: { fontSize: "28px", fontWeight: "700", color: tile.color } }, String(tile.value)),
                React.createElement("div", { style: { fontSize: "12px", color: "#6B7280" } }, tile.label)
              )
            )
          )
        )
      ),
      React.createElement("div", { style: { marginTop: "16px" } },
        React.createElement(Button, { title: "Refresh", variant: "outline", onPress: loadMetrics, testID: "refresh-metrics" })
      )
    )
  );
}
