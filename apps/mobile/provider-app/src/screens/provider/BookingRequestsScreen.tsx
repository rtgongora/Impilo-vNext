import React, { useCallback, useEffect, useState } from "react";
import { View, Text, StyleSheet, ScrollView, RefreshControl } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import {
  Card,
  CardHeader,
  CardBody,
  Button,
  Badge,
  LoadingSpinner,
  EmptyState,
  ErrorState,
} from "@impilo/mobile-design-system";
import {
  fetchBookingRequests,
  approveBookingRequest,
  rejectBookingRequest,
  convertBookingToAppointment,
  type BookingRequest,
} from "../../services/bookingService";
import { useAppStore } from "../../stores/appStore";

export function BookingRequestsScreen() {
  const { facilityId } = useAppStore();
  const [requests, setRequests] = useState<BookingRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const [actingId, setActingId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      const items = await fetchBookingRequests({
        facilityId: facilityId ?? undefined,
        status: "REQUESTED",
        size: 50,
      });
      setRequests(items);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [facilityId]);

  useEffect(() => {
    load();
  }, [load]);

  async function handleApprove(id: string) {
    setActingId(id);
    try {
      await approveBookingRequest(id);
      await convertBookingToAppointment(id);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setActingId(null);
    }
  }

  async function handleReject(id: string) {
    setActingId(id);
    try {
      await rejectBookingRequest(id, "Rejected by provider");
      await load();
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setActingId(null);
    }
  }

  if (loading) return <LoadingSpinner label="Loading booking requests..." />;
  if (error) return <ErrorState message={error.message} onRetry={load} />;

  return (
    <ScrollView
      style={styles.container}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => { setRefreshing(true); load(); }} />}
    >
      <Card>
        <CardHeader
          title="Booking Requests"
          subtitle="Pending requests needing triage or approval"
        />
        <CardBody>
          {requests.length === 0 ? (
            <EmptyState
              icon="mail-open-outline"
              title="No pending bookings"
              description="Incoming booking requests for your facility will appear here."
            />
          ) : (
            requests.map((req) => (
              <View key={req.id} style={styles.row}>
                <View style={styles.rowMain}>
                  <Text style={styles.type}>{req.bookingType ?? "Consultation"}</Text>
                  <Text style={styles.meta}>{req.bookingNumber ?? req.id.slice(0, 8)}</Text>
                  {req.consentStatus === "PENDING" && (
                    <Text style={styles.mvumo}>Mvumo consent pending</Text>
                  )}
                </View>
                <Badge variant="outline">{req.bookingStatus ?? "REQUESTED"}</Badge>
                <View style={styles.actions}>
                  <Button
                    title="Approve"
                    size="sm"
                    disabled={actingId === req.id}
                    onPress={() => handleApprove(req.id)}
                  />
                  <Button
                    title="Reject"
                    variant="outline"
                    size="sm"
                    disabled={actingId === req.id}
                    onPress={() => handleReject(req.id)}
                  />
                </View>
              </View>
            ))
          )}
        </CardBody>
      </Card>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  row: { marginBottom: 16, gap: 8 },
  rowMain: { gap: 2 },
  type: { fontSize: 15, fontWeight: "600" },
  meta: { fontSize: 12, color: "#6b7280" },
  mvumo: { fontSize: 11, color: "#b45309" },
  actions: { flexDirection: "row", gap: 8 },
});
