package zw.gov.mohcc.impilo.tuso.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityLegitimacySource;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityLegitimacyStatus;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilitySourceLegitimacyEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilitySourceLegitimacyRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Issues the Ministry legitimacy verdict (FCV-W1) — the one governed act that may grant a facility
 * platform operation.
 *
 * <p>Both importers unconditionally stamp {@code PLATFORM_OPERATIONAL / PENDING_VERIFICATION /
 * allowed=false}, so nothing that merely loads or claims a facility can make it operational. This
 * service is what lifts that veto, and it records the whole basis for doing so: who decided, under
 * which appointment and jurisdiction, on what evidence, with what conditions, until when.</p>
 *
 * <p><b>The decision is the truth; the legitimacy rows are its projection.</b> Those rows carry
 * {@code decision_id} so the import paths skip them (V044) — otherwise the next import would quietly
 * re-stamp the facility back to unverified.</p>
 *
 * <p><b>A facility cannot legitimise itself.</b> Authority is an ACTIVE MoHCC verifier appointment
 * whose jurisdiction covers this facility, resolved from org-registry. Holding a facility
 * administrator claim grants nothing here, and neither does any request header.</p>
 */
@Service
public class FacilityLegitimacyDecisionService {

    private static final Logger log = LoggerFactory.getLogger(FacilityLegitimacyDecisionService.class);

    public static final String EVENT_ISSUED = "tuso.facility.legitimacy_decision.issued";

    private final JdbcTemplate jdbc;
    private final FacilityRepository facilityRepository;
    private final FacilitySourceLegitimacyRepository legitimacyRepository;
    private final MinistryAuthorityService ministryAuthority;
    private final ObjectMapper objectMapper;

    public FacilityLegitimacyDecisionService(JdbcTemplate jdbc,
                                             FacilityRepository facilityRepository,
                                             FacilitySourceLegitimacyRepository legitimacyRepository,
                                             MinistryAuthorityService ministryAuthority,
                                             ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.facilityRepository = facilityRepository;
        this.legitimacyRepository = legitimacyRepository;
        this.ministryAuthority = ministryAuthority;
        this.objectMapper = objectMapper;
    }

    /** What a verifier submits. */
    public record IssueDecisionRequest(
            String verdict,
            String disposition,
            UUID supersededFacilityUuid,
            String ministryAuthority,
            LocalDate effectiveDate,
            List<String> evidenceRefs,
            String mflSourceRef,
            String inspectionRef,
            String verificationCaseRef,
            String reason,
            String conditions,
            String restrictions,
            LocalDate reviewDate,
            LocalDate expiresAt) {}

    /**
     * The outcome, including what the verdict actually changed.
     *
     * <p>{@code platformAccessAllowed} is re-read <em>after</em> projecting, because recording a
     * decision and the facility becoming operational are not the same thing: a surviving deny from
     * another authority (an expired HPA licence, say) still vetoes. Returning a bare 200 would let a
     * verifier believe they had opened a facility that is still closed, so the blocking reason comes
     * back with it.</p>
     */
    public record DecisionOutcome(UUID decisionId, String verdict, boolean platformAccessAllowed,
                                  List<String> reasons) {}

    @Transactional
    public DecisionOutcome issue(UUID facilityUuid, IssueDecisionRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        FacilityEntity facility = requireFacility(facilityUuid, ctx.tenantId());

        String verdict = normalise(request == null ? null : request.verdict());
        if (verdict.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "verdict is required");
        }
        if (request.reason() == null || request.reason().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A reason is required — a legitimacy decision without a stated basis is not auditable.");
        }

