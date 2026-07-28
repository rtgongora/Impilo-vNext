package zw.gov.mohcc.impilo.mentalhealth.core;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.mentalhealth.persistence.entity.AssessmentEntity;
import zw.gov.mohcc.impilo.mentalhealth.persistence.entity.FollowupEntity;
import zw.gov.mohcc.impilo.mentalhealth.persistence.entity.ReferralEntity;
import zw.gov.mohcc.impilo.mentalhealth.persistence.entity.RiskFormulationEntity;
import zw.gov.mohcc.impilo.mentalhealth.persistence.entity.SafetyPlanEntity;
import zw.gov.mohcc.impilo.mentalhealth.persistence.repository.AssessmentRepository;
import zw.gov.mohcc.impilo.mentalhealth.persistence.repository.FollowupRepository;
import zw.gov.mohcc.impilo.mentalhealth.persistence.repository.ReferralRepository;
import zw.gov.mohcc.impilo.mentalhealth.persistence.repository.RiskFormulationRepository;
import zw.gov.mohcc.impilo.mentalhealth.persistence.repository.SafetyPlanRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The clinical record chain hung off a referral: assessment, risk formulation, safety plan,
 * follow-up. Kept as one service because each write is a simple create against an already-loaded
 * parent — the referral/assessment lifecycle with its handshake and legal/safety consequences
 * lives in {@link ReferralService}, {@link InvoluntaryEpisodeService} and
 * {@link RestraintEventService} instead.
 */
@Service
public class ClinicalRecordService {

    private static final Set<String> CAPACITY_OUTCOMES = Set.of("HAS_CAPACITY", "LACKS_CAPACITY", "FLUCTUATING");
    private static final Set<String> RISK_LEVELS = Set.of("LOW", "MODERATE", "HIGH");

    private final ReferralRepository referralRepository;
    private final AssessmentRepository assessmentRepository;
    private final RiskFormulationRepository riskFormulationRepository;
    private final SafetyPlanRepository safetyPlanRepository;
    private final FollowupRepository followupRepository;

    public ClinicalRecordService(ReferralRepository referralRepository,
                                 AssessmentRepository assessmentRepository,
                                 RiskFormulationRepository riskFormulationRepository,
                                 SafetyPlanRepository safetyPlanRepository,
                                 FollowupRepository followupRepository) {
        this.referralRepository = referralRepository;
        this.assessmentRepository = assessmentRepository;
        this.riskFormulationRepository = riskFormulationRepository;
        this.safetyPlanRepository = safetyPlanRepository;
        this.followupRepository = followupRepository;
    }

    @Transactional
    public AssessmentEntity recordAssessment(UUID tenantId, UUID referralId, String assessmentType,
                                             String mentalStateExam, boolean capacityAssessed,
                                             String capacityOutcome, String assessedBy, String notes) {
        loadReferral(referralId, tenantId);
        require(assessedBy != null && !assessedBy.isBlank(), "assessedBy is required");
        require(assessmentType == null || Set.of("INITIAL", "REVIEW", "DISCHARGE").contains(assessmentType),
                "assessmentType must be one of INITIAL, REVIEW, DISCHARGE");
        if (capacityAssessed) {
            require(capacityOutcome != null && CAPACITY_OUTCOMES.contains(capacityOutcome),
                    "capacityOutcome is required and must be one of " + CAPACITY_OUTCOMES + " when capacityAssessed is true");
        } else {
            require(capacityOutcome == null, "capacityOutcome must be absent when capacityAssessed is false");
        }

        AssessmentEntity a = new AssessmentEntity();
        a.setId(UUID.randomUUID());
        a.setTenantId(tenantId);
        a.setReferralId(referralId);
        a.setAssessmentType(assessmentType != null ? assessmentType : "INITIAL");
        a.setMentalStateExam(mentalStateExam);
        a.setCapacityAssessed(capacityAssessed);
        a.setCapacityOutcome(capacityOutcome);
        a.setAssessedBy(assessedBy);
        a.setAssessedAt(OffsetDateTime.now());
        a.setNotes(notes);
        a.setCreatedAt(OffsetDateTime.now());
        return assessmentRepository.save(a);
    }

    public List<AssessmentEntity> listAssessments(UUID tenantId, UUID referralId) {
        return assessmentRepository.findByTenantIdAndReferralIdOrderByAssessedAtDesc(tenantId, referralId);
    }

