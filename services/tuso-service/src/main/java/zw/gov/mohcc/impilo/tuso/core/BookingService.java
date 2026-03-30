package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.persistence.entity.BookingEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.ResourceEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.BookingRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.ResourceRepository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Core service for managing resource bookings.
 *
 * <p>Handles booking creation (with conflict detection), cancellation, and listing.</p>
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepository bookingRepository;
    private final ResourceRepository resourceRepository;
    private final BookingConflictDetector conflictDetector;

    public BookingService(BookingRepository bookingRepository,
                          ResourceRepository resourceRepository,
                          BookingConflictDetector conflictDetector) {
        this.bookingRepository = bookingRepository;
        this.resourceRepository = resourceRepository;
        this.conflictDetector = conflictDetector;
    }

    /**
     * Create a new booking for a resource after verifying no conflicts exist.
     *
     * @param resourceId the resource UUID to book
     * @param dto        the booking creation data
     * @return the created booking entity
     * @throws IllegalStateException if a conflicting booking exists
     */
    @Transactional
    public BookingEntity createBooking(UUID resourceId, CreateBookingRequest dto) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();
        String actorId = ctx.actorId();

        ResourceEntity resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new IllegalArgumentException("Resource not found: " + resourceId));

        if (!resource.getTenantId().equals(tenantId)) {
            throw new SecurityException("Tenant isolation violation: resource belongs to different tenant");
        }

        if (!resource.isActive()) {
            throw new IllegalStateException("Cannot book an inactive resource");
        }

        if (resource.isMaintenance()) {
            throw new IllegalStateException("Cannot book a resource under maintenance");
        }

        log.info("Creating booking for resource {} from {} to {} by {}",
                resourceId, dto.startTime(), dto.endTime(), actorId);

        // Check for conflicts
        List<BookingEntity> conflicts = conflictDetector.detectConflicts(
                resourceId, dto.startTime(), dto.endTime(), null);

        if (!conflicts.isEmpty()) {
            String conflictIds = conflicts.stream()
                    .map(b -> b.getId().toString())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            throw new IllegalStateException(
                    "Booking conflicts detected with existing booking(s): " + conflictIds);
        }

        BookingEntity booking = new BookingEntity();
        booking.setResourceId(resourceId);
        booking.setTenantId(tenantId);
        booking.setFacilityId(resource.getFacilityId());
        booking.setBookedBy(actorId);
        booking.setSubjectRef(dto.subjectRef());
        booking.setPurpose(dto.purpose());
        booking.setStartTime(dto.startTime());
        booking.setEndTime(dto.endTime());
        booking.setStatus("CONFIRMED");
        booking.setNotes(dto.notes());
        booking = bookingRepository.save(booking);

        log.info("Booking {} created for resource {} from {} to {}",
                booking.getId(), resourceId, dto.startTime(), dto.endTime());
        return booking;
    }

    /**
     * Cancel an existing booking.
     *
     * @param bookingId the booking UUID
     * @param reason    the cancellation reason
     * @return the cancelled booking entity
     */
    @Transactional
    public BookingEntity cancelBooking(UUID bookingId, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();
        String actorId = ctx.actorId();

        BookingEntity booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));

        if (!booking.getTenantId().equals(tenantId)) {
            throw new SecurityException("Tenant isolation violation: booking belongs to different tenant");
        }

        if ("CANCELLED".equals(booking.getStatus())) {
            throw new IllegalStateException("Booking is already cancelled");
        }

        log.info("Cancelling booking {} by actor {}, reason: {}", bookingId, actorId, reason);

        booking.setStatus("CANCELLED");
        booking.setCancelledAt(Instant.now());
        booking.setCancelledBy(actorId);
        booking.setCancelReason(reason);
        // updatedAt is handled by @PreUpdate in BookingEntity
        booking = bookingRepository.save(booking);

        log.info("Booking {} cancelled", bookingId);
        return booking;
    }

    /**
     * List bookings for a resource within a time range.
     *
     * @param resourceId the resource UUID
     * @param from       the start of the time range (inclusive)
     * @param to         the end of the time range (exclusive)
     * @return list of bookings in the time range
     */
    @Transactional(readOnly = true)
    public List<BookingEntity> listBookings(UUID resourceId, OffsetDateTime from, OffsetDateTime to) {
        log.debug("Listing bookings for resource {} from {} to {}", resourceId, from, to);
        return bookingRepository.findByResource_IdAndStartTimeLessThanAndEndTimeGreaterThan(
                resourceId, to.toInstant(), from.toInstant());
    }

    // ---- DTOs ----

    public record CreateBookingRequest(
            String subjectRef,
            String purpose,
            OffsetDateTime startTime,
            OffsetDateTime endTime,
            String notes
    ) {}
}