        // Authority: an ACTIVE Ministry verifier appointment covering THIS facility. Never a header,
        // never a facility administrator claim.
        MinistryAuthorityService.Authority authority = ministryAuthority
                .authorityOver(ctx.actorId(), facility, MinistryAuthorityService.VERIFIER_ROLES)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Issuing a Ministry legitimacy verdict requires an active Ministry verifier "
                                + "appointment whose jurisdiction covers this facility."));

        UUID decisionId = UUID.randomUUID();
        LocalDate effective = request.effectiveDate() != null ? request.effectiveDate() : LocalDate.now();

        // Supersede the standing decision first: the partial-unique index allows exactly one current
        // decision per facility, and the history is append-only.
        jdbc.update("UPDATE tuso.facility_legitimacy_decision SET superseded_by=?, superseded_at=now() "
                        + "WHERE facility_uuid=? AND superseded_by IS NULL",
                decisionId, facilityUuid);

        jdbc.update("INSERT INTO tuso.facility_legitimacy_decision ("
                        + "id, tenant_id, facility_uuid, verdict, disposition, superseded_facility_uuid, "
                        + "decided_by, decided_by_role, jurisdiction_code, ministry_authority, "
                        + "decision_date, effective_date, evidence_refs, mfl_source_ref, inspection_ref, "
                        + "verification_case_ref, reason, conditions, restrictions, review_date, expires_at, "
                        + "correlation_id) VALUES (?,?,?,?,?,?,?,?,?,?,CURRENT_DATE,?,?::jsonb,?,?,?,?,?,?,?,?,?)",
                decisionId, ctx.tenantId(), facilityUuid, verdict,
                blankToNull(request.disposition()), request.supersededFacilityUuid(),
                ctx.actorId(), authority.roleCode(), authority.jurisdictionCode(),
                blankToNull(request.ministryAuthority()), effective, toJson(request.evidenceRefs()),
                blankToNull(request.mflSourceRef()), blankToNull(request.inspectionRef()),
                blankToNull(request.verificationCaseRef()), request.reason().trim(),
                blankToNull(request.conditions()), blankToNull(request.restrictions()),
                request.reviewDate(), request.expiresAt(),
                ctx.correlationId() == null ? null : ctx.correlationId().toString());

        project(facility, decisionId, verdict, request, ctx);
        audit(facility, decisionId, verdict, authority, request, ctx);

        List<String> reasons = new ArrayList<>();
        boolean allowed = FacilitySourceLegitimacyService.verdictOf(
                legitimacyRepository.findByFacilityIdOrderBySourceAsc(facilityUuid))
                == FacilitySourceLegitimacyService.PlatformAccessVerdict.ALLOW;
        if (!allowed) {
            legitimacyRepository.findByFacilityIdOrderBySourceAsc(facilityUuid).stream()
                    .filter(r -> !r.isAllowedOnPlatform())
                    .forEach(r -> reasons.add("Recorded, but platform access remains denied — "
                            + r.getSource() + ": " + r.getStatus()));
        }

        log.info("Facility {} legitimacy decision {} verdict={} by {} ({}/{}) -> platformAccessAllowed={}",
                facilityUuid, decisionId, verdict, ctx.actorId(), authority.roleCode(),
                authority.jurisdictionCode(), allowed);
        return new DecisionOutcome(decisionId, verdict, allowed, reasons);
    }

    /**
     * Project the verdict onto the two legitimacy rows the veto lattice reads.
     *
     * <p>{@code MINISTRY_OPERATIONAL} carries the Ministry's recognition; {@code PLATFORM_OPERATIONAL}
     * carries whether the platform will let it operate. Only a verdict that affirmatively says the
     * facility is operating lifts the second one — recognition alone never does, which is why a
     * facility the Ministry recognises but has not verified as operating stays closed.</p>
     */
    private void project(FacilityEntity facility, UUID decisionId, String verdict,
                         IssueDecisionRequest request, TrustContext ctx) {
        boolean ministryRecognises;
        FacilityLegitimacyStatus ministryStatus;
        boolean platformAllows;
        FacilityLegitimacyStatus platformStatus;

        switch (verdict) {
            case "LEGITIMATE_OPERATIONAL" -> {
                ministryRecognises = true;  ministryStatus = FacilityLegitimacyStatus.REGISTERED_CURRENT;
                platformAllows = true;      platformStatus = FacilityLegitimacyStatus.REGISTERED_CURRENT;
            }
            case "LEGITIMATE_CONDITIONALLY_OPERATIONAL" -> {
                ministryRecognises = true;  ministryStatus = FacilityLegitimacyStatus.REGISTERED_CURRENT;
                platformAllows = true;      platformStatus = FacilityLegitimacyStatus.GOVERNMENT_OPERATIONAL_EXCEPTION;
            }
            case "LEGITIMATE_NOT_OPERATIONAL" -> {
                // Recognised, and affirmatively found not operating. The lattice cannot express the
                // difference between this and "nobody has looked" — decision_id can, which is why
                // the decision is the record and this row is only an index.
                ministryRecognises = true;  ministryStatus = FacilityLegitimacyStatus.REGISTERED_CURRENT;
                platformAllows = false;     platformStatus = FacilityLegitimacyStatus.PENDING_VERIFICATION;
            }
            case "SUSPENDED" -> {
                ministryRecognises = false; ministryStatus = FacilityLegitimacyStatus.SUSPENDED;
                platformAllows = false;     platformStatus = FacilityLegitimacyStatus.SUSPENDED;
            }
            case "REVOKED", "REJECTED" -> {
                ministryRecognises = false; ministryStatus = FacilityLegitimacyStatus.KNOWN_NOT_COMPLIANT;
                platformAllows = false;     platformStatus = FacilityLegitimacyStatus.KNOWN_NOT_COMPLIANT;
            }
            case "CLOSED" -> {
                ministryRecognises = false; ministryStatus = FacilityLegitimacyStatus.EXPIRED;
                platformAllows = false;     platformStatus = FacilityLegitimacyStatus.EXPIRED;
            }
            case "PENDING_VERIFICATION" -> {
                ministryRecognises = false; ministryStatus = FacilityLegitimacyStatus.PENDING_VERIFICATION;
                platformAllows = false;     platformStatus = FacilityLegitimacyStatus.PENDING_VERIFICATION;
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown verdict: " + verdict);
        }

        String basis = "Ministry legitimacy decision " + decisionId + " (" + verdict + ")"
                + (request.conditions() != null && !request.conditions().isBlank()
                        ? " — conditions: " + request.conditions().trim() : "");

        // PLATFORM_OPERATIONAL first: V044's invariant refuses a Ministry allow with no platform
        // verdict on record, and the ordering makes that hold on a facility that had neither row.
        upsertProjected(facility, FacilityLegitimacySource.PLATFORM_OPERATIONAL, platformStatus,
                platformAllows, decisionId, basis, ctx);
        upsertProjected(facility, FacilityLegitimacySource.MINISTRY_OPERATIONAL, ministryStatus,
                ministryRecognises, decisionId, basis, ctx);
    }

    private void upsertProjected(FacilityEntity facility, FacilityLegitimacySource source,
                                 FacilityLegitimacyStatus status, boolean allowed, UUID decisionId,
                                 String basis, TrustContext ctx) {
        FacilitySourceLegitimacyEntity row = legitimacyRepository
                .findByFacilityIdAndSource(facility.getFacilityUuid(), source)
                .orElseGet(FacilitySourceLegitimacyEntity::new);
        row.setFacilityId(facility.getFacilityUuid());
        row.setSource(source);
        row.setStatus(status);
        row.setAsOf(Instant.now());
        row.setAllowedOnPlatform(allowed);
        row.setEvidenceRef("LEGITIMACY_DECISION:" + decisionId);
        row.setReason(basis);
        row.setRecordedBy(ctx.actorId());
        row.setDecisionId(decisionId);
        legitimacyRepository.save(row);
    }

    /** Governed decisions are audited in the table the regulatory lane already uses. */
    private void audit(FacilityEntity facility, UUID decisionId, String verdict,
                       MinistryAuthorityService.Authority authority, IssueDecisionRequest request,
                       TrustContext ctx) {
        try {
            jdbc.update("INSERT INTO tuso.facility_audit_event ("
                            + "tenant_id, facility_id, actor_id, actor_type, authority_context, action, "
                            + "target_entity_type, target_entity_id, after_state, correlation_id, metadata) "
                            + "VALUES (?,?,?,?,?,?,?,?,?::jsonb,?,?::jsonb)",
                    ctx.tenantId(), facility.getId(), ctx.actorId(), ctx.actorType(),
                    authority.roleCode() + "@" + authority.jurisdictionCode(),
                    "MINISTRY_LEGITIMACY_DECISION", "FACILITY_LEGITIMACY_DECISION", decisionId.toString(),
                    toJson(Map.of("verdict", verdict, "reason", request.reason())),
                    ctx.correlationId() == null ? null : ctx.correlationId().toString(),
                    toJson(Map.of("jurisdiction", authority.jurisdictionCode())));
        } catch (Exception e) {
            // An unauditable decision is not acceptable — fail the transaction rather than record
            // authority silently.
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not write the audit record for this decision; the decision was not made.", e);
        }
    }

    /** The standing decision for a facility, or empty. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> history(UUID facilityUuid) {
        TrustContext ctx = TrustContextHolder.require();
        requireFacility(facilityUuid, ctx.tenantId());
        return jdbc.queryForList(
                "SELECT id, verdict, disposition, decided_by, decided_by_role, jurisdiction_code, "
                        + "decision_date, effective_date, reason, conditions, restrictions, review_date, "
                        + "expires_at, superseded_by, created_at "
                        + "FROM tuso.facility_legitimacy_decision WHERE facility_uuid=? AND tenant_id=? "
                        + "ORDER BY created_at DESC", facilityUuid, ctx.tenantId());
    }

    private FacilityEntity requireFacility(UUID facilityUuid, UUID tenantId) {
        FacilityEntity facility = facilityRepository.findByFacilityUuid(facilityUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Facility not found: " + facilityUuid));
        if (facility.getTenantId() != null && !facility.getTenantId().equals(tenantId)) {
            throw new SecurityException("Facility belongs to another tenant");
        }
        return facility;
    }

    private static String normalise(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o == null ? List.of() : o);
        } catch (Exception e) {
            return "[]";
        }
    }
}
