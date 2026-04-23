package zw.gov.mohcc.impilo.vito.core.patientshare.policy;

import java.util.List;

public record PatientSharePolicyEvaluationResponse(
        String policyOutcome,
        boolean permitRead,
        boolean permitWrite,
        boolean permitTempProviderId,
        boolean requireCouncilRegistration,
        Long matchedRuleId,
        List<String> reasons) {
}
