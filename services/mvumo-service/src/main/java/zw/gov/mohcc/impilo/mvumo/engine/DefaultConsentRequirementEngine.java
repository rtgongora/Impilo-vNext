package zw.gov.mohcc.impilo.mvumo.engine;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.mvumo.domain.ConsentAssuranceLevel;

import java.util.List;
import java.util.Locale;

/**
 * Default heuristic engine until policy packs from rules-service are wired.
 */
@Component
@Primary
public class DefaultConsentRequirementEngine implements ConsentRequirementEngine {

    @Override
    public RequirementEvaluationResult evaluate(RequirementEvaluationRequest request) {
        String type = request.consentType() == null ? "" : request.consentType().toUpperCase(Locale.ROOT);
        ConsentAssuranceLevel min = switch (type) {
            case "GOVERNANCE_BREAK_GLASS", "EMERGENCY_OVERRIDE" -> ConsentAssuranceLevel.L4_EMERGENCY_OVERRIDE;
            case "CLINICAL_PROCEDURE", "RESEARCH_DATA_USE", "SURGERY" -> ConsentAssuranceLevel.L3_WITNESSED_HIGH_ASSURANCE;
            case "CLINICAL_IMAGING", "CLINICAL_GENERAL_TREATMENT", "FINANCIAL_MEDICAL_AID" ->
                    ConsentAssuranceLevel.L2_VERIFIED;
            case "LIFE_ADMIN_ACK" -> ConsentAssuranceLevel.L0_RECORDED_ADMIN;
            default -> ConsentAssuranceLevel.L1_SIMPLE_DIGITAL;
        };
        if ("SUPERCRITICAL".equalsIgnoreCase(request.clinicalRisk())) {
            min = ConsentAssuranceLevel.L3_WITNESSED_HIGH_ASSURANCE;
        }
        return new RequirementEvaluationResult(
                true,
                min,
                List.of(
                        "PORTAL_CONFIRM",
                        "TOKEN_LINK",
                        "OTP",
                        "PIN",
                        "ASSISTED_FACILITY",
                        "PAPER_TO_DIGITAL"),
                type.contains("PAEDIATRIC") || (request.patientAge() != null && request.patientAge() < 16),
                min == ConsentAssuranceLevel.L3_WITNESSED_HIGH_ASSURANCE,
                "LOW_LITERACY".equalsIgnoreCase(request.channelHint()),
                !type.contains("BREAK_GLASS"),
                !"OFFLINE_CONFLICT".equalsIgnoreCase(request.connectivity()),
                "CREATE_OR_REUSE_CONSENT_REQUEST",
                "Heuristic default — replace with rules-service policy.");
    }
}
