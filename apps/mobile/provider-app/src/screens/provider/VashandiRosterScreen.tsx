/**
 * VashandiRosterScreen — provider self-service roster view from governed mobile BFF.
 */
import React, { useCallback, useEffect, useState } from "react";
import { View, Text, ScrollView, StyleSheet, RefreshControl } from "react-native";
import { Screen, Header, Card, CardBody, CardHeader, Badge, Button, LoadingSpinner, EmptyState, ErrorState, colors } from "@impilo/mobile-design-system";
import { fetchMyRosters, normalizeSessionShifts, type VashandiRosterRow, type VashandiShiftRow } from "../../services/vashandiService";
import { fetchSessionExperienceContract } from "../../services/sessionExperienceService";

const STATUS_VARIANT: Record<string, "primary" | "secondary" | "warning" | "outline"> = {
  approved: "primary",
  draft: "secondary",
  scheduled: "warning",
  checked_in: "primary",
  completed: "outline",
};

function formatDate(value: string): string {
  if (!value) return "—";
  return new Date(value).toLocaleDateString([], { weekday: "short", month: "short", day: "numeric" });
}

function formatDateTime(value: string): string {
  if (!value) return "—";
  return new Date(value).toLocaleString([], { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" });
}

export function VashandiRosterScreen() {
  const [rosters, setRosters] = useState<VashandiRosterRow[]>([]);
  const [shifts, setShifts] = useState<VashandiShiftRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (isRefresh = false) => {
    if (isRefresh) setRefreshing(true);
    else setLoading(true);
    setError(null);
    try {
      const [rosterRows, contract] = await Promise.all([
        fetchMyRosters(),
        fetchSessionExperienceContract().catch(() => null),
      ]);
      setRosters(rosterRows);
      setShifts(normalizeSessionShifts(contract?.activeRosteredShifts));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load roster");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  if (loading) {
    return (
      <Screen>
        <Header title="My Roster" subtitle="Vashandi operational workforce" />
        <LoadingSpinner size="lg" />
      </Screen>
    );
  }

  if (error) {
    return (
      <Screen>
        <Header title="My Roster" subtitle="Vashandi operational workforce" />
        <ErrorState title="Unable to load roster" message={error} onRetry={() => void load()} />
      </Screen>
    );
  }

  return (
    <Screen>
      <Header title="My Roster" subtitle="Governed roster periods and active shifts" />
      <ScrollView
        testID="vashandi-roster-screen"
        style={styles.container}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => void load(true)} />}
      >
        <CardHeader>Active shifts</CardHeader>
        {shifts.length === 0 ? (
          <EmptyState title="No active shifts" message="Your session has no rostered shifts right now." />
        ) : (
          shifts.map((shift) => (
            <Card key={shift.id}>
              <CardBody>
                <View style={styles.row}>
                  <Text style={styles.title}>{shift.shiftType}</Text>
                  <Badge variant={STATUS_VARIANT[shift.status] ?? "outline"}>{shift.status}</Badge>
                </View>
                <Text style={styles.meta}>{formatDateTime(shift.startTime)} → {formatDateTime(shift.endTime)}</Text>
                {shift.facilityId ? <Text style={styles.meta}>Facility: {shift.facilityId}</Text> : null}
              </CardBody>
            </Card>
          ))
        )}

        <CardHeader>Roster periods</CardHeader>
        {rosters.length === 0 ? (
          <EmptyState title="No roster periods" message="No governed roster periods are available for your scope." />
        ) : (
          rosters.map((roster) => (
            <Card key={roster.id}>
              <CardBody>
                <View style={styles.row}>
                  <Text style={styles.title}>{roster.rosterType}</Text>
                  <Badge variant={STATUS_VARIANT[roster.status] ?? "outline"}>{roster.status}</Badge>
                </View>
                <Text style={styles.meta}>
                  {formatDate(roster.periodStart)} – {formatDate(roster.periodEnd)}
                </Text>
                {roster.departmentId ? <Text style={styles.meta}>Department: {roster.departmentId}</Text> : null}
              </CardBody>
            </Card>
          ))
        )}

        <View style={styles.footer}>
          <Button title="Refresh" variant="outline" onPress={() => void load(true)} testID="refresh-vashandi-roster" />
        </View>
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { padding: 16 },
  row: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", gap: 8 },
  title: { fontSize: 15, fontWeight: "700", color: colors.gray[900], flex: 1 },
  meta: { fontSize: 13, color: colors.gray[500], marginTop: 4 },
  footer: { marginTop: 16, marginBottom: 32 },
});
