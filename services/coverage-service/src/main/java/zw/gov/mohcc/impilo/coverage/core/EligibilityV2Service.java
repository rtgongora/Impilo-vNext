package zw.gov.mohcc.impilo.coverage.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.coverage.api.dto.EligibilityV2Dtos.BenefitLine;
import zw.gov.mohcc.impilo.coverage.api.dto.EligibilityV2Dtos.EligibilityV2Request;
import zw.gov.mohcc.impilo.coverage.api.dto.EligibilityV2Dtos.EligibilityV2Response;
import zw.gov.mohcc.impilo.coverage.core.BenefitAccumulatorService.BenefitPosition;
import zw.gov.mohcc.impilo.coverage.domain.CoveragePlanEntity;
import zw.gov.mohcc.impilo.coverage.domain.EligibilityCheckEntity;
import zw.gov.mohcc.impilo.coverage.domain.MemberCoverageEntity;
import zw.gov.mohcc.impilo.coverage.repository.CoveragePlanRepository;
import zw.gov.mohcc.impilo.coverage.repository.EligibilityCheckRepository;
import zw.gov.mohcc.impilo.coverage.repository.MemberCoverageRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Eligibility v2 (spec §12.2): a point-in-time, explainable coverage decision carrying
 * benefit position, co-payment, referral/authorisation flags, reason codes, the ruleset
 * version used, and a validity period — optionally with a signed offline token (§12.3).
 *
 * <p>Rules are evaluated in-service but versioned + explainable this wave; migration into
 * rules-service is Wave 2. Every decision is persisted on cv_eligibility_checks.
 */
@Service
public class EligibilityV2Service {

    /** Bump when the in-service eligibility logic changes (recorded on every decision). */
    public static final String RULESET_VERSION = "ruvimbo-eligibility-v1";
    private static final long TOKEN_TTL_SECONDS = 24 * 3600;
    private static final long VALIDITY_SECONDS = 24 * 3600;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MemberCoverageRepository memberRepository;
    private final CoveragePlanRepository planRepository;
    private final EligibilityCheckRepository eligibilityRepository;
    private final BenefitAccumulatorService accumulatorService;
    private final EligibilityTokenService tokenService;
    private final CoverageEventService eventService;

    public EligibilityV2Service(MemberCoverageRepository memberRepository,
                                CoveragePlanRepository planRepository,
                                EligibilityCheckRepository eligibilityRepository,
                                BenefitAccumulatorService accumulatorService,
                                EligibilityTokenService tokenService,
                                CoverageEventService eventService) {
        this.memberRepository = memberRepository;
        this.planRepository = planRepository;
        this.eligibilityRepository = eligibilityRepository;
        this.accumulatorService = accumulatorService;
        this.tokenService = tokenService;
        this.eventService = eventService;
    }

    @Transactional
    public EligibilityV2Response check(UUID tenantId, String podId, EligibilityV2Request req, UUID correlationId) {
        MemberCoverageEntity member = memberRepository.findByIdAndTenantId(req.coverageId(), tenantId).orElse(null);
        List<String> reasons = new ArrayList<>();

        if (member == null) {
            return persistAndRespond(tenantId, podId, req, null, null, "INELIGIBLE",
                    List.of("COVERAGE_NOT_FOUND"), "NOT_APPLICABLE", false, false, List.of(), correlationId);
        }

        CoveragePlanEntity plan = planRepository.findById(member.getPlanId()).orElse(null);
        UUID planVersionId = plan != null ? plan.getPlanVersionId() : null;

        // Membership status gate (spec §10.2 statuses).
        String status;
        boolean coverageActive = isActiveForService(member);
        if (!coverageActive) {
            status = "INELIGIBLE";
            reasons.add(membershipReason(member));
        } else {
            status = "ELIGIBLE";
            reasons.add("COVERAGE_ACTIVE");
        }
        if (!"VERIFIED".equals(member.getVerificationStatus())) {
            // Progressive certainty (§3.6): unverified coverage may proceed but is flagged.
            reasons.add("VERIFICATION_" + member.getVerificationStatus());
            if ("ELIGIBLE".equals(status)) status = "PENDING";
        }

        // Benefit position (filtered to the requested benefit/service where given).
        List<BenefitLine> benefits = new ArrayList<>();
        boolean referralRequired = false;
        boolean authorisationRequired = false;
        String networkStatus = "NOT_APPLICABLE";
        String wantCode = req.benefitCode() != null ? req.benefitCode()
                : req.serviceCode() != null ? req.serviceCode() : null;

        if (planVersionId != null) {
            List<BenefitPosition> positions = accumulatorService.positionsForMember(tenantId, member.getId(), planVersionId);
            for (BenefitPosition p : positions) {
                if (wantCode != null && !p.benefitCode().equalsIgnoreCase(wantCode)) continue;
                benefits.add(toLine(p));
                referralRequired = referralRequired || p.requiresReferral();
                authorisationRequired = authorisationRequired || p.requiresAuthorisation();
                networkStatus = mergeNetwork(networkStatus, networkStatusFor(p.networkRequirement(), req.facilityId()));
                // Partial eligibility when a specific benefit is exhausted.
                if (p.remaining() != null && p.remaining().signum() <= 0 && "ELIGIBLE".equals(status)) {
                    status = "PARTIALLY_ELIGIBLE";
                    reasons.add("BENEFIT_LIMIT_EXHAUSTED");
                }
            }
            if (wantCode != null && benefits.isEmpty()) {
                reasons.add("BENEFIT_NOT_COVERED");
                status = "INELIGIBLE".equals(status) ? status : "PARTIALLY_ELIGIBLE";
            }
        } else {
            reasons.add("NO_PLAN_VERSION");
        }
        if (referralRequired) reasons.add("REFERRAL_REQUIRED");
        if (authorisationRequired) reasons.add("AUTHORISATION_REQUIRED");

        return persistAndRespond(tenantId, podId, req, member, plan, status, reasons, networkStatus,
                referralRequired, authorisationRequired, benefits, correlationId);
    }

