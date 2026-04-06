/**
 * PharmacyDispensingScreen — Pending dispensing, 5-rights verification, dispense.
 */
import React, { useState } from "react";
import { View, Text, FlatList, TouchableOpacity, StyleSheet, Alert } from "react-native";
import { Screen, Header, Badge, LoadingSpinner, Button } from "@impilo/mobile-design-system";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { fetchPendingDispensing, dispense, verifyFiveRights } from "../../services/queueService";
import { useAppStore } from "../../stores/appStore";

export function PharmacyDispensingScreen() {
  const queryClient = useQueryClient();
  const { data: pending = [], isLoading } = useQuery({ queryKey: ["pharmacy-pending"], queryFn: fetchPendingDispensing });
  const [verifying, setVerifying] = useState<string | null>(null);

  const dispenseMutation = useMutation({
    mutationFn: (id: string) => dispense(id, "current-pharmacist"),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["pharmacy-pending"] }); Alert.alert("Dispensed", "Medication dispensed successfully"); },
  });

  const verifyMutation = useMutation({
    mutationFn: (id: string) => verifyFiveRights({ prescriptionId: id }),
    onSuccess: (data) => {
      const allPassed = Object.values(data).every(Boolean);
      if (allPassed) { Alert.alert("Verified", "All 5 rights verified. Ready to dispense."); }
      else { Alert.alert("Warning", "Some rights checks failed. Review before dispensing."); }
    },
  });

  if (isLoading) return <Screen><Header title="Pharmacy" /><LoadingSpinner /></Screen>;

  return (
    <Screen><Header title="Pharmacy Dispensing" />
      <View style={styles.container}>
        <Text style={styles.title}>Pending Prescriptions ({(pending as unknown[]).length})</Text>
        <FlatList data={pending as Array<Record<string, unknown>>} keyExtractor={(item) => String(item.id)}
          renderItem={({ item }) => (
            <View style={styles.card}>
              <View style={{ flex: 1 }}>
                <Text style={styles.medName}>{String(item.medication_name ?? "Medication")}</Text>
                <Text style={styles.meta}>{String(item.dosage ?? "")} · {String(item.frequency ?? "")} · {String(item.duration ?? "")}</Text>
                <Text style={styles.meta}>Patient: {String(item.patient_name ?? item.patient_id ?? "")}</Text>
              </View>
              <View style={styles.actions}>
                <TouchableOpacity style={styles.verifyBtn} onPress={() => verifyMutation.mutate(String(item.id))}>
                  <Text style={styles.btnText}>Verify</Text>
                </TouchableOpacity>
                <TouchableOpacity style={styles.dispenseBtn} onPress={() => dispenseMutation.mutate(String(item.id))}>
                  <Text style={styles.btnText}>Dispense</Text>
                </TouchableOpacity>
              </View>
            </View>
          )} />
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 16, gap: 8 },
  title: { fontSize: 16, fontWeight: "700", color: "#111827" },
  card: { flexDirection: "row", backgroundColor: "#FFF", borderRadius: 8, padding: 12, marginBottom: 6, borderWidth: 1, borderColor: "#E5E7EB" },
  medName: { fontSize: 14, fontWeight: "600", color: "#111827" },
  meta: { fontSize: 12, color: "#6B7280" },
  actions: { gap: 4, justifyContent: "center" },
  verifyBtn: { backgroundColor: "#F59E0B", paddingHorizontal: 10, paddingVertical: 6, borderRadius: 6 },
  dispenseBtn: { backgroundColor: "#22C55E", paddingHorizontal: 10, paddingVertical: 6, borderRadius: 6 },
  btnText: { color: "#FFF", fontSize: 11, fontWeight: "600", textAlign: "center" },
});
