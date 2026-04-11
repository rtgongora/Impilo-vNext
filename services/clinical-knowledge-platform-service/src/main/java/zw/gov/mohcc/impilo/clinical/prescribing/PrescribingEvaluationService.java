package zw.gov.mohcc.impilo.clinical.prescribing;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.clinical.persistence.entity.MedicineGuidanceEntity;
import zw.gov.mohcc.impilo.clinical.persistence.repository.MedicineGuidanceRepository;
import zw.gov.mohcc.impilo.clinical.rules.ClinicalRulesEngine;
import zw.gov.mohcc.impilo.clinical.rules.model.ClinicalEvaluationContext;
import zw.gov.mohcc.impilo.clinical.rules.model.RuleAlert;

import java.util.*;

@Service
public class PrescribingEvaluationService {

    private final ClinicalRulesEngine rulesEngine;
    private final MedicineGuidanceRepository medicineGuidanceRepository;

    public PrescribingEvaluationService(
            ClinicalRulesEngine rulesEngine,
            MedicineGuidanceRepository medicineGuidanceRepository) {
        this.rulesEngine = rulesEngine;
        this.medicineGuidanceRepository = medicineGuidanceRepository;
    }

    public Map<String, Object> evaluate(Map<String, Object> body) {
        ClinicalEvaluationContext ctx = ClinicalEvaluationContext.fromMap(mergeProposedIntoContext(body == null ? Map.of() : body));
        List<RuleAlert> alerts = rulesEngine.evaluate(ctx);

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
            therapyRows.add(row);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("alerts", alerts.stream().map(RuleAlert::toMap).toList());
        out.put("therapy_evaluation", therapyRows);
        out.put("neonatal_gentamicin_hint", neonatalHint(ctx));
        return out;
    }

    private static Map<String, Object> neonatalHint(ClinicalEvaluationContext ctx) {
        if (ctx.ageDays() == null || ctx.weightKg() == null || ctx.ageDays() > 28) {
            return Map.of();
        }
        return NeonatalGentamicinDosing.suggestMgPerDose(ctx.weightKg(), ctx.ageDays())
                .map(d -> Map.<String, Object>of("gentamicin_seed_dose", d))
                .orElse(Map.of());
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
