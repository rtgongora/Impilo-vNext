package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.persistence.entity.BookingEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.ResourceCalendarEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.ResourceEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.BookingRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.ResourceCalendarRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.ResourceRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Core service for managing resources (rooms, beds, equipment) within facilities.
 *
 * <p>Handles resource CRUD, calendar management, and available slot computation.</p>
 */
@Service
public class ResourceService {

    private static final Logger log = LoggerFactory.getLogger(ResourceService.class);

    private final ResourceRepository resourceRepository;
    private final ResourceCalendarRepository calendarRepository;
    private final BookingRepository bookingRepository;
    private final FacilityRepository facilityRepository;
    private final EventOutboxRepository outboxRepository;

    public ResourceService(ResourceRepository resourceRepository,
                           ResourceCalendarRepository calendarRepository,
                           BookingRepository bookingRepository,
                           FacilityRepository facilityRepository,
                           EventOutboxRepository outboxRepository) {
        this.resourceRepository = resourceRepository;
        this.calendarRepository = calendarRepository;
        this.bookingRepository = bookingRepository;
        this.facilityRepository = facilityRepository;
        this.outboxRepository = outboxRepository;
    }

    /**
     * Create a new resource within a facility.
     *
     * @param facilityId the owning facility ID
     * @param dto        the resource creation data
     * @return the created resource entity
     */
    @Transactional
    public ResourceEntity createResource(Long facilityId, CreateResourceRequest dto) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();

