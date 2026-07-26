/**
 * ControlTowerScreen — facility operations command view for supervisors / facility staff.
 *
 * Surfaces the fleet aggregate (facility count, open alerts, per-facility occupancy) and,
 * on selecting a facility, its open alerts with a tap-to-acknowledge action. Backed by the
 * live experience-bff facility-mode control tower (see controlTowerService), the same routes
 * the web control tower consumes. Closes GAP-19 (no mobile control-tower surface). No mock data.
 */

import React, { useCallback, useEffect, useState } from "react";
import { View, Text, ScrollView, StyleSheet, TouchableOpacity } from "react-native";
import { Screen, Header, Card, CardHeader, CardBody, Badge, Button, LoadingSpinner, EmptyState, ErrorState, colors } from "@impilo/mobile-design-system";
import type { BadgeVariant } from "@impilo/mobile-design-system";
import {
  acknowledgeAlert,
  fetchControlTowerAggregate,
  fetchFacilityAlerts,
  type ControlTowerAggregate,
  type ControlTowerFacility,
  type FacilityAlert,
} from "../../services/controlTowerService";

function severityVariant(severity: string): BadgeVariant {
  switch (severity?.toUpperCase()) {
    case "CRITICAL":
    case "HIGH":
      return "destructive";
    case "MEDIUM":
      return "warning";
    case "LOW":
      return "info";
    default:
      return "secondary";
  }
}

