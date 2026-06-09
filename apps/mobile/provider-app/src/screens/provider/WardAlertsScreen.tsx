/**
 * WardAlertsScreen — Provider inbox for citizen bedside / call-nurse alerts.
 */
import React from "react";
import { View, Text, FlatList, TouchableOpacity, StyleSheet, Alert } from "react-native";
import { Badge, Button, LoadingSpinner } from "@impilo/mobile-design-system";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { acknowledgeWardAlert, listWardAlerts } from "../../services/inpatientService";

const DEMO_WARD_ID = "11111111-1111-1111-1111-111111111101";

interface WardAlertRow {
  id: string;
  subject_cpid?: string;
  alert_type?: string;
  message?: string;
  status?: string;
  created_at?: string;
  bed_id?: string;
}

export function WardAlertsScreen({ wardId = DEMO_WARD_ID }: { wardId?: string }) {
  const qc = useQueryClient();
  const { data: alerts = [], isLoading, refetch, isRefetching } = useQuery({
    queryKey: ["ward-alerts", wardId],
    queryFn: () => listWardAlerts(wardId) as Promise<WardAlertRow[]>,
    refetchInterval: 15_000,
  });

  const ackMut = useMutation({
    mutationFn: (alertId: string) => acknowledgeWardAlert(alertId, { acknowledgedBy: "provider-mobile" }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["ward-alerts"] }),
  });

  const onAck = (alert: WardAlertRow) => {
    Alert.alert("Acknowledge alert", alert.message ?? "Patient needs assistance", [
      { text: "Cancel", style: "cancel" },
      { text: "Acknowledge", onPress: () => ackMut.mutate(alert.id) },
    ]);
  };

  if (isLoading) {
    return <LoadingSpinner />;
  }

  return (
    <View style={s.wrap}>
      <View style={s.headerRow}>
        <Text style={s.title}>Ward alerts</Text>
        <Badge label={`${alerts.length} active`} variant={alerts.length > 0 ? "warning" : "default"} />
      </View>
      <Button title={isRefetching ? "Refreshing…" : "Refresh"} variant="outline" onPress={() => refetch()} />
      <FlatList
        data={alerts}
        keyExtractor={(item) => item.id}
        style={s.list}
        ListEmptyComponent={<Text style={s.empty}>No active ward alerts.</Text>}
        renderItem={({ item }) => (
          <View style={s.card}>
            <View style={s.cardTop}>
              <Badge label={item.alert_type ?? "CALL_NURSE"} variant="warning" />
              <Text style={s.time}>{item.created_at ? new Date(item.created_at).toLocaleTimeString() : "—"}</Text>
            </View>
            <Text style={s.patient}>{item.subject_cpid ?? "Unknown patient"}</Text>
            {item.bed_id ? <Text style={s.meta}>Bed: {item.bed_id.slice(0, 8)}…</Text> : null}
            <Text style={s.message}>{item.message ?? "Assistance requested"}</Text>
            <TouchableOpacity style={s.ackBtn} onPress={() => onAck(item)} disabled={ackMut.isPending}>
              <Text style={s.ackText}>Acknowledge</Text>
            </TouchableOpacity>
          </View>
        )}
      />
    </View>
  );
}

const s = StyleSheet.create({
  wrap: { flex: 1, gap: 12 },
  headerRow: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  title: { fontSize: 18, fontWeight: "700", color: "#111827" },
  list: { marginTop: 8 },
  empty: { color: "#6B7280", textAlign: "center", marginTop: 24 },
  card: { backgroundColor: "#FFF7ED", borderRadius: 12, padding: 14, marginBottom: 10, borderWidth: 1, borderColor: "#FDBA74" },
  cardTop: { flexDirection: "row", justifyContent: "space-between", marginBottom: 6 },
  time: { fontSize: 12, color: "#6B7280" },
  patient: { fontSize: 15, fontWeight: "600", color: "#1F2937" },
  meta: { fontSize: 12, color: "#6B7280", marginTop: 2 },
  message: { fontSize: 14, color: "#374151", marginTop: 8 },
  ackBtn: { marginTop: 10, alignSelf: "flex-start", backgroundColor: "#EA580C", paddingHorizontal: 14, paddingVertical: 8, borderRadius: 8 },
  ackText: { color: "#fff", fontWeight: "600", fontSize: 13 },
});
