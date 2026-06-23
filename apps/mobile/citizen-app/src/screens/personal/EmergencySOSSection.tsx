/**
 * EmergencySOSSection — Hold-to-activate SOS, emergency contacts with call shortcuts.
 */
import React, { useState, useRef } from "react";
import {
  View, Text, TextInput, StyleSheet, Alert,
  Animated, Linking, Pressable, ScrollView,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { LoadingSpinner } from "@impilo/mobile-design-system";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  fetchEmergencyContacts, addEmergencyContact,
  deleteEmergencyContact, triggerSOS,
} from "../../services/sosService";
import { useAppStore } from "../../stores/appStore";
import {
  APP_RED, APP_RED_LIGHT, APP_TEXT, APP_TEXT_2, APP_TEXT_3,
  APP_BORDER, APP_SURFACE, APP_BG, APP_GREEN, APP_GREEN_XLIGHT,
} from "../../lib/colors";

const HOLD_MS = 3000;
const AVATAR_COLORS = ["#E53935", "#1E88E5", "#43A047", "#FB8C00", "#8E24AA", "#00897B"];

function InitialsAvatar({ name, colorIdx }: { name: string; colorIdx: number }) {
  const color = AVATAR_COLORS[colorIdx % AVATAR_COLORS.length];
  const initials = name
    .split(" ")
    .map((n) => n[0] ?? "")
    .slice(0, 2)
    .join("")
    .toUpperCase();
  return (
    <View style={[av.wrap, { backgroundColor: color + "22", borderColor: color + "55" }]}>
      <Text style={[av.text, { color }]}>{initials}</Text>
    </View>
  );
}

