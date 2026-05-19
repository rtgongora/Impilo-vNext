/**
 * VitalsMonitorScreen — Real-time vital signs with visual bar sparklines,
 * trend detection, normal/critical ranges, flash alerts, compact/expanded toggle,
 * and card mount animations.
 */
import React, { useState, useEffect, useMemo, useRef } from "react";
import { View, Text, ScrollView, TouchableOpacity, StyleSheet, Animated, LayoutAnimation, Platform, UIManager } from "react-native";
import { Screen, Header, LoadingSpinner, Badge } from "@impilo/mobile-design-system";
import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@impilo/mobile-api-client";
import { useEncounterStore } from "../../stores/encounterStore";

if (Platform.OS === "android" && UIManager.setLayoutAnimationEnabledExperimental) {
  UIManager.setLayoutAnimationEnabledExperimental(true);
}

interface VitalConfig {
  id: string; label: string; unit: string;
  normalMin: number; normalMax: number; criticalMin: number; criticalMax: number;
  color: string; icon: string;
}

const VITAL_CONFIGS: VitalConfig[] = [
  { id: "HEART_RATE", label: "Heart Rate", unit: "bpm", normalMin: 60, normalMax: 100, criticalMin: 40, criticalMax: 150, color: "#DC2626", icon: "❤️" },
  { id: "BLOOD_PRESSURE_SYS", label: "Systolic BP", unit: "mmHg", normalMin: 90, normalMax: 140, criticalMin: 70, criticalMax: 200, color: "#F97316", icon: "🩸" },
  { id: "BLOOD_PRESSURE_DIA", label: "Diastolic BP", unit: "mmHg", normalMin: 60, normalMax: 90, criticalMin: 40, criticalMax: 120, color: "#F59E0B", icon: "🩸" },
  { id: "TEMPERATURE", label: "Temperature", unit: "°C", normalMin: 36.1, normalMax: 38.0, criticalMin: 35.0, criticalMax: 40.0, color: "#EAB308", icon: "🌡️" },
  { id: "RESPIRATORY_RATE", label: "Resp. Rate", unit: "/min", normalMin: 12, normalMax: 20, criticalMin: 8, criticalMax: 30, color: "#8B5CF6", icon: "💨" },
  { id: "OXYGEN_SATURATION", label: "SpO₂", unit: "%", normalMin: 95, normalMax: 100, criticalMin: 88, criticalMax: 100, color: "#3B82F6", icon: "🫁" },
  { id: "BLOOD_GLUCOSE", label: "Blood Glucose", unit: "mmol/L", normalMin: 3.9, normalMax: 7.8, criticalMin: 2.5, criticalMax: 20.0, color: "#22C55E", icon: "🍬" },
];

function getStatus(value: number, config: VitalConfig): "NORMAL" | "WARNING" | "CRITICAL" {
  if (value < config.criticalMin || value > config.criticalMax) return "CRITICAL";
  if (value < config.normalMin || value > config.normalMax) return "WARNING";
  return "NORMAL";
}

function getTrend(values: number[]): "RISING" | "FALLING" | "STABLE" {
  if (values.length < 3) return "STABLE";
  const last3 = values.slice(-3);
  const diff = last3[2] - last3[0];
  if (diff > 2) return "RISING";
  if (diff < -2) return "FALLING";
  return "STABLE";
}

const STATUS_COLORS = { NORMAL: "#22C55E", WARNING: "#F59E0B", CRITICAL: "#DC2626" };
const TREND_ICONS = { RISING: "↑", FALLING: "↓", STABLE: "→" };
const TREND_COLORS = { RISING: "#DC2626", FALLING: "#3B82F6", STABLE: "#6B7280" };

