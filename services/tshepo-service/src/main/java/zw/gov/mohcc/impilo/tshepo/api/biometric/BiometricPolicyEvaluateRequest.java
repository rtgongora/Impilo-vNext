package zw.gov.mohcc.impilo.tshepo.api.biometric;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request to evaluate biometric policy for a concrete workflow.
 *
 * @param subjectType     CLIENT, PROVIDER, USER, PROXY, STAFF, …
 * @param workflowType    e.g. POINT_OF_CARE, CLIENT_REGISTRATION, PROVIDER_STEP_UP
 * @param contextType     FACILITY, COMMUNITY, VIRTUAL, LOGISTICS, SITE, …
 * @param modality        Optional concrete modality (FINGERPRINT, IRIS, FACE) for modality-specific rules
 * @param biometricIntent ENROLL, VERIFY, IDENTIFY, DEDUP_SUPPORT, STEP_UP
 * @param resourceSensitivity optional hint for future extensions (ignored in baseline matcher)
 * @param actorType           optional actor classification (PROVIDER, STAFF, SYSTEM, …) for role-scoped rules
 * @param assuranceLevel      optional numeric LOA (1–4) parsed from {@code X-Assurance-Level} upstream
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record BiometricPolicyEvaluateRequest(
        String subjectType,
        String workflowType,
        String contextType,
        String modality,
        String biometricIntent,
        String resourceSensitivity,
        String actorType,
        Integer assuranceLevel) {
}
