/**
 * MonitoringSection — Remote wearable device management.
 */
import React, { useState } from "react";
import { View, Text, TouchableOpacity, TextInput, StyleSheet } from "react-native";
import { Button, Badge, LoadingSpinner, colors } from "@impilo/mobile-design-system";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { fetchDevices, pairDevice, syncDevice } from "../../services/monitoringService";
import { useAppStore } from "../../stores/appStore";

const DEVICE_TYPES = ["BLOOD_PRESSURE", "GLUCOSE_METER", "PULSE_OXIMETER", "SMARTWATCH", "THERMOMETER", "SCALE"];

export function MonitoringSection() {
  const profile = useAppStore((s) => s.profile);
  const patientId = profile?.cpid ?? "";
  const queryClient = useQueryClient();
  const [showPair, setShowPair] = useState(false);
  const [form, setForm] = useState({ deviceName: "", deviceType: "SMARTWATCH", manufacturer: "", model: "" });

  const { data: devices = [], isLoading } = useQuery({
    queryKey: ["monitoring-devices", patientId], queryFn: () => fetchDevices(patientId), enabled: !!patientId,
  });

  const pairMutation = useMutation({
    mutationFn: () => pairDevice({ patientId, ...form }),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["monitoring-devices"] }); setShowPair(false); },
  });

  const syncMutation = useMutation({
    mutationFn: ({ id, value, unit }: { id: string; value: number; unit: string }) =>
      syncDevice(id, { readings: [{ value, unit }] }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["monitoring-devices"] });
      setSyncTargetId(null);
      setSyncValue("");
    },
  });

  const [syncTargetId, setSyncTargetId] = useState<string | null>(null);
  const [syncValue, setSyncValue] = useState("");

  if (isLoading) return <LoadingSpinner />;

  function defaultUnit(deviceType: string) {
    switch (deviceType) {
      case "BLOOD_PRESSURE":
        return "mmHg";
      case "PULSE_OXIMETER":
        return "%";
      case "GLUCOMETER":
      case "GLUCOSE_METER":
        return "mmol/L";
      case "SCALE":
        return "kg";
      default:
        return "bpm";
    }
  }

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.sectionTitle}>My Devices</Text>
        <TouchableOpacity onPress={() => setShowPair(!showPair)}><Text style={styles.addLink}>+ Pair Device</Text></TouchableOpacity>
      </View>

      {showPair && (
        <View style={styles.form}>
          <TextInput style={styles.input} placeholder="Device Name" value={form.deviceName} onChangeText={(v) => setForm({ ...form, deviceName: v })} />
          <View style={styles.chipRow}>
            {DEVICE_TYPES.map((dt) => (
              <TouchableOpacity key={dt} onPress={() => setForm({ ...form, deviceType: dt })} style={[styles.chip, form.deviceType === dt && styles.activeChip]}>
                <Text style={[styles.chipText, form.deviceType === dt && styles.activeChipText]}>{dt.replace(/_/g, " ")}</Text>
              </TouchableOpacity>
            ))}
          </View>
          <TextInput style={styles.input} placeholder="Manufacturer" value={form.manufacturer} onChangeText={(v) => setForm({ ...form, manufacturer: v })} />
          <TextInput style={styles.input} placeholder="Model" value={form.model} onChangeText={(v) => setForm({ ...form, model: v })} />
          <Button title="Pair" onPress={() => pairMutation.mutate()} disabled={!form.deviceName || pairMutation.isPending} />
        </View>
      )}

      {devices.length === 0 ? (
        <View style={styles.empty}>
          <Text style={styles.emptyText}>No devices paired</Text>
          <Text style={styles.emptyHint}>Pair a Bluetooth device to track vitals automatically</Text>
        </View>
      ) : (
        devices.map((d) => (
          <View key={d.id} style={styles.deviceCardWrap}>
            <View style={styles.deviceCard}>
              <View style={{ flex: 1 }}>
                <Text style={styles.deviceName}>{d.deviceName}</Text>
                <Text style={styles.deviceMeta}>{d.deviceType.replace(/_/g, " ")} · {d.manufacturer} {d.model}</Text>
                <Text style={styles.deviceMeta}>{d.connectionType} · {d.status} {d.batteryLevel != null ? `· ${d.batteryLevel}%` : ""}</Text>
                {d.lastSyncAt && <Text style={styles.syncTime}>Last sync: {new Date(d.lastSyncAt).toLocaleString()}</Text>}
              </View>
              <TouchableOpacity style={styles.syncButton} onPress={() => setSyncTargetId(d.id)}>
                <Text style={styles.syncText}>Sync</Text>
              </TouchableOpacity>
            </View>
            {syncTargetId === d.id && (
              <View style={styles.syncForm}>
                <TextInput
                  style={styles.input}
                  placeholder="Latest reading"
                  keyboardType="decimal-pad"
                  value={syncValue}
                  onChangeText={setSyncValue}
                />
                <Button
                  title={syncMutation.isPending ? "Syncing…" : "Submit reading"}
                  onPress={() => {
                    const value = Number(syncValue);
                    if (Number.isNaN(value)) return;
                    syncMutation.mutate({ id: d.id, value, unit: defaultUnit(d.deviceType) });
                  }}
                  disabled={!syncValue.trim() || syncMutation.isPending}
                />
              </View>
            )}
          </View>
        ))
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { gap: 12 },
  header: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  sectionTitle: { fontSize: 16, fontWeight: "700", color: colors.gray[900] },
  addLink: { color: "#2563EB", fontSize: 14, fontWeight: "600" },
  form: { gap: 8, backgroundColor: colors.gray[50], borderRadius: 12, padding: 16 },
  input: { borderWidth: 1, borderColor: colors.gray[300], borderRadius: 8, padding: 10, fontSize: 14 },
  chipRow: { flexDirection: "row", flexWrap: "wrap", gap: 6 },
  chip: { paddingHorizontal: 10, paddingVertical: 5, borderRadius: 14, backgroundColor: colors.gray[200] },
  activeChip: { backgroundColor: "#2563EB" },
  chipText: { fontSize: 10, color: colors.gray[500] },
  activeChipText: { color: "#FFF" },
  empty: { alignItems: "center", paddingVertical: 32 },
  emptyText: { fontSize: 15, fontWeight: "600", color: colors.gray[700] },
  emptyHint: { fontSize: 13, color: colors.gray[400] },
  deviceCardWrap: { gap: 8 },
  deviceCard: { flexDirection: "row", alignItems: "center", backgroundColor: colors.gray[50], borderRadius: 12, padding: 16 },
  deviceName: { fontSize: 15, fontWeight: "600", color: colors.gray[900] },
  deviceMeta: { fontSize: 12, color: colors.gray[500] },
  syncTime: { fontSize: 11, color: "#2563EB", marginTop: 2 },
  syncButton: { backgroundColor: "#2563EB", paddingHorizontal: 16, paddingVertical: 8, borderRadius: 8 },
  syncText: { color: "#FFF", fontSize: 13, fontWeight: "600" },
  syncForm: { marginTop: 8, gap: 8 },
});
