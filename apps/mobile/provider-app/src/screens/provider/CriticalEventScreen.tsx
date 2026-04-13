/**
 * CriticalEventScreen — Generic critical event activation with real-time
 * elapsed timer, timestamped action log, and outcome selection.
 */
import React, { useState, useEffect, useCallback } from "react";
import { View, Text, ScrollView, TextInput, TouchableOpacity, StyleSheet, Alert, Animated } from "react-native";
import { Screen, Header, Button, Badge } from "@impilo/mobile-design-system";
import { useMutation } from "@tanstack/react-query";
import { apiClient } from "@impilo/mobile-api-client";

type EventType = "CODE_BLUE" | "RAPID_RESPONSE" | "RESUSCITATION" | "EMERGENCY";
type Outcome = "STABILISED" | "ADMITTED" | "TRANSFERRED" | "ESCALATED" | "DEATH";

const EVENT_TYPES: { id: EventType; label: string; color: string; description: string }[] = [
  { id: "CODE_BLUE", label: "Code Blue", color: "#1D4ED8", description: "Cardiac/respiratory arrest" },
  { id: "RAPID_RESPONSE", label: "Rapid Response", color: "#F59E0B", description: "Acute clinical deterioration" },
  { id: "RESUSCITATION", label: "Resuscitation", color: "#DC2626", description: "Life-threatening emergency" },
  { id: "EMERGENCY", label: "Generic Emergency", color: "#7C3AED", description: "Other critical event" },
];

const OUTCOMES: { id: Outcome; label: string; color: string }[] = [
  { id: "STABILISED", label: "Stabilised", color: "#22C55E" },
  { id: "ADMITTED", label: "Admitted", color: "#3B82F6" },
  { id: "TRANSFERRED", label: "Transferred", color: "#F59E0B" },
  { id: "ESCALATED", label: "Escalated", color: "#F97316" },
  { id: "DEATH", label: "Death", color: "#DC2626" },
];

interface ActionEntry {
  time: string;
  elapsed: string;
  description: string;
  user: string;
}

