/**
 * BedManagementScreen — Ward bed status, assignment, and discharge.
 */
import React, { useState } from "react";
import { View, Text, FlatList, TouchableOpacity, StyleSheet, Alert, TextInput } from "react-native";
import { Screen, Header, Badge, LoadingSpinner, Button, colors } from "@impilo/mobile-design-system";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { fetchBeds, assignBed, dischargeBed } from "../../services/queueService";

const STATUS_COLORS: Record<string, string> = { AVAILABLE: colors.ui.success.main, OCCUPIED: colors.ui.error.main, RESERVED: colors.ui.warning.main, MAINTENANCE: colors.gray[500] };

export function BedManagementScreen() {
  const queryClient = useQueryClient();
  const { data, isLoading } = useQuery({ queryKey: ["beds"], queryFn: fetchBeds });
  const [assigningBed, setAssigningBed] = useState<string | null>(null);
  const [patientId, setPatientId] = useState("");

  const assignMutation = useMutation({ mutationFn: () => assignBed(assigningBed!, patientId), onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["beds"] }); setAssigningBed(null); setPatientId(""); } });
  const dischargeMutation = useMutation({ mutationFn: (id: string) => dischargeBed(id), onSuccess: () => queryClient.invalidateQueries({ queryKey: ["beds"] }) });

  const wards = (data?.wards ?? []) as Array<Record<string, unknown>>;
  const beds = (data?.beds ?? []) as Array<Record<string, unknown>>;

  if (isLoading) return <Screen><Header title="Beds" /><LoadingSpinner /></Screen>;

  return (
    <Screen><Header title="Bed Management" />
      <View style={styles.container}>
        <View style={styles.summaryRow}>
          <Text style={styles.summaryText}>Wards: {wards.length}</Text>
          <Text style={styles.summaryText}>Beds: {beds.length}</Text>
          <Text style={[styles.summaryText, { color: colors.ui.success.main }]}>Available: {beds.filter((b) => b.status === "AVAILABLE").length}</Text>
          <Text style={[styles.summaryText, { color: colors.ui.error.main }]}>Occupied: {beds.filter((b) => b.status === "OCCUPIED").length}</Text>
        </View>
        {assigningBed && (
          <View style={styles.assignForm}>
            <TextInput style={styles.input} placeholder="Patient ID" value={patientId} onChangeText={setPatientId} />
            <View style={styles.assignActions}>
              <Button title="Cancel" variant="outline" onPress={() => setAssigningBed(null)} size="sm" />
              <Button title="Assign" onPress={() => assignMutation.mutate()} disabled={!patientId} size="sm" />
            </View>
          </View>
        )}
        <FlatList data={beds} keyExtractor={(item) => String(item.id)} renderItem={({ item }) => (
          <View style={[styles.bedCard, { borderLeftColor: STATUS_COLORS[String(item.status)] ?? colors.gray[500] }]}>
            <View style={{ flex: 1 }}>
              <Text style={styles.bedNumber}>Bed {String(item.bed_number)}</Text>
              <Text style={styles.bedMeta}>{String(item.ward_name ?? "")} · {String(item.bed_type ?? "")}</Text>
              <Badge label={String(item.status)} variant={item.status === "AVAILABLE" ? "success" : item.status === "OCCUPIED" ? "destructive" : "default"} />
            </View>
            {item.status === "AVAILABLE" && <TouchableOpacity style={styles.actionBtn} onPress={() => setAssigningBed(String(item.id))}><Text style={styles.actionText}>Assign</Text></TouchableOpacity>}
            {item.status === "OCCUPIED" && <TouchableOpacity style={[styles.actionBtn, { backgroundColor: colors.ui.warning.main }]} onPress={() => dischargeMutation.mutate(String(item.id))}><Text style={styles.actionText}>Discharge</Text></TouchableOpacity>}
          </View>
        )} />
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 16, gap: 8 },
  summaryRow: { flexDirection: "row", gap: 12, flexWrap: "wrap", marginBottom: 8 },
  summaryText: { fontSize: 13, fontWeight: "600", color: colors.gray[700] },
  assignForm: { backgroundColor: "#EFF6FF", borderRadius: 12, padding: 12, gap: 8 },
  input: { borderWidth: 1, borderColor: colors.gray[300], borderRadius: 8, padding: 10, fontSize: 14 },
  assignActions: { flexDirection: "row", gap: 8, justifyContent: "flex-end" },
  bedCard: { flexDirection: "row", alignItems: "center", backgroundColor: "#FFF", borderRadius: 8, padding: 12, marginBottom: 6, borderLeftWidth: 4 },
  bedNumber: { fontSize: 15, fontWeight: "700", color: colors.gray[900] },
  bedMeta: { fontSize: 12, color: colors.gray[500] },
  actionBtn: { backgroundColor: "#2563EB", paddingHorizontal: 12, paddingVertical: 8, borderRadius: 6 },
  actionText: { color: "#FFF", fontSize: 12, fontWeight: "600" },
});
