package zw.gov.mohcc.impilo.tuso.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.tuso.persistence.entity.BookingEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.ResourceEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.BookingRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BookingConflictDetector}.
 *
 * <p>Validates the half-open interval overlap detection logic:
 * existing.start &lt; newEnd AND existing.end &gt; newStart.
 * Cancelled bookings are excluded via the repository query.</p>
 */
@ExtendWith(MockitoExtension.class)
class BookingConflictDetectorTest {

    @Mock
    private BookingRepository bookingRepository;

    @InjectMocks
    private BookingConflictDetector detector;

    private static final UUID RESOURCE_ID = UUID.fromString("aaaa0000-0000-0000-0000-000000000001");
    private static final UUID RESOURCE_ID_2 = UUID.fromString("aaaa0000-0000-0000-0000-000000000002");
    private static final UUID BOOKING_ID_1 = UUID.fromString("bbbb0000-0000-0000-0000-000000000001");
    private static final UUID BOOKING_ID_2 = UUID.fromString("bbbb0000-0000-0000-0000-000000000002");
    private static final OffsetDateTime BASE = OffsetDateTime.of(2026, 3, 10, 9, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void detectConflicts_noExistingBookings_returnsEmpty() {
        when(bookingRepository.findByResourceIdAndStartTimeLessThanAndEndTimeGreaterThanAndStatusNot(
                eq(RESOURCE_ID), eq(BASE.plusHours(1)), eq(BASE), eq("CANCELLED")))
                .thenReturn(Collections.emptyList());

        List<BookingEntity> conflicts = detector.detectConflicts(RESOURCE_ID, BASE, BASE.plusHours(1), null);

        assertThat(conflicts).isEmpty();
    }

    @Test
    void detectConflicts_overlappingStart_returnsConflict() {
        // Existing: 09:00 - 10:00. New: 09:30 - 10:30 (new starts during existing)
        OffsetDateTime newStart = BASE.plusMinutes(30);
        OffsetDateTime newEnd = BASE.plusMinutes(90);

        BookingEntity existing = createBooking(BOOKING_ID_1, RESOURCE_ID,
                BASE, BASE.plusHours(1), "CONFIRMED");

        when(bookingRepository.findByResourceIdAndStartTimeLessThanAndEndTimeGreaterThanAndStatusNot(
                eq(RESOURCE_ID), eq(newEnd), eq(newStart), eq("CANCELLED")))
                .thenReturn(List.of(existing));

        List<BookingEntity> conflicts = detector.detectConflicts(RESOURCE_ID, newStart, newEnd, null);

        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.get(0).getId()).isEqualTo(BOOKING_ID_1);
    }

