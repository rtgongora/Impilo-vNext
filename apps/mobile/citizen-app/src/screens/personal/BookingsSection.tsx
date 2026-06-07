import React, { useState, useEffect, useCallback } from "react";
import { View, Text, StyleSheet, ScrollView } from "react-native";
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
import { fetchBookings, cancelBooking, type CitizenBooking } from "../../services/bookingService";

const STATUS_COLORS: Record<string, "default" | "secondary" | "destructive" | "outline"> = {
  REQUESTED: "default",
  PENDING_CONSENT: "outline",
  PENDING_APPROVAL: "outline",
  CONFIRMED: "secondary",
  CANCELLED: "destructive",
  REJECTED: "destructive",
  FULFILLED: "secondary",
};

export function BookingsSection() {
  const [bookings, setBookings] = useState<CitizenBooking[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const result = await fetchBookings({ size: 50 });
      setBookings(result.items);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const handleCancel = useCallback(async (id: string) => {
    try {
      await cancelBooking(id, "Cancelled by citizen");
      load();
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    }
  }, [load]);

  if (isLoading) return <LoadingSpinner label="Loading bookings..." />;
  if (error) return <ErrorState message={error.message} onRetry={load} />;

  return (
    <ScrollView style={styles.container}>
      <Card>
        <CardHeader
          title="My Bookings"
          subtitle="Booking requests and transaction status"
          icon={<Ionicons name="document-text-outline" size={20} />}
        />
        <CardBody>
          {bookings.length === 0 ? (
            <EmptyState
              icon="document-text-outline"
              title="No booking requests"
              description="When you book a service, your request will appear here until it is confirmed as an appointment."
            />
          ) : (
            bookings.map((booking) => (
              <View key={booking.id} style={styles.row} testID={`booking-row-${booking.id}`}>
                <View style={styles.rowMain}>
                  <Text style={styles.type}>{booking.bookingType ?? "Consultation"}</Text>
                  <Text style={styles.meta}>
                    {booking.bookingNumber ?? booking.id.slice(0, 8)}
                    {booking.preferredStartTime ? ` · ${booking.preferredStartTime}` : ""}
                  </Text>
                  {booking.consentStatus === "PENDING" && (
                    <Text style={styles.mvumo}>Consent required</Text>
                  )}
                </View>
                <Badge variant={STATUS_COLORS[booking.bookingStatus ?? "REQUESTED"] ?? "default"}>
                  {booking.bookingStatus ?? "REQUESTED"}
                </Badge>
                {booking.bookingStatus !== "CANCELLED" && booking.bookingStatus !== "FULFILLED" && (
                  <Button
                    variant="outline"
                    size="sm"
                    onPress={() => handleCancel(booking.id)}
                    testID={`cancel-booking-${booking.id}`}
                  >
                    Cancel request
                  </Button>
                )}
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
  row: { marginBottom: 12, gap: 6 },
  rowMain: { gap: 2 },
  type: { fontSize: 15, fontWeight: "600" },
  meta: { fontSize: 12, color: "#6b7280" },
  mvumo: { fontSize: 11, color: "#b45309", fontWeight: "500" },
});
