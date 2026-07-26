import React, { useCallback, useEffect, useState } from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
import { Screen, Header, LoadingSpinner, EmptyState, ErrorState, colors } from "@impilo/mobile-design-system";
import { fetchDonorHistory, type DonorHistory } from "../../services/madiService";

interface Props {
  onBack: () => void;
}

export function DonorHistoryScreen({ onBack }: Props) {
  const [history, setHistory] = useState<DonorHistory>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setHistory(await fetchDonorHistory());
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const donations = history.donations ?? [];
  const screenings = history.screenings ?? [];
  const deferrals = history.deferrals ?? [];

  return (
    <Screen>
      <Header title="Donation History" onBack={onBack} />
      <ScrollView style={styles.container} contentContainerStyle={styles.content}>
        {loading ? <LoadingSpinner label="Loading history..." /> : null}
        {error ? <ErrorState message={error.message} onRetry={load} /> : null}
        {!loading && !error && donations.length === 0 && screenings.length === 0 && deferrals.length === 0 ? (
          <EmptyState icon="time-outline" title="No history yet" description="Your donations and screenings will appear here." />
        ) : null}
        {donations.length > 0 ? (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Donations</Text>
            {donations.map((d, i) => (
              <View key={d.donation_id ?? `don-${i}`} style={styles.row}>
                <Text style={styles.rowTitle}>Bag {d.bag_number ?? "—"}</Text>
                <Text style={styles.rowMeta}>
                  {d.volume_ml ? `${d.volume_ml} ml` : ""}
                  {d.collected_at ? ` · ${new Date(d.collected_at).toLocaleDateString()}` : ""}
                </Text>
              </View>
            ))}
          </View>
        ) : null}
        {screenings.length > 0 ? (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Screenings</Text>
            {screenings.map((s, i) => (
              <View key={s.screening_id ?? `scr-${i}`} style={styles.row}>
                <Text style={styles.rowTitle}>{s.result ?? "Screened"}</Text>
                <Text style={styles.rowMeta}>{s.screened_at ? new Date(s.screened_at).toLocaleDateString() : ""}</Text>
              </View>
            ))}
          </View>
        ) : null}
        {deferrals.length > 0 ? (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Deferrals</Text>
            {deferrals.map((d, i) => (
              <View key={`def-${i}`} style={styles.row}>
                <Text style={styles.rowTitle}>{d.reason_code ?? "Deferred"}</Text>
                <Text style={styles.rowMeta}>Until {d.deferred_until ?? "review"}</Text>
              </View>
            ))}
          </View>
        ) : null}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: 16, gap: 16 },
  section: { gap: 8 },
  sectionTitle: { fontSize: 14, fontWeight: "600", color: colors.gray[900] },
  row: { backgroundColor: "#FFFFFF", borderRadius: 10, padding: 12, borderWidth: 1, borderColor: colors.gray[200] },
  rowTitle: { fontSize: 14, fontWeight: "600" },
  rowMeta: { fontSize: 12, color: colors.gray[500], marginTop: 2 },
});
