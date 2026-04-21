import React, { useState, useCallback, useEffect } from "react";
import { View, Text, ScrollView, StyleSheet, TouchableOpacity } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import {
  Screen,
  Header,
  Badge,
  TextField,
  LoadingSpinner,
} from "@impilo/mobile-design-system";
import { useSyncEngine, useEdgeSnapshot, useConflicts } from "@impilo/mobile-offline";
import { useAppStore } from "../../stores/appStore";
import { apiClient } from "@impilo/mobile-api-client";
import type { EntitlementCheck } from "../../types";

export function OfflineDashboardScreen() {
  const { isOnline, facilityId, pendingSyncCount } = useAppStore();
  const { status: syncStatus, pendingCount, lastSyncAt, triggerSync } = useSyncEngine();
  const { snapshot, loading: snapshotLoading, download } = useEdgeSnapshot(facilityId ?? "");
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
      await download();
    } catch {}
  }, [facilityId, download]);

  return (
    <Screen>
      <Header title="Offline Edge" />
      <ScrollView testID="offline-dashboard" style={styles.container} contentContainerStyle={styles.contentContainer}>
        <View style={styles.heroBanner}>
          <View style={styles.heroLeft}>
            <View style={styles.heroIconCircle}>
              <Ionicons
                name={isOnline ? "cloud-done-outline" : "cloud-offline-outline"}
                size={36}
                color="#FFFFFF"
              />
            </View>
            <View style={styles.heroText}>
              <Text style={styles.heroTitle}>Edge Mode</Text>
              <Text style={styles.heroSubtitle}>
                {isOnline ? "Connected — ready to sync" : "Working offline"}
              </Text>
            </View>
          </View>
          <View style={[styles.statusPill, { backgroundColor: isOnline ? "rgba(16,185,129,0.25)" : "rgba(239,68,68,0.25)" }]}>
            <View style={[styles.statusDot, { backgroundColor: isOnline ? "#34D399" : "#F87171" }]} />
            <Text style={[styles.statusPillText, { color: isOnline ? "#D1FAE5" : "#FEE2E2" }]}>
              {isOnline ? "Online" : "Offline"}
            </Text>
          </View>
        </View>

        <View style={styles.syncCard}>
          <View style={styles.syncRow}>
            <View style={styles.syncLeft}>
              <Ionicons
                name={syncStatus === "syncing" ? "sync-outline" : "checkmark-circle-outline"}
                size={18}
                color={syncStatus === "syncing" ? "#F59E0B" : "#059669"}
              />
              <Text style={styles.syncStatusText}>
                {syncStatus === "syncing" ? "Syncing..." : `${pendingCount} pending operations`}
              </Text>
            </View>
            {lastSyncAt && (
              <Text style={styles.lastSyncText}>
                {`Synced ${new Date(lastSyncAt).toLocaleTimeString()}`}
              </Text>
            )}
          </View>
          <View style={styles.actionRow}>
            <TouchableOpacity
              style={[styles.syncButton, !isOnline && styles.syncButtonDisabled]}
              onPress={triggerSync}
              disabled={!isOnline}
              testID="sync-now-btn"
            >
              <Ionicons name="sync-outline" size={16} color="#FFFFFF" />
              <Text style={styles.syncButtonText}>Sync Now</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.downloadButton, (!isOnline || snapshotLoading) && styles.downloadButtonDisabled]}
              onPress={handleDownloadSnapshot}
              disabled={!isOnline || snapshotLoading}
              testID="download-snapshot-btn"
            >
              <Ionicons name="download-outline" size={16} color="#1E40AF" />
              <Text style={styles.downloadButtonText}>Download Snapshot</Text>
            </TouchableOpacity>
          </View>
        </View>

        <View style={styles.statsGrid}>
          <View style={styles.statCard}>
            <View style={[styles.statIconCircle, { backgroundColor: "#DBEAFE" }]}>
              <Ionicons name="cloud-upload-outline" size={20} color="#3B82F6" />
            </View>
            <Text style={[styles.statValue, { color: "#3B82F6" }]}>{String(pendingCount)}</Text>
            <Text style={styles.statLabel}>Pending Ops</Text>
          </View>
          <View style={styles.statCard}>
            <View style={[styles.statIconCircle, { backgroundColor: conflicts.length > 0 ? "#FEE2E2" : "#F3F4F6" }]}>
              <Ionicons name="warning-outline" size={20} color={conflicts.length > 0 ? "#DC2626" : "#9CA3AF"} />
            </View>
            <Text style={[styles.statValue, { color: conflicts.length > 0 ? "#DC2626" : "#111827" }]}>
              {String(conflicts.length)}
            </Text>
            <Text style={styles.statLabel}>Conflicts</Text>
          </View>
          <View style={styles.statCard}>
            <View style={[styles.statIconCircle, { backgroundColor: snapshot ? "#D1FAE5" : "#F3F4F6" }]}>
              <Ionicons name="server-outline" size={20} color={snapshot ? "#059669" : "#9CA3AF"} />
            </View>
            <Text style={[styles.statValue, { color: snapshot ? "#059669" : "#6B7280" }]}>
              {snapshot ? "Yes" : "No"}
            </Text>
            <Text style={styles.statLabel}>Edge Snapshot</Text>
          </View>
        </View>

        <View style={styles.sectionHeader}>
          <Ionicons name="finger-print-outline" size={16} color="#374151" />
          <Text style={styles.sectionHeaderText}>Entitlement Verification</Text>
        </View>

        <View style={styles.entitlementCard}>
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
            <TouchableOpacity
              style={[styles.verifyButton, checkingEntitlement && styles.verifyButtonDisabled]}
              onPress={handleCheckEntitlement}
              disabled={checkingEntitlement}
              testID="verify-entitlement-btn"
            >
              {checkingEntitlement ? (
                <LoadingSpinner size="sm" />
              ) : (
                <>
                  <Ionicons name="finger-print-outline" size={16} color="#FFFFFF" />
                  <Text style={styles.verifyButtonText}>Verify</Text>
                </>
              )}
            </TouchableOpacity>
          </View>

          {entitlement && (
            <View testID="entitlement-result" style={styles.entitlementResult}>
              <Text style={styles.entitlementName}>{entitlement.patientName}</Text>
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
                <View style={styles.validUntilRow}>
                  <Ionicons name="calendar-outline" size={12} color="#9CA3AF" />
                  <Text style={styles.validUntilText}>
                    {`Valid until: ${new Date(entitlement.validUntil).toLocaleDateString()}`}
                  </Text>
                </View>
              )}
            </View>
          )}
        </View>
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#F8FAFC",
  },
  contentContainer: {
    padding: 16,
    paddingBottom: 32,
  },
  heroBanner: {
    backgroundColor: "#B45309",
    borderRadius: 20,
    padding: 20,
    marginBottom: 16,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    shadowColor: "#B45309",
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.35,
    shadowRadius: 10,
    elevation: 5,
  },
  heroLeft: {
    flexDirection: "row",
    alignItems: "center",
    gap: 14,
    flex: 1,
  },
  heroIconCircle: {
    width: 60,
    height: 60,
    borderRadius: 30,
    backgroundColor: "rgba(255,255,255,0.2)",
    alignItems: "center",
    justifyContent: "center",
  },
  heroText: {
    gap: 4,
  },
  heroTitle: {
    fontSize: 20,
    fontWeight: "800",
    color: "#FFFFFF",
    letterSpacing: 0.3,
  },
  heroSubtitle: {
    fontSize: 13,
    color: "rgba(255,255,255,0.85)",
    fontWeight: "500",
  },
  statusPill: {
    flexDirection: "row",
    alignItems: "center",
    gap: 5,
    borderRadius: 20,
    paddingHorizontal: 10,
    paddingVertical: 6,
  },
  statusDot: {
    width: 7,
    height: 7,
    borderRadius: 4,
  },
  statusPillText: {
    fontSize: 11,
    fontWeight: "700",
  },
  syncCard: {
    backgroundColor: "#FFFFFF",
    borderRadius: 16,
    padding: 16,
    marginBottom: 16,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06,
    shadowRadius: 6,
    elevation: 2,
    gap: 12,
  },
  syncRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  syncLeft: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
  },
  syncStatusText: {
    fontSize: 14,
    color: "#374151",
    fontWeight: "500",
  },
  lastSyncText: {
    fontSize: 12,
    color: "#9CA3AF",
  },
  actionRow: {
    flexDirection: "row",
    gap: 10,
  },
  syncButton: {
    flex: 1,
    backgroundColor: "#1E40AF",
    borderRadius: 12,
    paddingVertical: 12,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 6,
  },
  syncButtonDisabled: {
    opacity: 0.5,
  },
  syncButtonText: {
    color: "#FFFFFF",
    fontWeight: "600",
    fontSize: 14,
  },
  downloadButton: {
    flex: 1,
    borderWidth: 1.5,
    borderColor: "#1E40AF",
    borderRadius: 12,
    paddingVertical: 12,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 6,
    backgroundColor: "#EFF6FF",
  },
  downloadButtonDisabled: {
    opacity: 0.5,
  },
  downloadButtonText: {
    color: "#1E40AF",
    fontWeight: "600",
    fontSize: 14,
  },
  statsGrid: {
    flexDirection: "row",
    gap: 10,
    marginBottom: 20,
  },
  statCard: {
    flex: 1,
    backgroundColor: "#FFFFFF",
    borderRadius: 16,
    padding: 14,
    alignItems: "center",
    gap: 5,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06,
    shadowRadius: 6,
    elevation: 2,
  },
  statIconCircle: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: "center",
    justifyContent: "center",
    marginBottom: 2,
  },
  statValue: {
    fontSize: 22,
    fontWeight: "700",
  },
  statLabel: {
    fontSize: 10,
    color: "#6B7280",
    fontWeight: "500",
    textAlign: "center",
  },
  sectionHeader: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    marginBottom: 12,
  },
  sectionHeaderText: {
    fontSize: 16,
    fontWeight: "700",
    color: "#111827",
  },
  entitlementCard: {
    backgroundColor: "#FFFFFF",
    borderRadius: 16,
    padding: 16,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06,
    shadowRadius: 6,
    elevation: 2,
    gap: 12,
  },
  entitlementInputRow: {
    flexDirection: "row",
    gap: 10,
    alignItems: "center",
  },
  cpidInputContainer: {
    flex: 1,
  },
  verifyButton: {
    backgroundColor: "#B45309",
    borderRadius: 12,
    paddingHorizontal: 16,
    paddingVertical: 12,
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
  },
  verifyButtonDisabled: {
    opacity: 0.6,
  },
  verifyButtonText: {
    color: "#FFFFFF",
    fontWeight: "600",
    fontSize: 14,
  },
  entitlementResult: {
    backgroundColor: "#F9FAFB",
    borderRadius: 12,
    padding: 14,
    gap: 8,
  },
  entitlementName: {
    fontSize: 15,
    fontWeight: "700",
    color: "#111827",
  },
  entitlementBadges: {
    flexDirection: "row",
    gap: 8,
    flexWrap: "wrap",
  },
  validUntilRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    marginTop: 2,
  },
  validUntilText: {
    fontSize: 12,
    color: "#9CA3AF",
  },
});
