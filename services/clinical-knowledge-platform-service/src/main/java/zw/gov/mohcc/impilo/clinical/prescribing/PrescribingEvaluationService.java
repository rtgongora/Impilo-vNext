package zw.gov.mohcc.impilo.clinical.prescribing;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.clinical.audit.TraceService;
import zw.gov.mohcc.impilo.clinical.persistence.entity.MedicineGuidanceEntity;
import zw.gov.mohcc.impilo.clinical.persistence.entity.SourceDocumentEntity;
import zw.gov.mohcc.impilo.clinical.persistence.repository.MedicineGuidanceRepository;
import zw.gov.mohcc.impilo.clinical.persistence.repository.SourceDocumentRepository;
import zw.gov.mohcc.impilo.clinical.rules.ClinicalRulesEngine;
import zw.gov.mohcc.impilo.clinical.rules.model.ClinicalEvaluationContext;
import zw.gov.mohcc.impilo.clinical.rules.model.RuleAlert;

import java.util.*;

@Service
public class PrescribingEvaluationService {

    private final ClinicalRulesEngine rulesEngine;
    private final MedicineGuidanceRepository medicineGuidanceRepository;
    private final TraceService traceService;
    private final SourceDocumentRepository sourceDocumentRepository;

    public PrescribingEvaluationService(
            ClinicalRulesEngine rulesEngine,
            MedicineGuidanceRepository medicineGuidanceRepository,
            TraceService traceService,
            SourceDocumentRepository sourceDocumentRepository) {
        this.rulesEngine = rulesEngine;
        this.medicineGuidanceRepository = medicineGuidanceRepository;
        this.traceService = traceService;
        this.sourceDocumentRepository = sourceDocumentRepository;
    }

    /**
     * Weight-based paediatric dose calculator. Field-injected so the shared constructor stays
     * a stable merge surface, matching the convention used elsewhere in the clinical plane.
     */
    @org.springframework.beans.factory.annotation.Autowired
    private DoseCalculationService doseCalculationService;

