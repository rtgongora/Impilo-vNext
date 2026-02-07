package zw.gov.mohcc.impilo.tuso.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tuso.api.dto.AvailableSlotResponse;
import zw.gov.mohcc.impilo.tuso.api.dto.BookingResponse;
import zw.gov.mohcc.impilo.tuso.api.dto.CreateBookingRequest;
import zw.gov.mohcc.impilo.tuso.api.dto.CreateResourceRequest;
import zw.gov.mohcc.impilo.tuso.api.dto.ResourceResponse;
import zw.gov.mohcc.impilo.tuso.core.BookingService;
import zw.gov.mohcc.impilo.tuso.core.ResourceService;
import zw.gov.mohcc.impilo.tuso.persistence.entity.BookingEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.ResourceEntity;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Internal REST API for resource and booking management within facilities.
 *
 * Resources represent physical or logical assets (rooms, equipment, beds)
 * that can be booked by providers. Bookings enforce time-slot availability
 * and prevent double-booking.
 */
@RestController
@RequestMapping("/v1/internal")
public class ResourceController {

    private static final Logger log = LoggerFactory.getLogger(ResourceController.class);

    private final ResourceService resourceService;
    private final BookingService bookingService;

    public ResourceController(ResourceService resourceService, BookingService bookingService) {
        this.resourceService = resourceService;
        this.bookingService = bookingService;
    }

    @PostMapping("/facilities/{facilityId}/resources")
    public ResponseEntity<ApiResponse<ResourceResponse>> createResource(
            @PathVariable Long facilityId,
            @Valid @RequestBody CreateResourceRequest request) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Creating resource [facilityId={}, name={}, type={}] correlationId={}",
                facilityId, request.name(), request.resourceType(), ctx.correlationId());

        ResourceService.CreateResourceRequest coreRequest = new ResourceService.CreateResourceRequest(
                request.name(), request.resourceType(), request.capacity(),
                null, null, request.locationDesc(), request.metadata());
        ResourceEntity entity = resourceService.createResource(facilityId, coreRequest);
        ResourceResponse response = toResourceResponse(entity);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    @GetMapping("/facilities/{facilityId}/resources")
    public ResponseEntity<ApiResponse<List<ResourceResponse>>> listResources(
            @PathVariable Long facilityId,
            @RequestParam(required = false) String resourceType) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Listing resources [facilityId={}, resourceType={}] correlationId={}",
                facilityId, resourceType, ctx.correlationId());

        List<ResourceEntity> entities = resourceService.listResources(facilityId, resourceType);
        List<ResourceResponse> response = entities.stream().map(this::toResourceResponse).toList();

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    @PostMapping("/resources/{resourceId}/bookings")
    public ResponseEntity<ApiResponse<BookingResponse>> createBooking(
            @PathVariable UUID resourceId,
            @Valid @RequestBody CreateBookingRequest request) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Creating booking [resourceId={}, start={}, end={}] correlationId={}",
                resourceId, request.startTime(), request.endTime(), ctx.correlationId());

        BookingService.CreateBookingRequest coreRequest = new BookingService.CreateBookingRequest(
                request.subjectRef(), request.purpose(),
                request.startTime().atOffset(ZoneOffset.UTC), request.endTime().atOffset(ZoneOffset.UTC),
                request.notes());
        BookingEntity entity = bookingService.createBooking(resourceId, coreRequest);
        BookingResponse response = toBookingResponse(entity);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    @GetMapping("/resources/{resourceId}/bookings")
    public ResponseEntity<ApiResponse<List<BookingResponse>>> listBookings(
            @PathVariable UUID resourceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Listing bookings [resourceId={}, from={}, to={}] correlationId={}",
                resourceId, from, to, ctx.correlationId());

        List<BookingEntity> entities = bookingService.listBookings(resourceId, from, to);
        List<BookingResponse> response = entities.stream().map(this::toBookingResponse).toList();

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    @DeleteMapping("/bookings/{bookingId}")
    public ResponseEntity<ApiResponse<BookingResponse>> cancelBooking(
            @PathVariable UUID bookingId,
            @RequestParam String reason) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Cancelling booking [bookingId={}, reason={}] correlationId={}",
                bookingId, reason, ctx.correlationId());

        BookingEntity entity = bookingService.cancelBooking(bookingId, reason);
        BookingResponse response = toBookingResponse(entity);

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    @GetMapping("/facilities/{facilityId}/calendars/slots")
    public ResponseEntity<ApiResponse<AvailableSlotResponse>> getAvailableSlots(
            @PathVariable Long facilityId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String resourceType) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Fetching available slots [facilityId={}, date={}, resourceType={}] correlationId={}",
                facilityId, date, resourceType, ctx.correlationId());

        List<ResourceService.AvailableSlot> slots = resourceService.getAvailableSlots(facilityId, date, resourceType);
        AvailableSlotResponse response = toSlotResponse(slots);

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    // ---- Entity → DTO Mappers ----

    private ResourceResponse toResourceResponse(ResourceEntity e) {
        return new ResourceResponse(
                e.getId(), e.getFacilityId(), e.getName(), e.getResourceType(),
                e.getCapacity(), e.getStatus(), e.isMaintenance(), e.getLocationDesc(),
                e.getMetadata(), e.isActive(), e.getCreatedAt(), e.getUpdatedAt());
    }

    private BookingResponse toBookingResponse(BookingEntity b) {
        return new BookingResponse(
                b.getId(), b.getResourceId(),
                b.getResource() != null ? b.getResource().getName() : null,
                b.getFacilityId(), b.getBookedBy(), b.getSubjectRef(), b.getPurpose(),
                b.getStartTime(), b.getEndTime(), b.getStatus(), b.getNotes(),
                b.getCancelledBy(), b.getCancelledAt(), b.getCancelReason(),
                b.getCreatedAt(), b.getUpdatedAt());
    }

    private AvailableSlotResponse toSlotResponse(List<ResourceService.AvailableSlot> slots) {
        return new AvailableSlotResponse(
                slots.stream()
                        .map(s -> new AvailableSlotResponse.Slot(
                                s.startTime().toInstant(), s.endTime().toInstant(),
                                s.resourceId(), s.resourceName()))
                        .toList());
    }
}
