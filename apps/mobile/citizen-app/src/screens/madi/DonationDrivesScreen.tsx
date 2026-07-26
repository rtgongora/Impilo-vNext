import React, { useCallback, useEffect, useState } from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
import { Screen, Header, Button, TextField, LoadingSpinner, EmptyState, ErrorState, Badge, colors } from "@impilo/mobile-design-system";
import { MobileNearbyMapView } from "@impilo/mobile-ndila";
import { fetchDrivesNearMe, registerForDrive, type DonationDriveSummary } from "../../services/madiService";

interface Props {
  onBack: () => void;
}

export function DonationDrivesScreen({ onBack }: Props) {
  const [siteRef, setSiteRef] = useState("");
  const [drives, setDrives] = useState<DonationDriveSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const [registeringId, setRegisteringId] = useState<string | null>(null);

  const search = useCallback(async () => {
    if (!siteRef.trim()) return;
    setLoading(true);
    setError(null);
    try {
      const items = await fetchDrivesNearMe({ ndila_site_ref: siteRef.trim(), radius_km: 25 });
      setDrives(items);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setLoading(false);
    }
  }, [siteRef]);

  useEffect(() => {
    if (siteRef.length >= 3) {
      search();
    }
  }, [siteRef, search]);

  async function handleRegister(driveId: string) {
    setRegisteringId(driveId);
    await registerForDrive(driveId);
    setRegisteringId(null);
  }

  return (
    <Screen>
      <Header title="Donation Drives" onBack={onBack} />
      <ScrollView style={styles.container} contentContainerStyle={styles.content}>
        <MobileNearbyMapView entityTypes={["PUBLIC_HEALTH_SITE", "FACILITY"]} autoCapture={false} height={180} />
        <Text style={styles.hint}>Enter your Ndila site reference (district or facility site) to find drives near you.</Text>
        <TextField
          label="Ndila site reference"
          value={siteRef}
          onChange={setSiteRef}
          placeholder="e.g. harare-central-site"
          testID="madi-drives-site-ref"
        />
        <Button title="Search drives" variant="primary" onPress={search} disabled={!siteRef.trim() || loading} />
        {loading ? <LoadingSpinner label="Searching drives..." /> : null}
        {error ? <ErrorState message={error.message} onRetry={search} /> : null}
        {!loading && drives.length === 0 && siteRef.length >= 3 ? (
          <EmptyState icon="location-outline" title="No drives found" description="Try a wider area or check back later." />
        ) : null}
        {drives.map((drive) => (
          <View key={drive.drive_id} style={styles.card} testID={`madi-drive-${drive.drive_id}`}>
            <Text style={styles.title}>{drive.title ?? "Donation drive"}</Text>
            <Text style={styles.meta}>
              {drive.start_at ? new Date(drive.start_at).toLocaleString() : "Date TBC"}
              {drive.distance_km != null ? ` · ${drive.distance_km.toFixed(1)} km` : ""}
            </Text>
            {drive.description ? <Text style={styles.desc}>{drive.description}</Text> : null}
            <View style={styles.row}>
              <Badge variant="outline">{drive.status ?? "PUBLISHED"}</Badge>
              <Button
                title={registeringId === drive.drive_id ? "Registering…" : "Register"}
                size="sm"
                variant="outline"
                onPress={() => handleRegister(drive.drive_id)}
                disabled={registeringId === drive.drive_id}
              />
            </View>
          </View>
        ))}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: 16, gap: 12 },
  hint: { fontSize: 13, color: colors.gray[500] },
  card: { backgroundColor: "#FFFFFF", borderRadius: 12, padding: 14, borderWidth: 1, borderColor: colors.gray[200], gap: 6 },
  title: { fontSize: 15, fontWeight: "600" },
  meta: { fontSize: 12, color: colors.gray[500] },
  desc: { fontSize: 13, color: colors.gray[700] },
  row: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", marginTop: 6 },
});