    private EligibilityV2Response persistAndRespond(UUID tenantId, String podId, EligibilityV2Request req,
                                                    MemberCoverageEntity member, CoveragePlanEntity plan,
                                                    String status, List<String> reasons, String networkStatus,
                                                    boolean referralRequired, boolean authorisationRequired,
                                                    List<BenefitLine> benefits, UUID correlationId) {
        OffsetDateTime validUntil = OffsetDateTime.now().plusSeconds(VALIDITY_SECONDS);
        UUID planVersionId = plan != null ? plan.getPlanVersionId() : null;
        String patientRef = req.patientRef() != null ? req.patientRef()
                : member != null ? member.getClientId() : "unknown";

        // Persist the decision (evidence + reason codes + ruleset version + validity).
        EligibilityCheckEntity check = new EligibilityCheckEntity(tenantId, podId,
                req.coverageId(), patientRef, req.serviceCode(), status, String.join(",", reasons),
                evidenceJson(status, reasons));
        check.setReasonCodes(toJson(reasons));
        check.setRulesetVersion(RULESET_VERSION);
        check.setValidUntil(validUntil);
        check.setNetworkStatus(networkStatus);
        check.setPlanVersionId(planVersionId);
        eligibilityRepository.save(check);

        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("coverage_id", req.coverageId().toString());
        ev.put("status", status);
        ev.put("reason_codes", reasons);
        ev.put("ruleset_version", RULESET_VERSION);
        ev.put("meta", CoverageEventService.meta(correlationId));
        eventService.emitEligibilityChecked(check.getId(), correlationId, tenantId, podId, req.coverageId(), ev);

        // Signed token only for eligible outcomes (spec §12.3).
        String token = null;
        OffsetDateTime tokenExp = null;
        boolean issue = Boolean.TRUE.equals(req.issueToken())
                && ("ELIGIBLE".equals(status) || "PARTIALLY_ELIGIBLE".equals(status));
        if (issue) {
            EligibilityTokenService.IssuedToken t = tokenService.issue(
                    check.getId().toString(), req.coverageId().toString(), patientRef,
                    req.facilityId(), req.benefitCode() != null ? req.benefitCode() : req.serviceCode(),
                    plan != null ? plan.getPayerId() : null, TOKEN_TTL_SECONDS);
            token = t.token();
            tokenExp = OffsetDateTime.ofInstant(t.expiresAt(), java.time.ZoneOffset.UTC);
            Map<String, Object> tp = new LinkedHashMap<>();
            tp.put("eligibility_id", check.getId().toString());
            tp.put("coverage_id", req.coverageId().toString());
            tp.put("expires_at", tokenExp.toString());
            eventService.emitEligibilityTokenIssued(check.getId(), correlationId, tenantId, podId, tp);
        }

        return new EligibilityV2Response(
                check.getId(), status, req.coverageId(),
                member != null ? member.getClientId() : null,
                member != null ? member.getStatus() : null,
                member != null ? member.getVerificationStatus() : null,
                member != null ? member.getPlanId() : null,
                plan != null ? plan.getPlanCode() : null,
                planVersionId, networkStatus, referralRequired, authorisationRequired,
                benefits, reasons, RULESET_VERSION, validUntil, token, tokenExp);
    }

    private static boolean isActiveForService(MemberCoverageEntity m) {
        if (!java.util.Set.of("ACTIVE", "VERIFIED", "GRACE_PERIOD").contains(m.getStatus())) return false;
        LocalDate today = LocalDate.now();
        if (m.getEffectiveFrom() != null && today.isBefore(m.getEffectiveFrom())) return false;
        return m.getEffectiveTo() == null || !today.isAfter(m.getEffectiveTo());
    }

    private static String membershipReason(MemberCoverageEntity m) {
        if (m.getEffectiveTo() != null && m.getEffectiveTo().isBefore(LocalDate.now())) return "COVERAGE_EXPIRED";
        return "COVERAGE_" + m.getStatus();
    }

    private static BenefitLine toLine(BenefitPosition p) {
        return new BenefitLine(p.benefitCode(), p.category(), p.coveragePercent(), p.copay(),
                p.limit(), p.used(), p.reserved(), p.remaining(), p.requiresAuthorisation(),
                p.requiresReferral(), p.networkRequirement());
    }

    /** Wave-1 network status: report the requirement honestly rather than assert a verified match. */
    private static String networkStatusFor(String requirement, String facilityId) {
        if (requirement == null || "ANY".equals(requirement)) return "NOT_APPLICABLE";
        return facilityId != null && !facilityId.isBlank() ? "REQUIRES_IN_NETWORK" : "REQUIRES_IN_NETWORK";
    }

    private static String mergeNetwork(String current, String next) {
        if ("REQUIRES_IN_NETWORK".equals(next)) return next;
        return current;
    }

    private static String toJson(List<String> reasons) {
        try {
            return MAPPER.writeValueAsString(reasons);
        } catch (Exception e) {
            return "[]";
        }
    }

    private static String evidenceJson(String status, List<String> reasons) {
        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("consistency_class", "B");
        ev.put("staleness_source", "local_db_projection");
        ev.put("decision", status);
        ev.put("reason_codes", reasons);
        ev.put("ruleset_version", RULESET_VERSION);
        try {
            return MAPPER.writeValueAsString(ev);
        } catch (Exception e) {
            return "{}";
        }
    }
}
