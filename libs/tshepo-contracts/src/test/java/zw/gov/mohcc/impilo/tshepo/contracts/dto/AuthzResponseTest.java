package zw.gov.mohcc.impilo.tshepo.contracts.dto;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.Verdict;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthzResponseTest {

    @Test
    void allow_setsVerdictToAllow() {
        Obligations obligations = Obligations.NONE;
        Map<String, String> headers = Map.of("x-max-scope", "FACILITY");

        AuthzResponse response = AuthzResponse.allow(obligations, 15, headers);

        assertThat(response.verdict()).isEqualTo(Verdict.ALLOW);
        assertThat(response.obligations()).isEqualTo(obligations);
        assertThat(response.riskScore()).isEqualTo(15);
        assertThat(response.headerMutations()).containsEntry("x-max-scope", "FACILITY");
        assertThat(response.errorCode()).isNull();
        assertThat(response.errorMessage()).isNull();
        assertThat(response.stepUpMethods()).isNull();
    }

    @Test
    void deny_setsVerdictToDeny_withErrorCodeAndMessage() {
        AuthzResponse response = AuthzResponse.deny("FORBIDDEN", "Access denied by policy", 85);

        assertThat(response.verdict()).isEqualTo(Verdict.DENY);
        assertThat(response.errorCode()).isEqualTo("FORBIDDEN");
        assertThat(response.errorMessage()).isEqualTo("Access denied by policy");
        assertThat(response.riskScore()).isEqualTo(85);
        assertThat(response.obligations()).isNull();
        assertThat(response.headerMutations()).isNull();
        assertThat(response.stepUpMethods()).isNull();
    }

    @Test
    void stepUp_setsVerdictToStepUpRequired_withMethods() {
        List<String> methods = List.of("OTP", "BIOMETRIC");

        AuthzResponse response = AuthzResponse.stepUp(methods, 70);

        assertThat(response.verdict()).isEqualTo(Verdict.STEP_UP_REQUIRED);
        assertThat(response.stepUpMethods()).containsExactly("OTP", "BIOMETRIC");
        assertThat(response.riskScore()).isEqualTo(70);
        assertThat(response.errorCode()).isEqualTo("STEP_UP_REQUIRED");
        assertThat(response.errorMessage()).isEqualTo("Step-up authentication required");
        assertThat(response.obligations()).isNull();
        assertThat(response.headerMutations()).isNull();
    }
}
