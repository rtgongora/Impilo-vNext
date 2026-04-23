package zw.gov.mohcc.impilo.tshepo.api.patientshare;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.tshepo.persistence.PatientSharePolicyRuleEntity;
import zw.gov.mohcc.impilo.tshepo.persistence.PatientSharePolicyRuleRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PatientSharePolicyEvaluationServiceTest {

    @Test
    void shareCreate_allowedForCitizen() {
        PatientSharePolicyRuleRepository repo = mock(PatientSharePolicyRuleRepository.class);
        when(repo.findByActiveFlagTrueOrderByPriorityDesc()).thenReturn(List.of(seedShareCreateRule()));
        PatientSharePolicyEvaluationService svc = new PatientSharePolicyEvaluationService(repo);
        PatientSharePolicyEvaluationResponse r = svc.evaluate(new PatientSharePolicyEvaluateRequest(
                "SHARE_CREATE", "LIMITED_CLINICAL_SUMMARY", "CITIZEN", null, false, false));
        assertEquals("ALLOWED", r.policyOutcome());
        assertTrue(r.permitRead());
        assertFalse(r.permitWrite());
    }

    @Test
    void externalActivate_selfAssertedReadOnlyWhenCouncilSupplied() {
        PatientSharePolicyRuleRepository repo = mock(PatientSharePolicyRuleRepository.class);
        when(repo.findByActiveFlagTrueOrderByPriorityDesc()).thenReturn(List.of(
                seedShareCreateRule(),
                seedExternalSelfAsserted()));
        PatientSharePolicyEvaluationService svc = new PatientSharePolicyEvaluationService(repo);
        PatientSharePolicyEvaluationResponse r = svc.evaluate(new PatientSharePolicyEvaluateRequest(
                "EXTERNAL_ACTIVATE", "LIMITED_CLINICAL_SUMMARY", "EXTERNAL_PROVIDER",
                PatientShareTrustLevels.SELF_ASSERTED, true, false));
        assertEquals("ALLOWED", r.policyOutcome());
        assertTrue(r.permitRead());
        assertFalse(r.permitWrite());
    }

    @Test
    void externalActivate_deniedWhenCouncilMissing() {
        PatientSharePolicyRuleRepository repo = mock(PatientSharePolicyRuleRepository.class);
        when(repo.findByActiveFlagTrueOrderByPriorityDesc()).thenReturn(List.of(
                seedShareCreateRule(),
                seedExternalSelfAsserted()));
        PatientSharePolicyEvaluationService svc = new PatientSharePolicyEvaluationService(repo);
        PatientSharePolicyEvaluationResponse r = svc.evaluate(new PatientSharePolicyEvaluateRequest(
                "EXTERNAL_ACTIVATE", "LIMITED_CLINICAL_SUMMARY", "EXTERNAL_PROVIDER",
                PatientShareTrustLevels.SELF_ASSERTED, false, false));
        assertEquals("PROHIBITED", r.policyOutcome());
    }

    @Test
    void contribution_write_requiresCouncilMatchWhenTrustHigh() {
        PatientSharePolicyRuleRepository repo = mock(PatientSharePolicyRuleRepository.class);
        PatientSharePolicyRuleEntity formatRule = new PatientSharePolicyRuleEntity();
        formatRule.setWorkflowAction("CONTRIBUTION_WRITE");
        formatRule.setScopePattern("*");
        formatRule.setActorPattern("EXTERNAL_PROVIDER");
        formatRule.setMinTrustLevel(PatientShareTrustLevels.COUNCIL_NUMBER_FORMAT_VALID);
        formatRule.setPolicyOutcome("ALLOWED");
        formatRule.setPermitRead(false);
        formatRule.setPermitWrite(true);
        formatRule.setPermitTempProviderId(false);
        formatRule.setRequireCouncilRegistration(true);
        formatRule.setNotes("format");
        formatRule.setPriority(110);
        formatRule.setActiveFlag(true);
        formatRule.setId(2L);

        PatientSharePolicyRuleEntity foundRule = new PatientSharePolicyRuleEntity();
        foundRule.setWorkflowAction("CONTRIBUTION_WRITE");
        foundRule.setScopePattern("*");
        foundRule.setActorPattern("EXTERNAL_PROVIDER");
        foundRule.setMinTrustLevel(PatientShareTrustLevels.COUNCIL_NUMBER_FOUND);
        foundRule.setPolicyOutcome("ALLOWED");
        foundRule.setPermitRead(false);
        foundRule.setPermitWrite(true);
        foundRule.setPermitTempProviderId(false);
        foundRule.setRequireCouncilRegistration(true);
        foundRule.setNotes("found");
        foundRule.setPriority(115);
        foundRule.setActiveFlag(true);
        foundRule.setId(3L);

        when(repo.findByActiveFlagTrueOrderByPriorityDesc()).thenReturn(List.of(foundRule, formatRule));
        PatientSharePolicyEvaluationService svc = new PatientSharePolicyEvaluationService(repo);

        PatientSharePolicyEvaluationResponse noMatch = svc.evaluate(new PatientSharePolicyEvaluateRequest(
                "CONTRIBUTION_WRITE", "LIMITED_CLINICAL_SUMMARY", "EXTERNAL_PROVIDER",
                PatientShareTrustLevels.COUNCIL_NUMBER_FOUND, true, false));
        assertEquals("PROHIBITED", noMatch.policyOutcome());

        PatientSharePolicyEvaluationResponse matched = svc.evaluate(new PatientSharePolicyEvaluateRequest(
                "CONTRIBUTION_WRITE", "LIMITED_CLINICAL_SUMMARY", "EXTERNAL_PROVIDER",
                PatientShareTrustLevels.COUNCIL_NUMBER_FOUND, true, true));
        assertEquals("ALLOWED", matched.policyOutcome());
        assertTrue(matched.permitWrite());
    }

    private static PatientSharePolicyRuleEntity seedShareCreateRule() {
        PatientSharePolicyRuleEntity row = new PatientSharePolicyRuleEntity();
        row.setId(1L);
        row.setWorkflowAction("SHARE_CREATE");
        row.setScopePattern("*");
        row.setActorPattern("CITIZEN");
        row.setMinTrustLevel(null);
        row.setPolicyOutcome("ALLOWED");
        row.setPermitRead(true);
        row.setPermitWrite(false);
        row.setPermitTempProviderId(false);
        row.setRequireCouncilRegistration(false);
        row.setNotes("seed");
        row.setPriority(200);
        row.setActiveFlag(true);
        return row;
    }

    private static PatientSharePolicyRuleEntity seedExternalSelfAsserted() {
        PatientSharePolicyRuleEntity row = new PatientSharePolicyRuleEntity();
        row.setId(10L);
        row.setWorkflowAction("EXTERNAL_ACTIVATE");
        row.setScopePattern("*");
        row.setActorPattern("EXTERNAL_PROVIDER");
        row.setMinTrustLevel(PatientShareTrustLevels.SELF_ASSERTED);
        row.setPolicyOutcome("ALLOWED");
        row.setPermitRead(true);
        row.setPermitWrite(false);
        row.setPermitTempProviderId(false);
        row.setRequireCouncilRegistration(true);
        row.setNotes("weak");
        row.setPriority(120);
        row.setActiveFlag(true);
        return row;
    }
}
