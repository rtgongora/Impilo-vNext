package zw.gov.mohcc.impilo.mvumo.engine;

import zw.gov.mohcc.impilo.mvumo.domain.ConsentAssuranceLevel;

import java.util.List;
import java.util.Map;

/**
 * Rules-based consent requirement evaluation. Integrate with {@code rules-service} for production policy packs.
 */
public interface ConsentRequirementEngine {

    record RequirementEvaluationRequest(
            String workflow,
            String consentType,
            String clinicalRisk,
            String dataSensitivity,
            Integer patientAge,
            String connectivity,
            String channelHint,
            Map<String, Object> extension) {}

    record RequirementEvaluationResult(
            boolean consentRequired,
            ConsentAssuranceLevel minimumAssurance,
            List<String> acceptableMethodCategories,
            boolean guardianRequired,
            boolean witnessRequired,
            boolean interpreterRequired,
            boolean remoteAllowed,
            boolean offlineAllowed,
            String nextAction,
            String rationale) {}

    RequirementEvaluationResult evaluate(RequirementEvaluationRequest request);
}