export function ControlTowerScreen() {
  const [aggregate, setAggregate] = useState<ControlTowerAggregate | null>(null);
  const [selected, setSelected] = useState<ControlTowerFacility | null>(null);
  const [alerts, setAlerts] = useState<FacilityAlert[]>([]);
  const [loading, setLoading] = useState(true);
  const [alertsLoading, setAlertsLoading] = useState(false);
  const [acking, setAcking] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadAggregate = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setAggregate(await fetchControlTowerAggregate());
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load control tower");
    } finally {
      setLoading(false);
    }
  }, []);

  const loadAlerts = useCallback(async (facility: ControlTowerFacility) => {
    setSelected(facility);
    setAlertsLoading(true);
    try {
      setAlerts(await fetchFacilityAlerts(facility.facilityId));
    } catch {
      setAlerts([]);
    } finally {
      setAlertsLoading(false);
    }
  }, []);

  const onAcknowledge = useCallback(
    async (alertId: string) => {
      setAcking(alertId);
      try {
        await acknowledgeAlert(alertId);
        if (selected) await loadAlerts(selected);
        await loadAggregate();
      } finally {
        setAcking(null);
      }
    },
    [selected, loadAlerts, loadAggregate],
  );

  useEffect(() => {
    loadAggregate();
  }, [loadAggregate]);

  return (
    <Screen>
      <Header title="Control Tower" />
      <View testID="control-tower-screen" style={styles.container}>
        {loading ? (
          <LoadingSpinner size="md" />
        ) : error ? (
          <ErrorState title="Error" message={error} onRetry={loadAggregate} />
        ) : !aggregate ? (
          <EmptyState title="No data" message="Control tower is unavailable" />
        ) : (
          <ScrollView style={styles.scrollArea} contentContainerStyle={styles.scrollContent}>
            <Card>
              <CardBody>
                <View style={styles.statsGrid}>
                  <View style={styles.statItem}>
                    <Text style={styles.statValue}>{aggregate.facilityCount}</Text>
                    <Text style={styles.statLabel}>Facilities</Text>
                  </View>
                  <View style={styles.statItem}>
                    <Text style={styles.statValue}>{aggregate.openAlertCount}</Text>
                    <Text style={styles.statLabel}>Open Alerts</Text>
                  </View>
                </View>
                {aggregate.visibility === "AGGREGATE_ONLY" && (
                  <Text style={styles.footerNote}>
                    Aggregate view — per-facility detail is restricted for your context.
                  </Text>
                )}
              </CardBody>
            </Card>

            {aggregate.facilities.length === 0 ? (
              <EmptyState title="No facilities" message="No facilities in your control scope" />
            ) : (
              aggregate.facilities.map((f) => (
                <TouchableOpacity
                  key={f.facilityId}
                  testID={`facility-${f.facilityId}`}
                  onPress={() => loadAlerts(f)}
                  activeOpacity={0.7}
                >
                  <Card>
                    <CardBody>
                      <View style={styles.listRow}>
                        <View style={styles.listRowInfo}>
                          <Text style={styles.boldText}>{f.name}</Text>
                          <Text style={styles.detailText}>
                            {f.bedOccupancyPercent != null
                              ? `${Math.round(f.bedOccupancyPercent)}% beds occupied`
                              : "Occupancy unavailable"}
                          </Text>
                        </View>
                        <Badge variant={f.openAlerts > 0 ? "destructive" : "success"}>
                          {`${f.openAlerts} alert${f.openAlerts === 1 ? "" : "s"}`}
                        </Badge>
                      </View>
                    </CardBody>
                  </Card>
                </TouchableOpacity>
              ))
            )}

            {selected && (
              <Card>
                <CardHeader title={`${selected.name} — open alerts`} />
                <CardBody>
                  {alertsLoading ? (
                    <LoadingSpinner size="md" />
                  ) : alerts.length === 0 ? (
                    <EmptyState title="No open alerts" message="This facility is all clear" />
                  ) : (
                    <View style={styles.alertList}>
                      {alerts.map((a) => (
                        <View key={a.id} testID={`alert-${a.id}`} style={styles.alertRow}>
                          <View style={styles.alertInfo}>
                            <View style={styles.alertTitleRow}>
                              <Badge variant={severityVariant(a.severity)}>{a.severity}</Badge>
                              <Text style={styles.boldText}>{a.title}</Text>
                            </View>
                            {a.description ? (
                              <Text style={styles.detailText}>{a.description}</Text>
                            ) : null}
                            <Text style={styles.auditTime}>
                              {new Date(a.createdAt).toLocaleString()}
                            </Text>
                          </View>
                          {a.status === "OPEN" ? (
                            <Button
                              title={acking === a.id ? "…" : "Ack"}
                              variant="outline"
                              onPress={() => onAcknowledge(a.id)}
                              testID={`ack-${a.id}`}
                            />
                          ) : (
                            <Badge variant="secondary">{a.status}</Badge>
                          )}
                        </View>
                      ))}
                    </View>
                  )}
                </CardBody>
              </Card>
            )}
          </ScrollView>
        )}

        <View style={styles.refreshContainer}>
          <Button title="Refresh" variant="outline" onPress={loadAggregate} testID="refresh-control-tower" />
        </View>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 16 },
  scrollArea: { flex: 1 },
  scrollContent: { gap: 12, paddingBottom: 16 },
  statsGrid: { flexDirection: "row", flexWrap: "wrap", gap: 16 },
  statItem: { width: "45%", alignItems: "center", paddingVertical: 12 },
  statValue: { fontSize: 28, fontWeight: "900", color: colors.gray[900] },
  statLabel: { fontSize: 13, color: colors.gray[500], marginTop: 4 },
  listRow: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  listRowInfo: { flex: 1, gap: 2 },
  boldText: { fontWeight: "700", color: colors.gray[900] },
  detailText: { fontSize: 13, color: colors.gray[500] },
  alertList: { gap: 12 },
  alertRow: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", gap: 8 },
  alertInfo: { flex: 1, gap: 2 },
  alertTitleRow: { flexDirection: "row", alignItems: "center", gap: 8 },
  auditTime: { fontSize: 11, color: colors.gray[400], fontWeight: "600" },
  footerNote: { fontSize: 12, color: colors.gray[400], textAlign: "center", marginTop: 8 },
  refreshContainer: { marginTop: 8 },
});
