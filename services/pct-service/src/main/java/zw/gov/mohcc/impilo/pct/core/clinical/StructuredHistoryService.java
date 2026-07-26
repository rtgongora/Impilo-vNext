package zw.gov.mohcc.impilo.pct.core.clinical;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.*;
import zw.gov.mohcc.impilo.pct.persistence.repository.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Structured history — social, family, functional, past procedures and advance directives.
 *
 * <p>The system of record behind {@code /internal/v1/ehr/**}, which the experience layer has always
 * called and pct-service has never answered. Until this existed those endpoints returned a
 * transitional 502 naming this wave.</p>
 *
 * <p>Two properties recur and both are the estate's standing law rather than local choices. A
 * clinical judgement nobody made is never defaulted — an unassessed social risk stays null rather
 * than becoming "low". And an assessment that could not be scored records why instead of a number,
 * because a fabricated zero on a Barthel index reads as total dependence.</p>
 */
@Service
public class StructuredHistoryService {

    private static final Logger log = LoggerFactory.getLogger(StructuredHistoryService.class);

    private final SocialHistoryRepository socialRepository;
    private final FamilyHistoryMemberRepository familyMemberRepository;
    private final FamilyHistoryConditionRepository familyConditionRepository;
    private final FunctionalAssessmentRepository functionalRepository;
    private final PastProcedureRepository procedureRepository;
    private final AdvanceDirectiveRepository directiveRepository;
    private final ClinicalAccessGuard accessGuard;

    public StructuredHistoryService(SocialHistoryRepository socialRepository,
                                    FamilyHistoryMemberRepository familyMemberRepository,
                                    FamilyHistoryConditionRepository familyConditionRepository,
                                    FunctionalAssessmentRepository functionalRepository,
                                    PastProcedureRepository procedureRepository,
                                    AdvanceDirectiveRepository directiveRepository,
                                    ClinicalAccessGuard accessGuard) {
        this.socialRepository = socialRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.familyConditionRepository = familyConditionRepository;
        this.functionalRepository = functionalRepository;
        this.procedureRepository = procedureRepository;
        this.directiveRepository = directiveRepository;
        this.accessGuard = accessGuard;
    }

    private static UUID tenant() { return TrustContextHolder.require().tenantId(); }

    // ── Reads ───────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SocialHistoryEntity> socialHistory(String cpid) {
        return socialRepository.findByTenantIdAndSubjectCpidOrderByCreatedAtDesc(tenant(), cpid);
    }

    @Transactional(readOnly = true)
    public List<FamilyHistoryMemberEntity> familyHistory(String cpid) {
        return familyMemberRepository.findByTenantIdAndSubjectCpidOrderByCreatedAtDesc(tenant(), cpid);
    }

    @Transactional(readOnly = true)
    public List<FamilyHistoryConditionEntity> conditionsOf(UUID memberId) {
        return familyConditionRepository.findByMemberId(memberId);
    }

    @Transactional(readOnly = true)
    public List<FunctionalAssessmentEntity> functionalAssessments(String cpid) {
        return functionalRepository.findByTenantIdAndSubjectCpidOrderByAssessmentDateDesc(tenant(), cpid);
    }

    @Transactional(readOnly = true)
    public List<PastProcedureEntity> pastProcedures(String cpid) {
        return procedureRepository.findByTenantIdAndSubjectCpidOrderByCreatedAtDesc(tenant(), cpid);
    }

    @Transactional(readOnly = true)
    public List<AdvanceDirectiveEntity> advanceDirectives(String cpid) {
        return directiveRepository.findByTenantIdAndSubjectCpidOrderByCreatedAtDesc(tenant(), cpid);
    }

    // ── Writes ──────────────────────────────────────────────────────────────────

    @Transactional
    public SocialHistoryEntity recordSocialHistory(Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        SocialHistoryEntity e = new SocialHistoryEntity();
        e.setTenantId(ctx.tenantId());
        e.setSubjectCpid(required(body, "subject_cpid", "subjectCpid"));
        accessGuard.requireCareRelationship(ctx, e.getSubjectCpid(), null, null);
        e.setCategory(upper(required(body, "category", "category")));
        e.setStatus(required(body, "status", "status"));
        e.setDetail(str(body.get("detail")));
        // Never defaulted: an unassessed risk stays null. "Low" is the value that stops a reader.
        e.setRiskLevel(requireOneOf("risk_level", str(body.get("risk_level"), body.get("riskLevel")),
                List.of("LOW", "MODERATE", "HIGH")));
        e.setLastUpdated(LocalDate.now());
        e.setRecordedBy(ctx.actorId());
        e = socialRepository.save(e);
        log.info("pct.social_history.recorded subject={} category={} by={}",
                e.getSubjectCpid(), e.getCategory(), ctx.actorId());
        return e;
    }

    @Transactional
    public FamilyHistoryMemberEntity recordFamilyMember(Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        FamilyHistoryMemberEntity e = new FamilyHistoryMemberEntity();
        e.setTenantId(ctx.tenantId());
        e.setSubjectCpid(required(body, "subject_cpid", "subjectCpid"));
        accessGuard.requireCareRelationship(ctx, e.getSubjectCpid(), null, null);
        e.setRelationship(upper(required(body, "relationship", "relationship")));
        // Deliberately not a name — a relative is a third party with no record here.
        e.setDistinguisher(str(body.get("distinguisher")));
        e.setAge(integer(body.get("age")));
        e.setDeceased(bool(body.get("deceased")));
        e.setDeceasedAge(integer(body.get("deceased_age"), body.get("deceasedAge")));
        e.setCauseOfDeath(str(body.get("cause_of_death"), body.get("causeOfDeath")));
        // The database refuses this too; refusing it here gives a message a caller can act on.
        if (!Boolean.TRUE.equals(e.getDeceased())
                && (e.getDeceasedAge() != null || e.getCauseOfDeath() != null)) {
            throw new IllegalArgumentException(
                    "A cause or age of death was given for a relative not recorded as deceased.");
        }
        e.setRecordedBy(ctx.actorId());
        return familyMemberRepository.save(e);
    }

    @Transactional
    public FunctionalAssessmentEntity recordFunctionalAssessment(Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        FunctionalAssessmentEntity e = new FunctionalAssessmentEntity();
        e.setTenantId(ctx.tenantId());
        e.setSubjectCpid(required(body, "subject_cpid", "subjectCpid"));
        accessGuard.requireCareRelationship(ctx, e.getSubjectCpid(), null, null);
        e.setAssessmentType(upper(required(body, "assessment_type", "assessmentType")));
        String date = str(body.get("assessment_date"), body.get("assessmentDate"));
        e.setAssessmentDate(date == null ? LocalDate.now() : LocalDate.parse(date));
        e.setAssessor(str(body.get("assessor")));
        e.setTotalScore(integer(body.get("total_score"), body.get("totalScore")));
        e.setMaxScore(integer(body.get("max_score"), body.get("maxScore")));
        e.setScoreAbsentReason(str(body.get("score_absent_reason"), body.get("scoreAbsentReason")));
        e.setInterpretation(str(body.get("interpretation")));
        e.setRecordedBy(ctx.actorId());

        // A score or a reason, never neither and never both. An assessment that could not be
        // completed must say so; a fabricated zero on a Barthel index reads as total dependence.
        boolean hasScore = e.getTotalScore() != null;
        boolean hasReason = e.getScoreAbsentReason() != null;
        if (hasScore == hasReason) {
            throw new IllegalArgumentException(hasScore
                    ? "An assessment cannot carry both a score and a reason it could not be scored."
                    : "An assessment needs either total_score or score_absent_reason.");
        }
        if (hasScore && e.getMaxScore() != null
                && (e.getTotalScore() < 0 || e.getTotalScore() > e.getMaxScore())) {
            throw new IllegalArgumentException(
                    "total_score " + e.getTotalScore() + " is outside 0.." + e.getMaxScore());
        }
        return functionalRepository.save(e);
    }

    @Transactional
    public PastProcedureEntity recordPastProcedure(Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        PastProcedureEntity e = new PastProcedureEntity();
        e.setTenantId(ctx.tenantId());
        e.setSubjectCpid(required(body, "subject_cpid", "subjectCpid"));
        accessGuard.requireCareRelationship(ctx, e.getSubjectCpid(), null, null);
        e.setName(required(body, "name", "name"));
        e.setCode(str(body.get("code")));
        e.setCodeSystem(str(body.get("code_system"), body.get("codeSystem")));
        e.setProcedureType(str(body.get("procedure_type"), body.get("procedureType")));
        String date = str(body.get("procedure_date"), body.get("procedureDate"));
        if (date != null) e.setProcedureDate(LocalDate.parse(date));
        // "About ten years ago" is a real answer; recording it as exact would let a later reader
        // compute intervals from a number the patient never gave.
        e.setDatePrecision(requireOneOf("date_precision",
                str(body.get("date_precision"), body.get("datePrecision")),
                List.of("EXACT", "MONTH", "YEAR", "APPROXIMATE")));
        e.setPerformer(str(body.get("performer")));
        e.setFacility(str(body.get("facility")));
        e.setStatus(upperOr(str(body.get("status")), "COMPLETED"));
        e.setSourceEpisodeRef(str(body.get("source_episode_ref"), body.get("sourceEpisodeRef")));
        e.setNotes(str(body.get("notes")));
        e.setRecordedBy(ctx.actorId());
        return procedureRepository.save(e);
    }

    @Transactional
    public AdvanceDirectiveEntity recordAdvanceDirective(Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        AdvanceDirectiveEntity e = new AdvanceDirectiveEntity();
        e.setTenantId(ctx.tenantId());
        e.setSubjectCpid(required(body, "subject_cpid", "subjectCpid"));
        accessGuard.requireCareRelationship(ctx, e.getSubjectCpid(), null, null);
        e.setDirectiveType(upper(required(body, "directive_type", "directiveType")));
        e.setStatus(upperOr(str(body.get("status")), "ACTIVE"));
        e.setSummary(str(body.get("summary")));
        String documentId = str(body.get("document_id"), body.get("documentId"));
        if (documentId != null) e.setDocumentId(UUID.fromString(documentId));
        String from = str(body.get("effective_from"), body.get("effectiveFrom"));
        if (from != null) e.setEffectiveFrom(LocalDate.parse(from));
        String reviewed = str(body.get("reviewed_on"), body.get("reviewedOn"));
        if (reviewed != null) e.setReviewedOn(LocalDate.parse(reviewed));
        e.setRecordedBy(ctx.actorId());
        return directiveRepository.save(e);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private static String required(Map<String, Object> body, String snake, String camel) {
        String v = str(body.get(snake), body.get(camel));
        if (v == null) throw new IllegalArgumentException("Missing field: " + snake);
        return v;
    }

    private static String requireOneOf(String field, String value, List<String> allowed) {
        if (value == null) return null;
        String normalised = value.toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalised)) {
            throw new IllegalArgumentException(
                    "Unrecognised " + field + ": '" + value + "'. Allowed: " + allowed);
        }
        return normalised;
    }

    private static String str(Object... candidates) {
        for (Object v : candidates) {
            if (v == null) continue;
            String s = String.valueOf(v).trim();
            if (!s.isEmpty()) return s;
        }
        return null;
    }

    private static String upper(String v) { return v == null ? null : v.toUpperCase(Locale.ROOT); }
    private static String upperOr(String v, String def) { return v == null ? def : upper(v); }

    private static Integer integer(Object... candidates) {
        String s = str(candidates);
        return s == null ? null : Integer.valueOf(s);
    }

    private static Boolean bool(Object v) {
        String s = str(v);
        return s == null ? null : Boolean.valueOf(s);
    }
}
