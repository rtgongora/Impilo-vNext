package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tuso.persistence.entity.BookingEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.BookingRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Detects booking conflicts for resources.
 *
 * <p>A conflict exists when a non-cancelled booking for the same resource overlaps
 * with the requested time range. Overlap logic uses half-open intervals:
 * existing.start &lt; newEnd AND existing.end &gt; newStart.</p>
 */
@Service
public class BookingConflictDetector {

    private static final Logger log = LoggerFactory.getLogger(BookingConflictDetector.class);

    private final BookingRepository bookingRepository;

    public BookingConflictDetector(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    /**
     * Find all non-cancelled bookings for a resource that overlap with the given time range.
     *
     * <p>Overlap condition: existing.start_time &lt; endTime AND existing.end_time &gt; startTime.
     * Optionally excludes a specific booking (useful for update/reschedule scenarios).</p>
     *
     * @param resourceId       the resource UUID to check
     * @param startTime        the start of the proposed booking window
     * @param endTime          the end of the proposed booking window
     * @param excludeBookingId optional booking UUID to exclude from conflict check (may be null)
     * @return list of conflicting bookings (empty list means no conflict)
     */
    @Transactional(readOnly = true)
    public List<BookingEntity> detectConflicts(UUID resourceId, OffsetDateTime startTime,
                                                OffsetDateTime endTime, UUID excludeBookingId) {
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("Start time and end time must not be null");
        }
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("End time must be after start time");
        }

        log.debug("Checking conflicts for resource {} between {} and {} (excluding booking {})",
                resourceId, startTime, endTime, excludeBookingId);

        // Find all non-cancelled bookings that overlap: existing.start < newEnd AND existing.end > newStart
        // Convert OffsetDateTime to Instant for repository query (entity uses Instant internally)
        List<BookingEntity> overlapping = bookingRepository
                .findByResource_IdAndStartTimeLessThanAndEndTimeGreaterThanAndStatusNot(
                        resourceId, endTime.toInstant(), startTime.toInstant(), "CANCELLED");

        // Exclude the specified booking if provided (for reschedule scenarios)
        if (excludeBookingId != null) {
            overlapping = overlapping.stream()
                    .filter(b -> !excludeBookingId.equals(b.getId()))
                    .toList();
        }

        if (!overlapping.isEmpty()) {
            log.info("Found {} conflicting booking(s) for resource {} in range [{}, {})",
                    overlapping.size(), resourceId, startTime, endTime);
        }

        return overlapping;
    }
}
