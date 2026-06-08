package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.api.dto.AppointmentResponse;
import zw.gov.mohcc.impilo.tuso.persistence.entity.AppointmentEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.AppointmentRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AppointmentService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);

    private final AppointmentRepository appointmentRepository;
    private final FacilityRepository facilityRepository;
    private final EventOutboxRepository outboxRepository;

    public AppointmentService(AppointmentRepository appointmentRepository,
                              FacilityRepository facilityRepository,
                              EventOutboxRepository outboxRepository) {
        this.appointmentRepository = appointmentRepository;
        this.facilityRepository = facilityRepository;
        this.outboxRepository = outboxRepository;
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> list(String patientId, Long facilityId, String status, int page, int size) {
        TrustContext ctx = TrustContextHolder.require();
        Page<AppointmentEntity> result = appointmentRepository.search(
                ctx.tenantId(), blankToNull(patientId), facilityId, blankToNull(status),
                PageRequest.of(page, Math.min(size, 100)));
        return result.getContent().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> listForCitizen(String cpid, String status, int page, int size) {
        TrustContext ctx = TrustContextHolder.require();
        Page<AppointmentEntity> result = appointmentRepository.findByCitizen(
                ctx.tenantId(), cpid, blankToNull(status), PageRequest.of(page, Math.min(size, 100)));
        return result.getContent().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AppointmentResponse get(UUID id) {
        TrustContext ctx = TrustContextHolder.require();
        AppointmentEntity entity = appointmentRepository.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found: " + id));
        return toResponse(entity);
    }

    @Transactional
    public AppointmentResponse createProvider(CreateProviderAppointmentRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        FacilityEntity facility = resolveFacility(ctx.tenantId(), request.facilityId());
        Instant scheduledAt = parseDateTime(request.scheduledAt());
        Instant endAt = request.endAt() != null ? parseDateTime(request.endAt()) : scheduledAt.plusSeconds(30 * 60);

        AppointmentEntity entity = baseEntity(ctx, facility, request.patientCpid(), request.patientId());
        entity.setProviderId(request.providerId());
        entity.setProviderName(request.providerName());
        entity.setAppointmentType(request.appointmentType());
        entity.setStatus("SCHEDULED");
        entity.setScheduledAt(scheduledAt);
        entity.setEndAt(endAt);
        entity.setReason(request.reason());
        entity.setNotes(request.notes());
        entity.setResourceId(parseUuidOrNull(request.resourceId()));
        entity = appointmentRepository.save(entity);
        publishBookedEvent(entity);
        log.info("Provider appointment {} created at facility {}", entity.getId(), facility.getId());
        return toResponse(entity);
    }

    @Transactional
    public AppointmentResponse createCitizen(String cpid, CreateCitizenAppointmentRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        FacilityEntity facility = resolveFacility(ctx.tenantId(), request.facilityId());
        Instant scheduledAt = parsePreferredDate(request.preferredDate());

        AppointmentEntity entity = baseEntity(ctx, facility, cpid, ctx.actorId());
        entity.setAppointmentType(request.appointmentType());
        entity.setStatus("REQUESTED");
        entity.setScheduledAt(scheduledAt);
        entity.setEndAt(scheduledAt.plusSeconds(30 * 60));
        entity.setReason(request.reason());
        entity = appointmentRepository.save(entity);
        publishBookedEvent(entity);
        log.info("Citizen appointment {} requested by cpid {} at facility {}", entity.getId(), cpid, facility.getId());
        return toResponse(entity);
    }

    @Transactional
    public AppointmentResponse confirm(UUID id) {
        TrustContext ctx = TrustContextHolder.require();
        AppointmentEntity entity = requireMutable(id, ctx.tenantId());
        if ("CANCELLED".equals(entity.getStatus())) {
            throw new IllegalStateException("Cannot confirm a cancelled appointment");
        }
        entity.setStatus("CONFIRMED");
        entity = appointmentRepository.save(entity);
        return toResponse(entity);
    }

    @Transactional
    public AppointmentResponse cancel(UUID id, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        AppointmentEntity entity = requireMutable(id, ctx.tenantId());
        if ("CANCELLED".equals(entity.getStatus())) {
            return toResponse(entity);
        }
        entity.setStatus("CANCELLED");
        entity.setCancelledAt(Instant.now());
        entity.setCancelledBy(ctx.actorId());
        entity.setCancelReason(reason != null ? reason : "Cancelled");
        entity = appointmentRepository.save(entity);
        return toResponse(entity);
    }

    private AppointmentEntity baseEntity(TrustContext ctx, FacilityEntity facility, String cpid, String patientId) {
        AppointmentEntity entity = new AppointmentEntity();
        entity.setTenantId(ctx.tenantId());
        entity.setFacilityId(facility.getId());
        entity.setPatientCpid(cpid);
        entity.setPatientId(patientId != null && !patientId.isBlank() ? patientId : cpid);
        entity.setCreatedBy(ctx.actorId());
        return entity;
    }

    private AppointmentEntity requireMutable(UUID id, UUID tenantId) {
        return appointmentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found: " + id));
    }

    private FacilityEntity resolveFacility(UUID tenantId, String facilityIdRaw) {
        if (facilityIdRaw == null || facilityIdRaw.isBlank()) {
            throw new IllegalArgumentException("facility_id is required");
        }
        long facilityId;
        try {
            facilityId = Long.parseLong(facilityIdRaw.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("facility_id must be numeric: " + facilityIdRaw);
        }
        FacilityEntity facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new IllegalArgumentException("Facility not found: " + facilityId));
        if (!facility.getTenantId().equals(tenantId)) {
            throw new SecurityException("Tenant isolation violation for facility " + facilityId);
        }
        return facility;
    }

    private AppointmentResponse toResponse(AppointmentEntity entity) {
        String facilityName = facilityRepository.findById(entity.getFacilityId())
                .map(FacilityEntity::getName)
                .orElse(null);
        return AppointmentResponse.of(
                entity.getId(),
                entity.getFacilityId(),
                facilityName,
                entity.getPatientId(),
                entity.getPatientCpid(),
                entity.getProviderId(),
                entity.getProviderName(),
                entity.getAppointmentType(),
                entity.getStatus(),
                entity.getScheduledAt(),
                entity.getEndAt(),
                entity.getReason(),
                entity.getNotes(),
                entity.getResourceId(),
                entity.getCreatedAt());
    }

    private void publishBookedEvent(AppointmentEntity entity) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType("APPOINTMENT");
        event.setAggregateId(entity.getId().toString());
        event.setEventType("appointment.booked");
        event.setPayload(Map.of(
                "appointmentId", entity.getId().toString(),
                "facilityId", entity.getFacilityId(),
                "patientCpid", entity.getPatientCpid(),
                "status", entity.getStatus(),
                "scheduledAt", entity.getScheduledAt().toString()));
        outboxRepository.save(event);
    }

    private static Instant parsePreferredDate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("preferredDate is required");
        }
        try {
            return OffsetDateTime.parse(raw.trim()).toInstant();
        } catch (Exception ignored) {
        }
        try {
            return Instant.parse(raw.trim());
        } catch (Exception ignored) {
        }
        if (raw.length() == 10) {
            LocalDate date = LocalDate.parse(raw.trim());
            return date.atTime(9, 0).toInstant(ZoneOffset.UTC);
        }
        LocalDateTime ldt = LocalDateTime.parse(raw.trim());
        return ldt.toInstant(ZoneOffset.UTC);
    }

    private static Instant parseDateTime(String raw) {
        return parsePreferredDate(raw);
    }

    private static UUID parseUuidOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return UUID.fromString(raw.trim());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public record CreateProviderAppointmentRequest(
            String patientId,
            String patientCpid,
            String facilityId,
            String providerId,
            String providerName,
            String appointmentType,
            String scheduledAt,
            String endAt,
            String reason,
            String notes,
            String resourceId
    ) {}

    public record CreateCitizenAppointmentRequest(
            String facilityId,
            String appointmentType,
            String preferredDate,
            String reason
    ) {}
}
