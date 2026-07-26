/**
 * FacilityRegulatorsScreen — facility ↔ regulator (multi-council) relationships for facility staff.
 *
 * Lists a facility's council/regulator relationships and lets an operator link a new council.
 * Facilities in scope are read from the control-tower aggregate (reuses controlTowerService);
 * relationships + linking are backed by the live facility-mode regulators routes (regulatorService,
 * same as the web facility regulators page). Closes part of GAP-19. Real data only — no mock rows.
 */

import React, { useCallback, useEffect, useState } from "react";
import { View, Text, ScrollView, StyleSheet, TextInput, TouchableOpacity } from "react-native";
import { Screen, Header, Card, CardHeader, CardBody, Badge, Button, LoadingSpinner, EmptyState, ErrorState, colors } from "@impilo/mobile-design-system";
import type { BadgeVariant } from "@impilo/mobile-design-system";
import {
  fetchControlTowerAggregate,
  type ControlTowerFacility,
} from "../../services/controlTowerService";
import {
  fetchFacilityRegulators,
  linkRegulator,
  type RegulatorRelationship,
} from "../../services/regulatorService";

function statusVariant(status: string): BadgeVariant {
  switch (status?.toUpperCase()) {
    case "ACTIVE":
      return "success";
    case "PENDING":
      return "warning";
    case "SUSPENDED":
    case "REVOKED":
      return "destructive";
    default:
      return "secondary";
  }
}

export function FacilityRegulatorsScreen() {
  const [facilities, setFacilities] = useState<ControlTowerFacility[]>([]);
  const [selected, setSelected] = useState<ControlTowerFacility | null>(null);
  const [regulators, setRegulators] = useState<RegulatorRelationship[]>([]);
  const [loading, setLoading] = useState(true);
  const [regLoading, setRegLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // Link-council inline form
  const [councilId, setCouncilId] = useState("");
  const [councilCode, setCouncilCode] = useState("");
  const [regNumber, setRegNumber] = useState("");

  const loadFacilities = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const agg = await fetchControlTowerAggregate();
      setFacilities(agg.facilities);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load facilities");
    } finally {
      setLoading(false);
    }
  }, []);

  const loadRegulators = useCallback(async (facility: ControlTowerFacility) => {
    setSelected(facility);
    setRegLoading(true);
    try {
      setRegulators(await fetchFacilityRegulators(facility.facilityId));
    } catch {
      setRegulators([]);
    } finally {
      setRegLoading(false);
    }
  }, []);

  const onLink = useCallback(async () => {
    if (!selected || !councilId.trim() || !councilCode.trim()) return;
    setBusy(true);
    try {
      await linkRegulator(selected.facilityId, {
        councilId: councilId.trim(),
        councilCode: councilCode.trim(),
        registrationNumber: regNumber.trim() || undefined,
      });
      setCouncilId("");
      setCouncilCode("");
      setRegNumber("");
      await loadRegulators(selected);
    } finally {
      setBusy(false);
    }
  }, [selected, councilId, councilCode, regNumber, loadRegulators]);

  useEffect(() => {
    loadFacilities();
  }, [loadFacilities]);

  return (
    <Screen>
      <Header title="Regulators" />
      <View testID="facility-regulators-screen" style={styles.container}>
        {loading ? (
          <LoadingSpinner size="md" />
        ) : error ? (
          <ErrorState title="Error" message={error} onRetry={loadFacilities} />
        ) : (
          <ScrollView style={styles.scrollArea} contentContainerStyle={styles.scrollContent}>
            {facilities.length === 0 ? (
              <EmptyState title="No facilities" message="No facilities in your control scope" />
            ) : (
              <>
                <Text style={styles.sectionLabel}>Select a facility</Text>
                {facilities.map((f) => (
                  <TouchableOpacity
                    key={f.facilityId}
                    testID={`facility-${f.facilityId}`}
                    onPress={() => loadRegulators(f)}
                    activeOpacity={0.7}
                  >
                    <Card>
                      <CardBody>
                        <View style={styles.listRow}>
                          <Text style={styles.boldText}>{f.name}</Text>
                          {selected?.facilityId === f.facilityId ? (
                            <Badge variant="primary">Selected</Badge>
                          ) : null}
                        </View>
                      </CardBody>
                    </Card>
                  </TouchableOpacity>
                ))}
              </>
            )}

            {selected && (
              <>
                <Card>
                  <CardHeader title={`${selected.name} — councils`} />
                  <CardBody>
                    {regLoading ? (
                      <LoadingSpinner size="md" />
                    ) : regulators.length === 0 ? (
                      <EmptyState title="No councils linked" message="This facility has no regulator relationships" />
                    ) : (
                      <View style={styles.relList}>
                        {regulators.map((r) => (
                          <View key={r.id} testID={`regulator-${r.id}`} style={styles.relRow}>
                            <View style={styles.relInfo}>
                              <Text style={styles.boldText}>{r.councilCode}</Text>
                              <Text style={styles.detailText}>
                                {r.relationshipType}
                                {r.registrationNumber ? ` · ${r.registrationNumber}` : ""}
                              </Text>
                            </View>
                            <Badge variant={statusVariant(r.status)}>{r.status}</Badge>
                          </View>
                        ))}
                      </View>
                    )}
                  </CardBody>
                </Card>

                <Card>
                  <CardHeader title="Link a council" />
                  <CardBody>
                    <TextInput
                      testID="council-id"
                      style={styles.input}
                      placeholder="Council ID"
                      value={councilId}
                      onChangeText={setCouncilId}
                    />
                    <TextInput
                      testID="council-code"
                      style={styles.input}
                      placeholder="Council code (e.g. MDPCZ)"
                      value={councilCode}
                      onChangeText={setCouncilCode}
                    />
                    <TextInput
                      testID="reg-number"
                      style={styles.input}
                      placeholder="Registration number (optional)"
                      value={regNumber}
                      onChangeText={setRegNumber}
                    />
                    <Button
                      title={busy ? "Linking…" : "Link council"}
                      variant="primary"
                      onPress={onLink}
                      disabled={busy || !councilId.trim() || !councilCode.trim()}
                      testID="link-council"
                    />
                  </CardBody>
                </Card>
              </>
            )}
          </ScrollView>
        )}

        <View style={styles.refreshContainer}>
          <Button title="Refresh" variant="outline" onPress={loadFacilities} testID="refresh-regulators" />
        </View>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 16 },
  scrollArea: { flex: 1 },
  scrollContent: { gap: 12, paddingBottom: 16 },
  sectionLabel: { fontSize: 13, fontWeight: "700", color: colors.gray[500] },
  listRow: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  relList: { gap: 12 },
  relRow: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  relInfo: { flex: 1, gap: 2 },
  boldText: { fontWeight: "700", color: colors.gray[900] },
  detailText: { fontSize: 13, color: colors.gray[500] },
  input: {
    borderWidth: 1,
    borderColor: colors.gray[300],
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 8,
    marginBottom: 8,
    color: colors.gray[900],
  },
  refreshContainer: { marginTop: 8 },
});
