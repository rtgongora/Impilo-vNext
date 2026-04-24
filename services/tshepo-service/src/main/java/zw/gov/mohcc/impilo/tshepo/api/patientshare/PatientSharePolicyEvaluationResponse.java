package zw.gov.mohcc.impilo.tshepo.api.patientshare;

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
