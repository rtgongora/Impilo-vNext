/**
 * PharmacyDispensingScreen — Pending dispensing, 5-rights verification, dispense.
 */
import React, { useState } from "react";
import { View, Text, FlatList, TouchableOpacity, StyleSheet, Alert } from "react-native";
import { Screen, Header, LoadingSpinner, colors } from "@impilo/mobile-design-system";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { authStore } from "@impilo/mobile-auth";
import { fetchPendingDispensing, dispense, verifyFiveRights } from "../../services/queueService";

function pharmacistId(): string {
  const session = authStore.getState().session;
  return session?.providerId ?? session?.actorId ?? "mobile-provider";
}

export function PharmacyDispensingScreen() {
  const queryClient = useQueryClient();
  const { data: pending = [], isLoading } = useQuery({
    queryKey: ["pharmacy-pending"],
    queryFn: fetchPendingDispensing,
  });
  const [verifiedIds, setVerifiedIds] = useState<Set<string>>(new Set());

  const dispenseMutation = useMutation({
    mutationFn: (id: string) => dispense(id, pharmacistId()),
    onSuccess: (_data, id) => {
      setVerifiedIds((prev) => {
        const next = new Set(prev);
        next.delete(id);
        return next;
      });
      queryClient.invalidateQueries({ queryKey: ["pharmacy-pending"] });
      Alert.alert("Dispensed", "Medication dispensed successfully");
    },
  });

  const verifyMutation = useMutation({
    mutationFn: (id: string) => verifyFiveRights({ prescriptionId: id }),
    onSuccess: (data, id) => {
      const allPassed = Object.values(data).every(Boolean);
      if (allPassed) {
        setVerifiedIds((prev) => new Set(prev).add(id));
        Alert.alert("Verified", "All 5 rights verified. Ready to dispense.");
      } else {
        Alert.alert("Warning", "Some rights checks failed. Review before dispensing.");
      }
    },
  });

  if (isLoading) {
    return (
      <Screen>
        <Header title="Pharmacy" />
        <LoadingSpinner />
      </Screen>
    );
  }

  return (
    <Screen>
      <Header title="Pharmacy Dispensing" />
      <View testID="pharmacy-dispensing-screen" style={styles.container}>
        <Text style={styles.title}>Pending Prescriptions ({(pending as unknown[]).length})</Text>
        <FlatList
          data={pending as Array<Record<string, unknown>>}
          keyExtractor={(item) => String(item.id)}
          renderItem={({ item }) => {
            const id = String(item.id);
            const verified = verifiedIds.has(id);
            return (
              <View style={styles.card}>
                <View style={{ flex: 1 }}>
                  <Text style={styles.medName}>{String(item.medication_name ?? "Medication")}</Text>
                  <Text style={styles.meta}>
                    {String(item.dosage ?? "")} · {String(item.frequency ?? "")} · {String(item.duration ?? "")}
                  </Text>
                  <Text style={styles.meta}>Patient: {String(item.patient_name ?? item.patient_id ?? "")}</Text>
                  {verified ? <Text style={styles.verified}>5-rights verified</Text> : null}
                </View>
                <View style={styles.actions}>
                  <TouchableOpacity
                    testID={`pharmacy-verify-${id}`}
                    style={styles.verifyBtn}
                    onPress={() => verifyMutation.mutate(id)}
                  >
                    <Text style={styles.btnText}>Verify</Text>
                  </TouchableOpacity>
                  <TouchableOpacity
                    testID={`pharmacy-dispense-${id}`}
                    style={[styles.dispenseBtn, !verified && styles.dispenseBtnDisabled]}
                    disabled={!verified || dispenseMutation.isPending}
                    onPress={() => {
                      if (!verified) {
                        Alert.alert("Verify first", "Run 5-rights verification before dispensing.");
                        return;
                      }
                      dispenseMutation.mutate(id);
                    }}
                  >
                    <Text style={styles.btnText}>Dispense</Text>
                  </TouchableOpacity>
                </View>
              </View>
            );
          }}
        />
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 16, gap: 8 },
  title: { fontSize: 16, fontWeight: "700", color: colors.gray[900] },
  card: {
    flexDirection: "row",
    backgroundColor: "#FFF",
    borderRadius: 8,
    padding: 12,
    marginBottom: 6,
    borderWidth: 1,
    borderColor: colors.gray[200],
  },
  medName: { fontSize: 14, fontWeight: "600", color: colors.gray[900] },
  meta: { fontSize: 12, color: colors.gray[500] },
  verified: { fontSize: 11, color: "#059669", marginTop: 4, fontWeight: "600" },
  actions: { gap: 4, justifyContent: "center" },
  verifyBtn: { backgroundColor: colors.ui.warning.main, paddingHorizontal: 10, paddingVertical: 6, borderRadius: 6 },
  dispenseBtn: { backgroundColor: colors.ui.success.main, paddingHorizontal: 10, paddingVertical: 6, borderRadius: 6 },
  dispenseBtnDisabled: { backgroundColor: colors.gray[400] },
  btnText: { color: "#FFF", fontSize: 11, fontWeight: "600", textAlign: "center" },
});
