/**
 * HealthIdSection — Digital health card display and creation.
 */
import React, { useState } from "react";
import { View, Text, TouchableOpacity, TextInput, StyleSheet, Alert } from "react-native";
import { Card, Button, Badge, LoadingSpinner, ErrorState } from "@impilo/mobile-design-system";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { fetchHealthId, createHealthId } from "../../services/healthIdService";
import { useAppStore } from "../../stores/appStore";

export function HealthIdSection() {
  const profile = useAppStore((s) => s.profile);
  const queryClient = useQueryClient();
  const [showCreate, setShowCreate] = useState(false);
  const [form, setForm] = useState({ bloodType: "", allergiesSummary: "", emergencyContactName: "", emergencyContactPhone: "" });

  const { data: healthId, isLoading, error } = useQuery({
    queryKey: ["health-id", profile?.cpid],
    queryFn: () => fetchHealthId(profile?.cpid ?? ""),
    enabled: !!profile?.cpid,
  });

  const createMutation = useMutation({
    mutationFn: () => createHealthId({ patientId: profile!.cpid, ...form }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["health-id"] });
      setShowCreate(false);
      Alert.alert("Success", "Your Health ID has been created!");
    },
  });

  if (isLoading) return <LoadingSpinner />;
  if (error) return <ErrorState message="Failed to load Health ID" onRetry={() => queryClient.invalidateQueries({ queryKey: ["health-id"] })} />;

  if (healthId) {
    return (
      <View style={styles.container}>
        <View style={styles.card}>
          <View style={styles.cardHeader}>
            <Text style={styles.cardTitle}>IMPILO HEALTH ID</Text>
            <Badge label={healthId.status} variant="success" />
          </View>
          <Text style={styles.idNumber}>{healthId.healthIdNumber}</Text>
          <Text style={styles.name}>{profile?.givenName} {profile?.familyName}</Text>
          {healthId.bloodType ? <Text style={styles.detail}>Blood Type: {healthId.bloodType}</Text> : null}
          {healthId.allergiesSummary ? <Text style={styles.detail}>Allergies: {healthId.allergiesSummary}</Text> : null}
          {healthId.emergencyContactName ? (
            <Text style={styles.detail}>Emergency: {healthId.emergencyContactName} ({healthId.emergencyContactPhone})</Text>
          ) : null}
          <Text style={styles.expiry}>
            Issued: {new Date(healthId.issuedAt).toLocaleDateString()} · Expires: {healthId.expiresAt ? new Date(healthId.expiresAt).toLocaleDateString() : "N/A"}
          </Text>
          <View style={styles.qrPlaceholder}>
            <Text style={styles.qrText}>QR: {healthId.qrCodeData}</Text>
          </View>
        </View>
      </View>
    );
  }

  if (showCreate) {
    return (
      <View style={styles.container}>
        <Text style={styles.sectionTitle}>Create Your Health ID</Text>
        <TextInput style={styles.input} placeholder="Blood Type (e.g., O+)" value={form.bloodType} onChangeText={(v) => setForm({ ...form, bloodType: v })} />
        <TextInput style={styles.input} placeholder="Known Allergies" value={form.allergiesSummary} onChangeText={(v) => setForm({ ...form, allergiesSummary: v })} />
        <TextInput style={styles.input} placeholder="Emergency Contact Name" value={form.emergencyContactName} onChangeText={(v) => setForm({ ...form, emergencyContactName: v })} />
        <TextInput style={styles.input} placeholder="Emergency Contact Phone" value={form.emergencyContactPhone} onChangeText={(v) => setForm({ ...form, emergencyContactPhone: v })} keyboardType="phone-pad" />
        <Button label={createMutation.isPending ? "Creating..." : "Create Health ID"} onPress={() => createMutation.mutate()} disabled={createMutation.isPending} />
        <TouchableOpacity onPress={() => setShowCreate(false)} style={styles.cancelButton}>
          <Text style={styles.cancelText}>Cancel</Text>
        </TouchableOpacity>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <View style={styles.emptyState}>
        <Text style={styles.emptyTitle}>No Health ID Yet</Text>
        <Text style={styles.emptyText}>Create your digital health card to access services faster.</Text>
        <Button label="Create Health ID" onPress={() => setShowCreate(true)} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { gap: 16 },
  card: { backgroundColor: "#1E40AF", borderRadius: 16, padding: 20, gap: 8 },
  cardHeader: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  cardTitle: { color: "#93C5FD", fontSize: 11, fontWeight: "700", letterSpacing: 2 },
  idNumber: { color: "#FFFFFF", fontSize: 22, fontWeight: "700", letterSpacing: 1 },
  name: { color: "#DBEAFE", fontSize: 16, fontWeight: "600" },
  detail: { color: "#BFDBFE", fontSize: 13 },
  expiry: { color: "#93C5FD", fontSize: 11, marginTop: 4 },
  qrPlaceholder: { marginTop: 8, backgroundColor: "#FFFFFF", borderRadius: 8, padding: 12, alignItems: "center" },
  qrText: { fontSize: 10, color: "#6B7280", fontFamily: "monospace" },
  sectionTitle: { fontSize: 18, fontWeight: "700", color: "#111827" },
  input: { borderWidth: 1, borderColor: "#D1D5DB", borderRadius: 8, padding: 12, fontSize: 14 },
  cancelButton: { alignItems: "center", paddingVertical: 12 },
  cancelText: { color: "#6B7280", fontSize: 14 },
  emptyState: { alignItems: "center", paddingVertical: 40, gap: 12 },
  emptyTitle: { fontSize: 18, fontWeight: "600", color: "#374151" },
  emptyText: { fontSize: 14, color: "#6B7280", textAlign: "center" },
});
