package zw.gov.mohcc.impilo.clinical.rules;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.clinical.rules.model.ClinicalEvaluationContext;
import zw.gov.mohcc.impilo.clinical.rules.model.MedicationLine;
import zw.gov.mohcc.impilo.clinical.rules.model.RuleAlert;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Deterministic clinical rule evaluation — no LLM. Rules are versioned in-database for metadata;
 * executable logic lives here (importable from curated decision tables in a later phase).
 */
@Service
public class ClinicalRulesEngine {

    private static final Set<String> SABA = Set.of("salbutamol", "albuterol", "terbutaline");
    private static final Set<String> ICS = Set.of(
            "beclometasone", "beclomethasone", "budesonide", "fluticasone", "ciclesonide", "mometasone"
    );

    private final int stewardshipBroadSpectrumDays;

    public ClinicalRulesEngine(
            @Value("${impilo.clinical.steward.broad-spectrum-days-threshold:3}") int stewardshipBroadSpectrumDays) {
        this.stewardshipBroadSpectrumDays = stewardshipBroadSpectrumDays;
    }

    public List<RuleAlert> evaluate(ClinicalEvaluationContext ctx) {
        List<RuleAlert> out = new ArrayList<>();
        out.addAll(asthmaRules(ctx));
        out.addAll(gonorrhoeaRules(ctx));
        out.addAll(neonatalRules(ctx));
        out.addAll(stewardshipRules(ctx));
        out.addAll(levelOfCareRules(ctx));
        out.addAll(lastResortAbxRules(ctx));
        return out;
    }

    private List<RuleAlert> asthmaRules(ClinicalEvaluationContext ctx) {
        List<RuleAlert> out = new ArrayList<>();
        boolean asthmaDx = ctx.diagnoses().stream().anyMatch(d -> d.contains("ASTHMA"));
        boolean saba = ctx.activeMedications().stream().anyMatch(m -> SABA.contains(m.genericName()));
        boolean ics = ctx.activeMedications().stream().anyMatch(m -> ICS.stream().anyMatch(i -> m.genericName().contains(i)));
        if (asthmaDx && saba && !ics) {
            out.add(new RuleAlert(
                    "ASTHMA_SABA_MONOTHERAPY",
                    "HIGH",
                    "SABA monotherapy is not recommended for persistent asthma. Add or review ICS-containing therapy per EDLIZ.",
                    "Relievers alone do not address airway inflammation; national guidance centres ICS-containing maintenance regimens.",
                    true,
                    true
            ));
        }
        return out;
    }

    private List<RuleAlert> gonorrhoeaRules(ClinicalEvaluationContext ctx) {
        List<RuleAlert> out = new ArrayList<>();
        boolean gcx = ctx.diagnoses().stream().anyMatch(d -> d.contains("GONORRHOEA") || d.contains("GONORRHEA"));
        for (MedicationLine m : ctx.activeMedications()) {
            if (!m.genericName().contains("ceftriaxone")) {
                continue;
            }
            if (gcx && m.doseMg() != null && m.doseMg() < 500) {
                out.add(new RuleAlert(
                        "GONORRHOEA_CEFTRIAXONE_LEGACY_DOSE",
                        "HIGH",
                        "Ceftriaxone 500 mg IM single dose is recommended for uncomplicated gonorrhoea. Review dose and programme circulars.",
                        "Lower legacy doses are discouraged due to reduced efficacy and resistance risk.",
                        true,
                        true
                ));
            }
        }
        return out;
    }

    private List<RuleAlert> neonatalRules(ClinicalEvaluationContext ctx) {
        List<RuleAlert> out = new ArrayList<>();
        if (ctx.ageDays() == null || ctx.ageDays() > 28) {
            return out;
        }
        for (MedicationLine m : ctx.activeMedications()) {
            if (m.genericName().contains("gentamicin")) {
                out.add(new RuleAlert(
                        "NEONATAL_GENTAMICIN_SPECIALIST",
                        "HIGH",
                        "Gentamicin in neonates must follow weight- and age-based dosing with monitoring; specialist centre recommended.",
                        "Neonatal pharmacokinetics differ; avoid adult dosing logic.",
                        true,
                        false
                ));
            }
        }
        return out;
    }

