import React, { useState, useEffect, useCallback, useRef } from "react";
import { View, Text, StyleSheet, ScrollView, Pressable } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import {
  Card,
  CardHeader,
  CardBody,
  Button,
  Badge,
  TextField,
  Select,
  LoadingSpinner,
  EmptyState,
  ErrorState,
} from "@impilo/mobile-design-system";
import {
  fetchBookings,
  cancelBooking,
  createBookingRequest,
  type CitizenBooking,
} from "../../services/bookingService";
import { fetchFacilities, type FacilitySummary } from "../../services/facilityService";

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
  const [showBooking, setShowBooking] = useState(false);
  const [facilityId, setFacilityId] = useState("");
  const [facilityName, setFacilityName] = useState("");
  const [facilitySearch, setFacilitySearch] = useState("");
  const [facilityOptions, setFacilityOptions] = useState<FacilitySummary[]>([]);
  const [facilitySearchBusy, setFacilitySearchBusy] = useState(false);
  const [showFacilityPicker, setShowFacilityPicker] = useState(false);
  const [bookingType, setBookingType] = useState("GENERAL");
  const [preferredDate, setPreferredDate] = useState("");
  const [reason, setReason] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const facilitySearchTimeout = useRef<ReturnType<typeof setTimeout> | null>(null);

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

  useEffect(() => {
    if (!showFacilityPicker) return;
    if (facilitySearchTimeout.current) clearTimeout(facilitySearchTimeout.current);
    facilitySearchTimeout.current = setTimeout(() => {
      setFacilitySearchBusy(true);
      fetchFacilities(facilitySearch.length >= 2 ? facilitySearch : undefined)
        .then((items) => setFacilityOptions(items))
        .catch(() => setFacilityOptions([]))
        .finally(() => setFacilitySearchBusy(false));
    }, 300);
    return () => {
      if (facilitySearchTimeout.current) clearTimeout(facilitySearchTimeout.current);
    };
  }, [facilitySearch, showFacilityPicker]);

  const handleSubmitBooking = useCallback(async () => {
    setSubmitting(true);
    try {
      await createBookingRequest({
        facilityId,
        appointmentType: bookingType,
        preferredDate,
        reason,
      });
      setShowBooking(false);
      setFacilityId("");
      setFacilityName("");
      setFacilitySearch("");
      setShowFacilityPicker(false);
      setReason("");
      load();
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setSubmitting(false);
    }
  }, [facilityId, bookingType, preferredDate, reason, load]);

  const handleCancel = useCallback(async (id: string) => {
    try {
      await cancelBooking(id, "Cancelled by citizen");
      load();
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    }
  }, [load]);

  if (isLoading) return <LoadingSpinner label="Loading bookings..." />;
  if (error && !showBooking) return <ErrorState message={error.message} onRetry={load} />;

  return (
    <ScrollView style={styles.container}>
      <Card>
        <CardHeader
          title="My Bookings"
          subtitle="Booking requests and transaction status — not yet confirmed appointments"
          icon={<Ionicons name="document-text-outline" size={20} />}
          action={
            <Button
              title={showBooking ? "Cancel" : "Book service"}
              variant={showBooking ? "ghost" : "primary"}
              size="sm"
              onPress={() => setShowBooking(!showBooking)}
              testID="toggle-booking-form"
            />
          }
        />
        <CardBody>
          {showBooking ? (
            <View style={styles.bookingForm}>
              <TextField
                label="Facility"
                value={facilitySearch}
                onChange={(value) => {
                  setFacilitySearch(value);
                  setFacilityId("");
                  setFacilityName("");
                  setShowFacilityPicker(true);
                }}
                placeholder="Search clinics and hospitals"
                testID="booking-facility-search"
              />
              {facilityName ? (
                <Text style={styles.selectedFacilityText} testID="booking-facility-selected">
                  Selected: {facilityName}
                </Text>
              ) : null}
              {showFacilityPicker ? (
                <ScrollView style={styles.facilityPicker} nestedScrollEnabled>
                  {facilitySearchBusy ? (
                    <Text style={styles.facilityPickerHint}>Searching facilities…</Text>
                  ) : facilityOptions.length === 0 ? (
                    <Text style={styles.facilityPickerHint}>No facilities found</Text>
                  ) : (
                    facilityOptions.map((facility) => (
                      <Pressable
                        key={facility.id}
                        style={styles.facilityOption}
                        onPress={() => {
                          setFacilityId(facility.id);
                          setFacilityName(facility.name);
                          setFacilitySearch(facility.name);
                          setShowFacilityPicker(false);
                        }}
                        testID={`booking-facility-option-${facility.id}`}
                      >
                        <Text style={styles.facilityOptionName}>{facility.name}</Text>
                        <Text style={styles.facilityOptionMeta}>
                          {[facility.facilityType, facility.district].filter(Boolean).join(" · ")}
                        </Text>
                      </Pressable>
                    ))
                  )}
                </ScrollView>
              ) : null}
              <Select
                label="Service type"
                value={bookingType}
                onChange={setBookingType}
                options={[
                  { label: "General Consultation", value: "GENERAL" },
                  { label: "Follow-Up", value: "FOLLOW_UP" },
                  { label: "Specialist Referral", value: "SPECIALIST" },
                  { label: "Telemedicine", value: "TELEMEDICINE" },
                  { label: "Lab Work", value: "LAB_WORK" },
                  { label: "Vaccination", value: "VACCINATION" },
                ]}
                testID="booking-type"
              />
              <TextField
                label="Preferred date"
                value={preferredDate}
                onChange={setPreferredDate}
                placeholder="YYYY-MM-DD"
                testID="booking-date"
              />
              <TextField
                label="Reason (optional)"
                value={reason}
                onChange={setReason}
                placeholder="Brief description"
                testID="booking-reason"
              />
              <Text style={styles.mvumoHint}>
                If consent or agreement is required, you will be prompted before confirmation.
              </Text>
              <Button
                title={submitting ? "Submitting…" : "Submit booking request"}
                variant="primary"
                onPress={handleSubmitBooking}
                disabled={submitting || !facilityId || !preferredDate}
                testID="submit-booking"
              />
            </View>
          ) : null}

          {bookings.length === 0 && !showBooking ? (
            <EmptyState
              icon="document-text-outline"
              title="No booking requests"
              description="Tap Book service to request care. Your booking stays here until it becomes a confirmed appointment."
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
                    <Text style={styles.mvumo}>Consent required — complete Mvumo before confirmation</Text>
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
  bookingForm: { gap: 12, marginBottom: 16 },
  selectedFacilityText: {
    fontSize: 12,
    color: "#059669",
    fontWeight: "600",
  },
  facilityPicker: {
    maxHeight: 160,
    borderWidth: 1,
    borderColor: "#E5E7EB",
    borderRadius: 12,
    backgroundColor: "#FFFFFF",
  },
  facilityPickerHint: {
    fontSize: 12,
    color: "#9CA3AF",
    padding: 12,
  },
  facilityOption: {
    paddingHorizontal: 12,
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: "#F3F4F6",
  },
  facilityOptionName: {
    fontSize: 14,
    fontWeight: "600",
    color: "#111827",
  },
  facilityOptionMeta: {
    fontSize: 12,
    color: "#6B7280",
    marginTop: 2,
  },
  mvumoHint: {
    fontSize: 12,
    color: "#b45309",
  },
  row: { marginBottom: 12, gap: 6 },
  rowMain: { gap: 2 },
  type: { fontSize: 15, fontWeight: "600" },
  meta: { fontSize: 12, color: "#6b7280" },
  mvumo: { fontSize: 11, color: "#b45309", fontWeight: "500" },
});