export function CriticalEventScreen() {
  const [active, setActive] = useState(false);
  const [eventType, setEventType] = useState<EventType>("CODE_BLUE");
  const [activationId, setActivationId] = useState<string | null>(null);
  const [elapsedSecs, setElapsedSecs] = useState(0);
  const [actions, setActions] = useState<ActionEntry[]>([]);
  const [actionInput, setActionInput] = useState("");
  const [outcome, setOutcome] = useState<Outcome>("STABILISED");
  const [location, setLocation] = useState("");

  // Pulsing icon animation
  const pulseAnim = useState(() => new Animated.Value(1))[0];

  // Elapsed timer
  useEffect(() => {
    if (!active) return;
    const t = setInterval(() => setElapsedSecs((s) => s + 1), 1000);
    return () => clearInterval(t);
  }, [active]);

  // Pulse animation
  useEffect(() => {
    if (!active) return;
    const loop = Animated.loop(
      Animated.sequence([
        Animated.timing(pulseAnim, { toValue: 1.2, duration: 600, useNativeDriver: true }),
        Animated.timing(pulseAnim, { toValue: 1, duration: 600, useNativeDriver: true }),
      ])
    );
    loop.start();
    return () => loop.stop();
  }, [active]);

  const fmt = (secs: number) => {
    const h = Math.floor(secs / 3600).toString().padStart(2, "0");
    const m = Math.floor((secs % 3600) / 60).toString().padStart(2, "0");
    const s = (secs % 60).toString().padStart(2, "0");
    return `${h}:${m}:${s}`;
  };

  const activateMut = useMutation({
    mutationFn: () => apiClient.post<{ data: { id: string } }>("/internal/v1/emergency/activate", { protocolType: eventType, location }),
    onSuccess: (data) => {
      setActivationId(data.data.data.id);
      setActive(true);
      setElapsedSecs(0);
      setActions([{ time: new Date().toLocaleTimeString(), elapsed: "00:00:00", description: `${eventType.replace(/_/g, " ")} activated`, user: "System" }]);
    },
  });

  const endMut = useMutation({
    mutationFn: () => apiClient.post(`/internal/v1/emergency/${activationId}/end`, { outcome, notes: actions.map((a) => `[${a.elapsed}] ${a.description}`).join("\n") }),
    onSuccess: () => { setActive(false); Alert.alert("Event Ended", `Outcome: ${outcome}`); },
  });

  const recordAction = useCallback(() => {
    if (!actionInput.trim()) return;
    const entry: ActionEntry = {
      time: new Date().toLocaleTimeString(),
      elapsed: fmt(elapsedSecs),
      description: actionInput.trim(),
      user: "Current User",
    };
    setActions((prev) => [...prev, entry]);
    setActionInput("");
    // Also log to backend
    if (activationId) {
      apiClient.post(`/internal/v1/emergency/${activationId}/action`, { actionType: "INTERVENTION", description: actionInput.trim() });
    }
  }, [actionInput, elapsedSecs, activationId]);

  const eventConfig = EVENT_TYPES.find((e) => e.id === eventType)!;

  // ── INACTIVE STATE ──
  if (!active) {
    return (
      <Screen><Header title="Critical Event" />
        <ScrollView style={st.scroll} contentContainerStyle={st.pad}>
          <Text style={st.title}>Activate Critical Event</Text>
          {EVENT_TYPES.map((et) => (
            <TouchableOpacity key={et.id} onPress={() => setEventType(et.id)}
              style={[st.eventCard, eventType === et.id && { borderColor: et.color, borderWidth: 2 }]}>
              <View style={[st.eventDot, { backgroundColor: et.color }]} />
              <View style={{ flex: 1 }}>
                <Text style={st.eventLabel}>{et.label}</Text>
                <Text style={st.eventDesc}>{et.description}</Text>
              </View>
            </TouchableOpacity>
          ))}
          <TextInput style={st.input} placeholder="Location (e.g., Ward 3, Bay 2)" value={location} onChangeText={setLocation} />
          <TouchableOpacity style={[st.activateBtn, { backgroundColor: eventConfig.color }]} onPress={() => activateMut.mutate()}>
            <Text style={st.activateBtnText}>ACTIVATE {eventConfig.label.toUpperCase()}</Text>
          </TouchableOpacity>
        </ScrollView>
      </Screen>
    );
  }

  // ── ACTIVE STATE ──
  return (
    <Screen><Header title="CRITICAL EVENT ACTIVE" />
      {/* Header bar with timer */}
      <View style={[st.activeHeader, { backgroundColor: eventConfig.color }]}>
        <View style={st.activeHeaderLeft}>
          <Animated.Text style={[st.activeIcon, { transform: [{ scale: pulseAnim }] }]}>⚡</Animated.Text>
          <View>
            <Text style={st.activeLabel}>{eventConfig.label}</Text>
            {location ? <Text style={st.activeLocation}>{location}</Text> : null}
          </View>
        </View>
        <View style={st.timerBox}>
          <Text style={st.timerLabel}>ELAPSED</Text>
          <Text style={st.timerValue}>{fmt(elapsedSecs)}</Text>
        </View>
        <Badge label="ACTIVE" variant="destructive" />
      </View>

      <ScrollView style={st.scroll} contentContainerStyle={st.pad}>
        {/* Action log */}
        <Text style={st.sectionTitle}>Time-Critical Actions</Text>
        <View style={st.actionLog}>
          {actions.map((a, i) => (
            <View key={i} style={st.actionRow}>
              <Text style={st.actionTime}>{a.elapsed}</Text>
              <View style={st.actionDivider} />
              <View style={{ flex: 1 }}>
                <Text style={st.actionDesc}>{a.description}</Text>
                <Text style={st.actionUser}>{a.user} · {a.time}</Text>
              </View>
            </View>
          ))}
        </View>

        {/* Record new action */}
        <View style={st.newActionRow}>
          <TextInput style={[st.input, { flex: 1 }]} placeholder="Record action..." value={actionInput} onChangeText={setActionInput} onSubmitEditing={recordAction} />
          <TouchableOpacity style={st.recordBtn} onPress={recordAction}>
            <Text style={st.recordBtnText}>+ Record</Text>
          </TouchableOpacity>
        </View>

        {/* End event */}
        <Text style={st.sectionTitle}>End Critical Event</Text>
        <View style={st.outcomeRow}>
          {OUTCOMES.map((o) => (
            <TouchableOpacity key={o.id} onPress={() => setOutcome(o.id)}
              style={[st.outcomeBtn, outcome === o.id && { backgroundColor: o.color, borderColor: o.color }]}>
              <Text style={[st.outcomeBtnText, outcome === o.id && { color: "#FFF" }]}>{o.label}</Text>
            </TouchableOpacity>
          ))}
        </View>
        <Button title={`End Event — ${outcome}`} onPress={() => endMut.mutate()} variant="outline" />
      </ScrollView>
    </Screen>
  );
}

