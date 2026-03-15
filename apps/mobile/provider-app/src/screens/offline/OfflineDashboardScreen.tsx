/**
 * OfflineDashboardScreen — Offline status, sync progress, and entitlement verification.
 */

import React, { useState, useCallback, useEffect } from "react";
import { Screen, Header, Card, CardBody, CardHeader, Button, Badge, LoadingSpinner, ErrorState } from "@impilo/mobile-design-system";
import { useSyncEngine, useEdgeSnapshot, useConflicts } from "@impilo/mobile-offline";
import { useAppStore } from "../../stores/appStore";
import { apiClient } from "@impilo/mobile-api-client";
import type { EntitlementCheck } from "../../types";

export function OfflineDashboardScreen() {
  const { isOnline, facilityId, pendingSyncCount } = useAppStore();
  const { status: syncStatus, pendingCount, lastSyncAt, triggerSync } = useSyncEngine();
  const { snapshot, loading: snapshotLoading, download } = useEdgeSnapshot();
  const { conflicts } = useConflicts();
  const [entitlement, setEntitlement] = useState<EntitlementCheck | null>(null);
  const [cpidInput, setCpidInput] = useState("");
  const [checkingEntitlement, setCheckingEntitlement] = useState(false);

  useEffect(() => {
    useAppStore().setPendingSyncCount(pendingCount);
  }, [pendingCount]);

  const handleCheckEntitlement = useCallback(async () => {
    if (!cpidInput.trim()) return;
    setCheckingEntitlement(true);
    try {
      const response = await apiClient.get<{
        data: {
          patient_id: string;
          cpid: string;
          patient_name: string;
          coverage_status: string;
          coverage_type?: string;
          valid_until?: string;
          benefits_remaining?: Record<string, number>;
          verified_at: string;
          verified_offline: boolean;
        };
      }>(`/internal/v1/mobile/provider/entitlement/verify?cpid=${encodeURIComponent(cpidInput)}`);
      const d = response.data.data;
      setEntitlement({
        patientId: d.patient_id,
        cpid: d.cpid,
        patientName: d.patient_name,
        coverageStatus: d.coverage_status as EntitlementCheck["coverageStatus"],
        coverageType: d.coverage_type,
        validUntil: d.valid_until,
        benefitsRemaining: d.benefits_remaining,
        verifiedAt: d.verified_at,
        verifiedOffline: d.verified_offline,
      });
    } catch {
      setEntitlement({
        patientId: "",
        cpid: cpidInput,
        patientName: "Verification unavailable",
        coverageStatus: "NOT_FOUND",
        verifiedAt: new Date().toISOString(),
        verifiedOffline: true,
      });
    } finally {
      setCheckingEntitlement(false);
    }
  }, [cpidInput]);

  const handleDownloadSnapshot = useCallback(async () => {
    if (!facilityId) return;
    try {
      await download(facilityId);
    } catch {}
  }, [facilityId, download]);

  return React.createElement(
    Screen, null,
    React.createElement(Header, { title: "Offline Edge" }),
    React.createElement("div", { "data-testid": "offline-dashboard", style: { padding: "16px" } },

      // Connection status
      React.createElement(Card, null,
        React.createElement(CardBody, null,
          React.createElement("div", { style: { display: "flex", justifyContent: "space-between", alignItems: "center" } },
            React.createElement("div", null,
              React.createElement(Badge, { variant: isOnline ? "primary" : "destructive", children: isOnline ? "Online" : "Offline" }),
              React.createElement("span", { style: { marginLeft: "8px", fontSize: "14px" } },
                syncStatus === "syncing" ? "Syncing..." : `${pendingCount} pending operations`
              )
            ),
            lastSyncAt
              ? React.createElement("span", { style: { fontSize: "12px", color: "#9CA3AF" } },
                  `Last sync: ${new Date(lastSyncAt).toLocaleTimeString()}`
                )
              : null
          ),
          React.createElement("div", { style: { display: "flex", gap: "8px", marginTop: "12px" } },
            React.createElement(Button, { title: "Sync Now", variant: "primary", onPress: triggerSync, disabled: !isOnline, testID: "sync-now-btn" }),
            React.createElement(Button, { title: "Download Snapshot", variant: "outline", onPress: handleDownloadSnapshot, disabled: !isOnline, testID: "download-snapshot-btn" })
          )
        )
      ),

      // Stats
      React.createElement("div", { style: { display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: "12px", margin: "16px 0" } },
        React.createElement(Card, null, React.createElement(CardBody, null,
          React.createElement("div", { style: { textAlign: "center" } },
            React.createElement("div", { style: { fontSize: "24px", fontWeight: "700" } }, String(pendingCount)),
            React.createElement("div", { style: { fontSize: "12px", color: "#6B7280" } }, "Pending Ops")
          )
        )),
        React.createElement(Card, null, React.createElement(CardBody, null,
          React.createElement("div", { style: { textAlign: "center" } },
            React.createElement("div", { style: { fontSize: "24px", fontWeight: "700", color: conflicts.length > 0 ? "#DC2626" : "#111827" } }, String(conflicts.length)),
            React.createElement("div", { style: { fontSize: "12px", color: "#6B7280" } }, "Conflicts")
          )
        )),
        React.createElement(Card, null, React.createElement(CardBody, null,
          React.createElement("div", { style: { textAlign: "center" } },
            React.createElement("div", { style: { fontSize: "24px", fontWeight: "700" } }, snapshot ? "Yes" : "No"),
            React.createElement("div", { style: { fontSize: "12px", color: "#6B7280" } }, "Edge Snapshot")
          )
        ))
      ),

      // Entitlement verification
      React.createElement(CardHeader, { children: "Entitlement Verification" }),
      React.createElement(Card, null,
        React.createElement(CardBody, null,
          React.createElement("div", { style: { display: "flex", gap: "8px" } },
            React.createElement("input", {
              value: cpidInput,
              onChange: (e: React.ChangeEvent<HTMLInputElement>) => setCpidInput(e.target.value),
              placeholder: "Enter CPID...",
              "data-testid": "cpid-input",
              style: { flex: 1, padding: "8px 12px", border: "1px solid #D1D5DB", borderRadius: "6px" },
            }),
            React.createElement(Button, {
              title: "Verify",
              onPress: handleCheckEntitlement,
              loading: checkingEntitlement,
              testID: "verify-entitlement-btn",
            })
          ),
          entitlement
            ? React.createElement("div", { style: { marginTop: "12px", padding: "12px", backgroundColor: "#F9FAFB", borderRadius: "8px" }, "data-testid": "entitlement-result" },
                React.createElement("strong", null, entitlement.patientName),
                React.createElement("div", { style: { display: "flex", gap: "8px", marginTop: "4px" } },
                  React.createElement(Badge, {
                    variant: entitlement.coverageStatus === "ACTIVE" ? "primary" : "destructive",
                    children: entitlement.coverageStatus,
                  }),
                  entitlement.coverageType ? React.createElement(Badge, { variant: "outline", children: entitlement.coverageType }) : null,
                  entitlement.verifiedOffline ? React.createElement(Badge, { variant: "secondary", children: "Verified Offline" }) : null
                ),
                entitlement.validUntil
                  ? React.createElement("p", { style: { fontSize: "12px", color: "#6B7280", marginTop: "4px" } },
                      `Valid until: ${new Date(entitlement.validUntil).toLocaleDateString()}`
                    )
                  : null
              )
            : null
        )
      )
    )
  );
}
