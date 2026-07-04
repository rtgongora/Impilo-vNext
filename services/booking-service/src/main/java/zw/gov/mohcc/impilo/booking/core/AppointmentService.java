package zw.gov.mohcc.impilo.booking.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.booking.api.dto.AppointmentResponse;
import zw.gov.mohcc.impilo.booking.domain.AppointmentStatus;
import zw.gov.mohcc.impilo.booking.domain.AppointmentType;
import zw.gov.mohcc.impilo.booking.integration.PctClient;
import zw.gov.mohcc.impilo.booking.persistence.entity.AppointmentEntity;
import zw.gov.mohcc.impilo.booking.persistence.entity.BookingEntity;
import zw.gov.mohcc.impilo.booking.persistence.repository.AppointmentRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AppointmentService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);

    private final AppointmentRepository appointmentRepository;
    private final BookingOutboxPublisher outboxPublisher;
    private final PctClient pctClient;

    public AppointmentService(AppointmentRepository appointmentRepository,
                              BookingOutboxPublisher outboxPublisher,
                              PctClient pctClient) {
        this.appointmentRepository = appointmentRepository;
        this.outboxPublisher = outboxPublisher;
        this.pctClient = pctClient;
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> list(String patientId, Long facilityId, String status, int page, int size) {
        TrustContext ctx = TrustContextHolder.require();
        AppointmentStatus parsed = parseStatus(status);
        return appointmentRepository.search(
                ctx.tenantId(), blankToNull(patientId), facilityId, parsed,
                PageRequest.of(page, Math.min(size, 100)))
                .getContent().stream().map(AppointmentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> listForCitizen(String cpid, String status, int page, int size) {
        TrustContext ctx = TrustContextHolder.require();
        return appointmentRepository.findByCitizen(
                ctx.tenantId(), cpid, parseStatus(status), PageRequest.of(page, Math.min(size, 100)))
                .getContent().stream().map(AppointmentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AppointmentResponse get(UUID id) {
        TrustContext ctx = TrustContextHolder.require();
        return AppointmentResponse.from(requireAppointment(id, ctx.tenantId()));
    }

    @Transactional
    public AppointmentResponse createProvider(CreateProviderAppointmentRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        Instant scheduledAt = parseDateTime(request.scheduledAt());
        Instant endAt = request.endAt() != null ? parseDateTime(request.endAt()) : scheduledAt.plusSeconds(30 * 60);

        AppointmentEntity entity = baseEntity(ctx, request.patientCpid(), request.patientId());
        entity.setFacilityId(parseFacilityId(request.facilityId()));
        entity.setProviderId(request.providerId());
        entity.setProviderName(request.providerName());
        entity.setAppointmentType(request.appointmentType());
        entity.setStatus(AppointmentStatus.SCHEDULED);
        entity.setStartTime(scheduledAt);
        entity.setEndTime(endAt);
        entity.setReason(request.reason());
        entity.setNotes(request.notes());
        entity.setResourceId(parseUuidOrNull(request.resourceId()));
        entity = appointmentRepository.save(entity);
        publishBookedEvent(entity);
        return AppointmentResponse.from(entity);
    }

    @Transactional
    public AppointmentResponse createCitizen(String cpid, CreateCitizenAppointmentRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        Instant scheduledAt = parsePreferredDate(request.preferredDate());

        AppointmentEntity entity = baseEntity(ctx, cpid, ctx.actorId());
        entity.setFacilityId(parseFacilityId(request.facilityId()));
        entity.setAppointmentType(request.appointmentType());
        entity.setStatus(AppointmentStatus.REQUESTED);
        entity.setStartTime(scheduledAt);
        entity.setEndTime(scheduledAt.plusSeconds(30 * 60));
        entity.setReason(request.reason());
        entity = appointmentRepository.save(entity);
        publishBookedEvent(entity);
        log.info("Citizen appointment {} requested by cpid {}", entity.getId(), cpid);
        return AppointmentResponse.from(entity);
    }

    @Transactional
    public AppointmentResponse createFromBooking(BookingEntity booking) {
        TrustContext ctx = TrustContextHolder.require();
        if (booking.getLinkedAppointmentId() != null) {
            return AppointmentResponse.from(
                    appointmentRepository.findByIdAndTenantId(booking.getLinkedAppointmentId(), ctx.tenantId())
                            .orElseThrow(() -> new IllegalStateException("Linked appointment missing")));
        }

        Instant start = booking.getPreferredStartAt() != null
                ? booking.getPreferredStartAt()
                : Instant.now().plusSeconds(3600);
        Instant end = booking.getPreferredEndAt() != null
                ? booking.getPreferredEndAt()
                : start.plusSeconds(30 * 60);

        AppointmentEntity entity = baseEntity(ctx, booking.getClientId(), booking.getClientId());
        entity.setBookingId(booking.getId());
        entity.setFacilityId(booking.getFacilityId());
        entity.setProviderId(booking.getProviderId());
        entity.setProviderName(booking.getProviderName());
        entity.setServiceId(booking.getServiceId());
        entity.setAppointmentType(mapBookingType(booking.getBookingType()));
        entity.setStatus(AppointmentStatus.SCHEDULED);
        entity.setStartTime(start);
        entity.setEndTime(end);
        entity.setChannel(booking.getPreferredChannel() != null ? booking.getPreferredChannel().name() : null);
        entity.setLocation(booking.getLocation());
        entity.setReason(booking.getReason());
        entity.setNotes(booking.getNotes());
        entity.setResourceId(booking.getResourceId());
        entity = appointmentRepository.save(entity);
        publishBookedEvent(entity);
        return AppointmentResponse.from(entity);
    }

    @Transactional
    public AppointmentResponse confirm(UUID id) {
        TrustContext ctx = TrustContextHolder.require();
        AppointmentEntity entity = requireAppointment(id, ctx.tenantId());
        if (entity.getStatus() == AppointmentStatus.CANCELLED) {
            throw new IllegalStateException("Cannot confirm a cancelled appointment");
        }
        entity.setStatus(AppointmentStatus.CONFIRMED);
        return AppointmentResponse.from(appointmentRepository.save(entity));
    }

    @Transactional
    public AppointmentResponse cancel(UUID id, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        AppointmentEntity entity = requireAppointment(id, ctx.tenantId());
        if (entity.getStatus() == AppointmentStatus.CANCELLED) {
            return AppointmentResponse.from(entity);
        }
        entity.setStatus(AppointmentStatus.CANCELLED);
        entity.setCancelledAt(Instant.now());
        entity.setCancelledBy(ctx.actorId());
        entity.setCancelReason(reason != null ? reason : "Cancelled");
        return AppointmentResponse.from(appointmentRepository.save(entity));
    }

    /**
     * Check a patient in against an appointment: start a PCT journey (carrying
     * appointment provenance) and enqueue it into the given queue.
     *
     * <p>PCT journey facility resolution relies on the {@code X-Facility-ID}
     * trust header: the local {@code facilityId} is a TUSO numeric id, not the
     * platform facility UUID PCT expects, so it is deliberately not sent.
     * The clinical encounter is NOT started here — check-in ends at queue
     * entry; the encounter starts when the provider begins service from the
     * queue. When the journey or queue link cannot be established the
     * appointment still checks in, but with the honest
     * {@code CHECKED_IN_NO_QUEUE} state instead of silently dropping the
     * queue placement.</p>
     */
    @Transactional
    public AppointmentResponse checkIn(UUID id, UUID queueId) {
        TrustContext ctx = TrustContextHolder.require();
        AppointmentEntity entity = requireAppointment(id, ctx.tenantId());

        Map<String, Object> journeyPayload = new LinkedHashMap<>();
        journeyPayload.put("patientCpid", entity.getPatientCpid());
        journeyPayload.put("appointmentId", entity.getId().toString());
        Map<String, Object> journey = pctClient.startJourney(ctx, journeyPayload);

        // PCT journeys are keyed by "journeyId" (a ULID string). The former
        // "id" lookup was always null, so check-ins never actually enqueued.
        Object journeyId = journey.get("journeyId") != null ? journey.get("journeyId") : journey.get("id");
        boolean queueLinked = false;
        if (journeyId != null && queueId != null) {
            Map<String, Object> enqueuePayload = new LinkedHashMap<>();
            enqueuePayload.put("journeyId", journeyId.toString());
            enqueuePayload.put("priority", 3); // routine scheduled arrival on the 1-5 scale
            Map<String, Object> queueItem = pctClient.enqueue(ctx, queueId, enqueuePayload);
            Object tokenId = queueItem.get("id");
            if (tokenId != null) {
                entity.setQueueTokenId(UUID.fromString(tokenId.toString()));
                queueLinked = true;
            }
        }

        if (!queueLinked) {
            log.warn("Appointment {} checked in without a queue link (journeyId={}, queueId={})",
                    id, journeyId, queueId);
        }

        entity.setStatus(AppointmentStatus.CHECKED_IN);
        entity.setCheckInStatus(queueLinked ? "CHECKED_IN" : "CHECKED_IN_NO_QUEUE");
        entity = appointmentRepository.save(entity);
        publishBookedEvent(entity);
        return AppointmentResponse.from(entity);
    }

    @Transactional
    public AppointmentResponse complete(UUID id) {
        TrustContext ctx = TrustContextHolder.require();
        AppointmentEntity entity = requireAppointment(id, ctx.tenantId());
        entity.setStatus(AppointmentStatus.COMPLETED);
        return AppointmentResponse.from(appointmentRepository.save(entity));
    }

    @Transactional
    public AppointmentResponse noShow(UUID id) {
        TrustContext ctx = TrustContextHolder.require();
        AppointmentEntity entity = requireAppointment(id, ctx.tenantId());
        entity.setStatus(AppointmentStatus.NO_SHOW);
        return AppointmentResponse.from(appointmentRepository.save(entity));
    }

    @Transactional
    public AppointmentResponse reschedule(UUID id, String scheduledAt, String endAt) {
        TrustContext ctx = TrustContextHolder.require();
        AppointmentEntity entity = requireAppointment(id, ctx.tenantId());
        Instant start = parseDateTime(scheduledAt);
        entity.setStartTime(start);
        entity.setEndTime(endAt != null ? parseDateTime(endAt) : start.plusSeconds(30 * 60));
        entity.setStatus(AppointmentStatus.RESCHEDULED);
        return AppointmentResponse.from(appointmentRepository.save(entity));
    }

    private AppointmentEntity baseEntity(TrustContext ctx, String cpid, String patientId) {
        AppointmentEntity entity = new AppointmentEntity();
        entity.setTenantId(ctx.tenantId());
        entity.setAppointmentNumber(BookingNumberGenerator.nextAppointmentNumber());
        entity.setClientId(cpid);
        entity.setPatientCpid(cpid);
        entity.setPatientId(patientId != null && !patientId.isBlank() ? patientId : cpid);
        entity.setCreatedBy(ctx.actorId());
        return entity;
    }

    private AppointmentEntity requireAppointment(UUID id, UUID tenantId) {
        return appointmentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found: " + id));
    }

    private void publishBookedEvent(AppointmentEntity entity) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("appointmentId", entity.getId().toString());
        payload.put("facilityId", entity.getFacilityId());
        payload.put("patientCpid", entity.getPatientCpid());
        payload.put("status", entity.getStatus().name());
        payload.put("scheduledAt", entity.getStartTime().toString());
        outboxPublisher.append("APPOINTMENT", entity.getId().toString(), "appointment.booked", payload);
    }

    private static String mapBookingType(zw.gov.mohcc.impilo.booking.domain.BookingType type) {
        if (type == null) {
            return AppointmentType.GENERAL.name();
        }
        return switch (type) {
            case TELECONSULT -> AppointmentType.TELECONSULT.name();
            case LAB -> AppointmentType.LAB.name();
            case RADIOLOGY -> AppointmentType.RADIOLOGY.name();
            case TRAINING -> AppointmentType.TRAINING.name();
            case OUTREACH -> AppointmentType.OUTREACH.name();
            case FOLLOW_UP -> AppointmentType.FOLLOW_UP.name();
            case PROCEDURE, THEATRE -> AppointmentType.PROCEDURE.name();
            default -> AppointmentType.GENERAL.name();
        };
    }

    private static Long parseFacilityId(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("facility_id is required");
        }
        return Long.parseLong(raw.trim());
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

    private static AppointmentStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return AppointmentStatus.valueOf(raw.trim().toUpperCase());
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
