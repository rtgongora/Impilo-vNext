/**
 * DischargeClearanceScreen — Staged multi-department discharge clearance workflow.
 * 9 clearance types: Clinical, Nursing, Pharmacy, Laboratory, Imaging, Financial, Admin, Records, CRVS.
 */
import React, { useState } from "react";
import { View, Text, ScrollView, TouchableOpacity, TextInput, StyleSheet, Alert } from "react-native";
import { Screen, Header, Button, Badge, LoadingSpinner } from "@impilo/mobile-design-system";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@impilo/mobile-api-client";
import { useEncounterStore } from "../../stores/encounterStore";

const CLEARANCE_ICONS: Record<string, string> = {
  CLINICAL: "🩺", NURSING: "💉", PHARMACY: "💊", LABORATORY: "🔬",
  IMAGING: "📷", FINANCIAL: "💰", ADMINISTRATIVE: "📋", RECORDS: "📁", CRVS: "📝",
};

const CLEARANCE_DESCRIPTIONS: Record<string, string> = {
  CLINICAL: "Attending physician confirms patient safe for discharge",
  NURSING: "Nursing staff confirms care tasks complete, patient education done",
  PHARMACY: "Discharge medications dispensed, reconciliation complete",
  LABORATORY: "All pending lab results reviewed, no outstanding critical values",
  IMAGING: "All imaging reports reviewed, no pending studies",
  FINANCIAL: "All charges captured, payment/insurance clearance obtained",
  ADMINISTRATIVE: "Bed release processed, transport arranged if needed",
  RECORDS: "Discharge summary complete, documents compiled",
  CRVS: "Civil registration completed if applicable (birth/death)",
};

export function DischargeClearanceScreen() {
  const { activeEncounter } = useEncounterStore();
  const eid = activeEncounter?.id ?? "";
  const pid = activeEncounter?.patientId ?? "";
  const qc = useQueryClient();

  const { data, isLoading } = useQuery<{ data: Array<Record<string, unknown>>; progress: Record<string, number> }>({
    queryKey: ["clearances", eid],
    queryFn: () => apiClient.get(`/internal/v1/discharge-clearances?encounterId=${eid}`).then((r: any) => r.data),
    enabled: !!eid,
  });

  const initMut = useMutation({
    mutationFn: () => apiClient.post("/internal/v1/discharge-clearances/init", { encounterId: eid, patientId: pid }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["clearances"] }),
  });

  const clearMut = useMutation({
    mutationFn: (id: string) => apiClient.post(`/internal/v1/discharge-clearances/${id}/clear`, { clearedBy: "current-user" }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["clearances"] }),
  });

  const waiveMut = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) =>
      apiClient.post(`/internal/v1/discharge-clearances/${id}/waive`, { reason, clearedBy: "current-user" }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["clearances"] }),
  });

  const clearances = data?.data ?? [];
  const progress = data?.progress ?? { total: 0, cleared: 0, percent: 0 };

  if (isLoading) return <Screen><Header title="Discharge Clearance" /><LoadingSpinner /></Screen>;

  if (clearances.length === 0) {
    return (
      <Screen><Header title="Discharge Clearance" />
        <View style={styles.center}>
          <Text style={styles.emptyTitle}>No Clearance Initiated</Text>
          <Text style={styles.emptyText}>Initialize the 9-stage discharge clearance workflow for this encounter.</Text>
          <Button label="Initialize Clearances" onPress={() => initMut.mutate()} disabled={initMut.isPending} />
        </View>
      </Screen>
    );
  }

  return (
    <Screen><Header title="Discharge Clearance" />
      <ScrollView style={styles.container} contentContainerStyle={styles.content}>
        {/* Progress bar */}
        <View style={styles.progressCard}>
          <View style={styles.progressHeader}>
            <Text style={styles.progressTitle}>Clearance Progress</Text>
            <Text style={styles.progressPercent}>{progress.percent}%</Text>
          </View>
          <View style={styles.progressBarBg}>
            <View style={[styles.progressBarFill, { width: `${progress.percent}%` }]} />
          </View>
          <Text style={styles.progressDetail}>{progress.cleared} of {progress.total} cleared</Text>
          {progress.percent === 100 && (
            <Badge label="ALL CLEARED — READY FOR DISCHARGE" variant="success" />
          )}
        </View>

        {/* Clearance items */}
        {clearances.map((c) => {
          const type = String(c.clearance_type);
          const status = String(c.status);
          const icon = CLEARANCE_ICONS[type] ?? "📋";
          const desc = CLEARANCE_DESCRIPTIONS[type] ?? "";

          return (
            <ClearanceItem
              key={String(c.id)}
              id={String(c.id)}
              type={type}
              icon={icon}
              description={desc}
              status={status}
              clearedBy={c.cleared_by ? String(c.cleared_by) : undefined}
              clearedAt={c.cleared_at ? String(c.cleared_at) : undefined}
              waived={Boolean(c.waived)}
              waivedReason={c.waived_reason ? String(c.waived_reason) : undefined}
              onClear={() => clearMut.mutate(String(c.id))}
              onWaive={(reason) => waiveMut.mutate({ id: String(c.id), reason })}
            />
          );
        })}
      </ScrollView>
    </Screen>
  );
}

