package zw.gov.mohcc.impilo.vito.core.patientshare.policy;

public record PatientSharePolicyEvaluateRequest(
        String workflowAction,
        String scopeType,
        String actorType,
        String trustLevel,
        Boolean councilRegistrationSupplied,
        Boolean councilRegistrationMatched) {
}
