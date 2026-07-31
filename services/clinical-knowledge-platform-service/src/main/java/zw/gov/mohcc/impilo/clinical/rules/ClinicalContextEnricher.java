package zw.gov.mohcc.impilo.clinical.rules;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.clinical.persistence.entity.MedicineGuidanceEntity;
import zw.gov.mohcc.impilo.clinical.persistence.repository.MedicineGuidanceRepository;
import zw.gov.mohcc.impilo.clinical.renal.EgfrDerivation;
import zw.gov.mohcc.impilo.clinical.rules.model.ClinicalEvaluationContext;
import zw.gov.mohcc.impilo.clinical.rules.model.MedicationLine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Enriches a deterministic {@link ClinicalEvaluationContext} with facts the calling system did not
 * supply but that this platform can establish.
 *
 * <p>Two enrichments today.</p>
 *
 * <p><strong>Specialist-only designation.</strong> The national-formulary {@code specialistOnly}
 * flag, looked up from {@link MedicineGuidanceEntity} by generic name. This is what makes the
 * engine's level-of-care gating fire in production. A caller-supplied {@code specialistOnly=true} is
 * preserved; only un-flagged medications are resolved, so this never downgrades a flag.</p>
 *
 * <p><strong>eGFR.</strong> Derived from a creatinine observation when the caller did not send one.
 * Several rules — {@code RENAL_IMPAIRMENT_LOW_EGFR} chief among them — are keyed on an eGFR that
 * nothing in the platform produced, so they were silent for every clinic that measures creatinine
 * and leaves the filtration rate to be worked out. A supplied eGFR is never overwritten, and a
 * calculator refusal writes nothing, so a rule that could not assess renal function still cannot.</p>
 */
@Service
public class ClinicalContextEnricher {

    private static final Logger log = LoggerFactory.getLogger(ClinicalContextEnricher.class);

    private final MedicineGuidanceRepository medicineGuidanceRepository;

    public ClinicalContextEnricher(MedicineGuidanceRepository medicineGuidanceRepository) {
        this.medicineGuidanceRepository = medicineGuidanceRepository;
    }

    public ClinicalEvaluationContext enrich(ClinicalEvaluationContext ctx) {
        return withDerivedEgfr(withSpecialistOnly(ctx));
    }

    private ClinicalEvaluationContext withSpecialistOnly(ClinicalEvaluationContext ctx) {
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
                ctx.vitals(), ctx.allergies(),
                // Pass the interpretation fields through unchanged — enrichment must never drop them.
                ctx.sex(), ctx.pregnancyStatus(), ctx.gestationalAgeWeeks(),
                ctx.interpretedObservations(), ctx.derivedValues());
    }

    /**
     * Fills in an eGFR from a creatinine when, and only when, the caller did not supply one.
     *
     * <p>The existing-value check is deliberately case-insensitive: {@code fromMap} preserves
     * whatever casing the caller used, the rules engine looks the key up case-insensitively, and a
     * derivation that missed a caller's {@code "eGFR"} would write a second, competing entry.</p>
     */
    private ClinicalEvaluationContext withDerivedEgfr(ClinicalEvaluationContext ctx) {
        if (hasEgfr(ctx.derivedValues())) {
            return ctx;
        }
        Integer ageYears = ctx.ageYears() == null ? null : (int) Math.floor(ctx.ageYears());
        var derived = EgfrDerivation.fromObservations(
                ctx.interpretedObservations(), ageYears, ctx.sex());

        if (!derived.present()) {
            if (derived.refusalReason() != null) {
                // Worth a line: a creatinine was found and the calculator still declined, which is
                // the difference between "no bloods" and "bloods this equation cannot be used on".
                log.debug("eGFR not derived despite a creatinine being present: {} — {}",
                        derived.refusalReason(), derived.refusalDetail());
            }
            return ctx;
        }

        Map<String, Double> derivedValues = new LinkedHashMap<>(ctx.derivedValues());
        derivedValues.put(EgfrDerivation.EGFR_KEY, derived.asDouble());
        return ctx.withInterpretations(ctx.interpretedObservations(), derivedValues);
    }

    private static boolean hasEgfr(Map<String, Double> derivedValues) {
        return derivedValues.keySet().stream()
                .anyMatch(k -> k != null && k.equalsIgnoreCase(EgfrDerivation.EGFR_KEY));
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
