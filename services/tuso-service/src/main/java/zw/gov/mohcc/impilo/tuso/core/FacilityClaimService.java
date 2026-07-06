package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityClaimDtos;
import zw.gov.mohcc.impilo.tuso.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityAdminAppointmentEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilitySourceLegitimacyEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityAdminAppointmentRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilitySourceLegitimacyRepository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Facility administrator claim/appointment flow (IATG Wave 2, WS-E) — TUSO owns the facility and
 * the appointment; org-registry records the resulting relationship; the experience-BFF orchestrates.
 *
 * <p>Mirrors the Wave-1 WS-F provider-claim pattern, with the key differences of the facility
 * subject:</p>
 * <ul>
 *   <li><b>Subject is the canonical facility UUID</b> (never a Health ID) — a facility is
 *       administered, it is not an identity anchor that gets rebound.</li>
 *   <li><b>Eligibility is gated on platform-legitimacy</b>: a facility is claimable only if its
 *       {@link FacilitySourceLegitimacyEntity} verdicts allow it on the platform — no denying
 *       source and at least one allowing source (the same derivation the status-composite uses).
 *       "Already administered" never blocks a claim: a facility may have many administrators.</li>
 *   <li><b>No recover-not-reissue analogue</b>: appointments are append-only, not a rebind.</li>
 * </ul>
 */
@Service
public class FacilityClaimService {

    public static final String EVENT_SUBMITTED = "tuso.facility.admin_appointment.submitted";
    public static final String EVENT_APPROVED = "tuso.facility.admin_appointment.approved";

    private static final Logger log = LoggerFactory.getLogger(FacilityClaimService.class);

    private final FacilityRepository facilityRepository;
    private final FacilitySourceLegitimacyRepository legitimacyRepository;
    private final FacilityAdminAppointmentRepository appointmentRepository;
    private final EventOutboxRepository outboxRepository;

    public FacilityClaimService(FacilityRepository facilityRepository,
                                FacilitySourceLegitimacyRepository legitimacyRepository,
                                FacilityAdminAppointmentRepository appointmentRepository,
                                EventOutboxRepository outboxRepository) {
        this.facilityRepository = facilityRepository;
        this.legitimacyRepository = legitimacyRepository;
        this.appointmentRepository = appointmentRepository;
        this.outboxRepository = outboxRepository;
    }

    // ── Eligibility ─────────────────────────────────────────────────────────────

    /**
     * Is this facility claimable/administrable? Consults the per-source legitimacy verdicts:
     * a facility not allowed on the platform is not claimable. Silence never grants.
     */
    @Transactional(readOnly = true)
    public FacilityClaimDtos.EligibilityView checkEligibility(UUID facilityUuid) {
        TrustContext ctx = TrustContextHolder.require();
        FacilityEntity facility = requireFacility(facilityUuid, ctx.tenantId());

        List<String> reasons = new ArrayList<>();
        boolean allowed = platformAccessAllowed(facility.getFacilityUuid(), reasons);

        long activeCount = appointmentRepository
                .findByFacilityUuidOrderByCreatedAtDesc(facility.getFacilityUuid()).stream()
                .filter(a -> FacilityAdminAppointmentEntity.STATE_ACTIVE.equals(a.getApprovalState()))
                .count();
        boolean alreadyAdministered = activeCount > 0;

        return new FacilityClaimDtos.EligibilityView(
                facility.getFacilityUuid(), allowed, allowed, alreadyAdministered,
                (int) activeCount, reasons);
    }

    // ── Preview ─────────────────────────────────────────────────────────────────

    /** Non-sensitive facility summary + the claimability verdict (confirm-before-commit). */
    @Transactional(readOnly = true)
    public FacilityClaimDtos.PreviewView preview(UUID facilityUuid) {
        TrustContext ctx = TrustContextHolder.require();
        FacilityEntity facility = requireFacility(facilityUuid, ctx.tenantId());
        boolean allowed = platformAccessAllowed(facility.getFacilityUuid(), new ArrayList<>());
        return new FacilityClaimDtos.PreviewView(
                facility.getFacilityUuid(),
                facility.getFacilityCode(),
                facility.getName(),
                facility.getFacilityType(),
                facility.getProvince(),
                facility.getDistrict(),
                allowed,
                allowed);
    }

