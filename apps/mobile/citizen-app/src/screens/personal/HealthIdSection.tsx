import React, { useState } from "react";
import {
  View, Text, TextInput, ScrollView,
  Pressable, StyleSheet, Alert,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Button, LoadingSpinner } from "@impilo/mobile-design-system";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { fetchHealthId, createHealthId } from "../../services/healthIdService";
import { useAppStore } from "../../stores/appStore";
import { USE_SEED_DATA, SEED_HEALTH_ID } from "../../data/seedHealthRecords";
import {
  APP_GREEN, APP_GREEN_DARK, APP_GREEN_DEEP, APP_GREEN_LIGHT, APP_GREEN_XLIGHT,
  APP_RED, APP_RED_LIGHT, APP_GOLD, APP_GOLD_LIGHT,
  APP_SURFACE, APP_BG, APP_TEXT, APP_TEXT_2, APP_TEXT_3, APP_BORDER,
} from "../../lib/colors";

export function HealthIdSection() {
  const profile      = useAppStore((s) => s.profile);
  const queryClient  = useQueryClient();
  const [showCreate, setShowCreate] = useState(false);
  const [form, setForm] = useState({
    bloodType:            "",
    allergiesSummary:     "",
    emergencyContactName: "",
    emergencyContactPhone:"",
  });

  const { data: healthId, isLoading, error } = useQuery({
    queryKey: ["health-id", profile?.cpid],
    queryFn: async () => {
      if (USE_SEED_DATA) {
        await new Promise((r) => setTimeout(r, 400));
        return SEED_HEALTH_ID;
      }
      return fetchHealthId(profile?.cpid ?? "");
    },
    enabled: USE_SEED_DATA || !!profile?.cpid,
  });

  const createMutation = useMutation({
    mutationFn: () => createHealthId({ patientId: profile!.cpid, ...form }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["health-id"] });
      setShowCreate(false);
      Alert.alert("Success", "Your Health ID has been created!");
    },
  });

  if (isLoading) return <View style={s.centred}><LoadingSpinner size="md" /></View>;

  if (error && !USE_SEED_DATA) {
    return (
      <View style={s.centred}>
        <Ionicons name="alert-circle-outline" size={48} color={APP_RED} />
        <Text style={s.errorTitle}>Could not load Health ID</Text>
        <Pressable onPress={() => queryClient.invalidateQueries({ queryKey: ["health-id"] })} style={s.retryBtn}>
          <Text style={s.retryText}>Retry</Text>
        </Pressable>
      </View>
    );
  }

  // ── Create form ────────────────────────────────────────────────────────────
  if (showCreate) {
    return (
      <ScrollView style={s.root} contentContainerStyle={s.content}>
        <View style={s.formCard}>
          <Text style={s.formTitle}>Create Your Health ID</Text>
          <Text style={s.formSub}>This creates your digital health card used across all Impilo services.</Text>

          {(["bloodType", "allergiesSummary", "emergencyContactName", "emergencyContactPhone"] as const).map((field) => {
            const labels: Record<string, string> = {
              bloodType:             "Blood Type",
              allergiesSummary:      "Known Allergies",
              emergencyContactName:  "Emergency Contact Name",
              emergencyContactPhone: "Emergency Contact Phone",
            };
            const placeholders: Record<string, string> = {
              bloodType:             "e.g. O+",
              allergiesSummary:      "e.g. Penicillin, Sulfonamides",
              emergencyContactName:  "Full name",
              emergencyContactPhone: "+263 7X XXX XXXX",
            };
            return (
              <View key={field} style={s.inputGroup}>
                <Text style={s.inputLabel}>{labels[field]}</Text>
                <TextInput
                  style={s.input}
                  placeholder={placeholders[field]}
                  placeholderTextColor={APP_TEXT_3}
                  value={form[field]}
                  onChangeText={(v) => setForm({ ...form, [field]: v })}
                  keyboardType={field === "emergencyContactPhone" ? "phone-pad" : "default"}
                />
              </View>
            );
          })}

          <Button
            title={createMutation.isPending ? "Creating..." : "Create Health ID"}
            onPress={() => createMutation.mutate()}
            disabled={createMutation.isPending}
          />
          <Pressable onPress={() => setShowCreate(false)} style={s.cancelBtn}>
            <Text style={s.cancelText}>Cancel</Text>
          </Pressable>
        </View>
      </ScrollView>
    );
  }

  // ── Health ID card ─────────────────────────────────────────────────────────
  if (healthId) {
    return (
      <ScrollView style={s.root} contentContainerStyle={s.content} showsVerticalScrollIndicator={false}>
        {/* Main card */}
        <View style={s.card}>
          {/* Decorative rings */}
          <View style={s.cardRing1} />
          <View style={s.cardRing2} />

          {/* Header row */}
          <View style={s.cardHeader}>
            <View style={s.cardHeaderLeft}>
              <Text style={s.cardChipLabel}>NATIONAL HEALTH ID</Text>
              <View style={s.statusPill}>
                <View style={[s.statusDot, { backgroundColor: healthId.status === "ACTIVE" ? "#34D399" : APP_GOLD }]} />
                <Text style={s.statusText}>{healthId.status}</Text>
              </View>
            </View>
            <View style={s.cardIconWrap}>
              <Ionicons name="id-card" size={24} color="rgba(255,255,255,0.9)" />
            </View>
          </View>

          {/* ID number */}
          <Text style={s.idNumber}>{healthId.healthIdNumber}</Text>
          <Text style={s.cardName}>{profile?.givenName} {profile?.familyName}</Text>

          {/* Meta chips */}
          <View style={s.cardChips}>
            {healthId.bloodType ? (
              <View style={s.bloodChip}>
                <Ionicons name="water" size={12} color="#FCA5A5" />
                <Text style={s.bloodChipText}>{healthId.bloodType}</Text>
              </View>
            ) : null}
            <View style={s.issuedChip}>
              <Text style={s.issuedChipText}>
                Issued {new Date(healthId.issuedAt).toLocaleDateString([], { month: "short", year: "numeric" })}
              </Text>
            </View>
          </View>

          {/* Expiry */}
          {healthId.expiresAt ? (
            <Text style={s.expiryText}>
              Expires {new Date(healthId.expiresAt).toLocaleDateString([], { day: "numeric", month: "long", year: "numeric" })}
            </Text>
          ) : null}
        </View>

        {/* QR Code panel */}
        <View style={s.qrPanel}>
          <View style={s.qrIconBox}>
            <Ionicons name="qr-code-outline" size={56} color={APP_GREEN} />
          </View>
          <View style={s.qrInfo}>
            <Text style={s.qrTitle}>QR Code</Text>
            <Text style={s.qrSub} numberOfLines={2}>{healthId.qrCodeData}</Text>
            <Text style={s.qrHint}>Show this at any Impilo-registered facility</Text>
          </View>
        </View>

        {/* Allergies */}
        {healthId.allergiesSummary ? (
          <View style={s.infoCard}>
            <View style={[s.infoIcon, { backgroundColor: APP_RED_LIGHT }]}>
              <Ionicons name="medical" size={18} color={APP_RED} />
            </View>
            <View style={s.infoBody}>
              <Text style={s.infoLabel}>Allergy Alert</Text>
              <Text style={s.infoValue}>{healthId.allergiesSummary}</Text>
            </View>
          </View>
        ) : null}

        {/* Emergency contact */}
        {healthId.emergencyContactName ? (
          <View style={s.infoCard}>
            <View style={[s.infoIcon, { backgroundColor: APP_GOLD_LIGHT }]}>
              <Ionicons name="warning" size={18} color={APP_GOLD} />
            </View>
            <View style={s.infoBody}>
              <Text style={s.infoLabel}>Emergency Contact</Text>
              <Text style={s.infoValue}>{healthId.emergencyContactName}</Text>
              <Text style={s.infoSub}>{healthId.emergencyContactPhone}</Text>
            </View>
          </View>
        ) : null}
      </ScrollView>
    );
  }

  // ── No Health ID ───────────────────────────────────────────────────────────
  return (
    <View style={[s.root, s.centred]}>
      <Ionicons name="id-card-outline" size={64} color={APP_GREEN_LIGHT} />
      <Text style={s.emptyTitle}>No Health ID yet</Text>
      <Text style={s.emptySub}>Create your digital health card to access services faster across Zimbabwe.</Text>
      <Pressable onPress={() => setShowCreate(true)} style={s.createBtn}>
        <Ionicons name="add-circle-outline" size={18} color="#FFFFFF" />
        <Text style={s.createBtnText}>Create Health ID</Text>
      </Pressable>
    </View>
  );
}

