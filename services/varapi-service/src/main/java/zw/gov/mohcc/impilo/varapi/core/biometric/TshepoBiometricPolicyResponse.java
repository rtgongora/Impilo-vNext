package zw.gov.mohcc.impilo.varapi.core.biometric;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

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
