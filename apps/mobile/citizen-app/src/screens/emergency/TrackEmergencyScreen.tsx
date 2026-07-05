/**
 * TrackEmergencyScreen — Citizen live tracking of a raised SOS. Shows request status +
 * the linked incident's mission timeline (real data via the Daidzai BFF). No fake map.
 */
import React, { useCallback, useEffect, useState } from "react";
import { View, Text, StyleSheet, ScrollView, RefreshControl } from "react-native";
import { Screen, Header, Card, CardBody, LoadingSpinner, ErrorState, EmptyState, Badge } from "@impilo/mobile-design-system";
import { fetchRequest, fetchMissions, type EmergencyRequest, type MissionEvent } from "../../services/emergencyService";

const STATUS_LABEL: Record<string, string> = {
  RECEIVED: "Received",
  TRIAGED: "Triaged",
  LINKED: "Triaged",
  DISPATCH_REQUESTED: "Help requested",
  RESPONDING: "Responding",
  ON_SCENE: "On scene",
  TRANSPORTING: "Transporting",
  HANDOVER: "Handed over to care",
};

export function TrackEmergencyScreen({ requestId }: { requestId: string }) {
  const [req, setReq] = useState<EmergencyRequest | null>(null);
  const [missions, setMissions] = useState<MissionEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (refresh = false) => {
    if (refresh) setRefreshing(true); else setLoading(true);
    setError(null);
    try {
      const r = await fetchRequest(requestId);
      setReq(r);
      setMissions(r.incidentId ? await fetchMissions(r.incidentId) : []);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not load your request.");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [requestId]);

  useEffect(() => { void load(); }, [load]);

  return (
    <Screen>
      <Header title="Track your request" subtitle="Live status" />
      {loading ? (
        <LoadingSpinner />
      ) : error ? (
        <ErrorState message={error} onRetry={() => void load()} />
      ) : req ? (
        <ScrollView
          contentContainerStyle={styles.body}
          refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => void load(true)} />}
        >
          <Card>
            <CardBody>
              <Text style={styles.ref}>{req.requestReference ?? req.id}</Text>
              <Text style={styles.status}>{STATUS_LABEL[req.status ?? ""] ?? req.status}</Text>
              <Text style={styles.meta}>{req.emergencyCategory} · {req.severity}</Text>
            </CardBody>
          </Card>
          <Card>
            <CardBody>
              <Text style={styles.section}>Response timeline</Text>
              {missions.length === 0 ? (
                <EmptyState title="No updates yet" description="You'll see each step here as the team acts." />
              ) : (
                missions.map((m) => (
                  <View key={m.id} style={styles.event}>
                    <Badge label={STATUS_LABEL[m.status ?? ""] ?? m.status ?? ""} variant="info" />
                    {m.occurredAt ? <Text style={styles.meta}>{new Date(m.occurredAt).toLocaleString()}</Text> : null}
                  </View>
                ))
              )}
            </CardBody>
          </Card>
        </ScrollView>
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  body: { padding: 16, gap: 12 },
  ref: { fontFamily: "monospace", fontSize: 12, color: "#64748b" },
  status: { fontSize: 18, fontWeight: "700", marginTop: 4 },
  meta: { color: "#64748b", fontSize: 12, marginTop: 4 },
  section: { fontWeight: "600", marginBottom: 8 },
  event: { flexDirection: "row", alignItems: "center", gap: 8, marginBottom: 8 },
});
