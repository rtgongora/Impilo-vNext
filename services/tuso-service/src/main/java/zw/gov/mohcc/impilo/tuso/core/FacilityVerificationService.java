package zw.gov.mohcc.impilo.tuso.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityVerificationCaseEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityVerificationCaseRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Proof of place (FJ3/FJ4, D-L6). A verification case carries evidence refs
 * (document-service only), a GPS plausibility signal (haversine distance
 * between the claimed live position and the registry coordinates, computed at
 * submission), and — for video walkthroughs — the rtc session ref; the
 * recording is never retained. Every case is DECIDED by an authorised
 * verifier; nothing auto-verifies. A VERIFIED decision refreshes the
 * EXISTENCE/GEOSPATIAL trust dimensions.
 */
@Service
public class FacilityVerificationService {

    public static final String EVENT_OPENED = "tuso.facility.verification_case.opened";
    public static final String EVENT_DECIDED = "tuso.facility.verification_case.decided";

    /** Claimed GPS within this distance of the registry point = plausible. */
    static final double GPS_PLAUSIBLE_METERS = 250.0;

    private static final Logger log = LoggerFactory.getLogger(FacilityVerificationService.class);

    private final FacilityRepository facilityRepository;
    private final FacilityVerificationCaseRepository caseRepository;
    private final FacilityTrustDimensionService trustDimensionService;
    private final zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public FacilityVerificationService(
            FacilityRepository facilityRepository,
            FacilityVerificationCaseRepository caseRepository,
            FacilityTrustDimensionService trustDimensionService,
            zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository outboxRepository,
            ObjectMapper objectMapper) {
        this.facilityRepository = facilityRepository;
        this.caseRepository = caseRepository;
        this.trustDimensionService = trustDimensionService;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public record OpenCaseRequest(String verificationType, List<String> evidenceRefs,
                                  BigDecimal claimedLatitude, BigDecimal claimedLongitude,
                                  String rtcSessionRef) {}

    @Transactional
    public FacilityVerificationCaseEntity open(UUID facilityUuid, OpenCaseRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        FacilityEntity facility = requireFacility(facilityUuid, ctx.tenantId());

        String type = request.verificationType() == null ? ""
                : request.verificationType().trim().toUpperCase(Locale.ROOT);
        if (!FacilityVerificationCaseEntity.TYPES.contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown verification type: " + request.verificationType());
        }
        boolean hasEvidence = request.evidenceRefs() != null && !request.evidenceRefs().isEmpty();
        boolean hasRtc = request.rtcSessionRef() != null && !request.rtcSessionRef().isBlank();
        if (!hasEvidence && !hasRtc) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A verification case needs evidence refs or a video session reference.");
        }

        FacilityVerificationCaseEntity verificationCase = new FacilityVerificationCaseEntity();
        verificationCase.setTenantId(ctx.tenantId());
        verificationCase.setCaseRef("FVC-" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT));
        verificationCase.setFacilityUuid(facility.getFacilityUuid());
        verificationCase.setVerificationType(type);
        verificationCase.setRequestedBy(ctx.actorId());
        verificationCase.setEvidenceRefs(toJson(request.evidenceRefs()));
        verificationCase.setClaimedLatitude(request.claimedLatitude());
        verificationCase.setClaimedLongitude(request.claimedLongitude());
        verificationCase.setRtcSessionRef(request.rtcSessionRef());

        // GPS plausibility: computed once at submission against registry truth.
        if (request.claimedLatitude() != null && request.claimedLongitude() != null
                && facility.getLatitude() != null && facility.getLongitude() != null) {
            double meters = haversineMeters(
                    facility.getLatitude().doubleValue(), facility.getLongitude().doubleValue(),
                    request.claimedLatitude().doubleValue(), request.claimedLongitude().doubleValue());
            verificationCase.setGpsDistanceMeters(
                    BigDecimal.valueOf(meters).setScale(2, RoundingMode.HALF_UP));
        }