const s = StyleSheet.create({
  root: { flex: 1, backgroundColor: APP_BG },
  content: { padding: 16, gap: 14, paddingBottom: 48 },
  centred: { flex: 1, alignItems: "center", justifyContent: "center", padding: 32, gap: 14 },

  /* Card */
  card: {
    backgroundColor: APP_GREEN,
    borderRadius: 24,
    padding: 22,
    gap: 10,
    overflow: "hidden",
    shadowColor: APP_GREEN_DEEP,
    shadowOffset: { width: 0, height: 8 },
    shadowOpacity: 0.4,
    shadowRadius: 20,
    elevation: 12,
  },
  cardRing1: {
    position: "absolute", width: 180, height: 180, borderRadius: 90,
    borderWidth: 28, borderColor: "rgba(255,255,255,0.07)", top: -60, right: -50,
  },
  cardRing2: {
    position: "absolute", width: 100, height: 100, borderRadius: 50,
    borderWidth: 18, borderColor: "rgba(255,255,255,0.05)", bottom: -20, left: -20,
  },
  cardHeader: { flexDirection: "row", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 4 },
  cardHeaderLeft: { gap: 6 },
  cardChipLabel: { fontSize: 9, fontWeight: "800", color: "rgba(255,255,255,0.6)", letterSpacing: 2 },
  statusPill: {
    flexDirection: "row", alignItems: "center", gap: 5,
    backgroundColor: "rgba(255,255,255,0.15)", borderRadius: 10,
    paddingVertical: 3, paddingHorizontal: 8, alignSelf: "flex-start",
  },
  statusDot: { width: 6, height: 6, borderRadius: 3 },
  statusText: { color: "#FFFFFF", fontSize: 11, fontWeight: "600" },
  cardIconWrap: {
    width: 44, height: 44, borderRadius: 14,
    backgroundColor: "rgba(255,255,255,0.15)", alignItems: "center", justifyContent: "center",
  },
  idNumber: { color: "#FFFFFF", fontSize: 20, fontWeight: "700", letterSpacing: 2, fontFamily: "monospace" },
  cardName: { color: "rgba(255,255,255,0.9)", fontSize: 16, fontWeight: "600" },
  cardChips: { flexDirection: "row", gap: 8, marginTop: 4 },
  bloodChip: {
    flexDirection: "row", alignItems: "center", gap: 4,
    backgroundColor: "rgba(255,255,255,0.15)", borderRadius: 10,
    paddingVertical: 4, paddingHorizontal: 10,
  },
  bloodChipText: { color: "#FCA5A5", fontSize: 12, fontWeight: "700" },
  issuedChip: {
    backgroundColor: "rgba(255,255,255,0.12)", borderRadius: 10,
    paddingVertical: 4, paddingHorizontal: 10,
  },
  issuedChipText: { color: "rgba(255,255,255,0.75)", fontSize: 11 },
  expiryText: { fontSize: 11, color: "rgba(255,255,255,0.55)", marginTop: 2 },

  /* QR panel */
  qrPanel: {
    flexDirection: "row", alignItems: "center", gap: 16,
    backgroundColor: APP_SURFACE, borderRadius: 18, padding: 18,
    shadowColor: "#000", shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06, shadowRadius: 8, elevation: 3,
  },
  qrIconBox: {
    width: 80, height: 80, borderRadius: 16,
    backgroundColor: APP_GREEN_XLIGHT, alignItems: "center", justifyContent: "center",
  },
  qrInfo: { flex: 1 },
  qrTitle: { fontSize: 15, fontWeight: "700", color: APP_TEXT },
  qrSub: { fontSize: 11, color: APP_TEXT_3, fontFamily: "monospace", marginTop: 3 },
  qrHint: { fontSize: 12, color: APP_TEXT_2, marginTop: 6 },

  /* Info cards */
  infoCard: {
    flexDirection: "row", alignItems: "flex-start", gap: 14,
    backgroundColor: APP_SURFACE, borderRadius: 16, padding: 16,
    shadowColor: "#000", shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05, shadowRadius: 4, elevation: 2,
  },
  infoIcon: { width: 42, height: 42, borderRadius: 12, alignItems: "center", justifyContent: "center" },
  infoBody: { flex: 1 },
  infoLabel: { fontSize: 11, fontWeight: "700", color: APP_TEXT_3, textTransform: "uppercase", letterSpacing: 0.5, marginBottom: 3 },
  infoValue: { fontSize: 14, fontWeight: "600", color: APP_TEXT },
  infoSub: { fontSize: 13, color: APP_TEXT_2, marginTop: 2 },

  /* Form */
  formCard: {
    backgroundColor: APP_SURFACE, borderRadius: 18, padding: 20, gap: 16,
    shadowColor: "#000", shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06, shadowRadius: 8, elevation: 3,
  },
  formTitle: { fontSize: 18, fontWeight: "800", color: APP_TEXT },
  formSub: { fontSize: 13, color: APP_TEXT_2, marginTop: -6, lineHeight: 18 },
  inputGroup: { gap: 6 },
  inputLabel: { fontSize: 12, fontWeight: "600", color: APP_TEXT_2 },
  input: {
    borderWidth: 1, borderColor: APP_BORDER, borderRadius: 12,
    padding: 14, fontSize: 14, color: APP_TEXT, backgroundColor: APP_BG,
  },
  cancelBtn: { alignItems: "center", paddingVertical: 10 },
  cancelText: { color: APP_TEXT_2, fontSize: 14, fontWeight: "500" },

  /* Empty & error */
  emptyTitle: { fontSize: 18, fontWeight: "700", color: APP_TEXT, textAlign: "center" },
  emptySub: { fontSize: 13, color: APP_TEXT_3, textAlign: "center", lineHeight: 19, paddingHorizontal: 16 },
  createBtn: {
    flexDirection: "row", alignItems: "center", gap: 8,
    backgroundColor: APP_GREEN, paddingHorizontal: 24, paddingVertical: 14, borderRadius: 16,
  },
  createBtnText: { fontSize: 15, fontWeight: "700", color: "#FFFFFF" },
  errorTitle: { fontSize: 16, fontWeight: "600", color: APP_TEXT },
  retryBtn: { backgroundColor: APP_GREEN_LIGHT, paddingHorizontal: 24, paddingVertical: 12, borderRadius: 12 },
  retryText: { fontSize: 14, fontWeight: "700", color: APP_GREEN },
});
