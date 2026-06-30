/**
 * DaidzaiFieldMissionScreen — Provider/responder field-mission console. Shows incoming
 * incidents, lets the responder accept and advance the mission (responding → on-scene →
 * transporting → hand-over), and record the clinical hand-over to a PCT encounter. Daidzai
 * tracks the mission; Nhume executes; PCT/Butano own the clinical record.
 */
import React, { useCallback, useEffect, useState } from "react";
import { View, Text, StyleSheet, ScrollView, RefreshControl } from "react-native";
import { Screen, Header, Card, CardBody, Button, TextField, LoadingSpinner, EmptyState, ErrorState, Badge } from "@impilo/mobile-design-system";
import { listIncidents, recordMissionStatus, recordHandover, type FieldIncident } from "../../services/daidzaiFieldService";

const NEXT_STATUS: Record<string, string> = {
  TRIAGED: "RESPONDING",
  DISPATCH_REQUESTED: "RESPONDING",
  RESPONDING: "ON_SCENE",
  ON_SCENE: "TRANSPORTING",
  TRANSPORTING: "HANDOVER",
};

export function DaidzaiFieldMissionScreen() {
  const [items, setItems] = useState<FieldIncident[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [pctRef, setPctRef] = useState<Record<string, string>>({});

  const load = useCallback(async (refresh = false) => {
    if (refresh) setRefreshing(true); else setLoading(true);
    setError(null);
    try {
      setItems(await listIncidents());
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not load incidents.");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const advance = async (inc: FieldIncident) => {
    const next = NEXT_STATUS[inc.status ?? ""] ?? "RESPONDING";
    setBusy(inc.id);
    try {
      await recordMissionStatus(inc.id, next);
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not record status.");
    } finally {
      setBusy(null);
    }
  };

  const handover = async (inc: FieldIncident) => {
    const ref = (pctRef[inc.id] ?? "").trim();
    if (!ref) return;
    setBusy(inc.id);
    try {
      await recordHandover(inc.id, ref);
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not record hand-over.");
    } finally {
      setBusy(null);
    }
  };

  return (
    <Screen>
      <Header title="Field missions" subtitle="Incoming dispatch · accept · navigate · hand-over" />
      {loading ? (
        <LoadingSpinner />
      ) : error ? (
        <ErrorState message={error} onRetry={() => void load()} />
      ) : items.length === 0 ? (
        <EmptyState message="No incoming incidents." />
      ) : (
        <ScrollView
          contentContainerStyle={styles.body}
          refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => void load(true)} />}
        >
          {items.map((inc) => (
            <Card key={inc.id}>
              <CardBody>
                <View style={styles.head}>
                  <Text style={styles.ref}>{inc.incidentReference ?? inc.id}</Text>
                  {inc.triageCategory ? <Badge label={inc.triageCategory} tone="danger" /> : null}
                </View>
                <Text style={styles.meta}>
                  {inc.emergencyCategory} · {inc.severity} · {inc.status}
                </Text>
                {inc.locationDescription ? <Text style={styles.meta}>{inc.locationDescription}</Text> : null}

                {inc.status !== "HANDOVER" ? (
                  <Button
                    title={`Advance to ${NEXT_STATUS[inc.status ?? ""] ?? "RESPONDING"}`}
                    variant="primary"
                    loading={busy === inc.id}
                    disabled={busy === inc.id}
                    onPress={() => advance(inc)}
                  />
                ) : null}

                <Text style={styles.label}>Clinical hand-over (PCT encounter ref)</Text>
                <TextField
                  value={pctRef[inc.id] ?? ""}
                  onChangeText={(v) => setPctRef((p) => ({ ...p, [inc.id]: v }))}
                  placeholder="PCT encounter ref"
                />
                <Button
                  title="Record hand-over"
                  variant="secondary"
                  disabled={busy === inc.id || !(pctRef[inc.id] ?? "").trim()}
                  onPress={() => handover(inc)}
                />
              </CardBody>
            </Card>
          ))}
        </ScrollView>
      )}
    </Screen>
  );
}

const styles = StyleSheet.create({
  body: { padding: 16, gap: 12 },
  head: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  ref: { fontFamily: "monospace", fontSize: 12, color: "#64748b" },
  meta: { color: "#475569", fontSize: 13, marginTop: 4 },
  label: { fontWeight: "600", marginTop: 12, marginBottom: 6 },
});
