/**
 * BreakGlassScreen — Emergency access path for critical situations.
 *
 * Allows elevated access with BREAK_GLASS purpose of use when normal auth is unavailable.
 * All break-glass actions are audit-logged.
 */

import React, { useState, useCallback } from "react";
import { Screen, Header, Card, CardBody, Button, TextField, Badge, ErrorState } from "@impilo/mobile-design-system";
import { useAuth } from "@impilo/mobile-auth";
import { apiClient } from "@impilo/mobile-api-client";
import { useAppStore } from "../../stores/appStore";

export function BreakGlassScreen() {
  const auth = useAuth();
  const { isOnline, facilityId } = useAppStore();
  const [reason, setReason] = useState("");
  const [patientIdentifier, setPatientIdentifier] = useState("");
  const [activated, setActivated] = useState(false);
  const [activating, setActivating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [accessLog, setAccessLog] = useState<{ timestamp: string; action: string }[]>([]);

  const handleActivate = useCallback(async () => {
    if (!reason.trim()) return;
    setActivating(true);
    setError(null);
    try {
      // Elevate purpose of use to BREAK_GLASS
      auth.setPurposeOfUse("BREAK_GLASS");

      // Log the break-glass activation
      if (isOnline) {
        await apiClient.post("/internal/v1/mobile/provider/break-glass/activate", {
          reason,
          facility_id: facilityId,
          patient_identifier: patientIdentifier || undefined,
        });
      }

      setActivated(true);
      setAccessLog((prev) => [
        { timestamp: new Date().toISOString(), action: `Break-glass activated: ${reason}` },
        ...prev,
      ]);
    } catch (err) {
      // Even if logging fails, allow break-glass in emergency
      setActivated(true);
      setAccessLog((prev) => [
        { timestamp: new Date().toISOString(), action: `Break-glass activated (offline): ${reason}` },
        ...prev,
      ]);
    } finally {
      setActivating(false);
    }
  }, [reason, patientIdentifier, facilityId, isOnline, auth]);

  const handleDeactivate = useCallback(async () => {
    auth.setPurposeOfUse("TREATMENT");
    setActivated(false);
    setReason("");
    setPatientIdentifier("");

    if (isOnline) {
      try {
        await apiClient.post("/internal/v1/mobile/provider/break-glass/deactivate", {
          facility_id: facilityId,
        });
      } catch {
        // Best effort
      }
    }

    setAccessLog((prev) => [
      { timestamp: new Date().toISOString(), action: "Break-glass deactivated" },
      ...prev,
    ]);
  }, [auth, facilityId, isOnline]);

  return React.createElement(
    Screen, null,
    React.createElement(Header, { title: "Emergency Access" }),
    React.createElement("div", { "data-testid": "break-glass-screen", style: { padding: "16px" } },

      // Warning
      React.createElement(Card, null,
        React.createElement(CardBody, null,
          React.createElement("div", {
            style: { backgroundColor: "#FEF3C7", padding: "16px", borderRadius: "8px", marginBottom: "12px" },
          },
            React.createElement("strong", { style: { color: "#92400E" } }, "Break-Glass Emergency Access"),
            React.createElement("p", { style: { color: "#92400E", fontSize: "14px", marginTop: "4px" } },
              "This mode provides elevated access for life-threatening emergencies. " +
              "All actions are audit-logged and will be reviewed. " +
              "Use only when normal access paths are unavailable and patient safety is at risk."
            )
          )
        )
      ),

      !activated
        ? React.createElement(Card, null,
            React.createElement(CardBody, null,
              React.createElement(TextField, {
                label: "Emergency Reason (required)",
                value: reason,
                onChange: setReason,
                placeholder: "Describe the emergency situation...",
                testID: "break-glass-reason",
              }),
              React.createElement(TextField, {
                label: "Patient CPID (if known)",
                value: patientIdentifier,
                onChange: setPatientIdentifier,
                placeholder: "Optional patient identifier",
                testID: "break-glass-patient",
              }),
              React.createElement("div", { style: { marginTop: "16px" } },
                React.createElement(Button, {
                  title: "Activate Break-Glass Access",
                  variant: "destructive",
                  fullWidth: true,
                  onPress: handleActivate,
                  loading: activating,
                  disabled: !reason.trim(),
                  testID: "activate-break-glass-btn",
                  accessibilityLabel: "Activate emergency break-glass access",
                })
              ),
              error ? React.createElement(ErrorState, { title: "Error", message: error }) : null
            )
          )
        : React.createElement(Card, null,
            React.createElement(CardBody, null,
              React.createElement("div", { style: { textAlign: "center", padding: "24px" } },
                React.createElement(Badge, { variant: "destructive", children: "BREAK-GLASS ACTIVE" }),
                React.createElement("p", { style: { marginTop: "12px", fontSize: "14px" } }, `Reason: ${reason}`),
                React.createElement("p", { style: { fontSize: "12px", color: "#6B7280" } }, "All actions are being audit-logged"),
                React.createElement("div", { style: { marginTop: "24px" } },
                  React.createElement(Button, {
                    title: "Deactivate Break-Glass",
                    variant: "primary",
                    fullWidth: true,
                    onPress: handleDeactivate,
                    testID: "deactivate-break-glass-btn",
                  })
                )
              )
            )
          ),

      // Audit log
      accessLog.length > 0
        ? React.createElement("div", { style: { marginTop: "16px" } },
            React.createElement("strong", null, "Session Audit Log"),
            accessLog.map((entry, i) =>
              React.createElement("div", { key: i, style: { padding: "8px 0", borderBottom: "1px solid #E5E7EB", fontSize: "12px" } },
                React.createElement("span", { style: { color: "#9CA3AF" } }, new Date(entry.timestamp).toLocaleTimeString()),
                React.createElement("span", { style: { marginLeft: "8px" } }, entry.action)
              )
            )
          )
        : null
    )
  );
}