    private List<RuleAlert> stewardshipRules(ClinicalEvaluationContext ctx) {
        List<RuleAlert> out = new ArrayList<>();
        if (ctx.empiricBroadSpectrumDays() == null) {
            return out;
        }
        boolean broad = ctx.activeMedications().stream().anyMatch(MedicationLine::broadSpectrumAntibiotic);
        if (broad && ctx.empiricBroadSpectrumDays() > stewardshipBroadSpectrumDays) {
            out.add(new RuleAlert(
                    "STEWARDSHIP_BROAD_SPECTRUM_DURATION",
                    "MEDIUM",
                    "Empiric broad-spectrum therapy has exceeded the stewardship review threshold. Reassess diagnosis, narrow therapy, and document plan.",
                    "Prolonged empiric broad-spectrum use increases resistance and adverse events.",
                    false,
                    true
            ));
        }
        if (Boolean.TRUE.equals(ctx.cultureDocumented()) && broad) {
            out.add(new RuleAlert(
                    "STEWARDSHIP_DIRECT_THERAPY_HINT",
                    "LOW",
                    "Culture or sensitivity data are documented — review for directed or narrower therapy where clinically appropriate.",
                    "Guideline-aligned stewardship encourages de-escalation when microbiology supports it.",
                    false,
                    true
            ));
        }
        return out;
    }

    private List<RuleAlert> levelOfCareRules(ClinicalEvaluationContext ctx) {
        List<RuleAlert> out = new ArrayList<>();
        if (ctx.facilityLevel() == null) {
            return out;
        }
        String fac = ctx.facilityLevel().toUpperCase(Locale.ROOT);
        int facRank = rank(fac);
        // Specialist-only medicines (per national-formulary facility-level tables,
        // supplied on the medication context) may not be routinely initiated below a
        // specialist (S) facility — flag for referral / specialist authorisation.
        if (facRank < rank("S")) {
            for (MedicationLine m : ctx.activeMedications()) {
                if (m.specialistOnly()) {
                    String med = (m.displayName() != null && !m.displayName().isBlank())
                            ? m.displayName() : m.genericName();
                    out.add(new RuleAlert(
                            "LEVEL_OF_CARE_SPECIALIST_ONLY_MEDICINE",
                            "HIGH",
                            med + " is designated specialist-only and may exceed this facility's level-of-care scope — confirm referral or specialist authorisation.",
                            "National formulary facility-level tables restrict specialist-only medicines to specialist (S) centres.",
                            true,
                            true
                    ));
                }
            }
        }
        if (facRank < rank("S")) {
            for (MedicationLine m : ctx.activeMedications()) {
                if (m.genericName().contains("gentamicin") && ctx.ageDays() != null && ctx.ageDays() <= 28) {
                    out.add(new RuleAlert(
                            "LEVEL_OF_CARE_NEONATAL_SPECIALIST",
                            "HIGH",
                            "Neonatal parenteral therapy may exceed routine primary facility scope — confirm referral pathway.",
                            "Facility level C/B/A/S restrictions apply to medicines and age groups per national formulary tables.",
                            true,
                            true
                    ));
                }
            }
        }
        return out;
    }

    private int rank(String level) {
        return switch (level) {
            case "C" -> 1;
            case "B" -> 2;
            case "A" -> 3;
            case "S" -> 4;
            default -> 0;
        };
    }

    private List<RuleAlert> lastResortAbxRules(ClinicalEvaluationContext ctx) {
        List<RuleAlert> out = new ArrayList<>();
        if (!Boolean.TRUE.equals(ctx.lastResortAntibioticWithoutAstJustification())) {
            return out;
        }
        out.add(new RuleAlert(
                "STEWARDSHIP_LAST_RESORT_AST",
                "HIGH",
                "Last-resort antimicrobial use requires documented microbiology or governance-approved justification.",
                "Audit and stewardship controls apply to reserve agents.",
                true,
                false
        ));
        return out;
    }
}
