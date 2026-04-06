/**
 * VitalsMonitorScreen — Real-time vital signs with sparkline trends,
 * trend detection, normal/critical ranges, status badges, and flash alerts.
 */
import React, { useState, useEffect, useMemo, useCallback } from "react";
import { View, Text, ScrollView, StyleSheet, Animated } from "react-native";
import { Screen, Header, LoadingSpinner, Badge } from "@impilo/mobile-design-system";
import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@impilo/mobile-api-client";
import { useEncounterStore } from "../../stores/encounterStore";

interface VitalConfig {
  id: string;
  label: string;
  unit: string;
  normalMin: number;
  normalMax: number;
  criticalMin: number;
  criticalMax: number;
  color: string;
  icon: string;
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

  const { data: vitals = [], isLoading } = useQuery({
    queryKey: ["vitals-monitor", pid],
    queryFn: async () => {
      const r = await apiClient.get<{ data: Array<Record<string, unknown>> }>(`/internal/v1/mobile/provider/wellness/vitals?patientId=${pid}`);
      return r.data.data;
    },
    enabled: !!pid,
    refetchInterval: 15000,
  });

  if (isLoading) return <Screen><Header title="Vitals Monitor" /><LoadingSpinner /></Screen>;

  // Group by vital type and extract values
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

  return (
    <Screen><Header title="Vitals Monitor" />
      <ScrollView style={st.container} contentContainerStyle={st.content}>
        <Text style={st.hint}>Auto-refreshes every 15 seconds</Text>
        <View style={st.grid}>
          {VITAL_CONFIGS.map((config) => {
            const latest = latestValues[config.id];
            const values = groupedVitals[config.id] ?? [];
            const status = latest ? getStatus(latest.value, config) : "NORMAL";
            const trend = getTrend(values);

            return (
              <VitalCard
                key={config.id}
                config={config}
                value={latest?.value}
                lastUpdated={latest?.time}
                status={status}
                trend={trend}
                sparklineValues={values.slice(-12)}
              />
            );
          })}
        </View>
      </ScrollView>
    </Screen>
  );
}

function VitalCard({ config, value, lastUpdated, status, trend, sparklineValues }: {
  config: VitalConfig; value?: number; lastUpdated?: string;
  status: "NORMAL" | "WARNING" | "CRITICAL"; trend: "RISING" | "FALLING" | "STABLE";
  sparklineValues: number[];
}) {
  // Flash animation for critical values
  const flashAnim = useMemo(() => new Animated.Value(1), []);

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
  }, [status, flashAnim]);

  // ASCII sparkline
  const sparkline = useMemo(() => {
    if (sparklineValues.length < 2) return "";
    const min = Math.min(...sparklineValues);
    const max = Math.max(...sparklineValues);
    const range = max - min || 1;
    const chars = "▁▂▃▄▅▆▇█";
    return sparklineValues.map((v) => {
      const idx = Math.round(((v - min) / range) * (chars.length - 1));
      return chars[idx];
    }).join("");
  }, [sparklineValues]);

  const statusColor = STATUS_COLORS[status];
  const trendIcon = TREND_ICONS[trend];
  const trendColor = TREND_COLORS[trend];

  return (
    <Animated.View style={[st.card, { borderColor: statusColor, opacity: flashAnim }]}>
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
          <Text style={[st.trendIcon, { color: trendColor }]}>{trendIcon}</Text>
          <Text style={[st.trendText, { color: trendColor }]}>{trend.toLowerCase()}</Text>
        </View>
      </View>

      {/* Sparkline */}
      {sparkline.length > 0 && (
        <View style={st.sparklineContainer}>
          <Text style={[st.sparkline, { color: config.color }]}>{sparkline}</Text>
        </View>
      )}

      {/* Normal range */}
      <Text style={st.range}>Range: {config.normalMin}–{config.normalMax} {config.unit}</Text>

      {/* Alert indicator */}
      {status === "CRITICAL" && (
        <View style={st.alertBanner}>
          <Text style={st.alertText}>⚠️ CRITICAL VALUE — Immediate action required</Text>
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
  hint: { textAlign: "center", fontSize: 11, color: "#9CA3AF", marginBottom: 4 },
  grid: { gap: 10 },
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
  sparklineContainer: { marginTop: 2 },
  sparkline: { fontSize: 18, letterSpacing: 2, fontFamily: "monospace" },
  range: { fontSize: 10, color: "#9CA3AF" },
  alertBanner: { backgroundColor: "#FEE2E2", borderRadius: 6, padding: 6, marginTop: 2 },
  alertText: { fontSize: 11, fontWeight: "600", color: "#991B1B", textAlign: "center" },
});