    @Test
    void detectConflicts_overlappingEnd_returnsConflict() {
        // Existing: 10:00 - 11:00. New: 09:30 - 10:30 (new ends during existing)
        OffsetDateTime existStart = BASE.plusHours(1);
        OffsetDateTime existEnd = BASE.plusHours(2);
        OffsetDateTime newStart = BASE.plusMinutes(30);
        OffsetDateTime newEnd = BASE.plusMinutes(90);

        BookingEntity existing = createBooking(BOOKING_ID_1, RESOURCE_ID, existStart, existEnd, "CONFIRMED");

        when(bookingRepository.findByResourceIdAndStartTimeLessThanAndEndTimeGreaterThanAndStatusNot(
                eq(RESOURCE_ID), eq(newEnd), eq(newStart), eq("CANCELLED")))
                .thenReturn(List.of(existing));

        List<BookingEntity> conflicts = detector.detectConflicts(RESOURCE_ID, newStart, newEnd, null);

        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.get(0).getId()).isEqualTo(BOOKING_ID_1);
    }

    @Test
    void detectConflicts_enclosingExisting_returnsConflict() {
        // Existing: 09:30 - 10:30. New: 09:00 - 11:00 (new fully encloses existing)
        OffsetDateTime existStart = BASE.plusMinutes(30);
        OffsetDateTime existEnd = BASE.plusMinutes(90);
        OffsetDateTime newStart = BASE;
        OffsetDateTime newEnd = BASE.plusHours(2);

        BookingEntity existing = createBooking(BOOKING_ID_1, RESOURCE_ID, existStart, existEnd, "CONFIRMED");

        when(bookingRepository.findByResourceIdAndStartTimeLessThanAndEndTimeGreaterThanAndStatusNot(
                eq(RESOURCE_ID), eq(newEnd), eq(newStart), eq("CANCELLED")))
                .thenReturn(List.of(existing));

        List<BookingEntity> conflicts = detector.detectConflicts(RESOURCE_ID, newStart, newEnd, null);

        assertThat(conflicts).hasSize(1);
    }

    @Test
    void detectConflicts_enclosedByExisting_returnsConflict() {
        // Existing: 09:00 - 11:00. New: 09:30 - 10:30 (new fully inside existing)
        OffsetDateTime existStart = BASE;
        OffsetDateTime existEnd = BASE.plusHours(2);
        OffsetDateTime newStart = BASE.plusMinutes(30);
        OffsetDateTime newEnd = BASE.plusMinutes(90);

        BookingEntity existing = createBooking(BOOKING_ID_1, RESOURCE_ID, existStart, existEnd, "CONFIRMED");

        when(bookingRepository.findByResourceIdAndStartTimeLessThanAndEndTimeGreaterThanAndStatusNot(
                eq(RESOURCE_ID), eq(newEnd), eq(newStart), eq("CANCELLED")))
                .thenReturn(List.of(existing));

        List<BookingEntity> conflicts = detector.detectConflicts(RESOURCE_ID, newStart, newEnd, null);

        assertThat(conflicts).hasSize(1);
    }

    @Test
    void detectConflicts_adjacentBookings_noConflict() {
        // Existing: 09:00 - 10:00. New: 10:00 - 11:00 (end of one == start of next)
        // Half-open interval: existing.end == new.start => NOT overlapping
        OffsetDateTime newStart = BASE.plusHours(1);
        OffsetDateTime newEnd = BASE.plusHours(2);

        when(bookingRepository.findByResourceIdAndStartTimeLessThanAndEndTimeGreaterThanAndStatusNot(
                eq(RESOURCE_ID), eq(newEnd), eq(newStart), eq("CANCELLED")))
                .thenReturn(Collections.emptyList());

        List<BookingEntity> conflicts = detector.detectConflicts(RESOURCE_ID, newStart, newEnd, null);

        assertThat(conflicts).isEmpty();
    }

    @Test
    void detectConflicts_cancelledBooking_ignored() {
        // Cancelled bookings are excluded by the repository query (StatusNot = CANCELLED).
        // When only cancelled bookings exist in the time window, the repo returns empty.
        OffsetDateTime newStart = BASE;
        OffsetDateTime newEnd = BASE.plusHours(1);

        when(bookingRepository.findByResourceIdAndStartTimeLessThanAndEndTimeGreaterThanAndStatusNot(
                eq(RESOURCE_ID), eq(newEnd), eq(newStart), eq("CANCELLED")))
                .thenReturn(Collections.emptyList());

        List<BookingEntity> conflicts = detector.detectConflicts(RESOURCE_ID, newStart, newEnd, null);

        assertThat(conflicts).isEmpty();
    }

    @Test
    void detectConflicts_differentResource_noConflict() {
        // A booking exists on RESOURCE_ID_2, but we query RESOURCE_ID, so no results.
        OffsetDateTime newStart = BASE;
        OffsetDateTime newEnd = BASE.plusHours(1);

        when(bookingRepository.findByResourceIdAndStartTimeLessThanAndEndTimeGreaterThanAndStatusNot(
                eq(RESOURCE_ID), eq(newEnd), eq(newStart), eq("CANCELLED")))
                .thenReturn(Collections.emptyList());

        List<BookingEntity> conflicts = detector.detectConflicts(RESOURCE_ID, newStart, newEnd, null);

        assertThat(conflicts).isEmpty();
    }

    @Test
    void detectConflicts_excludeBookingId_ignoresSelf() {
        // Two overlapping bookings returned from repo; exclude BOOKING_ID_1 (self-reschedule)
        OffsetDateTime newStart = BASE;
        OffsetDateTime newEnd = BASE.plusHours(1);

        BookingEntity self = createBooking(BOOKING_ID_1, RESOURCE_ID, BASE, BASE.plusHours(1), "CONFIRMED");
        BookingEntity other = createBooking(BOOKING_ID_2, RESOURCE_ID,
                BASE.plusMinutes(30), BASE.plusMinutes(90), "CONFIRMED");

        when(bookingRepository.findByResourceIdAndStartTimeLessThanAndEndTimeGreaterThanAndStatusNot(
                eq(RESOURCE_ID), eq(newEnd), eq(newStart), eq("CANCELLED")))
                .thenReturn(List.of(self, other));

        List<BookingEntity> conflicts = detector.detectConflicts(RESOURCE_ID, newStart, newEnd, BOOKING_ID_1);

        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.get(0).getId()).isEqualTo(BOOKING_ID_2);
    }

    // ---- Helper ----

    private BookingEntity createBooking(UUID id, UUID resourceId,
                                         OffsetDateTime start, OffsetDateTime end, String status) {
        BookingEntity booking = new BookingEntity();
        booking.setId(id);
        ResourceEntity resource = new ResourceEntity();
        resource.setId(resourceId);
        booking.setResource(resource);
        booking.setStartTime(start.toInstant());
        booking.setEndTime(end.toInstant());
        booking.setStatus(status);
        booking.setTenantId(UUID.randomUUID());
        booking.setFacilityId(1L);
        booking.setBookedBy("test-actor");
        return booking;
    }
}