        FacilityEntity facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new IllegalArgumentException("Facility not found: " + facilityId));

        log.info("Creating resource '{}' of type {} for facility {} in tenant {}",
                dto.name(), dto.resourceType(), facilityId, tenantId);

        ResourceEntity resource = new ResourceEntity();
        resource.setFacility(facility);
        resource.setTenantId(tenantId);
        resource.setName(dto.name());
        resource.setResourceType(dto.resourceType());
        resource.setCapacity(dto.capacity() != null ? dto.capacity() : 1);
        resource.setStatus(dto.status() != null ? dto.status() : "AVAILABLE");
        resource.setMaintenance(dto.maintenance() != null && dto.maintenance());
        resource.setLocationDesc(dto.locationDesc());
        resource.setMetadata(dto.metadata());
        resource.setActive(true);
        resource = resourceRepository.save(resource);

        publishEvent("RESOURCE", resource.getId().toString(), "tuso.resource.created",
                String.format("{\"resourceId\":\"%s\",\"facilityId\":%d,\"tenantId\":\"%s\"," +
                        "\"name\":\"%s\",\"resourceType\":\"%s\",\"action\":\"CREATED\"}",
                        resource.getId(), facilityId, tenantId, dto.name(), dto.resourceType()));

        log.info("Resource '{}' created with id {}", dto.name(), resource.getId());
        return resource;
    }

    /**
     * Update an existing resource.
     *
     * @param resourceId the resource UUID
     * @param dto        the update data
     * @return the updated resource entity
     */
    @Transactional
    public ResourceEntity updateResource(UUID resourceId, UpdateResourceRequest dto) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();

        ResourceEntity resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new IllegalArgumentException("Resource not found: " + resourceId));

        if (!resource.getTenantId().equals(tenantId)) {
            throw new SecurityException("Tenant isolation violation: resource belongs to different tenant");
        }

        log.info("Updating resource {} for tenant {}", resourceId, tenantId);

        if (dto.name() != null) {
            resource.setName(dto.name());
        }
        if (dto.resourceType() != null) {
            resource.setResourceType(dto.resourceType());
        }
        if (dto.capacity() != null) {
            resource.setCapacity(dto.capacity());
        }
        if (dto.status() != null) {
            resource.setStatus(dto.status());
        }
        if (dto.maintenance() != null) {
            resource.setMaintenance(dto.maintenance());
        }
        if (dto.locationDesc() != null) {
            resource.setLocationDesc(dto.locationDesc());
        }
        if (dto.metadata() != null) {
            resource.setMetadata(dto.metadata());
        }
        if (dto.active() != null) {
            resource.setActive(dto.active());
        }

        resource = resourceRepository.save(resource);

        publishEvent("RESOURCE", resourceId.toString(), "tuso.resource.updated",
                String.format("{\"resourceId\":\"%s\",\"facilityId\":%d,\"tenantId\":\"%s\"," +
                        "\"name\":\"%s\",\"action\":\"UPDATED\"}",
                        resourceId, resource.getFacility().getId(), tenantId, resource.getName()));

        log.info("Resource {} updated", resourceId);
        return resource;
    }

    /**
     * List resources for a facility, optionally filtered by resource type.
     *
     * @param facilityId   the facility ID
     * @param resourceType optional resource type filter
     * @return list of matching active resources
     */
    @Transactional(readOnly = true)
    public List<ResourceEntity> listResources(Long facilityId, String resourceType) {
        if (resourceType != null && !resourceType.isBlank()) {
            return resourceRepository.findByFacility_IdAndResourceTypeAndActiveTrue(facilityId, resourceType);
        }
        return resourceRepository.findByFacility_IdAndActiveTrue(facilityId);
    }

    /**
     * Create a recurring calendar entry for a resource.
     *
     * @param resourceId the resource UUID
     * @param dto        the calendar entry data
     * @return the created calendar entity
     */
    @Transactional
    public ResourceCalendarEntity createCalendar(UUID resourceId, CreateCalendarRequest dto) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();

        ResourceEntity resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new IllegalArgumentException("Resource not found: " + resourceId));

        if (!resource.getTenantId().equals(tenantId)) {
            throw new SecurityException("Tenant isolation violation: resource belongs to different tenant");
        }

        log.info("Creating calendar entry for resource {} on day {}", resourceId, dto.dayOfWeek());

        ResourceCalendarEntity calendar = new ResourceCalendarEntity();
        calendar.setResource(resource);
        calendar.setDayOfWeek(dto.dayOfWeek());
        calendar.setStartTime(dto.startTime());
        calendar.setEndTime(dto.endTime());
        calendar.setSlotDurationMinutes(dto.slotDurationMinutes() != null ? dto.slotDurationMinutes() : 30);
        calendar.setActive(true);
        calendar.setEffectiveFrom(dto.effectiveFrom() != null ? dto.effectiveFrom() : LocalDate.now());
        calendar.setEffectiveTo(dto.effectiveTo());
        calendar = calendarRepository.save(calendar);

        log.info("Calendar entry {} created for resource {}", calendar.getId(), resourceId);
        return calendar;
    }

    /**
     * Compute available time slots for resources at a facility on a given date.
     *
     * <p>For each matching resource, finds active calendar entries for the day of the week,
     * generates slots based on the slot duration, then removes any slots that overlap
     * with existing non-cancelled bookings.</p>
     *
     * @param facilityId   the facility ID
     * @param date         the date to compute slots for
     * @param resourceType optional filter by resource type
     * @return list of available slots across all matching resources
     */
    @Transactional(readOnly = true)
    public List<AvailableSlot> getAvailableSlots(Long facilityId, LocalDate date, String resourceType) {
        List<ResourceEntity> resources = listResources(facilityId, resourceType);
        int dayOfWeek = date.getDayOfWeek().getValue(); // 1=Monday ... 7=Sunday

        List<AvailableSlot> allSlots = new ArrayList<>();

        for (ResourceEntity resource : resources) {
            if (resource.isMaintenance()) {
                continue;
            }

            List<ResourceCalendarEntity> calendars = calendarRepository.findByResource_IdAndActiveTrue(resource.getId());

            for (ResourceCalendarEntity calendar : calendars) {
                if (calendar.getDayOfWeek() != dayOfWeek) {
                    continue;
                }

                // Check effective date range
                if (calendar.getEffectiveFrom() != null && date.isBefore(calendar.getEffectiveFrom())) {
                    continue;
                }
                if (calendar.getEffectiveTo() != null && date.isAfter(calendar.getEffectiveTo())) {
                    continue;
                }

                // Generate slots from calendar entry
                List<AvailableSlot> calendarSlots = generateSlots(resource, calendar, date);

                // Remove conflicting slots (those overlapping with existing bookings)
                Instant dayStart = date.atStartOfDay().toInstant(ZoneOffset.UTC);
                Instant dayEnd = date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

                List<BookingEntity> existingBookings = bookingRepository
                        .findByResource_IdAndStartTimeLessThanAndEndTimeGreaterThanAndStatusNot(
                                resource.getId(), dayEnd, dayStart, "CANCELLED");

                for (AvailableSlot slot : calendarSlots) {
                    boolean hasConflict = existingBookings.stream().anyMatch(booking ->
                            booking.getStartTime().isBefore(slot.endTime().toInstant()) &&
                            booking.getEndTime().isAfter(slot.startTime().toInstant()));

                    if (!hasConflict) {
                        allSlots.add(slot);
                    }
                }
            }
        }

        log.debug("Found {} available slots for facility {} on {} (type={})",
                allSlots.size(), facilityId, date, resourceType);
        return allSlots;
    }

    private List<AvailableSlot> generateSlots(ResourceEntity resource,
                                               ResourceCalendarEntity calendar,
                                               LocalDate date) {
        List<AvailableSlot> slots = new ArrayList<>();
        LocalTime current = calendar.getStartTime();
        LocalTime end = calendar.getEndTime();
        int durationMinutes = calendar.getSlotDurationMinutes();

        while (current.plusMinutes(durationMinutes).compareTo(end) <= 0) {
            LocalTime slotEnd = current.plusMinutes(durationMinutes);
            OffsetDateTime slotStartDt = date.atTime(current).atOffset(ZoneOffset.UTC);
            OffsetDateTime slotEndDt = date.atTime(slotEnd).atOffset(ZoneOffset.UTC);

            slots.add(new AvailableSlot(
                    resource.getId(),
                    resource.getName(),
                    resource.getResourceType(),
                    slotStartDt,
                    slotEndDt,
                    durationMinutes
            ));

            current = slotEnd;
        }

        return slots;
    }

    // ---- Private helpers ----

    private void publishEvent(String aggregateType, String aggregateId,
                               String eventType, String payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        outboxRepository.save(event);
    }

    // ---- DTOs ----

    public record CreateResourceRequest(
            String name,
            String resourceType,
            Integer capacity,
            String status,
            Boolean maintenance,
            String locationDesc,
            Map<String, Object> metadata
    ) {}

    public record UpdateResourceRequest(
            String name,
            String resourceType,
            Integer capacity,
            String status,
            Boolean maintenance,
            String locationDesc,
            Map<String, Object> metadata,
            Boolean active
    ) {}

    public record CreateCalendarRequest(
            int dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            Integer slotDurationMinutes,
            LocalDate effectiveFrom,
            LocalDate effectiveTo
    ) {}

    public record AvailableSlot(
            UUID resourceId,
            String resourceName,
            String resourceType,
            OffsetDateTime startTime,
            OffsetDateTime endTime,
            int durationMinutes
    ) {}
}
