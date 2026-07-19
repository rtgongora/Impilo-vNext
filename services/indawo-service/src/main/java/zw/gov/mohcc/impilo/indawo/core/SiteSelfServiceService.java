package zw.gov.mohcc.impilo.indawo.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.indawo.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.indawo.domain.SiteEntity;
import zw.gov.mohcc.impilo.indawo.domain.SiteLifecycleStatus;
import zw.gov.mohcc.impilo.indawo.domain.SiteOperatorGrantEntity;
import zw.gov.mohcc.impilo.indawo.domain.SiteRegistrationCaseEntity;
import zw.gov.mohcc.impilo.indawo.repository.OutboxEventRepository;
import zw.gov.mohcc.impilo.indawo.repository.SiteOperatorGrantRepository;
import zw.gov.mohcc.impilo.indawo.repository.SiteRegistrationCaseRepository;
import zw.gov.mohcc.impilo.indawo.repository.SiteRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * SJ1/SJ2 self-service journeys (D-L4/D-L5) — the Indawo mirror of the
 * hardened Tuso rails.
 *
 * <ul>
 *   <li><b>Operator claim (SJ1):</b> role-scoped, evidence-backed, expiring
 *       PERSON grants; per-role ACTIVE uniqueness; the claimant is bound by
 *       the trusted BFF caller; the grant vocabulary can never touch
 *       inspection findings or regulator decisions.</li>
 *   <li><b>Registration (SJ2):</b> silent duplicate matching (exact code +
 *       category/district-scoped normalised name); ONE generic registrant
 *       receipt; credible match silently blocks into steward review; else a
 *       DRAFT provisional site is created — never operational from a form.</li>
 * </ul>
 */
@Service
public class SiteSelfServiceService {

    private static final Logger log = LoggerFactory.getLogger(SiteSelfServiceService.class);

    private final SiteRepository siteRepository;
    private final SiteOperatorGrantRepository grantRepository;
    private final SiteRegistrationCaseRepository caseRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public SiteSelfServiceService(SiteRepository siteRepository,
                                  SiteOperatorGrantRepository grantRepository,
                                  SiteRegistrationCaseRepository caseRepository,
                                  OutboxEventRepository outboxRepository,
                                  ObjectMapper objectMapper) {
        this.siteRepository = siteRepository;
        this.grantRepository = grantRepository;
        this.caseRepository = caseRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    // ── SJ1: operator claim ────────────────────────────────────────────────────

    public record SubmitGrantClaim(String personHealthId, String role, String claimType,
                                   String evidenceRef, LocalDate validFrom, LocalDate validTo,
                                   String notes) {}

    @Transactional
    public SiteOperatorGrantEntity submitGrantClaim(UUID siteId, SubmitGrantClaim request) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        SiteEntity site = siteRepository.findById(siteId)
                .filter(s -> tenantId.equals(s.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("Site not found"));

        require(request.personHealthId(), "personHealthId");
        // Knowing a site's name/code/permit grants nothing (D-L4).
        require(request.evidenceRef(), "evidenceRef");
        String role = request.role() == null ? "" : request.role().trim().toUpperCase(Locale.ROOT);
        if (!SiteOperatorGrantEntity.ROLE_SCOPES.contains(role)) {
            throw new IllegalArgumentException("Unknown site-management role: " + request.role());
        }
        if (grantRepository.existsByTenantIdAndSiteIdAndPersonHealthIdAndRoleAndApprovalState(
                tenantId, site.getSiteId(), request.personHealthId().trim(), role,
                SiteOperatorGrantEntity.STATE_ACTIVE)) {
            throw new IllegalStateException("You already hold this role at this site.");
        }

        SiteOperatorGrantEntity grant = new SiteOperatorGrantEntity();
        grant.setTenantId(tenantId);
        grant.setSiteId(site.getSiteId());
        grant.setPersonHealthId(request.personHealthId().trim());
        grant.setRole(role);
        grant.setClaimType(SiteOperatorGrantEntity.CLAIM_TYPE_RECOVERY
                .equalsIgnoreCase(request.claimType())
                ? SiteOperatorGrantEntity.CLAIM_TYPE_RECOVERY
                : SiteOperatorGrantEntity.CLAIM_TYPE_NEW);
        grant.setEvidenceRef(request.evidenceRef().trim());
        grant.setValidFrom(request.validFrom());
        grant.setValidTo(request.validTo());
        grant.setNotes(request.notes());
        grant = grantRepository.save(grant);

        publish("indawo.site.operator_grant.submitted", tenantId, ctx, site.getSiteId(), grant);
        log.info("site operator grant {} submitted PENDING (site {}, role {})",
                grant.getId(), site.getSiteId(), role);
        return grant;
    }

    @Transactional
    public SiteOperatorGrantEntity approveGrant(Long grantId, String approvedBy) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        SiteOperatorGrantEntity grant = grantRepository.findByIdAndTenantId(grantId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Grant not found: " + grantId));
        if (SiteOperatorGrantEntity.STATE_ACTIVE.equals(grant.getApprovalState())) {
            return grant;
        }
        if (!SiteOperatorGrantEntity.STATE_PENDING.equals(grant.getApprovalState())) {
            throw new IllegalStateException("Only a PENDING grant can be approved; state is "
                    + grant.getApprovalState());
        }
        grant.setApprovalState(SiteOperatorGrantEntity.STATE_ACTIVE);
        grant.setAppointedBy(approvedBy);
        grant = grantRepository.save(grant);
        publish("indawo.site.operator_grant.approved", tenantId, ctx, grant.getSiteId(), grant);
        return grant;
    }

    @Transactional(readOnly = true)
    public List<SiteOperatorGrantEntity> listGrantsByState(String state) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        String normalized = state == null ? "" : state.trim().toUpperCase(Locale.ROOT);
        if (!List.of(SiteOperatorGrantEntity.STATE_PENDING, SiteOperatorGrantEntity.STATE_ACTIVE,
                SiteOperatorGrantEntity.STATE_REJECTED, SiteOperatorGrantEntity.STATE_REVOKED,
                SiteOperatorGrantEntity.STATE_EXPIRED).contains(normalized)) {
            throw new IllegalArgumentException("Unknown grant state: " + state);
        }
        return grantRepository.findByTenantIdAndApprovalStateOrderByCreatedAtDesc(tenantId, normalized);
    }