    // ── Submit claim → PENDING appointment ──────────────────────────────────────

    /**
     * Submit a facility-administrator claim. Gated on platform legitimacy; creates a PENDING
     * appointment and emits {@code tuso.facility.admin_appointment.submitted}. The claimant
     * ({@code personHealthId}) is bound by the trusted caller (BFF) to the session actor — this
     * service never derives it from anywhere else.
     */
    @Transactional
    public FacilityClaimDtos.AppointmentView submitClaim(
            UUID facilityUuid, FacilityClaimDtos.SubmitClaimRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        FacilityEntity facility = requireFacility(facilityUuid, ctx.tenantId());

        if (request == null || request.personHealthId() == null || request.personHealthId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "personHealthId is required");
        }

        List<String> reasons = new ArrayList<>();
        if (!platformAccessAllowed(facility.getFacilityUuid(), reasons)) {
            // Not allowed on the platform → not claimable. Honest 409, never a fake success.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Facility is not allowed on the platform and cannot be claimed: "
                            + String.join("; ", reasons));
        }

        if (appointmentRepository.existsByFacilityUuidAndPersonHealthIdAndApprovalState(
                facility.getFacilityUuid(), request.personHealthId(),
                FacilityAdminAppointmentEntity.STATE_ACTIVE)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This person is already an active administrator of the facility.");
        }

        FacilityAdminAppointmentEntity appointment = new FacilityAdminAppointmentEntity();
        appointment.setFacilityUuid(facility.getFacilityUuid());
        appointment.setPersonHealthId(request.personHealthId());
        appointment.setRole(request.role() == null || request.role().isBlank()
                ? FacilityAdminAppointmentEntity.ROLE_FACILITY_ADMINISTRATOR : request.role().trim());
        appointment.setAppointedBy(ctx.actorId());
        appointment.setApprovalState(FacilityAdminAppointmentEntity.STATE_PENDING);
        appointment.setEvidenceRef(request.evidenceRef());
        appointment.setValidFrom(request.validFrom());
        appointment.setValidTo(request.validTo());
        appointment.setNotes(request.notes());
        appointment.setCreatedBy(ctx.actorId());
        appointment.setUpdatedBy(ctx.actorId());
        appointment = appointmentRepository.save(appointment);

        publish(EVENT_SUBMITTED, facility, appointment, ctx);
        log.info("Facility {} admin claim submitted (appointment {} PENDING) by {}",
                facility.getFacilityUuid(), appointment.getId(), ctx.actorId());
        return toView(appointment);
    }

    // ── Approve → ACTIVE ─────────────────────────────────────────────────────────

    /**
     * Approve a PENDING appointment → ACTIVE, emitting {@code tuso.facility.admin_appointment.approved}.
     * The approved event is the signal for org-registry to record the administering affiliation.
     */
    @Transactional
    public FacilityClaimDtos.AppointmentView approve(
            Long appointmentId, FacilityClaimDtos.ApproveAppointmentRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        FacilityAdminAppointmentEntity appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Appointment not found: " + appointmentId));
        FacilityEntity facility = requireFacility(appointment.getFacilityUuid(), ctx.tenantId());

        if (FacilityAdminAppointmentEntity.STATE_ACTIVE.equals(appointment.getApprovalState())) {
            return toView(appointment); // idempotent: already active
        }
        if (!FacilityAdminAppointmentEntity.STATE_PENDING.equals(appointment.getApprovalState())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only a PENDING appointment can be approved; current state is "
                            + appointment.getApprovalState());
        }

        appointment.setApprovalState(FacilityAdminAppointmentEntity.STATE_ACTIVE);
        appointment.setUpdatedBy(request != null && request.approvedBy() != null
                ? request.approvedBy() : ctx.actorId());
        appointment = appointmentRepository.save(appointment);

        publish(EVENT_APPROVED, facility, appointment, ctx);
        log.info("Facility {} admin appointment {} approved -> ACTIVE by {}",
                facility.getFacilityUuid(), appointment.getId(), ctx.actorId());
        return toView(appointment);
    }

    /** All appointments for a facility (canonical UUID), tenant-guarded. */
    @Transactional(readOnly = true)
    public List<FacilityClaimDtos.AppointmentView> list(UUID facilityUuid) {
        TrustContext ctx = TrustContextHolder.require();
        FacilityEntity facility = requireFacility(facilityUuid, ctx.tenantId());
        return appointmentRepository.findByFacilityUuidOrderByCreatedAtDesc(facility.getFacilityUuid())
                .stream().map(FacilityClaimService::toView).toList();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    /**
     * platformAccessAllowed = no source verdict denies AND at least one allows. Mirrors
     * {@code FacilitySourceLegitimacyService.getComposite} so the two never diverge.
     */
    private boolean platformAccessAllowed(UUID facilityUuid, List<String> reasons) {
        List<FacilitySourceLegitimacyEntity> rows =
                legitimacyRepository.findByFacilityIdOrderBySourceAsc(facilityUuid);
        if (rows.isEmpty()) {
            reasons.add("No source legitimacy recorded for this facility; platform access is not granted by default");
            return false;
        }
        boolean anyDenies = rows.stream().anyMatch(r -> !r.isAllowedOnPlatform());
        boolean anyAllows = rows.stream().anyMatch(FacilitySourceLegitimacyEntity::isAllowedOnPlatform);
        for (FacilitySourceLegitimacyEntity r : rows) {
            reasons.add(r.getSource() + ": " + r.getStatus()
                    + (r.isAllowedOnPlatform() ? " (allows platform operation)" : " (denies platform operation)"));
        }
        return !anyDenies && anyAllows;
    }

    private FacilityEntity requireFacility(UUID facilityUuid, UUID tenantId) {
        FacilityEntity facility = facilityRepository.findByFacilityUuid(facilityUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Facility not found: " + facilityUuid));
        if (!facility.getTenantId().equals(tenantId)) {
            throw new SecurityException("Tenant isolation violation for facility: " + facilityUuid);
        }
        return facility;
    }

    private void publish(String eventType, FacilityEntity facility,
                         FacilityAdminAppointmentEntity appointment, TrustContext ctx) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("facilityUuid", facility.getFacilityUuid().toString());
        payload.put("facilityId", facility.getId());
        payload.put("appointmentId", appointment.getId());
        payload.put("personHealthId", appointment.getPersonHealthId());
        payload.put("role", appointment.getRole());
        payload.put("approvalState", appointment.getApprovalState());
        payload.put("appointedBy", appointment.getAppointedBy());
        payload.put("evidenceRef", appointment.getEvidenceRef());

        String correlationId = ctx.correlationId() != null ? ctx.correlationId().toString() : null;
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType("FACILITY");
        outbox.setAggregateId(String.valueOf(facility.getId()));
        outbox.setEventType(eventType);
        outbox.setPayload(payload);
        outbox.setTenantId(ctx.tenantId() != null ? ctx.tenantId().toString() : null);
        outbox.setCorrelationId(correlationId);
        outbox.setCausationId(correlationId);
        outbox.setProducer("tuso");
        outbox.setSubjectType("Facility");
        outbox.setSubjectId(String.valueOf(facility.getId()));
        outbox.setPartitionKey(String.valueOf(facility.getId()));
        outbox.setSchemaVersion(1);
        outbox.setOccurredAt(Instant.now().atOffset(ZoneOffset.UTC));
        outboxRepository.save(outbox);
    }

    private static FacilityClaimDtos.AppointmentView toView(FacilityAdminAppointmentEntity a) {
        return new FacilityClaimDtos.AppointmentView(
                a.getId(), a.getFacilityUuid(), a.getPersonHealthId(), a.getRole(),
                a.getAppointedBy(), a.getApprovalState(), a.getEvidenceRef(),
                a.getValidFrom(), a.getValidTo(), a.getCreatedAt(), a.getUpdatedAt());
    }
}
