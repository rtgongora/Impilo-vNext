/**
 * VashandiFacilityStaffScreen — limited facility-manager view of roster periods and attendance.
 */
import React, { useCallback, useEffect, useState } from "react";
import { View, Text, ScrollView, StyleSheet, RefreshControl } from "react-native";
import {
  Screen,
  Header,
  Card,
  CardBody,
  CardHeader,
  Badge,
  Button,
  LoadingSpinner,
  EmptyState,
  ErrorState,
} from "@impilo/mobile-design-system";
import { useAppStore } from "../../stores/appStore";
import {
  fetchMyAttendance,
  fetchMyRosters,
  type VashandiAttendanceRow,
  type VashandiRosterRow,
} from "../../services/vashandiService";

function formatDate(value: string): string {
  if (!value) return "—";
  return new Date(value).toLocaleDateString([], { month: "short", day: "numeric", year: "numeric" });
}

function formatDateTime(value: string): string {
  if (!value) return "—";
  return new Date(value).toLocaleString([], { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" });
}

export function VashandiFacilityStaffScreen() {
  const { facilityId, facilityName } = useAppStore();
  const [rosters, setRosters] = useState<VashandiRosterRow[]>([]);
  const [attendance, setAttendance] = useState<VashandiAttendanceRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (isRefresh = false) => {
    if (isRefresh) setRefreshing(true);
    else setLoading(true);
    setError(null);
    try {
      const query = facilityId ? { facility_id: facilityId } : undefined;
      const [rosterRows, attendanceRows] = await Promise.all([
        fetchMyRosters(query),
        fetchMyAttendance(query),
      ]);
      setRosters(rosterRows);
      setAttendance(attendanceRows.slice(0, 20));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load facility workforce view");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [facilityId]);

  useEffect(() => {
    void load();
  }, [load]);

  if (loading) {
    return (
      <Screen>
        <Header title="Facility Staff" subtitle="Limited manager view" />
        <LoadingSpinner size="lg" />
      </Screen>
    );
  }

  if (error) {
    return (
      <Screen>
        <Header title="Facility Staff" subtitle="Limited manager view" />
        <ErrorState title="Unable to load facility workforce" message={error} onRetry={() => void load()} />
      </Screen>
    );
  }

  return (
    <Screen>
      <Header
        title="Facility Staff"
        subtitle={facilityName ? `${facilityName} workforce snapshot` : "Facility workforce snapshot"}
      />
      <ScrollView
        testID="vashandi-facility-staff-screen"
        style={styles.container}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => void load(true)} />}
      >
        <Card>
          <CardBody>
            <Text style={styles.hint}>
              Read-only facility scope: roster periods and recent attendance events visible under your Vashandi authority.
            </Text>
          </CardBody>
        </Card>

        <CardHeader>Facility rosters</CardHeader>
        {rosters.length === 0 ? (
          <EmptyState title="No rosters" message="No roster periods are visible for this facility scope." />
        ) : (
          rosters.map((roster) => (
            <Card key={roster.id}>
              <CardBody>
                <View style={styles.row}>
                  <Text style={styles.title}>{roster.rosterType}</Text>
                  <Badge variant="outline">{roster.status}</Badge>
                </View>
                <Text style={styles.meta}>
                  {formatDate(roster.periodStart)} – {formatDate(roster.periodEnd)}
                </Text>
                {roster.departmentId ? <Text style={styles.meta}>Department: {roster.departmentId}</Text> : null}
              </CardBody>
            </Card>
          ))
        )}

        <CardHeader>Recent attendance</CardHeader>
        {attendance.length === 0 ? (
          <EmptyState title="No attendance events" message="Recent check-ins and check-outs will appear here." />
        ) : (
          attendance.map((event) => (
            <Card key={event.id}>
              <CardBody>
                <View style={styles.row}>
                  <Text style={styles.title}>{event.eventType.replace(/_/g, " ")}</Text>
                  <Badge variant="secondary">{event.workforceProfileId.slice(0, 8)}</Badge>
                </View>
                <Text style={styles.meta}>{formatDateTime(event.eventTime)}</Text>
              </CardBody>
            </Card>
          ))
        )}

        <View style={styles.footer}>
          <Button title="Refresh" variant="outline" onPress={() => void load(true)} testID="refresh-vashandi-facility-staff" />
        </View>
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { padding: 16 },
  hint: { fontSize: 13, color: "#6B7280", lineHeight: 18 },
  row: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", gap: 8 },
  title: { fontSize: 14, fontWeight: "600", color: "#111827", textTransform: "capitalize", flex: 1 },
  meta: { fontSize: 13, color: "#6B7280", marginTop: 4 },
  footer: { marginTop: 16, marginBottom: 32 },
});
