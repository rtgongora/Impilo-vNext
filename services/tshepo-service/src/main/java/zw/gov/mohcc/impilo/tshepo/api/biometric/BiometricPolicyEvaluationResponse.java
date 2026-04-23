package zw.gov.mohcc.impilo.tshepo.api.biometric;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BiometricPolicyEvaluationResponse(
        String policyOutcome,
        boolean enrollmentAllowed,
        boolean verificationAllowed,
        boolean identificationAllowed,
        boolean dedupSupportAllowed,
        boolean fallbackAllowed,
        Long matchedRuleId,
        List<String> reasons) {
}
