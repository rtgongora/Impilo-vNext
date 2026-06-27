package zw.gov.mohcc.impilo.clinical.rules;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.clinical.persistence.entity.MedicineGuidanceEntity;
import zw.gov.mohcc.impilo.clinical.persistence.repository.MedicineGuidanceRepository;
import zw.gov.mohcc.impilo.clinical.rules.model.ClinicalEvaluationContext;
import zw.gov.mohcc.impilo.clinical.rules.model.MedicationLine;

import java.util.List;

/**
 * Enriches a deterministic {@link ClinicalEvaluationContext} with facts the calling
 * system did not supply but that the registry owns — currently the national-formulary
 * {@code specialistOnly} designation, looked up from {@link MedicineGuidanceEntity}
 * by generic name. This is what makes the engine's specialist-only level-of-care
 * gating fire in production (the rule reads {@code MedicationLine.specialistOnly}).
 *
 * <p>A caller-supplied {@code specialistOnly=true} is preserved; only un-flagged
 * medications are resolved against MedicineGuidance, so this never downgrades a flag.
 */
@Service
public class ClinicalContextEnricher {

    private final MedicineGuidanceRepository medicineGuidanceRepository;

    public ClinicalContextEnricher(MedicineGuidanceRepository medicineGuidanceRepository) {
        this.medicineGuidanceRepository = medicineGuidanceRepository;
    }

    public ClinicalEvaluationContext enrich(ClinicalEvaluationContext ctx) {
        List<MedicationLine> meds = ctx.activeMedications();
        if (meds.isEmpty() || meds.stream().allMatch(MedicationLine::specialistOnly)) {
            return ctx;
        }
        List<MedicationLine> enriched = meds.stream()
                .map(m -> m.specialistOnly() || !isSpecialistOnly(m.genericName())
                        ? m
                        : new MedicationLine(m.genericName(), m.displayName(), m.doseMg(), m.route(),
                                m.daysOnTherapy(), m.broadSpectrumAntibiotic(), true))
                .toList();
        return new ClinicalEvaluationContext(
                ctx.ageYears(), ctx.ageDays(), ctx.weightKg(), ctx.facilityLevel(),
                ctx.diagnoses(), enriched, ctx.empiricBroadSpectrumDays(),
                ctx.cultureDocumented(), ctx.lastResortAntibioticWithoutAstJustification(),
                ctx.vitals(), ctx.allergies());
    }

    private boolean isSpecialistOnly(String genericName) {
        if (genericName == null || genericName.isBlank()) {
            return false;
        }
        return medicineGuidanceRepository.search(genericName).stream()
                .findFirst()
                .map(m -> Boolean.TRUE.equals(m.getSpecialistOnly()))
                .orElse(false);
    }
}
