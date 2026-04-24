package zw.gov.mohcc.impilo.tshepo.api.patientshare;

/**
 * Request for patient-share / external-provider collaboration policy evaluation.
 *
 * @param workflowAction One of SHARE_CREATE, EXTERNAL_ACTIVATE, TEMP_PROVIDER_ISSUE, CONTRIBUTION_WRITE
 * @param scopeType      Concrete scope (e.g. LIMITED_CLINICAL_SUMMARY); matched against rule scope_pattern
 * @param actorType      CITIZEN, EXTERNAL_PROVIDER, SYSTEM
 * @param trustLevel     Current progressive trust label (e.g. SELF_ASSERTED, COUNCIL_NUMBER_FOUND)
 * @param councilRegistrationSupplied Whether council registration number was supplied (policy may require it)
 * @param councilRegistrationMatched Whether Varapi found a trusted council registration match
 */
public record PatientSharePolicyEvaluateRequest(
        String workflowAction,
        String scopeType,
        String actorType,
        String trustLevel,
        Boolean councilRegistrationSupplied,
        Boolean councilRegistrationMatched) {
}
