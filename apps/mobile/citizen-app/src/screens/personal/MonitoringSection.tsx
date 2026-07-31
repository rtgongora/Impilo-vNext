/**
 * MonitoringSection — Remote wearable device management, plus (RMNP W12) her home
 * blood-pressure series verdict for any active hypertension monitoring plan.
 */
import React, { useState } from "react";
import { View, Text, TouchableOpacity, TextInput, StyleSheet } from "react-native";
import { Button, Badge, LoadingSpinner, colors } from "@impilo/mobile-design-system";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { fetchDevices, pairDevice, syncDevice } from "../../services/monitoringService";
import {
  fetchMyMonitoringPlans,
  fetchSmbpVerdict,
  isSmbpUnavailableError,
  type SmbpVerdict,
} from "../../services/smbpService";
import { useAppStore } from "../../stores/appStore";

const DEVICE_TYPES = ["BLOOD_PRESSURE", "GLUCOSE_METER", "PULSE_OXIMETER", "SMARTWATCH", "THERMOMETER", "SCALE"];

/** Never green, never "controlled" until CKP says so — INSUFFICIENT_DATA is not reassurance. */
function verdictBadge(verdict: SmbpVerdict): { label: string; variant: "error" | "warning" | "success" | "default" } {
  switch (verdict) {
    case "URGENT":
      return { label: "Needs urgent care", variant: "error" };
    case "ELEVATED_REFER":
      return { label: "Come in for a check", variant: "warning" };
    case "CONTROLLED":
      return { label: "Looks controlled", variant: "success" };
    case "INSUFFICIENT_DATA":
    default:
      return { label: "Not enough readings yet", variant: "default" };
  }
}

/** The blood-pressure series verdict for one hypertension monitoring plan. */
function SmbpVerdictCard({ planId }: { planId: string }) {
  // dangerSignPresent stays omitted here — this card reads a series she already submitted, it
  // does not ask her a new question, so the field must never be coerced to `false`.
  const { data, isLoading, error } = useQuery({
    queryKey: ["smbp-verdict", planId],
    queryFn: () => fetchSmbpVerdict(planId),
  });

  if (isLoading) return <LoadingSpinner size="sm" />;

  if (error) {
    if (isSmbpUnavailableError(error)) {
      return (
        <Text style={styles.smbpUnavailable} testID="smbp-verdict-unavailable">
          We couldn&apos;t check your blood-pressure readings just now. This is not the same as
          having no readings — please try again shortly, and contact your care team if you feel
          unwell.
        </Text>
      );
    }
    return null;
  }

  if (!data) return null;
  const badge = verdictBadge(data.verdict);

  return (
    <View style={styles.smbpCard} testID="smbp-verdict-card">
      <View style={styles.smbpHeader}>
        <Text style={styles.smbpTitle}>Home blood-pressure check</Text>
        <Badge variant={badge.variant} testID="smbp-verdict-badge">
          {badge.label}
        </Badge>
      </View>
      <Text style={styles.smbpMessage}>{data.message}</Text>
      {data.note ? <Text style={styles.smbpNote}>{data.note}</Text> : null}
      {data.alerts.map((a) => (
        <Text key={a.code} style={styles.smbpAlert}>
          {a.message}
          {a.requiredAction ? ` — ${a.requiredAction}` : ""}
        </Text>
      ))}
    </View>
  );
}

/** Hypertension monitoring plans only — programme-gated so the card never shows for other plans. */
function SmbpVerdictSection() {
  const { data: plans } = useQuery({
    queryKey: ["my-monitoring-plans"],
    queryFn: fetchMyMonitoringPlans,
  });
  const hypertensionPlans = (plans ?? []).filter((p) => p.programmeCode === "HYPERTENSION");
  if (hypertensionPlans.length === 0) return null;
  return (
    <View style={styles.smbpSection}>
      {hypertensionPlans.map((p) => (
        <SmbpVerdictCard key={p.id} planId={p.id} />
      ))}
    </View>
  );
}

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
      <SmbpVerdictSection />

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
  smbpSection: { gap: 8, marginBottom: 4 },
  smbpCard: { backgroundColor: colors.gray[50], borderRadius: 12, padding: 14, gap: 6 },
  smbpHeader: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 8 },
  smbpTitle: { fontSize: 14, fontWeight: "700", color: colors.gray[900] },
  smbpMessage: { fontSize: 13, color: colors.gray[700] },
  smbpNote: { fontSize: 12, color: colors.gray[500] },
  smbpAlert: { fontSize: 12, color: "#92400E" },
  smbpUnavailable: { fontSize: 12, color: colors.gray[500], backgroundColor: colors.gray[50], borderRadius: 10, padding: 10 },
});