export function VitalsMonitorScreen() {
  const { activeEncounter } = useEncounterStore();
  const pid = activeEncounter?.patientId ?? "";
  const [compact, setCompact] = useState(false);

  const { data: vitals = [], isLoading } = useQuery({
    queryKey: ["vitals-monitor", pid],
    queryFn: async () => {
      const r = await apiClient.get<{ data: Array<Record<string, unknown>> }>(
        `/internal/v1/mobile/provider/vitals?patient_id=${pid}`,
      );
      return r.data.data;
    },
    enabled: !!pid,
    refetchInterval: 15000,
  });

  const groupedVitals: Record<string, number[]> = {};
  const latestValues: Record<string, { value: number; time: string }> = {};
  (vitals as Array<Record<string, unknown>>).forEach((v) => {
    const type = String(v.vital_type ?? v.vitalType ?? "");
    const val = Number(v.value ?? 0);
    if (!groupedVitals[type]) groupedVitals[type] = [];
    groupedVitals[type].push(val);
    if (!latestValues[type] || String(v.measured_at ?? v.measuredAt ?? "") > (latestValues[type]?.time ?? "")) {
      latestValues[type] = { value: val, time: String(v.measured_at ?? v.measuredAt ?? "") };
    }
  });

  const toggleCompact = () => {
    LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
    setCompact(!compact);
  };

  if (isLoading) return <Screen><Header title="Vitals Monitor" /><LoadingSpinner /></Screen>;

  return (
    <Screen><Header title="Vitals Monitor" />
      <View style={st.toolbar}>
        <Text style={st.hint}>Auto-refreshes every 15s</Text>
        <TouchableOpacity onPress={toggleCompact} style={st.toggleBtn}>
          <Text style={st.toggleText}>{compact ? "Expanded" : "Compact"}</Text>
        </TouchableOpacity>
      </View>
      <ScrollView style={st.container} contentContainerStyle={st.content}>
        {compact ? (
          <View style={st.compactGrid}>
            {VITAL_CONFIGS.map((config) => {
              const latest = latestValues[config.id];
              const status = latest ? getStatus(latest.value, config) : "NORMAL";
              return (
                <View key={config.id} style={[st.compactCard, { borderColor: STATUS_COLORS[status] }]}>
                  <Text style={st.compactIcon}>{config.icon}</Text>
                  <Text style={[st.compactValue, { color: STATUS_COLORS[status] }]}>{latest?.value?.toFixed(1) ?? "—"}</Text>
                  <Text style={st.compactUnit}>{config.unit}</Text>
                  <Text style={st.compactLabel}>{config.label}</Text>
                </View>
              );
            })}
          </View>
        ) : (
          <View style={st.grid}>
            {VITAL_CONFIGS.map((config, index) => {
              const latest = latestValues[config.id];
              const values = groupedVitals[config.id] ?? [];
              const status = latest ? getStatus(latest.value, config) : "NORMAL";
              const trend = getTrend(values);
              return (
                <VitalCard key={config.id} config={config} value={latest?.value}
                  lastUpdated={latest?.time} status={status} trend={trend}
                  sparklineValues={values.slice(-12)} index={index} />
              );
            })}
          </View>
        )}
      </ScrollView>
    </Screen>
  );
}

function VitalCard({ config, value, lastUpdated, status, trend, sparklineValues, index }: {
  config: VitalConfig; value?: number; lastUpdated?: string;
  status: "NORMAL" | "WARNING" | "CRITICAL"; trend: "RISING" | "FALLING" | "STABLE";
  sparklineValues: number[]; index: number;
}) {
  // Mount slide-in animation
  const slideAnim = useRef(new Animated.Value(50)).current;
  const fadeAnim = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    Animated.parallel([
      Animated.timing(slideAnim, { toValue: 0, duration: 300, delay: index * 60, useNativeDriver: true }),
      Animated.timing(fadeAnim, { toValue: 1, duration: 300, delay: index * 60, useNativeDriver: true }),
    ]).start();
  }, []);

  // Flash animation for critical values
  const flashAnim = useRef(new Animated.Value(1)).current;
  useEffect(() => {
    if (status === "CRITICAL") {
      const loop = Animated.loop(
        Animated.sequence([
          Animated.timing(flashAnim, { toValue: 0.3, duration: 500, useNativeDriver: true }),
          Animated.timing(flashAnim, { toValue: 1, duration: 500, useNativeDriver: true }),
        ])
      );
      loop.start();
      return () => loop.stop();
    } else {
      flashAnim.setValue(1);
    }
  }, [status]);

  const statusColor = STATUS_COLORS[status];
  const trendColor = TREND_COLORS[trend];

  // Visual bar sparkline
  const sparkBars = useMemo(() => {
    if (sparklineValues.length < 2) return [];
    const min = Math.min(...sparklineValues);
    const max = Math.max(...sparklineValues);
    const range = max - min || 1;
    return sparklineValues.map((v, i) => ({
      height: Math.max(4, ((v - min) / range) * 32),
      isLast: i === sparklineValues.length - 1,
    }));
  }, [sparklineValues]);

  return (
    <Animated.View style={[st.card, { borderColor: statusColor, opacity: Animated.multiply(fadeAnim, flashAnim), transform: [{ translateX: slideAnim }] }]}>
      <View style={st.cardHeader}>
        <Text style={st.cardIcon}>{config.icon}</Text>
        <View style={{ flex: 1 }}>
          <Text style={st.cardLabel}>{config.label}</Text>
          {lastUpdated && <Text style={st.cardTime}>{new Date(lastUpdated).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}</Text>}
        </View>
        <Badge label={status} variant={status === "CRITICAL" ? "destructive" : status === "WARNING" ? "warning" : "success"} />
      </View>

      <View style={st.valueRow}>
        <Text style={[st.value, { color: statusColor }]}>{value != null ? value.toFixed(1) : "—"}</Text>
        <Text style={st.unit}>{config.unit}</Text>
        <View style={[st.trendBadge, { backgroundColor: trendColor + "20" }]}>
          <Text style={[st.trendIcon, { color: trendColor }]}>{TREND_ICONS[trend]}</Text>
          <Text style={[st.trendText, { color: trendColor }]}>{trend.toLowerCase()}</Text>
        </View>
      </View>

      {/* Visual bar sparkline */}
      {sparkBars.length > 0 && (
        <View style={st.sparkContainer}>
          {sparkBars.map((bar, i) => (
            <View key={i} style={[st.sparkBar, {
              height: bar.height,
              backgroundColor: bar.isLast ? config.color : config.color + "60",
            }]} />
          ))}
        </View>
      )}

      <Text style={st.range}>Range: {config.normalMin}–{config.normalMax} {config.unit}</Text>

      {status === "CRITICAL" && (
        <View style={st.alertBanner}>
          <Text style={st.alertText}>⚠️ CRITICAL — Immediate action required</Text>
        </View>
      )}
      {status === "WARNING" && (
        <View style={[st.alertBanner, { backgroundColor: "#FEF3C7" }]}>
          <Text style={[st.alertText, { color: "#92400E" }]}>⚡ Outside normal range</Text>
        </View>
      )}
    </Animated.View>
  );
}