export function EmergencySOSSection() {
  const profile = useAppStore((s) => s.profile);
  const patientId = profile?.cpid ?? "";
  const queryClient = useQueryClient();

  const [showAdd, setShowAdd] = useState(false);
  const [form, setForm] = useState({ name: "", relationship: "", phone: "" });

  // Hold-to-activate state
  const holdProgress = useRef(new Animated.Value(0)).current;
  const holdAnim = useRef<Animated.CompositeAnimation | null>(null);
  const countdownTimer = useRef<ReturnType<typeof setInterval> | null>(null);
  const [holding, setHolding] = useState(false);
  const [countdown, setCountdown] = useState(3);

  const { data: contacts = [], isLoading } = useQuery({
    queryKey: ["sos-contacts", patientId],
    queryFn: () => fetchEmergencyContacts(patientId),
    enabled: !!patientId,
  });

  const addMutation = useMutation({
    mutationFn: () => addEmergencyContact({ patientId, ...form }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["sos-contacts"] });
      setShowAdd(false);
      setForm({ name: "", relationship: "", phone: "" });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteEmergencyContact(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["sos-contacts"] }),
  });

  const sosMutation = useMutation({
    mutationFn: () => triggerSOS({ patientId, alertType: "MEDICAL" }),
    onSuccess: (data) => Alert.alert("SOS Activated", data.message),
  });

  const startHold = () => {
    setHolding(true);
    setCountdown(3);
    holdProgress.setValue(0);

    countdownTimer.current = setInterval(() => {
      setCountdown((c) => (c > 1 ? c - 1 : c));
    }, 1000);

    holdAnim.current = Animated.timing(holdProgress, {
      toValue: 1,
      duration: HOLD_MS,
      useNativeDriver: false,
    });

    holdAnim.current.start(({ finished }) => {
      if (countdownTimer.current) clearInterval(countdownTimer.current);
      if (finished) sosMutation.mutate();
      setHolding(false);
      holdProgress.setValue(0);
      setCountdown(3);
    });
  };

  const cancelHold = () => {
    holdAnim.current?.stop();
    if (countdownTimer.current) clearInterval(countdownTimer.current);
    holdProgress.setValue(0);
    setHolding(false);
    setCountdown(3);
  };

  const progressWidth = holdProgress.interpolate({
    inputRange: [0, 1],
    outputRange: ["0%", "100%"],
  });

  const buttonScale = holdProgress.interpolate({
    inputRange: [0, 1],
    outputRange: [1, 1.06],
  });

  if (isLoading) return <LoadingSpinner />;

  return (
    <ScrollView
      contentContainerStyle={s.scroll}
      showsVerticalScrollIndicator={false}
    >
      {/* ── SOS Trigger ─────────────────────────────────────────────── */}
      <View style={s.sosSection}>
        <Text style={s.sectionLabel}>EMERGENCY ACTIVATION</Text>

        <Animated.View style={{ transform: [{ scale: buttonScale }] }}>
          <Pressable
            onPressIn={startHold}
            onPressOut={cancelHold}
            style={[s.sosButton, holding && s.sosButtonActive]}
          >
            {/* Outer pulse ring */}
            <View style={s.pulseRing} />
            <Ionicons name="call" size={38} color="#FFFFFF" />
            <Text style={s.sosLabel}>SOS</Text>
            <Text style={s.sosHint}>
              {holding ? `Activating in ${countdown}s…` : "Hold 3 sec to activate"}
            </Text>
          </Pressable>
        </Animated.View>

        {/* Progress track */}
        <View style={s.progressTrack}>
          <Animated.View style={[s.progressFill, { width: progressWidth }]} />
        </View>

        <Text style={s.sosNote}>
          Alerts emergency services and all your emergency contacts with your location.
        </Text>
      </View>

      {/* ── Emergency Contacts ──────────────────────────────────────── */}
      <View style={s.section}>
        <View style={s.sectionHeader}>
          <Text style={s.sectionTitle}>Emergency Contacts</Text>
          <Pressable
            onPress={() => setShowAdd((v) => !v)}
            style={({ pressed }) => [s.addBtn, pressed && { opacity: 0.75 }]}
          >
            <Ionicons name={showAdd ? "close" : "add"} size={15} color={APP_GREEN} />
            <Text style={s.addBtnText}>{showAdd ? "Cancel" : "Add"}</Text>
          </Pressable>
        </View>

        {showAdd && (
          <View style={s.addForm}>
            <TextInput
              style={s.input}
              placeholder="Full name"
              placeholderTextColor={APP_TEXT_3}
              value={form.name}
              onChangeText={(v) => setForm({ ...form, name: v })}
            />
            <TextInput
              style={s.input}
              placeholder="Relationship (e.g. Mother)"
              placeholderTextColor={APP_TEXT_3}
              value={form.relationship}
              onChangeText={(v) => setForm({ ...form, relationship: v })}
            />
            <TextInput
              style={s.input}
              placeholder="Phone number"
              placeholderTextColor={APP_TEXT_3}
              value={form.phone}
              onChangeText={(v) => setForm({ ...form, phone: v })}
              keyboardType="phone-pad"
            />
            <Pressable
              style={({ pressed }) => [
                s.saveBtn,
                pressed && { opacity: 0.85 },
                (!form.name || !form.phone || addMutation.isPending) && s.saveBtnDisabled,
              ]}
              onPress={() => addMutation.mutate()}
              disabled={!form.name || !form.phone || addMutation.isPending}
            >
              <Text style={s.saveBtnText}>
                {addMutation.isPending ? "Saving…" : "Save Contact"}
              </Text>
            </Pressable>
          </View>
        )}

        {contacts.length === 0 ? (
          <View style={s.emptyState}>
            <View style={s.emptyIcon}>
              <Ionicons name="people-outline" size={28} color={APP_TEXT_3} />
            </View>
            <Text style={s.emptyTitle}>No contacts added yet</Text>
            <Text style={s.emptyText}>
              Add trusted contacts who will be called and notified when SOS is activated.
            </Text>
          </View>
        ) : (
          <View style={s.contactsList}>
            {contacts.map((c, idx) => (
              <View
                key={c.id}
                style={[s.contactRow, idx === 0 && s.contactRowFirst]}
              >
                <InitialsAvatar name={c.name} colorIdx={idx} />

                <View style={s.contactInfo}>
                  <View style={s.nameRow}>
                    <Text style={s.contactName} numberOfLines={1}>{c.name}</Text>
                    {c.isPrimary && (
                      <View style={s.primaryChip}>
                        <Text style={s.primaryChipText}>PRIMARY</Text>
                      </View>
                    )}
                  </View>
                  <Text style={s.contactMeta}>
                    {c.relationship} · {c.phone}
                  </Text>
                </View>

                <View style={s.rowActions}>
                  <Pressable
                    style={({ pressed }) => [s.actionBtn, s.callActionBtn, pressed && { opacity: 0.7 }]}
                    onPress={() => Linking.openURL(`tel:${c.phone}`)}
                  >
                    <Ionicons name="call" size={15} color={APP_GREEN} />
                  </Pressable>
                  <Pressable
                    style={({ pressed }) => [s.actionBtn, s.deleteActionBtn, pressed && { opacity: 0.7 }]}
                    onPress={() =>
                      Alert.alert("Remove Contact", `Remove ${c.name}?`, [
                        { text: "Cancel", style: "cancel" },
                        { text: "Remove", style: "destructive", onPress: () => deleteMutation.mutate(c.id) },
                      ])
                    }
                  >
                    <Ionicons name="trash-outline" size={14} color={APP_RED} />
                  </Pressable>
                </View>
              </View>
            ))}
          </View>
        )}
      </View>
    </ScrollView>
  );
}

// ── Initials avatar ───────────────────────────────────────────────────────────
const av = StyleSheet.create({
  wrap: {
    width: 44, height: 44, borderRadius: 22,
    borderWidth: 1.5, alignItems: "center", justifyContent: "center",
    flexShrink: 0,
  },
  text: { fontSize: 15, fontWeight: "800" },
});

