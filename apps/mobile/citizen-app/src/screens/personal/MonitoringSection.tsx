import React, { useState } from "react";
import { View, Text, ScrollView, Pressable, StyleSheet, TextInput } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Button, LoadingSpinner } from "@impilo/mobile-design-system";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { fetchDevices, pairDevice, syncDevice } from "../../services/monitoringService";
import { useAppStore } from "../../stores/appStore";
import { USE_SEED_DATA, SEED_MONITORING_DEVICES } from "../../data/seedHealthRecords";
import {
  APP_GREEN, APP_GREEN_DARK, APP_GREEN_LIGHT, APP_GREEN_XLIGHT,
  APP_RED, APP_RED_LIGHT, APP_GOLD, APP_GOLD_LIGHT,
  APP_SURFACE, APP_BG, APP_TEXT, APP_TEXT_2, APP_TEXT_3, APP_BORDER,
} from "../../lib/colors";

const DEVICE_TYPES = [
  "BLOOD_PRESSURE", "GLUCOSE_METER", "PULSE_OXIMETER",
  "SMARTWATCH", "THERMOMETER", "SCALE",
];

const DEVICE_ICON: Record<string, React.ComponentProps<typeof Ionicons>["name"]> = {
  BLOOD_PRESSURE:  "heart-outline",
  GLUCOSE_METER:   "flask-outline",
  PULSE_OXIMETER:  "pulse-outline",
  SMARTWATCH:      "watch-outline",
  THERMOMETER:     "thermometer-outline",
  SCALE:           "body-outline",
};

const STATUS_CFG: Record<string, { color: string; bg: string }> = {
  CONNECTED:    { color: APP_GREEN_DARK, bg: APP_GREEN_LIGHT  },
  DISCONNECTED: { color: APP_TEXT_3,     bg: "#F3F4F6"        },
  ERROR:        { color: APP_RED,        bg: APP_RED_LIGHT    },
};

function BatteryIcon({ level }: { level: number }) {
  const color = level > 50 ? APP_GREEN : level > 20 ? APP_GOLD : APP_RED;
  const icon: React.ComponentProps<typeof Ionicons>["name"] =
    level > 75 ? "battery-full-outline" :
    level > 50 ? "battery-half-outline" :
    level > 20 ? "battery-dead-outline" : "battery-dead";
  return (
    <View style={bat.wrap}>
      <Ionicons name={icon} size={14} color={color} />
      <Text style={[bat.text, { color }]}>{level}%</Text>
    </View>
  );
}
const bat = StyleSheet.create({
  wrap: { flexDirection: "row", alignItems: "center", gap: 3 },
  text: { fontSize: 11, fontWeight: "600" },
});

