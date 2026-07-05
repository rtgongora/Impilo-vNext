/**
 * ConfirmDeathScreen — Provider death-confirmation slice (WS#8). An authorised clinician confirms a
 * death in the facility or in the community / home (including brought-in-dead), records the place,
 * context and identity status, and reviews the resulting case (the medico-legal screen runs
 * server-side in PCT). Respectful, plain language. PCT owns the DeathCase; this screen never
 * duplicates the record and never gates on payment.
 */
import React, { useCallback, useEffect, useState } from "react";
import { View, Text, StyleSheet, ScrollView, RefreshControl } from "react-native";
import { Screen, Header, Card, CardBody, Button, TextField, LoadingSpinner, EmptyState, ErrorState, Badge } from "@impilo/mobile-design-system";
import {
  listDeathCases,
  confirmDeath,
  confirmFieldDeath,
  reportBroughtInDead,
  caseId,
  type DeathCaseSummary,
} from "../../services/deathPathwayService";

export function ConfirmDeathScreen() {
  const [items, setItems] = useState<DeathCaseSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const [deathDatetime, setDeathDatetime] = useState("");
  const [location, setLocation] = useState("");
  const [cpid, setCpid] = useState("");
  const [community, setCommunity] = useState(false);
  const [broughtInDead, setBroughtInDead] = useState(false);

  const load = useCallback(async (refresh = false) => {
    if (refresh) setRefreshing(true); else setLoading(true);
    setError(null);
    try {
      setItems(await listDeathCases());
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not load death cases.");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const onConfirm = useCallback(async () => {
    if (!deathDatetime) return;
    setBusy(true);
    setMessage(null);
    setError(null);
    try {
      const identityStatus = community && !cpid ? "UNKNOWN" : "KNOWN";
      if (community && broughtInDead) {
        // Body physically brought in dead — its own endpoint (facility receipt + mortuary apply).
        await reportBroughtInDead({
          deathDatetime,
          placeOfDeathLocation: location || undefined,
          deceasedCpid: cpid || undefined,
          deceasedIdentityStatus: identityStatus,
        });
      } else if (community) {
        // Provider present in the field CONFIRMS the death (a confirmation, not a report).
        await confirmFieldDeath({
          deathDatetime,
          placeOfDeathLocation: location || undefined,
          deceasedCpid: cpid || undefined,
          deceasedIdentityStatus: identityStatus,
        });
      } else {
        await confirmDeath({
          deathDatetime,
          placeOfDeathContext: "INPATIENT",
          placeOfDeathLocation: location || undefined,
          deceasedCpid: cpid || undefined,
          deceasedIdentityStatus: identityStatus,
          sourceContext: "FACILITY",
        });
      }
      setMessage("Death confirmed. The case is now recorded.");
      setDeathDatetime("");
      setLocation("");
      setCpid("");
      void load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not confirm the death. Please try again.");
    } finally {
      setBusy(false);
    }
  }, [deathDatetime, location, cpid, community, broughtInDead, load]);

  return (
    <Screen>
      <Header title="Confirm a death" subtitle="Recorded with dignity" />
      <ScrollView
        contentContainerStyle={styles.content}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => void load(true)} />}
      >
        <Card>
          <CardBody>
            <Text style={styles.label}>Time of death</Text>
            <TextField value={deathDatetime} onChangeText={setDeathDatetime} placeholder="YYYY-MM-DDTHH:mm:ssZ" />
            <Text style={styles.label}>Place / ward</Text>
            <TextField value={location} onChangeText={setLocation} placeholder="Ward or location" />
            <Text style={styles.label}>Person (CPID, if known)</Text>
            <TextField value={cpid} onChangeText={setCpid} placeholder="Leave blank if unidentified" />
            <View style={styles.row}>
              <Button
                variant={community ? "primary" : "secondary"}
                title={community ? "Community / field death" : "Facility death"}
                onPress={() => setCommunity((v) => !v)}
              />
              {community && (
                <Button
                  variant={broughtInDead ? "primary" : "secondary"}
                  title={broughtInDead ? "Brought-in-dead" : "Mark brought-in-dead"}
                  onPress={() => setBroughtInDead((v) => !v)}
                />
              )}
            </View>
            <Button title={busy ? "Recording…" : "Record confirmation"} onPress={() => void onConfirm()} disabled={busy || !deathDatetime} />
            {message ? <Text style={styles.success}>{message}</Text> : null}
            {error ? <Text style={styles.error}>{error}</Text> : null}
          </CardBody>
        </Card>

        <Text style={styles.section}>Recent death cases</Text>
        {loading ? (
          <LoadingSpinner />
        ) : error && items.length === 0 ? (
          <ErrorState message={error} onRetry={() => void load()} />
        ) : items.length === 0 ? (
          <EmptyState title="No death cases recorded yet." />
        ) : (
          items.map((c) => (
            <Card key={caseId(c)}>
              <CardBody>
                <Text style={styles.caseTitle}>{c.patientCpid ?? "Unidentified"}</Text>
                <Text style={styles.meta}>{c.placeOfDeathContext ?? "—"} · {c.certificationStatus ?? "NOT_STARTED"}</Text>
                {c.coronerReferralRequired ? <Badge label="Coroner referral required" variant="warning" /> : null}
              </CardBody>
            </Card>
          ))
        )}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  content: { padding: 16, gap: 12 },
  label: { fontSize: 12, fontWeight: "600", marginTop: 8, marginBottom: 4 },
  row: { flexDirection: "row", gap: 8, marginVertical: 8, flexWrap: "wrap" },
  section: { fontSize: 14, fontWeight: "700", marginTop: 8 },
  caseTitle: { fontSize: 15, fontWeight: "600" },
  meta: { fontSize: 12, color: "#64748b", marginTop: 2 },
  success: { color: "#047857", marginTop: 8 },
  error: { color: "#b91c1c", marginTop: 8 },
});