    @Transactional
    public RiskFormulationEntity recordRiskFormulation(UUID tenantId, UUID assessmentId, String riskToSelf,
                                                        String riskToOthers, String riskSummary,
                                                        String formulatedBy) {
        AssessmentEntity assessment = assessmentRepository.findByIdAndTenantId(assessmentId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Mental health assessment not found in this tenant: " + assessmentId));
        require(riskToSelf != null && RISK_LEVELS.contains(riskToSelf), "riskToSelf must be one of " + RISK_LEVELS);
        require(riskToOthers != null && RISK_LEVELS.contains(riskToOthers), "riskToOthers must be one of " + RISK_LEVELS);
        require(riskSummary != null && !riskSummary.isBlank(), "riskSummary is required");
        require(formulatedBy != null && !formulatedBy.isBlank(), "formulatedBy is required");

        RiskFormulationEntity rf = new RiskFormulationEntity();
        rf.setId(UUID.randomUUID());
        rf.setTenantId(tenantId);
        rf.setAssessmentId(assessment.getId());
        rf.setRiskToSelf(riskToSelf);
        rf.setRiskToOthers(riskToOthers);
        rf.setRiskSummary(riskSummary);
        rf.setFormulatedBy(formulatedBy);
        rf.setFormulatedAt(OffsetDateTime.now());
        rf.setCreatedAt(OffsetDateTime.now());
        return riskFormulationRepository.save(rf);
    }

    public List<RiskFormulationEntity> listRiskFormulations(UUID tenantId, UUID assessmentId) {
        return riskFormulationRepository.findByTenantIdAndAssessmentIdOrderByFormulatedAtDesc(tenantId, assessmentId);
    }

    @Transactional
    public SafetyPlanEntity recordSafetyPlan(UUID tenantId, UUID referralId, String warningSigns,
                                             String copingStrategies, String supportContacts,
                                             String meansRestrictionNotes, String createdBy) {
        loadReferral(referralId, tenantId);
        require(createdBy != null && !createdBy.isBlank(), "createdBy is required");

        SafetyPlanEntity sp = new SafetyPlanEntity();
        sp.setId(UUID.randomUUID());
        sp.setTenantId(tenantId);
        sp.setReferralId(referralId);
        sp.setWarningSigns(warningSigns);
        sp.setCopingStrategies(copingStrategies);
        sp.setSupportContacts(supportContacts);
        sp.setMeansRestrictionNotes(meansRestrictionNotes);
        sp.setCreatedBy(createdBy);
        sp.setCreatedAt(OffsetDateTime.now());
        return safetyPlanRepository.save(sp);
    }

    public List<SafetyPlanEntity> listSafetyPlans(UUID tenantId, UUID referralId) {
        return safetyPlanRepository.findByTenantIdAndReferralIdOrderByCreatedAtDesc(tenantId, referralId);
    }

    @Transactional
    public FollowupEntity scheduleFollowup(UUID tenantId, UUID referralId, String followupType,
                                           OffsetDateTime scheduledAt, String scheduledBy) {
        loadReferral(referralId, tenantId);
        require(scheduledAt != null, "scheduledAt is required");
        require(scheduledBy != null && !scheduledBy.isBlank(), "scheduledBy is required");
        require(followupType == null
                        || Set.of("CLINIC_REVIEW", "HOME_VISIT", "PHONE_CHECK", "COMMUNITY_TEAM").contains(followupType),
                "followupType must be one of CLINIC_REVIEW, HOME_VISIT, PHONE_CHECK, COMMUNITY_TEAM");

        FollowupEntity f = new FollowupEntity();
        f.setId(UUID.randomUUID());
        f.setTenantId(tenantId);
        f.setReferralId(referralId);
        f.setFollowupType(followupType != null ? followupType : "CLINIC_REVIEW");
        f.setScheduledAt(scheduledAt);
        f.setScheduledBy(scheduledBy);
        f.setCreatedAt(OffsetDateTime.now());
        return followupRepository.save(f);
    }

    @Transactional
    public FollowupEntity completeFollowup(UUID id, UUID tenantId, String outcomeNotes) {
        FollowupEntity f = followupRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Mental health follow-up not found in this tenant: " + id));
        f.setCompletedAt(OffsetDateTime.now());
        f.setOutcomeNotes(outcomeNotes);
        return followupRepository.save(f);
    }

    public List<FollowupEntity> listFollowups(UUID tenantId, UUID referralId) {
        return followupRepository.findByTenantIdAndReferralIdOrderByScheduledAtDesc(tenantId, referralId);
    }

    private ReferralEntity loadReferral(UUID referralId, UUID tenantId) {
        return referralRepository.findByIdAndTenantId(referralId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Mental health referral not found in this tenant: " + referralId));
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