export function MonitoringSection() {
  const profile      = useAppStore((s) => s.profile);
  const patientId    = profile?.cpid ?? "";
  const queryClient  = useQueryClient();
  const [showPair, setShowPair] = useState(false);
  const [form, setForm] = useState({ deviceName: "", deviceType: "BLOOD_PRESSURE", manufacturer: "", model: "" });

  const { data: devices = [], isLoading } = useQuery({
    queryKey: ["monitoring-devices", patientId, USE_SEED_DATA],
    queryFn: async () => {
      if (USE_SEED_DATA) {
        await new Promise((r) => setTimeout(r, 400));
        return SEED_MONITORING_DEVICES;
      }
      return fetchDevices(patientId);
    },
    enabled: USE_SEED_DATA || !!patientId,
  });

  const pairMutation = useMutation({
    mutationFn: () => pairDevice({ patientId, ...form }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["monitoring-devices"] });
      setShowPair(false);
      setForm({ deviceName: "", deviceType: "BLOOD_PRESSURE", manufacturer: "", model: "" });
    },
  });

  const syncMutation = useMutation({
    mutationFn: (id: string) => syncDevice(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["monitoring-devices"] }),
  });

  if (isLoading) return <View style={s.centred}><LoadingSpinner size="md" /></View>;

  return (
    <ScrollView style={s.root} contentContainerStyle={s.content} showsVerticalScrollIndicator={false}>
      {/* Header row */}
      <View style={s.headerRow}>
        <View>
          <Text style={s.headerTitle}>Connected Devices</Text>
          <Text style={s.headerSub}>{devices.length} device{devices.length !== 1 ? "s" : ""} paired</Text>
        </View>
        <Pressable
          onPress={() => setShowPair(!showPair)}
          style={[s.pairBtn, showPair && s.pairBtnActive]}
        >
          <Ionicons name={showPair ? "close" : "add"} size={16} color={showPair ? APP_TEXT_2 : "#FFFFFF"} />
          {!showPair ? <Text style={s.pairBtnText}>Pair Device</Text> : null}
        </Pressable>
      </View>

      {/* Pair form */}
      {showPair ? (
        <View style={s.pairForm}>
          <Text style={s.formTitle}>Pair New Device</Text>
          <TextInput
            style={s.input} placeholder="Device name" placeholderTextColor={APP_TEXT_3}
            value={form.deviceName} onChangeText={(v) => setForm({ ...form, deviceName: v })}
          />
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={s.typeRow}>
            {DEVICE_TYPES.map((dt) => (
              <Pressable
                key={dt}
                onPress={() => setForm({ ...form, deviceType: dt })}
                style={[s.typeChip, form.deviceType === dt && s.typeChipActive]}
              >
                <Ionicons name={DEVICE_ICON[dt] ?? "hardware-chip-outline"} size={13} color={form.deviceType === dt ? APP_GREEN : APP_TEXT_2} />
                <Text style={[s.typeChipText, form.deviceType === dt && s.typeChipTextActive]}>
                  {dt.replace(/_/g, " ")}
                </Text>
              </Pressable>
            ))}
          </ScrollView>
          <View style={s.twoCol}>
            <TextInput
              style={[s.input, { flex: 1 }]} placeholder="Manufacturer" placeholderTextColor={APP_TEXT_3}
              value={form.manufacturer} onChangeText={(v) => setForm({ ...form, manufacturer: v })}
            />
            <TextInput
              style={[s.input, { flex: 1 }]} placeholder="Model" placeholderTextColor={APP_TEXT_3}
              value={form.model} onChangeText={(v) => setForm({ ...form, model: v })}
            />
          </View>
          <Button
            title={pairMutation.isPending ? "Pairing…" : "Pair Device"}
            onPress={() => pairMutation.mutate()}
            disabled={!form.deviceName || pairMutation.isPending}
          />
        </View>
      ) : null}

      {/* Device cards */}
      {devices.length === 0 ? (
        <View style={s.empty}>
          <Ionicons name="hardware-chip-outline" size={52} color={APP_GREEN_LIGHT} />
          <Text style={s.emptyTitle}>No devices paired</Text>
          <Text style={s.emptySub}>Pair a Bluetooth device to track vitals automatically.</Text>
        </View>
      ) : (
        devices.map((device) => {
          const statusCfg  = STATUS_CFG[device.status] ?? STATUS_CFG.DISCONNECTED;
          const deviceIcon = DEVICE_ICON[device.deviceType] ?? "hardware-chip-outline";
          return (
            <View key={device.id} style={s.deviceCard}>
              <View style={[s.deviceIconWrap, { backgroundColor: statusCfg.bg }]}>
                <Ionicons name={deviceIcon} size={24} color={statusCfg.color} />
              </View>
              <View style={s.deviceInfo}>
                <Text style={s.deviceName}>{device.deviceName}</Text>
                <Text style={s.deviceMeta}>{device.deviceType.replace(/_/g, " ")}</Text>
                {(device.manufacturer || device.model) ? (
                  <Text style={s.deviceSubMeta}>{[device.manufacturer, device.model].filter(Boolean).join(" ")}</Text>
                ) : null}
                <View style={s.deviceStatusRow}>
                  <View style={[s.statusDot, { backgroundColor: statusCfg.color }]} />
                  <Text style={[s.statusText, { color: statusCfg.color }]}>{device.status}</Text>
                  <Text style={s.connectionType}>· {device.connectionType}</Text>
                </View>
                {device.lastSyncAt ? (
                  <Text style={s.syncTime}>
                    Last sync: {new Date(device.lastSyncAt).toLocaleString([], { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" })}
                  </Text>
                ) : null}
              </View>
              <View style={s.deviceRight}>
                {device.batteryLevel != null ? <BatteryIcon level={device.batteryLevel} /> : null}
                <Pressable
                  onPress={() => syncMutation.mutate(device.id)}
                  disabled={syncMutation.isPending || USE_SEED_DATA}
                  style={[s.syncBtn, (syncMutation.isPending || USE_SEED_DATA) && s.syncBtnDisabled]}
                >
                  <Ionicons name="sync-outline" size={15} color={APP_GREEN} />
                  <Text style={s.syncBtnText}>Sync</Text>
                </Pressable>
              </View>
            </View>
          );
        })
      )}
    </ScrollView>
  );
}

const s = StyleSheet.create({
  root: { flex: 1, backgroundColor: APP_BG },
  content: { padding: 16, gap: 14, paddingBottom: 40 },
  centred: { flex: 1, alignItems: "center", justifyContent: "center" },

  headerRow: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  headerTitle: { fontSize: 16, fontWeight: "700", color: APP_TEXT },
  headerSub: { fontSize: 13, color: APP_TEXT_2, marginTop: 2 },
  pairBtn: {
    flexDirection: "row", alignItems: "center", gap: 5,
    backgroundColor: APP_GREEN, paddingHorizontal: 14, paddingVertical: 8, borderRadius: 20,
  },
  pairBtnActive: { backgroundColor: "#EEF2F2" },
  pairBtnText: { fontSize: 13, fontWeight: "700", color: "#FFFFFF" },

  pairForm: {
    backgroundColor: APP_SURFACE, borderRadius: 18, padding: 16, gap: 12,
    shadowColor: "#000", shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.06, shadowRadius: 8, elevation: 3,
  },
  formTitle: { fontSize: 15, fontWeight: "700", color: APP_TEXT },
  input: {
    borderWidth: 1, borderColor: APP_BORDER, borderRadius: 12,
    paddingHorizontal: 14, paddingVertical: 11, fontSize: 14, color: APP_TEXT, backgroundColor: APP_BG,
  },
  typeRow: { gap: 8 },
  typeChip: {
    flexDirection: "row", alignItems: "center", gap: 5,
    paddingHorizontal: 12, paddingVertical: 7, borderRadius: 20, backgroundColor: "#EEF2F2",
  },
  typeChipActive: { backgroundColor: APP_GREEN_XLIGHT, borderWidth: 1, borderColor: APP_GREEN_LIGHT },
  typeChipText: { fontSize: 12, fontWeight: "500", color: APP_TEXT_2 },
  typeChipTextActive: { color: APP_GREEN, fontWeight: "700" },
  twoCol: { flexDirection: "row", gap: 10 },

  deviceCard: {
    flexDirection: "row", alignItems: "flex-start", gap: 14,
    backgroundColor: APP_SURFACE, borderRadius: 18, padding: 16,
    shadowColor: "#000", shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.06, shadowRadius: 8, elevation: 3,
  },
  deviceIconWrap: { width: 52, height: 52, borderRadius: 16, alignItems: "center", justifyContent: "center" },
  deviceInfo: { flex: 1 },
  deviceName: { fontSize: 15, fontWeight: "700", color: APP_TEXT },
  deviceMeta: { fontSize: 12, color: APP_TEXT_2, marginTop: 2 },
  deviceSubMeta: { fontSize: 11, color: APP_TEXT_3 },
  deviceStatusRow: { flexDirection: "row", alignItems: "center", gap: 5, marginTop: 6 },
  statusDot: { width: 7, height: 7, borderRadius: 4 },
  statusText: { fontSize: 12, fontWeight: "600" },
  connectionType: { fontSize: 12, color: APP_TEXT_3 },
  syncTime: { fontSize: 11, color: APP_TEXT_3, marginTop: 3 },
  deviceRight: { alignItems: "flex-end", gap: 10 },
  syncBtn: {
    flexDirection: "row", alignItems: "center", gap: 5,
    backgroundColor: APP_GREEN_XLIGHT, paddingHorizontal: 12, paddingVertical: 7, borderRadius: 12,
  },
  syncBtnDisabled: { opacity: 0.5 },
  syncBtnText: { fontSize: 12, fontWeight: "700", color: APP_GREEN },

  empty: { alignItems: "center", paddingVertical: 60, gap: 12 },
  emptyTitle: { fontSize: 17, fontWeight: "700", color: APP_TEXT },
  emptySub: { fontSize: 13, color: APP_TEXT_3, textAlign: "center", paddingHorizontal: 32, lineHeight: 19 },
});
