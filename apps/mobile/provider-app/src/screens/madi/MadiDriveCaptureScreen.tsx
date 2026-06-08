import React, { useCallback, useEffect, useState } from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
import { Screen, Header, Button, TextField, Select, LoadingSpinner, EmptyState, ErrorState, Badge, Card, CardBody } from "@impilo/mobile-design-system";
import { getOfflineStorage } from "@impilo/mobile-offline";
import {
  fetchOperationalDrives,
  screenDonorAtDrive,
  recordDriveDonation,
  resolveDriveSyncConflict,
  type DonationDriveOperational,
  type DriveSyncConflict,
} from "../../services/madiService";
import { replayPendingDriveCaptures } from "../../services/madiDriveOfflineSync";

const SCREEN_RESULTS = [
  { label: "Eligible", value: "ELIGIBLE" },
  { label: "Temporarily deferred", value: "DEFERRED_TEMPORARY" },
  { label: "Permanently deferred", value: "DEFERRED_PERMANENT" },
];

const RESOLUTIONS = [
  { label: "Keep local capture", value: "KEEP_LOCAL" },
  { label: "Use server record", value: "USE_SERVER" },
  { label: "Merge both", value: "MERGE" },
] as const;

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
  const [pendingCount, setPendingCount] = useState(0);
  const [conflicts, setConflicts] = useState<DriveSyncConflict[]>([]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setDrives(await fetchOperationalDrives());
      const storage = getOfflineStorage();
      const pending = await storage.peekQueue();
      const drivePending = pending.filter((op) => op.collection === "madi_drive_capture" && op.status === "pending");
      setPendingCount(drivePending.length);
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
    const result = await screenDonorAtDrive(selectedDrive, { donor_id: donorId.trim(), result: screenResult });
    if (result.ok) {
      setMessage(result.queued ? "Screening queued offline — will sync when connected." : "Screening recorded.");
      if (result.queued) await load();
    } else if (result.conflict) {
      setConflicts((prev) => [...prev, result.conflict!]);
      setMessage("Sync conflict detected — choose how to resolve below.");
    } else {
      setMessage(result.message ?? "Screening failed.");
    }
    setBusy(false);
  }

  async function handleDonation() {
    if (!selectedDrive || !donorId.trim() || !bagNumber.trim()) return;
    setBusy(true);
    setMessage(null);
    const result = await recordDriveDonation(selectedDrive, {
      donor_id: donorId.trim(),
      bag_number: bagNumber.trim(),
      volume_ml: parseInt(volumeMl, 10) || undefined,
    });
    if (result.ok) {
      setMessage(result.queued ? "Donation queued offline — will sync when connected." : "Donation captured.");
      setBagNumber("");
      if (result.queued) await load();
      else await load();
    } else if (result.conflict) {
      setConflicts((prev) => [...prev, result.conflict!]);
      setMessage("Sync conflict detected — choose how to resolve below.");
    } else {
      setMessage(result.message ?? "Donation capture failed.");
    }
    setBusy(false);
  }

  async function handleResolveConflict(conflict: DriveSyncConflict, resolution: "KEEP_LOCAL" | "USE_SERVER" | "MERGE") {
    const conflictId = conflict.conflict_id;
    const driveId = conflict.drive_id ?? selectedDrive;
    if (!conflictId || !driveId) return;
    setBusy(true);
    const ok = await resolveDriveSyncConflict(conflictId, { drive_id: driveId, resolution });
    if (ok) {
      setConflicts((prev) => prev.filter((c) => c.conflict_id !== conflictId));
      setMessage(`Conflict resolved (${resolution.replace("_", " ").toLowerCase()}).`);
      await load();
    } else {
      setMessage("Could not resolve conflict — try again or contact blood bank supervisor.");
    }
    setBusy(false);
  }

  if (loading) return <Screen><Header title="Drive Capture" /><LoadingSpinner label="Loading drives..." /></Screen>;
  if (error) return <Screen><Header title="Drive Capture" /><ErrorState message={error.message} onRetry={load} /></Screen>;

  return (
    <Screen>
      <Header title="Donation Drive Capture" />
      <ScrollView style={styles.container} contentContainerStyle={styles.content}>
        {pendingCount > 0 ? (
          <View style={styles.pendingBanner}>
            <Text style={styles.pendingText}>{pendingCount} capture(s) waiting to sync when online.</Text>
            <Button
              title="Sync pending captures"
              size="sm"
              variant="outline"
              disabled={busy}
              onPress={async () => {
                setBusy(true);
                const result = await replayPendingDriveCaptures();
                setMessage(
                  `Synced ${result.replayed}, failed ${result.failed}, conflicts ${result.conflicts}.`,
                );
                await load();
                setBusy(false);
              }}
            />
          </View>
        ) : null}

        {conflicts.length > 0 ? (
          <View style={styles.conflictSection}>
            <Text style={styles.sectionTitle}>Sync conflicts</Text>
            {conflicts.map((conflict) => (
              <Card key={conflict.conflict_id ?? conflict.local_op_id}>
                <CardBody>
                  <Text style={styles.conflictTitle}>Drive capture conflict</Text>
                  <Text style={styles.meta}>Op: {conflict.local_op_id ?? "—"}</Text>
                  <View style={styles.diffBlock}>
                    <Text style={styles.diffLabel}>Local</Text>
                    <Text style={styles.diffValue}>{JSON.stringify(conflict.local_state ?? {}, null, 0)}</Text>
                    <Text style={[styles.diffLabel, { marginTop: 6 }]}>Server</Text>
                    <Text style={styles.diffValue}>{JSON.stringify(conflict.server_state ?? {}, null, 0)}</Text>
                  </View>
                  <View style={styles.resolutionRow}>
                    {RESOLUTIONS.map((r) => (
                      <Button
                        key={r.value}
                        title={r.label}
                        size="sm"
                        variant={r.value === "KEEP_LOCAL" ? "primary" : "outline"}
                        onPress={() => handleResolveConflict(conflict, r.value)}
                        disabled={busy}
                      />
                    ))}
                  </View>
                </CardBody>
              </Card>
            ))}
          </View>
        ) : null}

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
            <TextField label="Donor ID (UUID)" value={donorId} onChange={setDonorId} testID="madi-drive-donor-id" />
            <Select label="Screening result" value={screenResult} onChange={setScreenResult} options={SCREEN_RESULTS} />
            <Button title="Record screening" variant="outline" onPress={handleScreen} disabled={busy || !donorId.trim()} />
            <TextField label="Bag number" value={bagNumber} onChange={setBagNumber} />
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
  pendingBanner: { backgroundColor: "#FEF3C7", borderRadius: 10, padding: 10, borderWidth: 1, borderColor: "#FCD34D" },
  pendingText: { fontSize: 12, color: "#92400E" },
  conflictSection: { gap: 8 },
  conflictTitle: { fontSize: 14, fontWeight: "600", marginBottom: 4 },
  diffBlock: { marginVertical: 8 },
  diffLabel: { fontSize: 11, fontWeight: "600", color: "#6B7280" },
  diffValue: { fontSize: 11, color: "#374151", fontFamily: "monospace" },
  resolutionRow: { gap: 6, marginTop: 8 },
  card: { backgroundColor: "#FFFFFF", borderRadius: 12, padding: 14, borderWidth: 1, borderColor: "#E5E7EB", gap: 6 },
  cardSelected: { borderColor: "#DC2626", backgroundColor: "#FEF2F2" },
  title: { fontSize: 15, fontWeight: "600" },
  meta: { fontSize: 12, color: "#6B7280" },
  form: { gap: 10, marginTop: 8 },
  sectionTitle: { fontSize: 14, fontWeight: "600" },
  message: { fontSize: 13, color: "#059669" },
});
