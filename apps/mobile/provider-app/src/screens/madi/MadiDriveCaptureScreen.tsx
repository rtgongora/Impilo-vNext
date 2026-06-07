import React, { useCallback, useEffect, useState } from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
import { Screen, Header, Button, TextField, Select, LoadingSpinner, EmptyState, ErrorState, Badge } from "@impilo/mobile-design-system";
import {
  fetchOperationalDrives,
  screenDonorAtDrive,
  recordDriveDonation,
  type DonationDriveOperational,
} from "../../services/madiService";

const SCREEN_RESULTS = [
  { label: "Eligible", value: "ELIGIBLE" },
  { label: "Temporarily deferred", value: "DEFERRED_TEMPORARY" },
  { label: "Permanently deferred", value: "DEFERRED_PERMANENT" },
];

export function MadiDriveCaptureScreen() {
  const [drives, setDrives] = useState<DonationDriveOperational[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [selectedDrive, setSelectedDrive] = useState<string | null>(null);
  const [donorId, setDonorId] = useState("");
  const [screenResult, setScreenResult] = useState("ELIGIBLE");
  const [bagNumber, setBagNumber] = useState("");
  const [volumeMl, setVolumeMl] = useState("450");
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setDrives(await fetchOperationalDrives());
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function handleScreen() {
    if (!selectedDrive || !donorId.trim()) return;
    setBusy(true);
    setMessage(null);
    const ok = await screenDonorAtDrive(selectedDrive, { donor_id: donorId.trim(), result: screenResult });
    setMessage(ok ? "Screening recorded." : "Screening failed.");
    setBusy(false);
  }

  async function handleDonation() {
    if (!selectedDrive || !donorId.trim() || !bagNumber.trim()) return;
    setBusy(true);
    setMessage(null);
    const ok = await recordDriveDonation(selectedDrive, {
      donor_id: donorId.trim(),
      bag_number: bagNumber.trim(),
      volume_ml: parseInt(volumeMl, 10) || undefined,
    });
    setMessage(ok ? "Donation captured." : "Donation capture failed.");
    setBagNumber("");
    setBusy(false);
    await load();
  }

  if (loading) return <Screen><Header title="Drive Capture" /><LoadingSpinner label="Loading drives..." /></Screen>;
  if (error) return <Screen><Header title="Drive Capture" /><ErrorState message={error.message} onRetry={load} /></Screen>;

  return (
    <Screen>
      <Header title="Donation Drive Capture" />
      <ScrollView style={styles.container} contentContainerStyle={styles.content}>
        {drives.length === 0 ? (
          <EmptyState icon="calendar-outline" title="No active drives" description="Operational drives for your facility will appear here." />
        ) : (
          drives.map((drive) => (
            <View key={drive.drive_id} style={[styles.card, selectedDrive === drive.drive_id && styles.cardSelected]}>
              <Text style={styles.title}>{drive.title ?? "Drive"}</Text>
              <Text style={styles.meta}>
                {drive.start_at ? new Date(drive.start_at).toLocaleString() : ""} · {drive.collected_count ?? 0}/{drive.capacity ?? "—"} collected
              </Text>
              <Badge variant="outline">{drive.status ?? "OPEN"}</Badge>
              <Button title={selectedDrive === drive.drive_id ? "Selected" : "Select drive"} size="sm" variant="outline" onPress={() => setSelectedDrive(drive.drive_id)} />
            </View>
          ))
        )}
        {selectedDrive ? (
          <View style={styles.form}>
            <Text style={styles.sectionTitle}>Field capture</Text>
            <TextField label="Donor ID" value={donorId} onChange={setDonorId} placeholder="Donor UUID" testID="madi-drive-donor-id" />
            <Select label="Screening result" value={screenResult} onChange={setScreenResult} options={SCREEN_RESULTS} />
            <Button title="Record screening" variant="outline" onPress={handleScreen} disabled={busy || !donorId.trim()} />
            <TextField label="Bag number" value={bagNumber} onChange={setBagNumber} placeholder="Collection bag ID" />
            <TextField label="Volume (ml)" value={volumeMl} onChange={setVolumeMl} keyboardType="numeric" />
            <Button title="Record donation" variant="primary" onPress={handleDonation} disabled={busy || !donorId.trim() || !bagNumber.trim()} testID="madi-drive-record-donation" />
            {message ? <Text style={styles.message}>{message}</Text> : null}
          </View>
        ) : null}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: 16, gap: 12 },
  card: { backgroundColor: "#FFFFFF", borderRadius: 12, padding: 14, borderWidth: 1, borderColor: "#E5E7EB", gap: 6 },
  cardSelected: { borderColor: "#DC2626", backgroundColor: "#FEF2F2" },
  title: { fontSize: 15, fontWeight: "600" },
  meta: { fontSize: 12, color: "#6B7280" },
  form: { gap: 10, marginTop: 8 },
  sectionTitle: { fontSize: 14, fontWeight: "600" },
  message: { fontSize: 13, color: "#059669" },
});
