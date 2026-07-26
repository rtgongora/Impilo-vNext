import React, { useCallback, useEffect, useState } from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
import { Screen, Header, Button, TextField, Select, LoadingSpinner, EmptyState, ErrorState, Badge, colors } from "@impilo/mobile-design-system";
import {
  fetchTransfusionEpisodes,
  startTransfusion,
  preVerifyTransfusion,
  recordTransfusionObservation,
  completeTransfusion,
  type TransfusionEpisode,
} from "../../services/madiService";

const OUTCOMES = [
  { label: "Completed without incident", value: "COMPLETED" },
  { label: "Stopped — reaction suspected", value: "STOPPED_REACTION" },
  { label: "Stopped — other", value: "STOPPED_OTHER" },
];

export function MadiTransfusionScreen() {
  const [episodes, setEpisodes] = useState<TransfusionEpisode[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [patientCpid, setPatientCpid] = useState("");
  const [orderId, setOrderId] = useState("");
  const [selectedEpisode, setSelectedEpisode] = useState<string | null>(null);
  const [obsType, setObsType] = useState("TEMPERATURE");
  const [obsValue, setObsValue] = useState("");
  const [outcome, setOutcome] = useState("COMPLETED");
  const [busy, setBusy] = useState(false);
  const [verifyPatientCpid, setVerifyPatientCpid] = useState("");
  const [bloodUnitId, setBloodUnitId] = useState("");
  const [patientMethod, setPatientMethod] = useState("BIOMETRIC");
  const [unitMethod, setUnitMethod] = useState("BARCODE_SCAN");
  const [biometricRef, setBiometricRef] = useState("");
  const [unitScanRef, setUnitScanRef] = useState("");
  const [verified, setVerified] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setEpisodes(await fetchTransfusionEpisodes());
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function handleStart() {
    if (!patientCpid.trim()) return;
    setBusy(true);
    await startTransfusion({ patient_cpid: patientCpid.trim(), order_id: orderId.trim() || undefined });
    setPatientCpid("");
    setOrderId("");
    await load();
    setBusy(false);
  }

  async function handlePreVerify() {
    if (!selectedEpisode || !verifyPatientCpid.trim() || !bloodUnitId.trim()) return;
    setBusy(true);
    const ok = await preVerifyTransfusion(selectedEpisode, {
      patient_cpid: verifyPatientCpid.trim(),
      blood_unit_id: bloodUnitId.trim(),
      patient_method: patientMethod,
      patient_biometric_ref: biometricRef.trim() || undefined,
      unit_method: unitMethod,
      unit_scan_ref: unitScanRef.trim() || undefined,
    });
    setVerified(!!ok);
    setBusy(false);
  }

  async function handleObservation() {
    if (!selectedEpisode || !obsValue.trim() || !verified) return;
    setBusy(true);
    await recordTransfusionObservation(selectedEpisode, {
      observation_type: obsType,
      value_numeric: parseFloat(obsValue),
      unit: obsType === "TEMPERATURE" ? "C" : undefined,
    });
    setObsValue("");
    setBusy(false);
  }

  async function handleComplete() {
    if (!selectedEpisode) return;
    setBusy(true);
    await completeTransfusion(selectedEpisode, { outcome_status: outcome });
    setSelectedEpisode(null);
    await load();
    setBusy(false);
  }

  if (loading) return <Screen><Header title="Transfusion" /><LoadingSpinner label="Loading episodes..." /></Screen>;
  if (error) return <Screen><Header title="Transfusion" /><ErrorState message={error.message} onRetry={load} /></Screen>;

  return (
    <Screen>
      <Header title="Transfusion (MADI)" />
      <ScrollView style={styles.container} contentContainerStyle={styles.content}>
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Start episode</Text>
          <TextField label="Patient CPID" value={patientCpid} onChange={setPatientCpid} placeholder="CPID" testID="madi-tx-patient" />
          <TextField label="Order ID (optional)" value={orderId} onChange={setOrderId} placeholder="Linked blood order" />
          <Button title={busy ? "Starting…" : "Start transfusion"} variant="primary" onPress={handleStart} disabled={busy || !patientCpid.trim()} />
        </View>
        {episodes.length === 0 ? (
          <EmptyState icon="pulse-outline" title="No active episodes" description="Start a transfusion when a unit is issued." />
        ) : (
          episodes.map((ep) => (
            <View key={ep.episode_id} style={styles.card}>
              <Text style={styles.title}>Episode {ep.episode_id.slice(0, 8)}</Text>
              <Text style={styles.meta}>Patient: {ep.patient_cpid?.slice(0, 12) ?? "—"}</Text>
              <Badge variant="outline">{ep.status ?? "IN_PROGRESS"}</Badge>
              <Button title="Manage" size="sm" variant="outline" onPress={() => setSelectedEpisode(ep.episode_id)} />
            </View>
          ))
        )}
        {selectedEpisode ? (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Bedside verification</Text>
            <TextField label="Patient CPID" value={verifyPatientCpid} onChange={setVerifyPatientCpid} placeholder="Confirm patient" />
            <TextField label="Blood unit ID" value={bloodUnitId} onChange={setBloodUnitId} placeholder="Unit UUID" />
            <Select label="Patient method" value={patientMethod} onChange={setPatientMethod} options={[
              { label: "Biometric", value: "BIOMETRIC" },
              { label: "Wristband barcode", value: "BARCODE_SCAN" },
              { label: "Manual (two staff)", value: "MANUAL_GOVERNED" },
              { label: "Emergency override", value: "EMERGENCY_OVERRIDE" },
            ]} />
            <TextField label="Biometric / override ref" value={biometricRef} onChange={setBiometricRef} placeholder="Optional capture reference" />
            <Select label="Unit method" value={unitMethod} onChange={setUnitMethod} options={[
              { label: "Bag barcode", value: "BARCODE_SCAN" },
              { label: "Manual label check", value: "MANUAL_GOVERNED" },
            ]} />
            <TextField label="Unit scan ref" value={unitScanRef} onChange={setUnitScanRef} placeholder="Barcode value" />
            <Button title={verified ? "Verified ✓" : "Verify patient + unit"} variant={verified ? "outline" : "primary"} onPress={handlePreVerify} disabled={busy} />
            <Text style={styles.sectionTitle}>Record observation</Text>
            <Select label="Type" value={obsType} onChange={setObsType} options={[
              { label: "Temperature", value: "TEMPERATURE" },
              { label: "Pulse", value: "PULSE" },
              { label: "Blood pressure", value: "BP" },
            ]} />
            <TextField label="Value" value={obsValue} onChange={setObsValue} placeholder="Numeric value" keyboardType="numeric" />
            <Button title="Record" variant="outline" onPress={handleObservation} disabled={busy || !verified} />
            <Select label="Outcome" value={outcome} onChange={setOutcome} options={OUTCOMES} />
            <Button title="Complete episode" variant="primary" onPress={handleComplete} disabled={busy} />
          </View>
        ) : null}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: 16, gap: 14 },
  section: { gap: 10 },
  sectionTitle: { fontSize: 14, fontWeight: "600" },
  card: { backgroundColor: "#FFFFFF", borderRadius: 12, padding: 14, borderWidth: 1, borderColor: colors.gray[200], gap: 6 },
  title: { fontSize: 15, fontWeight: "600" },
  meta: { fontSize: 12, color: colors.gray[500] },
});