    /** Enforced expiry (mirror of the tuso appointment sweep). */
    @Scheduled(fixedDelayString = "${indawo.grant-sweep.interval-ms:3600000}")
    public void sweepExpiredGrants() {
        int expired = expireLapsedGrants(LocalDate.now());
        if (expired > 0) {
            log.info("site operator grant sweep: {} expired", expired);
        }
    }

    @Transactional
    public int expireLapsedGrants(LocalDate asOf) {
        List<SiteOperatorGrantEntity> lapsed = grantRepository
                .findByApprovalStateAndValidToBefore(SiteOperatorGrantEntity.STATE_ACTIVE, asOf);
        for (SiteOperatorGrantEntity grant : lapsed) {
            grant.setApprovalState(SiteOperatorGrantEntity.STATE_EXPIRED);
            grantRepository.save(grant);
        }
        return lapsed.size();
    }

    // ── SJ2: registration with silent duplicate matching ──────────────────────

    public record RegisterSiteRequest(String personHealthId, String name, String type,
                                      String siteCategory, String province, String district,
                                      String siteCode, String evidenceRef, String notes) {}

    public record RegistrationReceipt(String caseRef, String status, String message) {}

    @Transactional
    public RegistrationReceipt registerSite(RegisterSiteRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        require(request.personHealthId(), "personHealthId");
        require(request.name(), "name");
        require(request.type(), "type");
        require(request.evidenceRef(), "evidenceRef");

        // Silent matching: exact code, then category/district-scoped normalised name.
        List<Map<String, Object>> candidates = new ArrayList<>();
        if (request.siteCode() != null && !request.siteCode().isBlank()) {
            siteRepository.findFirstByTenantIdAndSiteCodeIgnoreCase(tenantId, request.siteCode().trim())
                    .ifPresent(s -> candidates.add(candidate(s, "SITE_CODE", 1.0)));
        }
        String normalisedName = request.name().trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        siteRepository.findByNormalisedName(tenantId, normalisedName,
                        lowerOrNull(request.siteCategory()), lowerOrNull(request.district()))
                .forEach(s -> candidates.add(candidate(s, "NAME_CATEGORY_DISTRICT", 1.0)));

        SiteRegistrationCaseEntity registrationCase = new SiteRegistrationCaseEntity();
        registrationCase.setTenantId(tenantId);
        registrationCase.setCaseRef("SRC-" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT));
        registrationCase.setApplicantHealthId(request.personHealthId().trim());
        registrationCase.setSubmittedName(request.name().trim());
        registrationCase.setSiteCategory(request.siteCategory());
        registrationCase.setSubmittedPayload(toJson(request));
        registrationCase.setEvidenceRef(request.evidenceRef().trim());

