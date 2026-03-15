/**
 * OfflineDashboardScreen — Offline status, sync progress, and entitlement verification.
 */

import React, { useState, useCallback, useEffect } from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
import {
  Screen,
  Header,
  Card,
  CardBody,
  CardHeader,
  Button,
  Badge,
  TextField,
  LoadingSpinner,
  ErrorState,
} from "@impilo/mobile-design-system";
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

  return (
    <Screen>
      <Header title="Offline Edge" />
      <ScrollView testID="offline-dashboard" style={styles.container}>
        {/* Connection status */}
        <Card>
          <CardBody>
            <View style={styles.statusRow}>
              <View style={styles.statusLeft}>
                <Badge variant={isOnline ? "primary" : "destructive"}>
                  {isOnline ? "Online" : "Offline"}
                </Badge>
                <Text style={styles.syncStatusText}>
                  {syncStatus === "syncing" ? "Syncing..." : `${pendingCount} pending operations`}
                </Text>
              </View>
              {lastSyncAt && (
                <Text style={styles.lastSyncText}>
                  {`Last sync: ${new Date(lastSyncAt).toLocaleTimeString()}`}
                </Text>
              )}
            </View>
            <View style={styles.actionRow}>
              <Button
                title="Sync Now"
                variant="primary"
                onPress={triggerSync}
                disabled={!isOnline}
                testID="sync-now-btn"
              />
              <Button
                title="Download Snapshot"
                variant="outline"
                onPress={handleDownloadSnapshot}
                disabled={!isOnline}
                testID="download-snapshot-btn"
              />
            </View>
          </CardBody>
        </Card>

        {/* Stats */}
        <View style={styles.statsGrid}>
          <Card>
            <CardBody>
              <View style={styles.statCenter}>
                <Text style={styles.statValue}>{String(pendingCount)}</Text>
                <Text style={styles.statLabel}>Pending Ops</Text>
              </View>
            </CardBody>
          </Card>
          <Card>
            <CardBody>
              <View style={styles.statCenter}>
                <Text
                  style={[
                    styles.statValue,
                    { color: conflicts.length > 0 ? "#DC2626" : "#111827" },
                  ]}
                >
                  {String(conflicts.length)}
                </Text>
                <Text style={styles.statLabel}>Conflicts</Text>
              </View>
            </CardBody>
          </Card>
          <Card>
            <CardBody>
              <View style={styles.statCenter}>
                <Text style={styles.statValue}>{snapshot ? "Yes" : "No"}</Text>
                <Text style={styles.statLabel}>Edge Snapshot</Text>
              </View>
            </CardBody>
          </Card>
        </View>

        {/* Entitlement verification */}
        <CardHeader>Entitlement Verification</CardHeader>
        <Card>
          <CardBody>
            <View style={styles.entitlementInputRow}>
              <View style={styles.cpidInputContainer}>
                <TextField
                  label=""
                  value={cpidInput}
                  onChange={setCpidInput}
                  placeholder="Enter CPID..."
                  testID="cpid-input"
                />
              </View>
              <Button
                title="Verify"
                onPress={handleCheckEntitlement}
                loading={checkingEntitlement}
                testID="verify-entitlement-btn"
              />
            </View>
            {entitlement && (
              <View testID="entitlement-result" style={styles.entitlementResult}>
                <Text style={styles.boldText}>{entitlement.patientName}</Text>
                <View style={styles.entitlementBadges}>
                  <Badge
                    variant={entitlement.coverageStatus === "ACTIVE" ? "primary" : "destructive"}
                  >
                    {entitlement.coverageStatus}
                  </Badge>
                  {entitlement.coverageType && (
                    <Badge variant="outline">{entitlement.coverageType}</Badge>
                  )}
                  {entitlement.verifiedOffline && (
                    <Badge variant="secondary">Verified Offline</Badge>
                  )}
                </View>
                {entitlement.validUntil && (
                  <Text style={styles.validUntilText}>
                    {`Valid until: ${new Date(entitlement.validUntil).toLocaleDateString()}`}
                  </Text>
                )}
              </View>
            )}
          </CardBody>
        </Card>
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 16,
  },
  statusRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  statusLeft: {
    flexDirection: "row",
    alignItems: "center",
  },
  syncStatusText: {
    marginLeft: 8,
    fontSize: 14,
  },
  lastSyncText: {
    fontSize: 12,
    color: "#9CA3AF",
  },
  actionRow: {
    flexDirection: "row",
    gap: 8,
    marginTop: 12,
  },
  statsGrid: {
    flexDirection: "row",
    gap: 12,
    marginVertical: 16,
  },
  statCenter: {
    alignItems: "center",
  },
  statValue: {
    fontSize: 24,
    fontWeight: "700",
  },
  statLabel: {
    fontSize: 12,
    color: "#6B7280",
  },
  entitlementInputRow: {
    flexDirection: "row",
    gap: 8,
    alignItems: "center",
  },
  cpidInputContainer: {
    flex: 1,
  },
  entitlementResult: {
    marginTop: 12,
    padding: 12,
    backgroundColor: "#F9FAFB",
    borderRadius: 8,
  },
  boldText: {
    fontWeight: "700",
  },
  entitlementBadges: {
    flexDirection: "row",
    gap: 8,
    marginTop: 4,
  },
  validUntilText: {
    fontSize: 12,
    color: "#6B7280",
    marginTop: 4,
  },
});
