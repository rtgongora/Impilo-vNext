/**
 * BreakGlassScreen — Emergency access path for critical situations.
 *
 * Allows elevated access with BREAK_GLASS purpose of use when normal auth is unavailable.
 * All break-glass actions are audit-logged.
 */

import React, { useState, useCallback } from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
import {
  Screen,
  Header,
  Card,
  CardBody,
  Button,
  TextField,
  Badge,
  ErrorState,
} from "@impilo/mobile-design-system";
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

  return (
    <Screen>
      <Header title="Emergency Access" />
      <ScrollView testID="break-glass-screen" style={styles.container}>
        {/* Warning */}
        <Card>
          <CardBody>
            <View style={styles.warningBox}>
              <Text style={styles.warningTitle}>Break-Glass Emergency Access</Text>
              <Text style={styles.warningText}>
                This mode provides elevated access for life-threatening emergencies.
                All actions are audit-logged and will be reviewed.
                Use only when normal access paths are unavailable and patient safety is at risk.
              </Text>
            </View>
          </CardBody>
        </Card>

        {!activated ? (
          <Card>
            <CardBody>
              <TextField
                label="Emergency Reason (required)"
                value={reason}
                onChange={setReason}
                placeholder="Describe the emergency situation..."
                testID="break-glass-reason"
              />
              <TextField
                label="Patient CPID (if known)"
                value={patientIdentifier}
                onChange={setPatientIdentifier}
                placeholder="Optional patient identifier"
                testID="break-glass-patient"
              />
              <View style={styles.activateContainer}>
                <Button
                  title="Activate Break-Glass Access"
                  variant="destructive"
                  fullWidth
                  onPress={handleActivate}
                  loading={activating}
                  disabled={!reason.trim()}
                  testID="activate-break-glass-btn"
                  accessibilityLabel="Activate emergency break-glass access"
                />
              </View>
              {error && <ErrorState title="Error" message={error} />}
            </CardBody>
          </Card>
        ) : (
          <Card>
            <CardBody>
              <View style={styles.activeCenter}>
                <Badge variant="destructive">BREAK-GLASS ACTIVE</Badge>
                <Text style={styles.reasonText}>{`Reason: ${reason}`}</Text>
                <Text style={styles.auditNote}>All actions are being audit-logged</Text>
                <View style={styles.deactivateContainer}>
                  <Button
                    title="Deactivate Break-Glass"
                    variant="primary"
                    fullWidth
                    onPress={handleDeactivate}
                    testID="deactivate-break-glass-btn"
                  />
                </View>
              </View>
            </CardBody>
          </Card>
        )}

        {/* Audit log */}
        {accessLog.length > 0 && (
          <View style={styles.auditLogContainer}>
            <Text style={styles.boldText}>Session Audit Log</Text>
            {accessLog.map((entry, i) => (
              <View key={i} style={styles.auditEntry}>
                <Text style={styles.auditTimestamp}>
                  {new Date(entry.timestamp).toLocaleTimeString()}
                </Text>
                <Text style={styles.auditAction}>{entry.action}</Text>
              </View>
            ))}
          </View>
        )}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 16,
  },
  warningBox: {
    backgroundColor: "#FEF3C7",
    padding: 16,
    borderRadius: 8,
    marginBottom: 12,
  },
  warningTitle: {
    fontWeight: "700",
    color: "#92400E",
  },
  warningText: {
    color: "#92400E",
    fontSize: 14,
    marginTop: 4,
  },
  activateContainer: {
    marginTop: 16,
  },
  activeCenter: {
    alignItems: "center",
    padding: 24,
  },
  reasonText: {
    marginTop: 12,
    fontSize: 14,
  },
  auditNote: {
    fontSize: 12,
    color: "#6B7280",
  },
  deactivateContainer: {
    marginTop: 24,
    width: "100%",
  },
  auditLogContainer: {
    marginTop: 16,
  },
  boldText: {
    fontWeight: "700",
  },
  auditEntry: {
    flexDirection: "row",
    paddingVertical: 8,
    borderBottomWidth: 1,
    borderBottomColor: "#E5E7EB",
  },
  auditTimestamp: {
    fontSize: 12,
    color: "#9CA3AF",
  },
  auditAction: {
    fontSize: 12,
    marginLeft: 8,
  },
});