        if (!candidates.isEmpty()) {
            registrationCase.setOutcome(SiteRegistrationCaseEntity.OUTCOME_DUPLICATE_REVIEW);
            registrationCase.setMatchedSiteId(UUID.fromString(
                    String.valueOf(candidates.get(0).get("siteId"))));
            registrationCase.setMatchContext(toJson(Map.of("candidates", candidates)));
            log.info("site registration silently blocked on credible match — case {} to steward review",
                    registrationCase.getCaseRef());
        } else {
            SiteEntity provisional = new SiteEntity(
                    UUID.randomUUID(), tenantId, request.name().trim(), request.type().trim());
            provisional.setSiteCategory(request.siteCategory());
            provisional.setProvince(request.province());
            provisional.setDistrict(request.district());
            provisional.setSiteCode("PRS-" + UUID.randomUUID().toString()
                    .replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT));
            provisional.setLifecycleStatus(SiteLifecycleStatus.DRAFT.name());
            provisional = siteRepository.save(provisional);
            registrationCase.setOutcome(SiteRegistrationCaseEntity.OUTCOME_PROVISIONAL_CREATED);
            registrationCase.setProvisionalSiteId(provisional.getSiteId());
            log.info("provisional DRAFT site {} created for registration case {}",
                    provisional.getSiteId(), registrationCase.getCaseRef());
        }

        registrationCase = caseRepository.save(registrationCase);
        publishCase(ctx, tenantId, registrationCase);

        // ONE shape, both paths — the anti-enumeration boundary.
        return new RegistrationReceipt(registrationCase.getCaseRef(), "RECEIVED",
                "Your site registration has been received and will be reviewed. "
                        + "You will be notified of the next verification step.");
    }

    @Transactional(readOnly = true)
    public List<SiteRegistrationCaseEntity> listCasesByOutcome(String outcome) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        String normalized = outcome == null ? "" : outcome.trim().toUpperCase(Locale.ROOT);
        if (!List.of(SiteRegistrationCaseEntity.OUTCOME_DUPLICATE_REVIEW,
                SiteRegistrationCaseEntity.OUTCOME_PROVISIONAL_CREATED,
                SiteRegistrationCaseEntity.OUTCOME_MATCHED_EXISTING,
                SiteRegistrationCaseEntity.OUTCOME_REJECTED,
                SiteRegistrationCaseEntity.OUTCOME_CONFIRMED).contains(normalized)) {
            throw new IllegalArgumentException("Unknown case outcome: " + outcome);
        }
        return caseRepository.findByTenantIdAndOutcomeOrderByCreatedAtDesc(tenantId, normalized);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static Map<String, Object> candidate(SiteEntity site, String matchType, double score) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("siteId", site.getSiteId().toString());
        m.put("matchType", matchType);
        m.put("score", score);
        return m;
    }

    private static String lowerOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private void publish(String eventType, UUID tenantId, RequestContext ctx,
                         UUID siteId, SiteOperatorGrantEntity grant) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("grantId", grant.getId());
        payload.put("siteId", siteId.toString());
        payload.put("personHealthId", grant.getPersonHealthId());
        payload.put("role", grant.getRole());
        payload.put("approvalState", grant.getApprovalState());
        appendOutbox(eventType, "SITE_OPERATOR_GRANT", String.valueOf(grant.getId()),
                tenantId, ctx, payload, siteId.toString());
    }

    private void publishCase(RequestContext ctx, UUID tenantId, SiteRegistrationCaseEntity c) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("caseRef", c.getCaseRef());
        payload.put("outcome", c.getOutcome());
        payload.put("applicantHealthId", c.getApplicantHealthId());
        appendOutbox("indawo.site.registration_case.submitted", "SITE_REGISTRATION_CASE",
                c.getCaseRef(), tenantId, ctx, payload, c.getCaseRef());
    }

    private void appendOutbox(String eventType, String aggregateType, String aggregateId,
                              UUID tenantId, RequestContext ctx, Map<String, Object> payload,
                              String partitionKey) {
        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setSchemaVersion("1");
        outbox.setIdempotencyKey(eventType + ":" + aggregateId);
        outbox.setProducer("indawo-service");
        outbox.setTenantId(tenantId);
        outbox.setPodId(ctx.podId() != null ? ctx.podId() : "national-spine");
        outbox.setSubjectId(aggregateId);
        outbox.setSubjectType(aggregateType);
        outbox.setOccurredAt(OffsetDateTime.now());
        outbox.setPayloadJson(toJson(payload));
        outbox.setPartitionKey(partitionKey);
        outboxRepository.save(outbox);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise payload", e);
        }
    }
}
