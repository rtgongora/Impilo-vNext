import React, { useCallback, useEffect, useState } from "react";
import { View, Text, ScrollView, StyleSheet, RefreshControl, Alert } from "react-native";
import { LoadingSpinner, EmptyState, ErrorState, Button, Badge, colors } from "@impilo/mobile-design-system";
import {
  approveEmergencyRedistribution,
  emergencyRedistributionId,
  fetchCentralBankMetrics,
  fetchEmergencyRedistributions,
  initiateEmergencyHandoff,
  type CentralBankMetrics,
  type EmergencyRedistribution,
} from "../../services/madiService";

function rowBloodGroup(row: EmergencyRedistribution): string {
  return row.blood_group ?? row.bloodGroup ?? "—";
}

function rowComponentType(row: EmergencyRedistribution): string {
  return (row.component_type ?? row.componentType ?? "Blood component").replace(/_/g, " ");
}

function rowUnits(row: EmergencyRedistribution): number {
  return row.units_requested ?? row.unitsRequested ?? 1;
}

function rowSource(row: EmergencyRedistribution): string {
  return row.source_facility_id ?? row.sourceFacilityId ?? "—";
}

function rowTarget(row: EmergencyRedistribution): string {
  return row.target_facility_id ?? row.targetFacilityId ?? "—";
}

function rowHandoffStatus(row: EmergencyRedistribution): string | undefined {
  return row.handoff_status ?? row.handoffStatus;
}

function rowHandoffError(row: EmergencyRedistribution): string | undefined {
  return row.handoff_error ?? row.handoffError;
}

export function MadiCentralBankScreen() {
  const [metrics, setMetrics] = useState<CentralBankMetrics | null>(null);
  const [rows, setRows] = useState<EmergencyRedistribution[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const [actingId, setActingId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      const [metricsResult, redistributionRows] = await Promise.all([
        fetchCentralBankMetrics(),
        fetchEmergencyRedistributions(),
      ]);
      setMetrics(metricsResult);
      setRows(redistributionRows);
      if (!metricsResult) {
        setError(new Error("Central bank metrics unavailable — verify admin role and retry."));
      }
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function handleApprove(row: EmergencyRedistribution) {
    const id = emergencyRedistributionId(row);
    if (!id) return;
    setActingId(id);
    const result = await approveEmergencyRedistribution(id, {
      approved_by: "mobile-central-ops",
      units_allocated: rowUnits(row),
      status: "FULFILLED",
    });
    setActingId(null);
    if (result) {
      Alert.alert("Approved", "Emergency redistribution approved and fulfilled.");
      await load();
    } else {
      Alert.alert("Failed", "Approval failed — verify admin role.");
    }
  }

  async function handleHandoff(row: EmergencyRedistribution) {
    const id = emergencyRedistributionId(row);
    if (!id) return;
    setActingId(id);
    const result = await initiateEmergencyHandoff(id, { initiated_by: "mobile-central-ops" });
    setActingId(null);
    if (result) {
      Alert.alert("Handoff", "NHUME handoff retry initiated.");
      await load();
    } else {
      Alert.alert("Failed", "Handoff retry failed.");
    }
  }

  if (loading) return <LoadingSpinner label="Loading central bank metrics…" />;
  if (error && !metrics) return <ErrorState message={error.message} onRetry={load} />;

  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={styles.content}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => { setRefreshing(true); load(); }} />}
    >
      <Text style={styles.title}>Central Blood Bank</Text>
      <Text style={styles.subtitle}>National inventory and emergency redistribution queue</Text>

      {metrics ? (
        <View style={styles.kpiRow}>
          {[
            { label: "Total units", value: metrics.totalUnits ?? 0 },
            { label: "Available units", value: metrics.availableUnits ?? 0 },
            { label: "Open haemovigilance", value: metrics.openHaemovigilanceCases ?? 0 },
          ].map(({ label, value }) => (
            <View key={label} style={styles.kpiCard} testID={`central-bank-kpi-${label.replace(/\s+/g, "-").toLowerCase()}`}>
              <Text style={styles.kpiLabel}>{label}</Text>
              <Text style={styles.kpiValue}>{String(value)}</Text>
            </View>
          ))}
        </View>
      ) : (
        <Text style={styles.hint}>Central metrics unavailable.</Text>
      )}

      <Text style={styles.sectionTitle}>Emergency redistribution queue</Text>
      {rows.length === 0 ? (
        <EmptyState
          icon="water-outline"
          title="No redistribution requests"
          description="Emergency allocation requests will appear here for central bank approval."
        />
      ) : (
        rows.map((row) => {
          const id = emergencyRedistributionId(row);
          const handoff = rowHandoffStatus(row);
          return (
            <View key={id || `${rowSource(row)}-${rowTarget(row)}`} style={styles.card} testID={`central-bank-row-${id}`}>
              <Text style={styles.cardTitle}>
                {rowBloodGroup(row)} {rowComponentType(row)} — {rowUnits(row)} unit(s)
              </Text>
              <Text style={styles.meta}>
                {rowSource(row)} → {rowTarget(row)} · {row.status ?? "—"}
                {handoff ? ` · handoff ${handoff}` : ""}
              </Text>
              {row.reason ? <Text style={styles.meta}>{row.reason}</Text> : null}
              {rowHandoffError(row) ? (
                <Text style={styles.warn}>{rowHandoffError(row)}</Text>
              ) : null}
              <View style={styles.actions}>
                <Badge variant="outline">{row.status ?? "UNKNOWN"}</Badge>
                {row.status === "REQUESTED" && id ? (
                  <Button
                    title={actingId === id ? "Approving…" : "Approve & fulfil"}
                    size="sm"
                    onPress={() => handleApprove(row)}
                    disabled={actingId === id}
                  />
                ) : null}
                {handoff === "HANDOFF_FAILED" && id ? (
                  <Button
                    title={actingId === id ? "Retrying…" : "Retry NHUME handoff"}
                    size="sm"
                    onPress={() => handleHandoff(row)}
                    disabled={actingId === id}
                  />
                ) : null}
              </View>
            </View>
          );
        })
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: 16, paddingBottom: 32, gap: 12 },
  title: { fontSize: 18, fontWeight: "700", color: colors.gray[900] },
  subtitle: { fontSize: 13, color: colors.gray[500], marginBottom: 4 },
  kpiRow: { flexDirection: "row", flexWrap: "wrap", gap: 8 },
  kpiCard: {
    flex: 1,
    minWidth: 100,
    backgroundColor: "#FFFFFF",
    borderRadius: 12,
    padding: 12,
    borderWidth: 1,
    borderColor: colors.gray[200],
  },
  kpiLabel: { fontSize: 10, textTransform: "uppercase", color: colors.gray[500], fontWeight: "600" },
  kpiValue: { fontSize: 24, fontWeight: "700", color: colors.gray[900], marginTop: 4 },
  hint: { fontSize: 13, color: colors.gray[400] },
  sectionTitle: { fontSize: 15, fontWeight: "600", color: colors.gray[900], marginTop: 8 },
  card: {
    backgroundColor: "#FFFFFF",
    borderRadius: 12,
    padding: 14,
    borderWidth: 1,
    borderColor: colors.gray[200],
    gap: 4,
  },
  cardTitle: { fontSize: 14, fontWeight: "600", color: colors.gray[900] },
  meta: { fontSize: 12, color: colors.gray[500] },
  warn: { fontSize: 12, color: "#B45309" },
  actions: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", marginTop: 8, gap: 8 },
});