    @Transactional
    public Map<String, Object> evaluate(Map<String, Object> body) {
        ClinicalEvaluationContext ctx = ClinicalEvaluationContext.fromMap(mergeProposedIntoContext(body == null ? Map.of() : body));
        List<RuleAlert> alerts = new ArrayList<>(rulesEngine.evaluate(ctx));

        List<Map<String, Object>> therapyRows = new ArrayList<>();
        for (MedicationLineInput line : parseProposed(body)) {
            List<MedicineGuidanceEntity> matches = medicineGuidanceRepository.search(line.genericName());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("input", line.toMap());
            if (!matches.isEmpty()) {
                MedicineGuidanceEntity m = matches.get(0);
                row.put("policy_metadata", Map.of(
                        "level_of_care", m.getLevelOfCare(),
                        "ven_class", m.getVenClass(),
                        "specialist_only", m.getSpecialistOnly(),
                        "dose_expression", m.getDoseExpression(),
                        "dose_unit", m.getDoseUnit(),
                        "route", m.getRoute(),
                        "frequency", m.getFrequencyExpression(),
                        "duration", m.getDurationExpression(),
                        "notes", m.getNotes()
                ));
                row.put("source_section_id", m.getSourceSectionId() != null ? m.getSourceSectionId().toString() : null);
            } else {
                row.put("policy_metadata", null);
            }
            // Paediatric dosing: suggest from governed content and check what was proposed
            // against the maximum. A proposed dose above the ceiling is a hard stop, not a hint.
            var suggestion = doseCalculationService.calculate(
                    line.genericName(), indicationOf(body), ctx.ageDays(), ctx.weightKg());
            if (isPaediatric(ctx)) {
                row.put("paediatric_dose", suggestion.toMap());
                RuleAlert unsafe = unsafeDoseAlert(line, suggestion);
                if (unsafe != null) {
                    alerts.add(unsafe);
                }
            }
            therapyRows.add(row);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("alerts", alerts.stream().map(RuleAlert::toMap).toList());
        out.put("therapy_evaluation", therapyRows);

        if (body != null && Boolean.TRUE.equals(body.get("record_trace"))) {
            String actorId = Objects.toString(body.get("actor_id"), "anonymous");
            String role = Objects.toString(body.get("role"), "PRESCRIBER");
            String patientId = body.get("patient_id") != null ? body.get("patient_id").toString() : null;
            String encounterId = body.get("encounter_id") != null ? body.get("encounter_id").toString() : null;
            String tenantId = Objects.toString(body.get("tenant_id"), "default");
            String sourceVersion = sourceDocumentRepository.findFirstByStatusOrderByEffectiveDateDesc("ACTIVE")
                    .map(SourceDocumentEntity::getVersionLabel)
                    .orElse("unknown");
            String supportMode = alerts.isEmpty() ? "SOURCE_GROUNDED" : "RULE_EVALUATED";
            traceService.record(
                    actorId,
                    role,
                    patientId,
                    encounterId,
                    "PRESCRIBING_EVALUATE",
                    auditInputContext(body, tenantId),
                    out,
                    List.of(),
                    alerts.stream().map(RuleAlert::toMap).toList(),
                    "ckp-seed-1",
                    sourceVersion,
                    supportMode);
        }
        return out;
    }

    private static Map<String, Object> auditInputContext(Map<String, Object> body, String tenantId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tenant_id", tenantId);
        m.put("diagnoses", body.get("diagnoses"));
        m.put("proposedMedications", body.get("proposedMedications"));
        m.put("activeMedications", body.get("activeMedications"));
        m.put("facility_level", body.get("facility_level"));
        m.put("patient_age_days", body.get("patient_age_days"));
        m.put("weight_kg", body.get("weight_kg"));
        return m;
    }

    /** Below thirteen years the paediatric dosing rules apply. */
    private static boolean isPaediatric(ClinicalEvaluationContext ctx) {
        if (ctx.ageDays() != null) {
            return ctx.ageDays() < 13 * 365;
        }
        return ctx.ageYears() != null && ctx.ageYears() < 13;
    }

    private static String indicationOf(Map<String, Object> body) {
        Object indication = body == null ? null : body.get("indication");
        return indication == null ? null : String.valueOf(indication);
    }

    /**
     * Compares a proposed dose against the calculated maximum. A dose above the ceiling is a
     * critical, non-overridable alert: in paediatrics an overdose is usually a decimal-point
     * slip that looks entirely ordinary on the page, so it must stop the prescription rather
     * than warn beside it.
     */
    private static RuleAlert unsafeDoseAlert(MedicationLineInput line,
                                             DoseCalculationService.DoseSuggestion suggestion) {
        if (line.doseMg() == null || !suggestion.calculated() || suggestion.doseMg() == null) {
            return null;
        }
        double proposed = line.doseMg();
        double calculated = suggestion.doseMg();
        if (proposed <= calculated * 1.25) {
            return null;
        }
        return new RuleAlert(
                "UNSAFE_DOSE_PAEDIATRIC",
                "CRITICAL",
                "Proposed dose of " + line.genericName() + " (" + proposed + " mg) exceeds the"
                        + " weight-based dose of " + calculated + " mg — recheck the weight and the decimal point.",
                suggestion.basis(),
                true,
                false);
    }

    private record MedicationLineInput(String genericName, Double doseMg, String route, Integer daysOnTherapy) {
        Map<String, Object> toMap() {
            return Map.of(
                    "genericName", genericName,
                    "doseMg", doseMg == null ? "" : doseMg,
                    "route", route == null ? "" : route,
                    "daysOnTherapy", daysOnTherapy == null ? "" : daysOnTherapy
            );
        }
    }

    @SuppressWarnings("unchecked")
    private static List<MedicationLineInput> parseProposed(Map<String, Object> body) {
        if (body == null) {
            return List.of();
        }
        Object o = body.get("proposedMedications");
        if (!(o instanceof List<?> list)) {
            return List.of();
        }
        List<MedicationLineInput> rows = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                String gen = Objects.toString(m.get("genericName"), "").toLowerCase(Locale.ROOT);
                Double dose = m.get("doseMg") instanceof Number n ? n.doubleValue() : null;
                String route = Objects.toString(m.get("route"), null);
                Integer days = m.get("daysOnTherapy") instanceof Number n ? n.intValue() : null;
                if (!gen.isBlank()) {
                    rows.add(new MedicationLineInput(gen, dose, route, days));
                }
            }
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mergeProposedIntoContext(Map<String, Object> body) {
        Map<String, Object> m = new HashMap<>(body == null ? Map.of() : body);
        List<Map<String, Object>> active = new ArrayList<>();
        Object a = m.get("activeMedications");
        if (a instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Map<?, ?> mm) {
                    active.add(new HashMap<>((Map<String, Object>) mm));
                }
            }
        }
        Object p = m.get("proposedMedications");
        if (p instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Map<?, ?> mm) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    if (mm.get("genericName") != null) {
                        row.put("genericName", mm.get("genericName").toString());
                    }
                    if (mm.get("doseMg") != null) {
                        row.put("doseMg", mm.get("doseMg"));
                    }
                    if (mm.get("route") != null) {
                        row.put("route", mm.get("route").toString());
                    }
                    if (mm.get("daysOnTherapy") != null) {
                        row.put("daysOnTherapy", mm.get("daysOnTherapy"));
                    }
                    active.add(row);
                }
            }
        }
        m.put("activeMedications", active);
        return m;
    }
}
