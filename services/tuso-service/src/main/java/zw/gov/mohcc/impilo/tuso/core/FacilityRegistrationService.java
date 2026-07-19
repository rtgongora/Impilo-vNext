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
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityRegistrationCaseEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityRegulatoryStatus;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRegistrationCaseRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * FJ2 — register a new facility with silent duplicate detection (D-L5).
 *
 * <p>The registrant receives ONE generic outcome shape regardless of what the
 * matcher found: a credible match silently blocks creation and opens a
 * DUPLICATE_REVIEW steward case (candidates recorded in steward-only match
 * context); no credible match creates a PROVISIONAL facility — status
 * {@value #STATUS_PROVISIONAL}, regulatory DRAFT, never publicly discoverable
 * and never operational merely because a form was completed. Confirmation,
 * classification, verification and publication are steward/regulator work
 * (FJ3/FJ4 + the T2 review flow).</p>
 */
@Service
public class FacilityRegistrationService {

    public static final String STATUS_PROVISIONAL = "PROVISIONAL";
    public static final String EVENT_SUBMITTED = "tuso.facility.registration_case.submitted";

    private static final Logger log = LoggerFactory.getLogger(FacilityRegistrationService.class);

    private final FacilityRepository facilityRepository;
    private final FacilityRegistrationCaseRepository caseRepository;
    private final FacilityMatchService matchService;
    private final zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public FacilityRegistrationService(
            FacilityRepository facilityRepository,
            FacilityRegistrationCaseRepository caseRepository,
            FacilityMatchService matchService,
            zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository outboxRepository,
            ObjectMapper objectMapper) {
        this.facilityRepository = facilityRepository;
        this.caseRepository = caseRepository;
        this.matchService = matchService;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public record RegisterFacilityRequest(
            String personHealthId,
            String name,
            String facilityType,
            String province,
            String district,
            String ownershipCategory,
            BigDecimal latitude,
            BigDecimal longitude,
            Map<String, String> identifiers,
            String evidenceRef,
            String notes) {}

    /** The registrant-facing view — identical shape for every outcome. */
    public record RegistrationReceipt(String caseRef, String status, String message) {}

    @Transactional
    public RegistrationReceipt register(RegisterFacilityRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        require(request.personHealthId(), "personHealthId");
        require(request.name(), "name");
        require(request.facilityType(), "facilityType");
        require(request.province(), "province");
        require(request.district(), "district");
        // Proof of authority/operation is mandatory (D-L4): a web form alone
        // never creates a facility.
        require(request.evidenceRef(), "evidenceRef");

        FacilityMatchService.MatchResult match = matchService.matchForRegistration(
                ctx.tenantId(), request.name(), null, request.identifiers(), request.district());

        FacilityRegistrationCaseEntity registrationCase = new FacilityRegistrationCaseEntity();
        registrationCase.setTenantId(ctx.tenantId());
        registrationCase.setCaseRef("FRC-" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT));
        registrationCase.setApplicantHealthId(request.personHealthId().trim());
        registrationCase.setSubmittedName(request.name().trim());
        registrationCase.setSubmittedPayload(toJson(request));
        registrationCase.setEvidenceRef(request.evidenceRef().trim());

        if (match.credibleMatch()) {
            // SILENT BLOCK (D-L5): no facility is created; the steward sees the
            // candidates; the registrant sees the same "received" shape as
            // everyone else and learns nothing about existing records.
            registrationCase.setOutcome(FacilityRegistrationCaseEntity.OUTCOME_DUPLICATE_REVIEW);
            registrationCase.setMatchedFacilityUuid(
                    match.candidates().get(0).facility().getFacilityUuid());
            registrationCase.setMatchContext(matchContextJson(match));
            log.info("facility registration silently blocked on credible match — case {} routed to steward",
                    registrationCase.getCaseRef());
        } else {
            FacilityEntity provisional = new FacilityEntity();
            provisional.setTenantId(ctx.tenantId());
            provisional.setName(request.name().trim());
            provisional.setFacilityCode("PRV-" + UUID.randomUUID().toString()
                    .replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT));
            provisional.setFacilityType(request.facilityType().trim());
            provisional.setProvince(request.province().trim());
            provisional.setDistrict(request.district().trim());
            provisional.setLatitude(request.latitude());
            provisional.setLongitude(request.longitude());
            provisional.setStatus(STATUS_PROVISIONAL);
            provisional.setRegulatoryStatus(FacilityRegulatoryStatus.DRAFT);
            provisional = facilityRepository.save(provisional);

            registrationCase.setOutcome(FacilityRegistrationCaseEntity.OUTCOME_PROVISIONAL_CREATED);
            registrationCase.setProvisionalFacilityUuid(provisional.getFacilityUuid());
            log.info("provisional facility {} created for registration case {}",
                    provisional.getFacilityUuid(), registrationCase.getCaseRef());
        }

        registrationCase = caseRepository.save(registrationCase);
        publishSubmitted(ctx, registrationCase);

        // ONE shape, both paths — the anti-enumeration boundary.
        return new RegistrationReceipt(registrationCase.getCaseRef(), "RECEIVED",
                "Your facility registration has been received and will be reviewed. "
                        + "You will be notified of the next verification step.");
    }

    @Transactional(readOnly = true)
    public List<FacilityRegistrationCaseEntity> listByOutcome(String outcome) {
        TrustContext ctx = TrustContextHolder.require();
        String normalized = outcome == null ? "" : outcome.trim().toUpperCase(Locale.ROOT);
        if (!List.of(FacilityRegistrationCaseEntity.OUTCOME_DUPLICATE_REVIEW,
                FacilityRegistrationCaseEntity.OUTCOME_PROVISIONAL_CREATED,
                FacilityRegistrationCaseEntity.OUTCOME_MATCHED_EXISTING,
                FacilityRegistrationCaseEntity.OUTCOME_REJECTED,
                FacilityRegistrationCaseEntity.OUTCOME_CONFIRMED).contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown registration-case outcome: " + outcome);
        }
        return caseRepository.findByTenantIdAndOutcomeOrderByCreatedAtDesc(ctx.tenantId(), normalized);
    }

    private String matchContextJson(FacilityMatchService.MatchResult match) {
        List<Map<String, Object>> candidates = match.candidates().stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("facilityUuid", c.facility().getFacilityUuid().toString());
            m.put("matchType", c.matchType());
            m.put("score", c.score());
            return m;
        }).toList();
        try {
            return objectMapper.writeValueAsString(Map.of("candidates", candidates));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise match context", e);
        }
    }

    private void publishSubmitted(TrustContext ctx, FacilityRegistrationCaseEntity registrationCase) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("caseRef", registrationCase.getCaseRef());
        payload.put("outcome", registrationCase.getOutcome());
        payload.put("applicantHealthId", registrationCase.getApplicantHealthId());
        payload.put("provisionalFacilityUuid", registrationCase.getProvisionalFacilityUuid() != null
                ? registrationCase.getProvisionalFacilityUuid().toString() : null);

        String correlationId = ctx.correlationId() != null ? ctx.correlationId().toString() : null;
        zw.gov.mohcc.impilo.tuso.persistence.entity.EventOutboxEntity outbox =
                new zw.gov.mohcc.impilo.tuso.persistence.entity.EventOutboxEntity();
        outbox.setAggregateType("FACILITY_REGISTRATION_CASE");
        outbox.setAggregateId(registrationCase.getCaseRef());
        outbox.setEventType(EVENT_SUBMITTED);
        outbox.setPayload(payload);
        outbox.setTenantId(ctx.tenantId() != null ? ctx.tenantId().toString() : null);
        outbox.setCorrelationId(correlationId);
        outbox.setCausationId(correlationId);
        outbox.setProducer("tuso");
        outbox.setSubjectType("FacilityRegistrationCase");
        outbox.setSubjectId(registrationCase.getCaseRef());
        outbox.setPartitionKey(registrationCase.getCaseRef());
        outbox.setSchemaVersion(1);
        outbox.setOccurredAt(java.time.Instant.now().atOffset(java.time.ZoneOffset.UTC));
        outboxRepository.save(outbox);
    }

    private String toJson(RegisterFacilityRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise registration payload", e);
        }
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
    }
}