const st = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: 12, gap: 8, paddingBottom: 32 },
  toolbar: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", paddingHorizontal: 16, paddingVertical: 8, borderBottomWidth: 1, borderBottomColor: "#E5E7EB" },
  hint: { fontSize: 11, color: "#9CA3AF" },
  toggleBtn: { backgroundColor: "#F3F4F6", paddingHorizontal: 12, paddingVertical: 6, borderRadius: 14 },
  toggleText: { fontSize: 12, fontWeight: "600", color: "#374151" },
  grid: { gap: 10 },
  compactGrid: { flexDirection: "row", flexWrap: "wrap", gap: 8 },
  compactCard: { width: "47%", backgroundColor: "#FFF", borderRadius: 12, padding: 12, alignItems: "center", borderWidth: 2, gap: 2 },
  compactIcon: { fontSize: 20 },
  compactValue: { fontSize: 24, fontWeight: "900", fontVariant: ["tabular-nums"] },
  compactUnit: { fontSize: 10, color: "#6B7280" },
  compactLabel: { fontSize: 10, color: "#9CA3AF", textAlign: "center" },
  card: { backgroundColor: "#FFF", borderRadius: 14, padding: 14, borderWidth: 2, gap: 6 },
  cardHeader: { flexDirection: "row", alignItems: "center", gap: 8 },
  cardIcon: { fontSize: 20 },
  cardLabel: { fontSize: 13, fontWeight: "600", color: "#111827" },
  cardTime: { fontSize: 10, color: "#9CA3AF" },
  valueRow: { flexDirection: "row", alignItems: "baseline", gap: 6 },
  value: { fontSize: 36, fontWeight: "900", fontVariant: ["tabular-nums"] },
  unit: { fontSize: 14, color: "#6B7280" },
  trendBadge: { flexDirection: "row", alignItems: "center", gap: 2, paddingHorizontal: 8, paddingVertical: 3, borderRadius: 10, marginLeft: "auto" },
  trendIcon: { fontSize: 16, fontWeight: "900" },
  trendText: { fontSize: 11, fontWeight: "600" },
  sparkContainer: { flexDirection: "row", alignItems: "flex-end", gap: 2, height: 36, paddingVertical: 2 },
  sparkBar: { flex: 1, borderRadius: 2, minWidth: 4 },
  range: { fontSize: 10, color: "#9CA3AF" },
  alertBanner: { backgroundColor: "#FEE2E2", borderRadius: 6, padding: 6, marginTop: 2 },
  alertText: { fontSize: 11, fontWeight: "600", color: "#991B1B", textAlign: "center" },
});