const st = StyleSheet.create({
  scroll: { flex: 1 },
  pad: { padding: 16, gap: 12, paddingBottom: 32 },
  title: { fontSize: 18, fontWeight: "700", color: "#111827" },
  sectionTitle: { fontSize: 14, fontWeight: "700", color: "#111827" },
  eventCard: { flexDirection: "row", alignItems: "center", gap: 12, backgroundColor: "#FFF", borderRadius: 12, padding: 14, borderWidth: 1, borderColor: "#E5E7EB" },
  eventDot: { width: 12, height: 12, borderRadius: 6 },
  eventLabel: { fontSize: 15, fontWeight: "600", color: "#111827" },
  eventDesc: { fontSize: 12, color: "#6B7280" },
  input: { borderWidth: 1, borderColor: "#D1D5DB", borderRadius: 8, padding: 10, fontSize: 14 },
  activateBtn: { borderRadius: 16, padding: 20, alignItems: "center" },
  activateBtnText: { color: "#FFF", fontSize: 18, fontWeight: "900" },
  activeHeader: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", padding: 14, gap: 8 },
  activeHeaderLeft: { flexDirection: "row", alignItems: "center", gap: 8 },
  activeIcon: { fontSize: 24 },
  activeLabel: { color: "#FFF", fontSize: 15, fontWeight: "700" },
  activeLocation: { color: "rgba(255,255,255,0.8)", fontSize: 11 },
  timerBox: { alignItems: "center" },
  timerLabel: { color: "rgba(255,255,255,0.7)", fontSize: 9, fontWeight: "600" },
  timerValue: { color: "#FFF", fontSize: 22, fontWeight: "900", fontVariant: ["tabular-nums"] },
  actionLog: { gap: 0 },
  actionRow: { flexDirection: "row", gap: 8, paddingVertical: 8, borderBottomWidth: 1, borderBottomColor: "#F3F4F6" },
  actionTime: { width: 60, fontSize: 12, fontWeight: "700", color: "#2563EB", fontVariant: ["tabular-nums"], fontFamily: "monospace" },
  actionDivider: { width: 2, backgroundColor: "#E5E7EB", borderRadius: 1 },
  actionDesc: { fontSize: 13, fontWeight: "500", color: "#111827" },
  actionUser: { fontSize: 10, color: "#9CA3AF" },
  newActionRow: { flexDirection: "row", gap: 8 },
  recordBtn: { backgroundColor: "#2563EB", paddingHorizontal: 14, borderRadius: 8, justifyContent: "center" },
  recordBtnText: { color: "#FFF", fontSize: 13, fontWeight: "600" },
  outcomeRow: { flexDirection: "row", gap: 6, flexWrap: "wrap" },
  outcomeBtn: { paddingHorizontal: 12, paddingVertical: 8, borderRadius: 8, borderWidth: 2, borderColor: "#E5E7EB" },
  outcomeBtnText: { fontSize: 12, fontWeight: "600", color: "#374151" },
});