        verificationCase = caseRepository.save(verificationCase);
        publish(EVENT_OPENED, ctx, verificationCase);
        log.info("facility verification case {} opened ({}, facility {})",
                verificationCase.getCaseRef(), type, facility.getFacilityUuid());
        return verificationCase;
    }

    /**
     * Verifier decision. The original case is never edited afterwards — a
     * repeat verification is a NEW case; MORE_EVIDENCE keeps this one open for
     * exactly one more decision round.
     */
    @Transactional
    public FacilityVerificationCaseEntity decide(Long caseId, String decision, String note) {
        TrustContext ctx = TrustContextHolder.require();
        FacilityVerificationCaseEntity verificationCase = caseRepository
                .findByIdAndTenantId(caseId, ctx.tenantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Verification case not found: " + caseId));

        String target = decision == null ? "" : decision.trim().toUpperCase(Locale.ROOT);
        if (!FacilityVerificationCaseEntity.DECISIONS.contains(target)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown decision: " + decision);
        }
        boolean decidable = FacilityVerificationCaseEntity.STATUS_OPEN.equals(verificationCase.getStatus())
                || FacilityVerificationCaseEntity.STATUS_MORE_EVIDENCE.equals(verificationCase.getStatus());
        if (!decidable) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Verification case is already decided: " + verificationCase.getStatus());
        }

        verificationCase.setStatus(target);
        verificationCase.setDecidedBy(ctx.actorId());
        verificationCase.setDecidedAt(Instant.now());
        verificationCase.setDecisionNote(note);
        verificationCase = caseRepository.save(verificationCase);

        publish(EVENT_DECIDED, ctx, verificationCase);
        log.info("facility verification case {} decided {} by {}",
                verificationCase.getCaseRef(), target, ctx.actorId());

        // A decision refreshes the trust dimensions (EXISTENCE/GEOSPATIAL read
        // the decided cases — see FacilityTrustDimensionService).
        trustDimensionService.recompute(ctx.tenantId(), verificationCase.getFacilityUuid());
        return verificationCase;
    }

    @Transactional(readOnly = true)
    public List<FacilityVerificationCaseEntity> listForFacility(UUID facilityUuid) {
        TrustContext ctx = TrustContextHolder.require();
        requireFacility(facilityUuid, ctx.tenantId());
        return caseRepository.findByTenantIdAndFacilityUuidOrderByCreatedAtDesc(
                ctx.tenantId(), facilityUuid);
    }

    static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusMeters = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return earthRadiusMeters * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
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

    private void publish(String eventType, TrustContext ctx, FacilityVerificationCaseEntity c) {
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("caseRef", c.getCaseRef());
        payload.put("facilityUuid", c.getFacilityUuid().toString());
        payload.put("verificationType", c.getVerificationType());
        payload.put("status", c.getStatus());
        payload.put("gpsDistanceMeters", c.getGpsDistanceMeters());

        String correlationId = ctx.correlationId() != null ? ctx.correlationId().toString() : null;
        zw.gov.mohcc.impilo.tuso.persistence.entity.EventOutboxEntity outbox =
                new zw.gov.mohcc.impilo.tuso.persistence.entity.EventOutboxEntity();
        outbox.setAggregateType("FACILITY_VERIFICATION_CASE");
        outbox.setAggregateId(c.getCaseRef());
        outbox.setEventType(eventType);
        outbox.setPayload(payload);
        outbox.setTenantId(ctx.tenantId() != null ? ctx.tenantId().toString() : null);
        outbox.setCorrelationId(correlationId);
        outbox.setCausationId(correlationId);
        outbox.setProducer("tuso");
        outbox.setSubjectType("FacilityVerificationCase");
        outbox.setSubjectId(c.getCaseRef());
        outbox.setPartitionKey(c.getFacilityUuid().toString());
        outbox.setSchemaVersion(1);
        outbox.setOccurredAt(Instant.now().atOffset(java.time.ZoneOffset.UTC));
        outboxRepository.save(outbox);
    }

    private String toJson(List<String> refs) {
        try {
            return refs == null ? null : objectMapper.writeValueAsString(refs);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise evidence refs", e);
        }
    }
}
