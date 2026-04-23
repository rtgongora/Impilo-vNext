package zw.gov.mohcc.impilo.vito.core.biometric.policy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * JSON shape returned by Tshepo {@code POST /v1/biometric-policy/evaluate}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TshepoBiometricPolicyResponse(
        String policyOutcome,
        boolean enrollmentAllowed,
        boolean verificationAllowed,
        boolean identificationAllowed,
        boolean dedupSupportAllowed,
        boolean fallbackAllowed,
        Long matchedRuleId,
        List<String> reasons) {
}
