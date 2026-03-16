/**
 * ScheduleScreen — Provider's schedule/roster view.
 *
 * Displays today's shift, upcoming schedule for the next 7 days,
 * shift details with status indicators, and a link to handover.
 */

import React, { useState, useEffect, useCallback } from "react";
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  Pressable,
} from "react-native";
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
import { getSchedule, type Shift } from "../../services/profileService";

const STATUS_VARIANT: Record<string, "destructive" | "warning" | "secondary" | "outline"> = {
  ACTIVE: "destructive",
  UPCOMING: "warning",
  COMPLETED: "secondary",
  CANCELLED: "outline",
};

function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString([], {
    weekday: "short",
    month: "short",
    day: "numeric",
  });
}

function isToday(dateStr: string): boolean {
  const shiftDate = new Date(dateStr);
  const now = new Date();
  return (
    shiftDate.getFullYear() === now.getFullYear() &&
    shiftDate.getMonth() === now.getMonth() &&
    shiftDate.getDate() === now.getDate()
  );
}

export function ScheduleScreen() {
  const [shifts, setShifts] = useState<Shift[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedShiftId, setSelectedShiftId] = useState<string | null>(null);

  const loadSchedule = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await getSchedule();
      setShifts(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load schedule");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadSchedule();
  }, [loadSchedule]);

  if (loading) {
    return (
      <Screen>
        <Header title="My Schedule" />
        <LoadingSpinner size="lg" />
      </Screen>
    );
  }

  if (error) {
    return (
      <Screen>
        <Header title="My Schedule" />
        <ErrorState title="Error" message={error} onRetry={loadSchedule} />
      </Screen>
    );
  }

  const todayShifts = shifts.filter((s) => isToday(s.date));
  const upcomingShifts = shifts.filter(
    (s) => !isToday(s.date) && (s.status === "UPCOMING" || s.status === "ACTIVE")
  );
  const completedShifts = shifts.filter(
    (s) => !isToday(s.date) && s.status === "COMPLETED"
  );

  return (
    <Screen>
      <Header title="My Schedule" />
      <ScrollView testID="schedule-screen" style={styles.container}>
        {/* Today's Shift */}
        <CardHeader>{"Today\u2019s Shift"}</CardHeader>
        {todayShifts.length === 0 ? (
          <EmptyState title="No shift today" message="You have no shifts scheduled for today" />
        ) : (
          todayShifts.map((shift) => (
            <Card key={shift.id}>
              <CardBody>
                <Pressable
                  testID={`today-shift-${shift.id}`}
                  onPress={() =>
                    setSelectedShiftId(shift.id === selectedShiftId ? null : shift.id)
                  }
                >
                  <View style={styles.shiftHeader}>
                    <Text style={styles.shiftType}>{shift.shiftType}</Text>
                    <Badge variant={STATUS_VARIANT[shift.status] ?? "outline"}>
                      {shift.status}
                    </Badge>
                  </View>
                  <View style={styles.shiftTime}>
                    <Text style={styles.timeText}>
                      {`${formatTime(shift.startTime)} - ${formatTime(shift.endTime)}`}
                    </Text>
                  </View>
                  <View style={styles.shiftDetail}>
                    <Text style={styles.label}>Facility</Text>
                    <Text style={styles.value}>{shift.facilityName}</Text>
                  </View>
                  <View style={styles.shiftDetail}>
                    <Text style={styles.label}>Department</Text>
                    <Text style={styles.value}>{shift.department}</Text>
                  </View>
                  {selectedShiftId === shift.id && shift.notes ? (
                    <View style={styles.notesContainer}>
                      <Text style={styles.notesLabel}>Notes</Text>
                      <Text style={styles.notesText}>{shift.notes}</Text>
                    </View>
                  ) : null}
                </Pressable>
                {shift.status === "ACTIVE" ? (
                  <View style={styles.handoverContainer}>
                    <Pressable
                      testID={`handover-${shift.id}`}
                      style={styles.handoverButton}
                    >
                      <Text style={styles.handoverText}>Initiate Handover</Text>
                    </Pressable>
                  </View>
                ) : null}
              </CardBody>
            </Card>
          ))
        )}

        {/* Upcoming Schedule */}
        <CardHeader>Upcoming Schedule</CardHeader>
        {upcomingShifts.length === 0 ? (
          <EmptyState
            title="No upcoming shifts"
            message="No shifts scheduled for the coming days"
          />
        ) : (
          upcomingShifts.map((shift) => (
            <Card key={shift.id}>
              <CardBody>
                <Pressable
                  testID={`upcoming-shift-${shift.id}`}
                  onPress={() =>
                    setSelectedShiftId(shift.id === selectedShiftId ? null : shift.id)
                  }
                >
                  <View style={styles.shiftHeader}>
                    <Text style={styles.dateText}>{formatDate(shift.date)}</Text>
                    <Badge variant={STATUS_VARIANT[shift.status] ?? "outline"}>
                      {shift.status}
                    </Badge>
                  </View>
                  <Text style={styles.shiftType}>{shift.shiftType}</Text>
                  <View style={styles.shiftTime}>
                    <Text style={styles.timeText}>
                      {`${formatTime(shift.startTime)} - ${formatTime(shift.endTime)}`}
                    </Text>
                  </View>
                  <View style={styles.shiftDetail}>
                    <Text style={styles.label}>Facility</Text>
                    <Text style={styles.value}>{shift.facilityName}</Text>
                  </View>
                  <View style={styles.shiftDetail}>
                    <Text style={styles.label}>Department</Text>
                    <Text style={styles.value}>{shift.department}</Text>
                  </View>
                  {selectedShiftId === shift.id && shift.notes ? (
                    <View style={styles.notesContainer}>
                      <Text style={styles.notesLabel}>Notes</Text>
                      <Text style={styles.notesText}>{shift.notes}</Text>
                    </View>
                  ) : null}
                </Pressable>
              </CardBody>
            </Card>
          ))
        )}

        {/* Completed Shifts */}
        {completedShifts.length > 0 ? (
          <>
            <CardHeader>Recently Completed</CardHeader>
            {completedShifts.map((shift) => (
              <Card key={shift.id}>
                <CardBody>
                  <View testID={`completed-shift-${shift.id}`}>
                    <View style={styles.shiftHeader}>
                      <Text style={styles.dateText}>{formatDate(shift.date)}</Text>
                      <Badge variant="secondary">COMPLETED</Badge>
                    </View>
                    <Text style={styles.shiftTypeSecondary}>{shift.shiftType}</Text>
                    <Text style={styles.completedTime}>
                      {`${formatTime(shift.startTime)} - ${formatTime(shift.endTime)}`}
                    </Text>
                    <Text style={styles.completedFacility}>
                      {`${shift.facilityName} \u2022 ${shift.department}`}
                    </Text>
                  </View>
                </CardBody>
              </Card>
            ))}
          </>
        ) : null}

        <View style={styles.refreshContainer}>
          <Button
            title="Refresh"
            variant="outline"
            onPress={loadSchedule}
            testID="refresh-schedule"
          />
        </View>
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 16,
  },
  shiftHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  shiftType: {
    fontSize: 16,
    fontWeight: "700",
    marginTop: 4,
  },
  shiftTypeSecondary: {
    fontSize: 15,
    fontWeight: "600",
    color: "#6B7280",
    marginTop: 4,
  },
  shiftTime: {
    marginTop: 8,
  },
  timeText: {
    fontSize: 18,
    fontWeight: "600",
    color: "#111827",
  },
  dateText: {
    fontSize: 14,
    fontWeight: "600",
    color: "#374151",
  },
  shiftDetail: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    paddingVertical: 4,
    marginTop: 2,
  },
  label: {
    fontSize: 14,
    color: "#6B7280",
    flex: 1,
  },
  value: {
    fontSize: 14,
    fontWeight: "500",
    flex: 1,
    textAlign: "right",
  },
  notesContainer: {
    marginTop: 8,
    padding: 8,
    backgroundColor: "#F9FAFB",
    borderRadius: 6,
  },
  notesLabel: {
    fontSize: 12,
    color: "#6B7280",
    fontWeight: "600",
    marginBottom: 2,
  },
  notesText: {
    fontSize: 13,
    color: "#374151",
  },
  handoverContainer: {
    marginTop: 12,
  },
  handoverButton: {
    alignSelf: "flex-start",
    paddingVertical: 8,
    paddingHorizontal: 16,
    borderRadius: 8,
    backgroundColor: "#EFF6FF",
  },
  handoverText: {
    color: "#2563EB",
    fontSize: 14,
    fontWeight: "600",
  },
  completedTime: {
    fontSize: 14,
    color: "#9CA3AF",
    marginTop: 4,
  },
  completedFacility: {
    fontSize: 13,
    color: "#9CA3AF",
    marginTop: 2,
  },
  refreshContainer: {
    marginTop: 16,
    marginBottom: 32,
  },
});
