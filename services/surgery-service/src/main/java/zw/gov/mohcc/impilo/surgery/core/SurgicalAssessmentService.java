package zw.gov.mohcc.impilo.surgery.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalAssessmentEntity;
import zw.gov.mohcc.impilo.surgery.persistence.repository.SurgicalAssessmentRepository;
import zw.gov.mohcc.impilo.surgery.persistence.repository.SurgicalEpisodeRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * S2 — the general surgical assessment (surgical domain pack §5). Refined over time on ONE row
 * per episode, mirroring mvumo's informed-consent-content idiom (V300): an assessment is a
 * conversation and an examination that gets more complete, not a series of separate versions.
 *
 * <p>See V003's own migration header for which fields are new content this service may own
 * outright, versus a reviewed-flag-plus-reference into a registry this service deliberately
 * does NOT duplicate (allergies, medications, functional status, pregnancy, imaging, pathology,
 * previous surgery — all owned elsewhere). This class enforces the same pairing at the
 * application layer that V003's CHECK constraints enforce at the schema layer: a reference id
 * cannot be set without its reviewed flag also being true, because a reference alone would
 * fabricate confidence about a review that never happened.</p>
 */
@Service
public class SurgicalAssessmentService {

    private final SurgicalAssessmentRepository repository;
    private final SurgicalEpisodeRepository episodeRepository;

    public SurgicalAssessmentService(SurgicalAssessmentRepository repository,
                                     SurgicalEpisodeRepository episodeRepository) {
        this.repository = repository;
        this.episodeRepository = episodeRepository;
    }

    private UUID currentTenant() {
        return TrustContextHolder.require().tenantId();
    }

    private String currentActor() {
        try {
            String actor = TrustContextHolder.require().actorId();
            return actor != null && !actor.isBlank() ? actor : "system";
        } catch (IllegalStateException e) {
            return "system";
        }
    }

    @Transactional
    public Map<String, Object> recordAssessment(UUID episodeId, Map<String, Object> body) {
        UUID tenant = currentTenant();
        episodeRepository.findByIdAndTenantId(episodeId, tenant)
                .orElseThrow(() -> new SurgeryDomainException("SURGICAL_EPISODE_NOT_FOUND", 404,
                        "No surgical episode " + episodeId));

        SurgicalAssessmentEntity e = repository.findBySurgicalEpisodeIdAndTenantId(episodeId, tenant)
                .orElseGet(() -> {
                    SurgicalAssessmentEntity fresh = new SurgicalAssessmentEntity();
                    fresh.setTenantId(tenant);
                    fresh.setSurgicalEpisodeId(episodeId);
                    return fresh;
                });

        applyIfPresent(body, "presentingProblem", e::setPresentingProblem);
        applyIfPresent(body, "symptomTimeline", e::setSymptomTimeline);
        applyIfPresent(body, "woundHealingHistory", e::setWoundHealingHistory);
        applyIfPresent(body, "bleedingThrombosisHistory", e::setBleedingThrombosisHistory);
        applyIfPresent(body, "infectionHistory", e::setInfectionHistory);
        applyIfPresent(body, "anaestheticHistoryNotes", e::setAnaestheticHistoryNotes);
        applyIfPresent(body, "nutritionAssessment", e::setNutritionAssessment);
        applyIfPresent(body, "frailtyNotes", e::setFrailtyNotes);
        applyIfPresent(body, "socialSupportNotes", e::setSocialSupportNotes);
        applyIfPresent(body, "transportPlan", e::setTransportPlan);
        applyIfPresent(body, "workLivelihoodImpact", e::setWorkLivelihoodImpact);
        applyIfPresent(body, "patientGoals", e::setPatientGoals);
        applyIfPresent(body, "examinationFindings", e::setExaminationFindings);
        applyIfPresent(body, "differentialDiagnosisNotes", e::setDifferentialDiagnosisNotes);
        applyIfPresent(body, "surgicalRiskAssessment", e::setSurgicalRiskAssessment);
        applyIfPresent(body, "previousSurgeryNotes", e::setPreviousSurgeryNotes);
        applyIfPresent(body, "allergyReviewNotes", e::setAllergyReviewNotes);
        applyIfPresent(body, "medicationReconciliationNotes", e::setMedicationReconciliationNotes);

        applyBooleanIfPresent(body, "previousSurgeryReviewed", e::setPreviousSurgeryReviewed);
        applyBooleanIfPresent(body, "allergiesReviewed", e::setAllergiesReviewed);
        applyBooleanIfPresent(body, "medicationsReviewed", e::setMedicationsReviewed);

        applyReferencePair(body, "functionalStatusReviewed", "functionalAssessmentRef",
                e::setFunctionalStatusReviewed, e::setFunctionalAssessmentRef,
                "functionalAssessmentRef requires functionalStatusReviewed=true");
        applyReferencePair(body, "pregnancyStatusConfirmed", "pregnancyEpisodeRef",
                e::setPregnancyStatusConfirmed, e::setPregnancyEpisodeRef,
                "pregnancyEpisodeRef requires pregnancyStatusConfirmed=true");

        if (body.get("imagingRef") != null) {
            if (!Boolean.TRUE.equals(bool(body, "imagingReviewed"))) {
                throw new SurgeryDomainException("IMAGING_REVIEW_REQUIRED", 400,
                        "imagingRef requires imagingReviewed=true");
            }
            e.setImagingReviewed(true);
            e.setImagingRef(body.get("imagingRef").toString());
        } else {
            applyBooleanIfPresent(body, "imagingReviewed", e::setImagingReviewed);
        }
        if (body.get("pathologyRef") != null) {
            if (!Boolean.TRUE.equals(bool(body, "pathologyReviewed"))) {
                throw new SurgeryDomainException("PATHOLOGY_REVIEW_REQUIRED", 400,
                        "pathologyRef requires pathologyReviewed=true");
            }
            e.setPathologyReviewed(true);
            e.setPathologyRef(body.get("pathologyRef").toString());
        } else {
            applyBooleanIfPresent(body, "pathologyReviewed", e::setPathologyReviewed);
        }

        e.setAssessedBy(currentActor());
        e.setAssessedAt(OffsetDateTime.now());
        e = repository.save(e);
        return toView(e);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAssessment(UUID episodeId) {
        SurgicalAssessmentEntity e = repository
                .findBySurgicalEpisodeIdAndTenantId(episodeId, currentTenant())
                .orElseThrow(() -> new SurgeryDomainException("SURGICAL_ASSESSMENT_NOT_FOUND", 404,
                        "No surgical assessment for episode " + episodeId));
        return toView(e);
    }

    private void applyIfPresent(Map<String, Object> body, String key, java.util.function.Consumer<String> setter) {
        if (body.containsKey(key) && body.get(key) != null) {
            setter.accept(body.get(key).toString());
        }
    }

    private void applyBooleanIfPresent(Map<String, Object> body, String key, java.util.function.Consumer<Boolean> setter) {
        Boolean v = bool(body, key);
        if (v != null) setter.accept(v);
    }

    private Boolean bool(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null) return null;
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(v.toString());
    }

