/**
 * VashandiAttendanceScreen — check-in/out and attendance history for the signed-in worker.
 */
import React, { useCallback, useEffect, useMemo, useState } from "react";
import { View, Text, ScrollView, StyleSheet, RefreshControl, Alert } from "react-native";
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
import {
  checkInAttendance,
  checkOutAttendance,
  fetchMyAttendance,
  normalizeSessionShifts,
  type VashandiAttendanceRow,
  type VashandiShiftRow,
} from "../../services/vashandiService";
import { fetchSessionExperienceContract } from "../../services/sessionExperienceService";

function formatDateTime(value: string): string {
  if (!value) return "—";
  return new Date(value).toLocaleString([], { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" });
}

export function VashandiAttendanceScreen() {
  const [events, setEvents] = useState<VashandiAttendanceRow[]>([]);
  const [shifts, setShifts] = useState<VashandiShiftRow[]>([]);
  const [workforceProfileId, setWorkforceProfileId] = useState<string | null>(null);
  const [attendanceStatus, setAttendanceStatus] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [acting, setActing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const activeShift = useMemo(
    () => shifts.find((shift) => shift.status === "scheduled" || shift.status === "confirmed" || shift.status === "checked_in"),
    [shifts],
  );

  const load = useCallback(async (isRefresh = false) => {
    if (isRefresh) setRefreshing(true);
    else setLoading(true);
    setError(null);
    try {
      const contract = await fetchSessionExperienceContract();
      setWorkforceProfileId(contract.vashandiWorkforceProfileId ?? null);
      setAttendanceStatus(contract.attendanceStatus ?? null);
      setShifts(normalizeSessionShifts(contract.activeRosteredShifts));
      const query = contract.vashandiWorkforceProfileId
        ? { workforce_profile_id: contract.vashandiWorkforceProfileId }
        : undefined;
      const rows = await fetchMyAttendance(query);
      setEvents(rows);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load attendance");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const performCheckIn = async () => {
    if (!workforceProfileId || !activeShift) {
      Alert.alert("Check-in unavailable", "Select an active rostered shift before checking in.");
      return;
    }
    setActing(true);
    try {
      const result = await checkInAttendance({
        shiftId: activeShift.id,
        workforceProfileId,
      });
      Alert.alert("Check-in recorded", result.message ?? `Status: ${result.status}`);
      await load(true);
    } catch (err) {
      Alert.alert("Check-in failed", err instanceof Error ? err.message : "Unable to check in");
    } finally {
      setActing(false);
    }
  };

  const performCheckOut = async () => {
    if (!workforceProfileId || !activeShift) {
      Alert.alert("Check-out unavailable", "No active shift is available for check-out.");
      return;
    }
    setActing(true);
    try {
      const result = await checkOutAttendance({
        shiftId: activeShift.id,
        workforceProfileId,
      });
      Alert.alert("Check-out recorded", result.message ?? `Status: ${result.status}`);
      await load(true);
    } catch (err) {
      Alert.alert("Check-out failed", err instanceof Error ? err.message : "Unable to check out");
    } finally {
      setActing(false);
    }
  };

  if (loading) {
    return (
      <Screen>
        <Header title="My Attendance" subtitle="Vashandi check-in and history" />
        <LoadingSpinner size="lg" />
      </Screen>
    );
  }

  if (error) {
    return (
      <Screen>
        <Header title="My Attendance" subtitle="Vashandi check-in and history" />
        <ErrorState title="Unable to load attendance" message={error} onRetry={() => void load()} />
      </Screen>
    );
  }

  return (
    <Screen>
      <Header title="My Attendance" subtitle="Self check-in/out with governed audit trail" />
      <ScrollView
        testID="vashandi-attendance-screen"
        style={styles.container}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => void load(true)} />}
      >
        <Card>
          <CardBody>
            <Text style={styles.sectionTitle}>Current status</Text>
            <Badge variant="secondary">{attendanceStatus ?? "unknown"}</Badge>
            {activeShift ? (
              <Text style={styles.meta}>
                Active shift: {activeShift.shiftType} · {formatDateTime(activeShift.startTime)}
              </Text>
            ) : (
              <Text style={styles.meta}>No active shift in your session context.</Text>
            )}
            <View style={styles.actionRow}>
              <Button title="Check in" onPress={() => void performCheckIn()} disabled={acting || !activeShift} testID="vashandi-check-in" />
              <Button title="Check out" variant="outline" onPress={() => void performCheckOut()} disabled={acting || !activeShift} testID="vashandi-check-out" />
            </View>
          </CardBody>
        </Card>

        <CardHeader>Recent attendance</CardHeader>
        {events.length === 0 ? (
          <EmptyState title="No attendance events" message="Your check-ins and check-outs will appear here." />
        ) : (
          events.map((event) => (
            <Card key={event.id}>
              <CardBody>
                <View style={styles.row}>
                  <Text style={styles.title}>{event.eventType.replace(/_/g, " ")}</Text>
                  <Badge variant="outline">{event.offline ? "offline" : "live"}</Badge>
                </View>
                <Text style={styles.meta}>{formatDateTime(event.eventTime)}</Text>
                {event.checkInMode ? <Text style={styles.meta}>Mode: {event.checkInMode}</Text> : null}
              </CardBody>
            </Card>
          ))
        )}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { padding: 16 },
  sectionTitle: { fontSize: 15, fontWeight: "700", color: "#111827", marginBottom: 8 },
  row: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", gap: 8 },
  title: { fontSize: 14, fontWeight: "600", color: "#111827", textTransform: "capitalize" },
  meta: { fontSize: 13, color: "#6B7280", marginTop: 4 },
  actionRow: { flexDirection: "row", gap: 8, marginTop: 12 },
});
