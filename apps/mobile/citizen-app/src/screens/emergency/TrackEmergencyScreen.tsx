/**
 * TrackEmergencyScreen — Citizen live tracking of a raised SOS. Two paths, one screen:
 *
 *   • requestId  — the AUTHENTICATED Daidzai path (UUID): status + linked-incident missions.
 *   • reference  — the PUBLIC status-by-reference path (human ref like "SOS-1A2B3C"): the
 *                  only handle a guest who submitted anonymously has. PII-free status only.
 *
 * Real data via the Daidzai BFF (authed) or the anonymous public gateway lane. No fake map.
 */
import React, { useCallback, useEffect, useState } from "react";
import { View, Text, StyleSheet, ScrollView, RefreshControl } from "react-native";
import { Screen, Header, Card, CardBody, LoadingSpinner, ErrorState, EmptyState, Badge } from "@impilo/mobile-design-system";
import {
  fetchRequest,
  fetchMissions,
  fetchRequestByReference,
  type EmergencyRequest,
  type MissionEvent,
} from "../../services/emergencyService";

const STATUS_LABEL: Record<string, string> = {
  RECEIVED: "Received",
  PENDING_CALLBACK: "Awaiting callback",
  TRIAGED: "Triaged",
  LINKED: "Triaged",
  DISPATCH_REQUESTED: "Help requested",
  RESPONDING: "Responding",
  ON_SCENE: "On scene",
  TRANSPORTING: "Transporting",
  HANDOVER: "Handed over to care",
};

interface TrackView {
  reference: string;
  status?: string;
  category?: string;
  severity?: string;
  message?: string;
}

export function TrackEmergencyScreen({ requestId, reference }: { requestId?: string; reference?: string }) {
  const [view, setView] = useState<TrackView | null>(null);
  const [missions, setMissions] = useState<MissionEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const byReference = !requestId && !!reference;

  const load = useCallback(async (refresh = false) => {
    if (refresh) setRefreshing(true); else setLoading(true);
    setError(null);
    try {
      if (byReference && reference) {
        const s = await fetchRequestByReference(reference);
        setView({
          reference: s.requestReference,
          status: s.status,
          category: s.emergencyCategory,
          severity: s.severity,
          message: s.message,
        });
        setMissions([]);
      } else if (requestId) {
        const r: EmergencyRequest = await fetchRequest(requestId);
        setView({
          reference: r.requestReference ?? r.id,
          status: r.status,
          category: r.emergencyCategory,
          severity: r.severity,
        });
        setMissions(r.incidentId ? await fetchMissions(r.incidentId) : []);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not load your request.");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [byReference, reference, requestId]);

  useEffect(() => { void load(); }, [load]);

  return (
    <Screen>
      <Header title="Track your request" subtitle={byReference ? "Live status by reference" : "Live status"} />
      {loading ? (
        <LoadingSpinner />
      ) : error ? (
        <ErrorState message={error} onRetry={() => void load()} />
      ) : view ? (
        <ScrollView
          contentContainerStyle={styles.body}
          refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => void load(true)} />}
        >
          <Card>
            <CardBody>
              <Text style={styles.ref}>{view.reference}</Text>
              <Text style={styles.status}>{STATUS_LABEL[view.status ?? ""] ?? view.status ?? "Received"}</Text>
              {view.category || view.severity ? (
                <Text style={styles.meta}>{[view.category, view.severity].filter(Boolean).join(" · ")}</Text>
              ) : null}
              {view.message ? <Text style={styles.message}>{view.message}</Text> : null}
            </CardBody>
          </Card>
          {byReference ? (
            <Text style={styles.help}>
              Keep this reference. A responder will call the number you gave to confirm before help is
              dispatched. If this is life-threatening, call the emergency numbers now.
            </Text>
          ) : (
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
          )}
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
  message: { color: "#334155", fontSize: 13, marginTop: 8 },
  help: { color: "#64748b", fontSize: 12, lineHeight: 18 },
  section: { fontWeight: "600", marginBottom: 8 },
  event: { flexDirection: "row", alignItems: "center", gap: 8, marginBottom: 8 },
});
