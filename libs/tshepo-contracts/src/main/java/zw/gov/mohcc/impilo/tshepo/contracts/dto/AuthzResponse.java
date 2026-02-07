package zw.gov.mohcc.impilo.tshepo.contracts.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.Verdict;

import java.util.List;
import java.util.Map;

/**
 * Authorization response from TSHEPO.
 *
 * <ul>
 *   <li>ALLOW — request proceeds with obligations injected as headers</li>
 *   <li>DENY — request rejected with errorCode</li>
 *   <li>STEP_UP_REQUIRED — client must present step-up credential</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthzResponse(
        Verdict verdict,
        Obligations obligations,
        String errorCode,
        String errorMessage,
        List<String> stepUpMethods,
        int riskScore,
        Map<String, String> headerMutations
) {

    public static AuthzResponse allow(Obligations obligations, int riskScore,
                                       Map<String, String> headerMutations) {
        return new AuthzResponse(Verdict.ALLOW, obligations, null, null, null,
                riskScore, headerMutations);
    }

    public static AuthzResponse deny(String errorCode, String errorMessage, int riskScore) {
        return new AuthzResponse(Verdict.DENY, null, errorCode, errorMessage, null,
                riskScore, null);
    }

    public static AuthzResponse stepUp(List<String> methods, int riskScore) {
        return new AuthzResponse(Verdict.STEP_UP_REQUIRED, null, "STEP_UP_REQUIRED",
                "Step-up authentication required", methods, riskScore, null);
    }
}