function ClearanceItem({ id, type, icon, description, status, clearedBy, clearedAt, waived, waivedReason, onClear, onWaive }: {
  id: string; type: string; icon: string; description: string; status: string;
  clearedBy?: string; clearedAt?: string; waived: boolean; waivedReason?: string;
  onClear: () => void; onWaive: (reason: string) => void;
}) {
  const [showWaive, setShowWaive] = useState(false);
  const [reason, setReason] = useState("");

  const statusColors: Record<string, { bg: string; border: string; text: string }> = {
    PENDING: { bg: "#FFF", border: "#E5E7EB", text: "#6B7280" },
    CLEARED: { bg: "#F0FDF4", border: "#22C55E", text: "#16A34A" },
    WAIVED: { bg: "#FFFBEB", border: "#F59E0B", text: "#D97706" },
  };
  const colors = statusColors[status] ?? statusColors.PENDING;

  return (
    <View style={[styles.clearanceCard, { backgroundColor: colors.bg, borderColor: colors.border }]}>
      <View style={styles.clearanceHeader}>
        <Text style={styles.clearanceIcon}>{icon}</Text>
        <View style={{ flex: 1 }}>
          <Text style={styles.clearanceType}>{type}</Text>
          <Text style={styles.clearanceDesc}>{description}</Text>
        </View>
        <Badge label={status} variant={status === "CLEARED" ? "success" : status === "WAIVED" ? "warning" : "default"} />
      </View>

      {status === "CLEARED" && clearedBy && (
        <Text style={styles.clearanceMeta}>Cleared by {clearedBy} {clearedAt ? `at ${new Date(clearedAt).toLocaleTimeString()}` : ""}</Text>
      )}
      {waived && waivedReason && (
        <Text style={styles.clearanceMeta}>Waived: {waivedReason}</Text>
      )}

      {status === "PENDING" && (
        <View style={styles.clearanceActions}>
          <TouchableOpacity style={styles.clearBtn} onPress={onClear}>
            <Text style={styles.clearBtnText}>✓ Clear</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.waiveBtn} onPress={() => setShowWaive(!showWaive)}>
            <Text style={styles.waiveBtnText}>Waive</Text>
          </TouchableOpacity>
        </View>
      )}

      {showWaive && status === "PENDING" && (
        <View style={styles.waiveForm}>
          <TextInput style={styles.waiveInput} placeholder="Waiver reason (required)" value={reason} onChangeText={setReason} />
          <Button label="Confirm Waiver" size="small" onPress={() => { if (reason) { onWaive(reason); setShowWaive(false); } }} disabled={!reason} />
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: 16, gap: 10, paddingBottom: 32 },
  center: { flex: 1, justifyContent: "center", alignItems: "center", padding: 32, gap: 12 },
  emptyTitle: { fontSize: 18, fontWeight: "700", color: "#374151" },
  emptyText: { fontSize: 14, color: "#6B7280", textAlign: "center" },
  progressCard: { backgroundColor: "#FFF", borderRadius: 16, padding: 16, gap: 8, borderWidth: 1, borderColor: "#E5E7EB" },
  progressHeader: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  progressTitle: { fontSize: 16, fontWeight: "700", color: "#111827" },
  progressPercent: { fontSize: 24, fontWeight: "900", color: "#2563EB" },
  progressBarBg: { height: 8, backgroundColor: "#E5E7EB", borderRadius: 4 },
  progressBarFill: { height: 8, backgroundColor: "#22C55E", borderRadius: 4 },
  progressDetail: { fontSize: 12, color: "#6B7280" },
  clearanceCard: { borderRadius: 12, padding: 12, borderWidth: 2, gap: 8 },
  clearanceHeader: { flexDirection: "row", alignItems: "flex-start", gap: 10 },
  clearanceIcon: { fontSize: 24 },
  clearanceType: { fontSize: 14, fontWeight: "700", color: "#111827" },
  clearanceDesc: { fontSize: 12, color: "#6B7280", lineHeight: 16 },
  clearanceMeta: { fontSize: 11, color: "#9CA3AF", marginLeft: 34 },
  clearanceActions: { flexDirection: "row", gap: 8, marginLeft: 34 },
  clearBtn: { backgroundColor: "#22C55E", paddingHorizontal: 16, paddingVertical: 8, borderRadius: 8 },
  clearBtnText: { color: "#FFF", fontSize: 13, fontWeight: "700" },
  waiveBtn: { backgroundColor: "#F3F4F6", paddingHorizontal: 16, paddingVertical: 8, borderRadius: 8 },
  waiveBtnText: { color: "#6B7280", fontSize: 13, fontWeight: "600" },
  waiveForm: { gap: 6, marginLeft: 34 },
  waiveInput: { borderWidth: 1, borderColor: "#D1D5DB", borderRadius: 6, padding: 8, fontSize: 13 },
});