// ── Main styles ───────────────────────────────────────────────────────────────
const s = StyleSheet.create({
  scroll: { padding: 20, gap: 28 },

  sectionLabel: {
    fontSize: 11, fontWeight: "700", color: APP_TEXT_3,
    letterSpacing: 1.2, marginBottom: 16, textAlign: "center",
  },

  // ── SOS trigger ────────────────────────────────────────────────────────────
  sosSection: { alignItems: "center", gap: 16 },

  sosButton: {
    width: 164, height: 164, borderRadius: 82,
    backgroundColor: APP_RED,
    alignItems: "center", justifyContent: "center", gap: 2,
    shadowColor: APP_RED,
    shadowOffset: { width: 0, height: 10 },
    shadowOpacity: 0.45,
    shadowRadius: 24,
    elevation: 14,
  },
  sosButtonActive: {
    shadowOpacity: 0.65,
    shadowRadius: 32,
    elevation: 20,
  },
  pulseRing: {
    position: "absolute",
    width: 188, height: 188, borderRadius: 94,
    borderWidth: 2.5, borderColor: APP_RED + "33",
  },

  sosLabel: { color: "#FFFFFF", fontSize: 30, fontWeight: "900", letterSpacing: 5, marginTop: 4 },
  sosHint: { color: "rgba(255,255,255,0.72)", fontSize: 11, marginTop: 2 },

  progressTrack: {
    width: "80%", height: 4, backgroundColor: APP_RED_LIGHT,
    borderRadius: 2, overflow: "hidden",
  },
  progressFill: { height: "100%", backgroundColor: APP_RED, borderRadius: 2 },

  sosNote: {
    fontSize: 12, color: APP_TEXT_3, textAlign: "center",
    lineHeight: 17, paddingHorizontal: 16,
  },

  // ── Contacts ───────────────────────────────────────────────────────────────
  section: { gap: 12 },
  sectionHeader: {
    flexDirection: "row", alignItems: "center", justifyContent: "space-between",
  },
  sectionTitle: { fontSize: 16, fontWeight: "700", color: APP_TEXT },

  addBtn: {
    flexDirection: "row", alignItems: "center", gap: 4,
    backgroundColor: APP_GREEN_XLIGHT,
    paddingHorizontal: 12, paddingVertical: 6, borderRadius: 20,
  },
  addBtnText: { fontSize: 13, fontWeight: "600", color: APP_GREEN },

  addForm: {
    backgroundColor: APP_SURFACE, borderRadius: 16, padding: 16, gap: 10,
    shadowColor: "#000", shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05, shadowRadius: 6, elevation: 2,
  },
  input: {
    borderWidth: 1, borderColor: APP_BORDER, borderRadius: 10,
    paddingHorizontal: 14, paddingVertical: 12,
    fontSize: 14, color: APP_TEXT, backgroundColor: APP_BG,
  },
  saveBtn: {
    backgroundColor: APP_GREEN, borderRadius: 10,
    paddingVertical: 13, alignItems: "center", marginTop: 2,
  },
  saveBtnDisabled: { opacity: 0.45 },
  saveBtnText: { color: "#FFFFFF", fontWeight: "700", fontSize: 14 },

  contactsList: {
    backgroundColor: APP_SURFACE, borderRadius: 16, overflow: "hidden",
    shadowColor: "#000", shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05, shadowRadius: 6, elevation: 2,
  },
  contactRow: {
    flexDirection: "row", alignItems: "center", gap: 12,
    paddingHorizontal: 16, paddingVertical: 14,
    borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: APP_BORDER,
  },
  contactRowFirst: { borderTopWidth: 0 },
  contactInfo: { flex: 1, minWidth: 0 },
  nameRow: { flexDirection: "row", alignItems: "center", gap: 6, marginBottom: 2 },
  contactName: { fontSize: 15, fontWeight: "600", color: APP_TEXT, flexShrink: 1 },
  primaryChip: {
    backgroundColor: APP_RED_LIGHT, borderRadius: 4,
    paddingHorizontal: 5, paddingVertical: 2,
  },
  primaryChipText: { fontSize: 9, fontWeight: "800", color: APP_RED, letterSpacing: 0.5 },
  contactMeta: { fontSize: 12, color: APP_TEXT_2 },

  rowActions: { flexDirection: "row", gap: 6, alignItems: "center" },
  actionBtn: {
    width: 34, height: 34, borderRadius: 17,
    alignItems: "center", justifyContent: "center",
  },
  callActionBtn: { backgroundColor: APP_GREEN_XLIGHT },
  deleteActionBtn: { backgroundColor: APP_RED_LIGHT },

  emptyState: {
    alignItems: "center", paddingVertical: 36, gap: 8,
    backgroundColor: APP_SURFACE, borderRadius: 16,
  },
  emptyIcon: {
    width: 56, height: 56, borderRadius: 28,
    backgroundColor: APP_BG,
    alignItems: "center", justifyContent: "center",
    marginBottom: 4,
  },
  emptyTitle: { fontSize: 15, fontWeight: "600", color: APP_TEXT_2 },
  emptyText: {
    fontSize: 13, color: APP_TEXT_3, textAlign: "center",
    lineHeight: 18, paddingHorizontal: 28,
  },
});