    private void applyReferencePair(Map<String, Object> body, String reviewedKey, String refKey,
                                    java.util.function.Consumer<Boolean> reviewedSetter,
                                    java.util.function.Consumer<UUID> refSetter, String errorMessage) {
        Object refRaw = body.get(refKey);
        if (refRaw != null) {
            if (!Boolean.TRUE.equals(bool(body, reviewedKey))) {
                throw new SurgeryDomainException("REFERENCE_REVIEW_REQUIRED", 400, errorMessage);
            }
            reviewedSetter.accept(true);
            refSetter.accept(UUID.fromString(refRaw.toString()));
        } else {
            applyBooleanIfPresent(body, reviewedKey, reviewedSetter);
        }
    }

    private Map<String, Object> toView(SurgicalAssessmentEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId().toString());
        m.put("surgicalEpisodeId", e.getSurgicalEpisodeId().toString());
        m.put("presentingProblem", e.getPresentingProblem());
        m.put("symptomTimeline", e.getSymptomTimeline());
        m.put("woundHealingHistory", e.getWoundHealingHistory());
        m.put("bleedingThrombosisHistory", e.getBleedingThrombosisHistory());
        m.put("infectionHistory", e.getInfectionHistory());
        m.put("anaestheticHistoryNotes", e.getAnaestheticHistoryNotes());
        m.put("nutritionAssessment", e.getNutritionAssessment());
        m.put("frailtyNotes", e.getFrailtyNotes());
        m.put("socialSupportNotes", e.getSocialSupportNotes());
        m.put("transportPlan", e.getTransportPlan());
        m.put("workLivelihoodImpact", e.getWorkLivelihoodImpact());
        m.put("patientGoals", e.getPatientGoals());
        m.put("examinationFindings", e.getExaminationFindings());
        m.put("differentialDiagnosisNotes", e.getDifferentialDiagnosisNotes());
        m.put("surgicalRiskAssessment", e.getSurgicalRiskAssessment());
        m.put("previousSurgeryReviewed", e.isPreviousSurgeryReviewed());
        m.put("previousSurgeryNotes", e.getPreviousSurgeryNotes());
        m.put("allergiesReviewed", e.isAllergiesReviewed());
        m.put("allergyReviewNotes", e.getAllergyReviewNotes());
        m.put("medicationsReviewed", e.isMedicationsReviewed());
        m.put("medicationReconciliationNotes", e.getMedicationReconciliationNotes());
        m.put("functionalStatusReviewed", e.isFunctionalStatusReviewed());
        m.put("functionalAssessmentRef", e.getFunctionalAssessmentRef() != null ? e.getFunctionalAssessmentRef().toString() : null);
        m.put("pregnancyStatusConfirmed", e.isPregnancyStatusConfirmed());
        m.put("pregnancyEpisodeRef", e.getPregnancyEpisodeRef() != null ? e.getPregnancyEpisodeRef().toString() : null);
        m.put("imagingReviewed", e.isImagingReviewed());
        m.put("imagingRef", e.getImagingRef());
        m.put("pathologyReviewed", e.isPathologyReviewed());
        m.put("pathologyRef", e.getPathologyRef());
        m.put("assessedBy", e.getAssessedBy());
        m.put("assessedAt", e.getAssessedAt() != null ? e.getAssessedAt().toString() : null);
        return m;
    }
}
