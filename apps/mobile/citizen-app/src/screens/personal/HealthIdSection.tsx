import React, { useState } from "react";
import { View, Text, TouchableOpacity, TextInput, StyleSheet, Alert } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Button, LoadingSpinner, ErrorState } from "@impilo/mobile-design-system";
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
          <View style={styles.cardTopStrip}>
            <Text style={styles.chipLabel}>NATIONAL HEALTH ID</Text>
            <View style={styles.statusPill}>
              <View style={styles.statusDot} />
              <Text style={styles.statusText}>{healthId.status}</Text>
            </View>
          </View>
          <Text style={styles.idNumber}>{healthId.healthIdNumber}</Text>
          <Text style={styles.cardName}>{profile?.givenName} {profile?.familyName}</Text>

          {healthId.bloodType ? (
            <View style={styles.bloodTypePill}>
              <Ionicons name="water" size={13} color="#EF4444" />
              <Text style={styles.bloodTypeText}>{healthId.bloodType}</Text>
            </View>
          ) : null}

          {healthId.emergencyContactName ? (
            <View style={styles.emergencyRow}>
              <View style={styles.emergencyIconCircle}>
                <Ionicons name="warning" size={14} color="#F59E0B" />
              </View>
              <View>
                <Text style={styles.emergencyLabel}>Emergency Contact</Text>
                <Text style={styles.emergencyValue}>{healthId.emergencyContactName} · {healthId.emergencyContactPhone}</Text>
              </View>
            </View>
          ) : null}

          {healthId.allergiesSummary ? (
            <View style={styles.allergiesRow}>
              <Ionicons name="medical-outline" size={13} color="#93C5FD" />
              <Text style={styles.allergiesText}>{healthId.allergiesSummary}</Text>
            </View>
          ) : null}

          <Text style={styles.expiryText}>
            Issued: {new Date(healthId.issuedAt).toLocaleDateString()} · Expires: {healthId.expiresAt ? new Date(healthId.expiresAt).toLocaleDateString() : "N/A"}
          </Text>

          <View style={styles.qrBox}>
            <Ionicons name="lock-closed" size={18} color="#9CA3AF" />
            <Text style={styles.qrLabel}>QR Code</Text>
            <Text style={styles.qrIdText}>{healthId.qrCodeData}</Text>
          </View>
        </View>
      </View>
    );
  }

  if (showCreate) {
    return (
      <View style={styles.container}>
        <View style={styles.formCard}>
          <Text style={styles.formTitle}>Create Your Health ID</Text>
          <View style={styles.formFields}>
            <View style={styles.inputGroup}>
              <Text style={styles.inputLabel}>Blood Type</Text>
              <TextInput
                style={styles.input}
                placeholder="e.g. O+"
                placeholderTextColor="#9CA3AF"
                value={form.bloodType}
                onChangeText={(v) => setForm({ ...form, bloodType: v })}
              />
            </View>
            <View style={styles.inputGroup}>
              <Text style={styles.inputLabel}>Known Allergies</Text>
              <TextInput
                style={styles.input}
                placeholder="List known allergies"
                placeholderTextColor="#9CA3AF"
                value={form.allergiesSummary}
                onChangeText={(v) => setForm({ ...form, allergiesSummary: v })}
              />
            </View>
            <View style={styles.inputGroup}>
              <Text style={styles.inputLabel}>Emergency Contact Name</Text>
              <TextInput
                style={styles.input}
                placeholder="Full name"
                placeholderTextColor="#9CA3AF"
                value={form.emergencyContactName}
                onChangeText={(v) => setForm({ ...form, emergencyContactName: v })}
              />
            </View>
            <View style={styles.inputGroup}>
              <Text style={styles.inputLabel}>Emergency Contact Phone</Text>
              <TextInput
                style={styles.input}
                placeholder="+263..."
                placeholderTextColor="#9CA3AF"
                value={form.emergencyContactPhone}
                onChangeText={(v) => setForm({ ...form, emergencyContactPhone: v })}
                keyboardType="phone-pad"
              />
            </View>
          </View>
          <Button
            title={createMutation.isPending ? "Creating..." : "Create Health ID"}
            onPress={() => createMutation.mutate()}
            disabled={createMutation.isPending}
          />
          <TouchableOpacity onPress={() => setShowCreate(false)} style={styles.cancelButton}>
            <Text style={styles.cancelText}>Cancel</Text>
          </TouchableOpacity>
        </View>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <View style={styles.emptyState}>
        <Ionicons name="id-card-outline" size={56} color="#D1D5DB" />
        <Text style={styles.emptyTitle}>No Health ID Yet</Text>
        <Text style={styles.emptyText}>Create your digital health card to access services faster.</Text>
        <Button title="Create Health ID" onPress={() => setShowCreate(true)} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { gap: 16 },
  card: {
    backgroundColor: "#1E40AF",
    borderRadius: 20,
    padding: 20,
    gap: 10,
    shadowColor: "#1E40AF",
    shadowOffset: { width: 0, height: 6 },
    shadowOpacity: 0.3,
    shadowRadius: 16,
    elevation: 8,
  },
  cardTopStrip: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    paddingBottom: 12,
    borderBottomWidth: 1,
    borderBottomColor: "rgba(147,197,253,0.3)",
    marginBottom: 4,
  },
  chipLabel: {
    color: "#93C5FD",
    fontSize: 10,
    fontWeight: "700",
    letterSpacing: 2,
  },
  statusPill: {
    flexDirection: "row",
    alignItems: "center",
    gap: 5,
    backgroundColor: "rgba(255,255,255,0.15)",
    borderRadius: 10,
    paddingVertical: 3,
    paddingHorizontal: 8,
  },
  statusDot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: "#34D399",
  },
  statusText: {
    color: "#FFFFFF",
    fontSize: 11,
    fontWeight: "600",
  },
  idNumber: {
    color: "#FFFFFF",
    fontSize: 22,
    fontWeight: "700",
    letterSpacing: 1.5,
    fontFamily: "monospace",
  },
  cardName: {
    color: "#DBEAFE",
    fontSize: 16,
    fontWeight: "600",
  },
  bloodTypePill: {
    flexDirection: "row",
    alignItems: "center",
    gap: 5,
    backgroundColor: "rgba(255,255,255,0.12)",
    borderRadius: 12,
    paddingVertical: 4,
    paddingHorizontal: 10,
    alignSelf: "flex-start",
  },
  bloodTypeText: {
    color: "#FCA5A5",
    fontSize: 13,
    fontWeight: "700",
  },
  emergencyRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    backgroundColor: "rgba(255,255,255,0.08)",
    borderRadius: 10,
    padding: 10,
  },
  emergencyIconCircle: {
    width: 30,
    height: 30,
    borderRadius: 15,
    backgroundColor: "rgba(245,158,11,0.2)",
    alignItems: "center",
    justifyContent: "center",
  },
  emergencyLabel: {
    color: "#FCD34D",
    fontSize: 10,
    fontWeight: "700",
    letterSpacing: 0.5,
  },
  emergencyValue: {
    color: "#FEF3C7",
    fontSize: 12,
    marginTop: 1,
  },
  allergiesRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
  },
  allergiesText: {
    color: "#BFDBFE",
    fontSize: 12,
    flex: 1,
  },
  expiryText: {
    color: "#93C5FD",
    fontSize: 10,
    marginTop: 4,
  },
  qrBox: {
    marginTop: 4,
    backgroundColor: "#FFFFFF",
    borderRadius: 12,
    padding: 14,
    alignItems: "center",
    gap: 6,
  },
  qrLabel: {
    fontSize: 11,
    fontWeight: "700",
    color: "#6B7280",
    letterSpacing: 0.5,
  },
  qrIdText: {
    fontSize: 10,
    color: "#9CA3AF",
    fontFamily: "monospace",
    textAlign: "center",
  },
  formCard: {
    backgroundColor: "#FFFFFF",
    borderRadius: 16,
    padding: 20,
    gap: 16,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06,
    shadowRadius: 8,
    elevation: 3,
  },
  formTitle: {
    fontSize: 18,
    fontWeight: "700",
    color: "#111827",
  },
  formFields: {
    gap: 12,
  },
  inputGroup: {
    gap: 6,
  },
  inputLabel: {
    fontSize: 12,
    fontWeight: "600",
    color: "#6B7280",
  },
  input: {
    borderWidth: 1,
    borderColor: "#E5E7EB",
    borderRadius: 12,
    padding: 14,
    fontSize: 14,
    color: "#111827",
    backgroundColor: "#F9FAFB",
  },
  cancelButton: {
    alignItems: "center",
    paddingVertical: 10,
  },
  cancelText: {
    color: "#6B7280",
    fontSize: 14,
    fontWeight: "500",
  },
  emptyState: {
    alignItems: "center",
    paddingVertical: 48,
    gap: 12,
  },
  emptyTitle: {
    fontSize: 18,
    fontWeight: "600",
    color: "#374151",
  },
  emptyText: {
    fontSize: 14,
    color: "#9CA3AF",
    textAlign: "center",
    paddingHorizontal: 24,
  },
});
