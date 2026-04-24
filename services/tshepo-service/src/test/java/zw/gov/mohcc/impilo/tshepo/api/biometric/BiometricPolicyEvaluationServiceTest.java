package zw.gov.mohcc.impilo.tshepo.api.biometric;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.tshepo.persistence.BiometricPolicyRuleEntity;
import zw.gov.mohcc.impilo.tshepo.persistence.BiometricPolicyRuleRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BiometricPolicyEvaluationServiceTest {

    @Mock
    BiometricPolicyRuleRepository ruleRepository;

    @InjectMocks
    BiometricPolicyEvaluationService service;

    @Test
    void prohibitesWhenNoRuleMatches() {
        when(ruleRepository.findByActiveFlagTrueOrderByPriorityDesc()).thenReturn(List.of());
        BiometricPolicyEvaluationResponse r = service.evaluate(
                new BiometricPolicyEvaluateRequest(
                        "CLIENT", "POINT_OF_CARE", "FACILITY", "FINGERPRINT", "VERIFY", null, null, null));
        assertThat(r.policyOutcome()).isEqualTo("PROHIBITED");
        assertThat(r.verificationAllowed()).isFalse();
    }

    @Test
    void matchesFacilityPointOfCareVerify() {
        BiometricPolicyRuleEntity row = new BiometricPolicyRuleEntity();
        row.setId(99L);
        row.setSubjectType("CLIENT");
        row.setWorkflowType("POINT_OF_CARE");
        row.setContextType("FACILITY");
        row.setBiometricIntent("VERIFY");
        row.setPolicyOutcome("ALLOWED");
        row.setEnrollmentAllowed(false);
        row.setVerificationAllowed(true);
        row.setIdentificationAllowed(false);
        row.setDedupSupportAllowed(false);
        row.setFallbackAllowed(true);
        row.setActiveFlag(true);
        row.setPriority(100);

        when(ruleRepository.findByActiveFlagTrueOrderByPriorityDesc()).thenReturn(List.of(row));

        BiometricPolicyEvaluationResponse r = service.evaluate(
                new BiometricPolicyEvaluateRequest(
                        "CLIENT", "POINT_OF_CARE", "FACILITY", "FINGERPRINT", "VERIFY", null, null, null));
        assertThat(r.policyOutcome()).isEqualTo("ALLOWED");
        assertThat(r.verificationAllowed()).isTrue();
        assertThat(r.matchedRuleId()).isEqualTo(99L);
    }

    @Test
    void virtualIdentifyProhibitedFromWildcardRule() {
        BiometricPolicyRuleEntity row = new BiometricPolicyRuleEntity();
        row.setId(2L);
        row.setSubjectType("CLIENT");
        row.setWorkflowType("*");
        row.setContextType("VIRTUAL");
        row.setBiometricIntent("IDENTIFY");
        row.setPolicyOutcome("PROHIBITED");
        row.setEnrollmentAllowed(false);
        row.setVerificationAllowed(false);
        row.setIdentificationAllowed(false);
        row.setDedupSupportAllowed(false);
        row.setFallbackAllowed(true);
        row.setActiveFlag(true);
        row.setPriority(200);

        when(ruleRepository.findByActiveFlagTrueOrderByPriorityDesc()).thenReturn(List.of(row));

        BiometricPolicyEvaluationResponse r = service.evaluate(
                new BiometricPolicyEvaluateRequest(
                        "CLIENT", "ANYTHING", "VIRTUAL", null, "IDENTIFY", null, null, null));
        assertThat(r.policyOutcome()).isEqualTo("PROHIBITED");
        assertThat(r.identificationAllowed()).isFalse();
    }

    @Test
    void staffAssuranceRuleDoesNotMatchWrongActor() {
        BiometricPolicyRuleEntity row = new BiometricPolicyRuleEntity();
        row.setId(50L);
        row.setSubjectType("CLIENT");
        row.setWorkflowType("DELEGATED_PICKUP");
        row.setContextType("FACILITY");
        row.setBiometricIntent("VERIFY");
        row.setPolicyOutcome("OPTIONAL");
        row.setEnrollmentAllowed(false);
        row.setVerificationAllowed(true);
        row.setIdentificationAllowed(false);
        row.setDedupSupportAllowed(false);
        row.setFallbackAllowed(true);
        row.setActiveFlag(true);
        row.setPriority(95);
        row.setActorType("STAFF");
        row.setMinAssuranceLevel((short) 2);

        when(ruleRepository.findByActiveFlagTrueOrderByPriorityDesc()).thenReturn(List.of(row));

        BiometricPolicyEvaluationResponse missingActor = service.evaluate(
                new BiometricPolicyEvaluateRequest(
                        "CLIENT", "DELEGATED_PICKUP", "FACILITY", null, "VERIFY", null, null, 3));
        assertThat(missingActor.policyOutcome()).isEqualTo("PROHIBITED");

        BiometricPolicyEvaluationResponse wrongActor = service.evaluate(
                new BiometricPolicyEvaluateRequest(
                        "CLIENT", "DELEGATED_PICKUP", "FACILITY", null, "VERIFY", null, "PATIENT", 3));
        assertThat(wrongActor.policyOutcome()).isEqualTo("PROHIBITED");

        BiometricPolicyEvaluationResponse ok = service.evaluate(
                new BiometricPolicyEvaluateRequest(
                        "CLIENT", "DELEGATED_PICKUP", "FACILITY", null, "VERIFY", null, "STAFF", 3));
        assertThat(ok.policyOutcome()).isEqualTo("OPTIONAL");
        assertThat(ok.verificationAllowed()).isTrue();
    }
}
