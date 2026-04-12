package zw.gov.mohcc.impilo.fhirgateway.core;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.fhirgateway.events.ConsentCacheService;

import java.util.Set;
import java.util.UUID;

/**
 * Consent enforcement boundary for the FHIR Gateway.
 *
 * <p>Before any FHIR resource is forwarded to BUTANO (the Shared Health Record),
 * this service evaluates whether the requesting actor has consent to access the
 * subject's clinical data. This enforces the Health OS "Privacy by Architecture"
 * and "Trust Layer" doctrine principles.</p>
 *
 * <h3>Behaviour</h3>
 * <ul>
 *   <li>Calls tshepo-consent-service GET /v1/consent/evaluate</li>
 *   <li>If consent service is unreachable, defaults to DENY (fail-closed)</li>
 *   <li>If purposeOfUse is BREAK_GLASS, consent check is bypassed but audit is enhanced</li>
 *   <li>Public-health resource types (e.g. CapabilityStatement, StructureDefinition)
 *       return NOT_APPLICABLE without calling the consent service</li>
 * </ul>
 */
@Service
public class ConsentEnforcementService {

    private static final Logger log = LoggerFactory.getLogger(ConsentEnforcementService.class);

    /**
     * FHIR resource types that are public metadata and do not require consent.
     * These are conformance/infrastructure resources per FHIR R4.
     */
    private static final Set<String> CONSENT_EXEMPT_RESOURCE_TYPES = Set.of(
            "CapabilityStatement",
            "StructureDefinition",
            "SearchParameter",
            "OperationDefinition",
            "CompartmentDefinition",
            "CodeSystem",
            "ValueSet",
            "ConceptMap",
            "NamingSystem",
            "ImplementationGuide"
    );

    private static final String BREAK_GLASS_PURPOSE = "BREAK_GLASS";

    private final RestTemplate restTemplate;
    private final String consentServiceBaseUrl;
    private final ConsentCacheService consentCacheService;

    public ConsentEnforcementService(
            RestTemplate consentRestTemplate,
            @Value("${fhir-gateway.consent.base-url:http://localhost:8182}") String consentServiceBaseUrl,
            ConsentCacheService consentCacheService) {
        this.restTemplate = consentRestTemplate;
        this.consentServiceBaseUrl = consentServiceBaseUrl;
        this.consentCacheService = consentCacheService;
    }

    /**
     * Evaluate consent for a FHIR resource access request.
     *
     * @param actorId       the actor requesting access (Health ID)
     * @param subjectCpid   the patient CPID whose data is being accessed
     * @param resourceType  the FHIR resource type being accessed
     * @param purposeOfUse  the declared purpose of use (e.g. TREATMENT, BREAK_GLASS)
     * @param tenantId      the tenant UUID
     * @return the consent outcome governing whether forwarding should proceed
     */
    public ConsentOutcome evaluate(String actorId, String subjectCpid,
                                   String resourceType, String purposeOfUse,
                                   UUID tenantId) {

        // 1. Public-health / conformance resources do not require consent
        if (CONSENT_EXEMPT_RESOURCE_TYPES.contains(resourceType)) {
            log.debug("Consent not required for conformance resource type={}", resourceType);
            return ConsentOutcome.NOT_APPLICABLE;
        }

        // 2. Break-glass bypass — consent check is skipped but decision is recorded
        if (BREAK_GLASS_PURPOSE.equalsIgnoreCase(purposeOfUse)) {
            log.warn("BREAK_GLASS consent bypass: actor={} subject={} resourceType={} tenant={}",
                    actorId, subjectCpid, resourceType, tenantId);
            return ConsentOutcome.BREAK_GLASS_BYPASS;
        }

        // 3. If no subject CPID is available, we cannot evaluate consent
        if (subjectCpid == null || subjectCpid.isBlank()) {
            log.debug("No subjectCpid provided; consent evaluation not applicable for resourceType={}",
                    resourceType);
            return ConsentOutcome.NOT_APPLICABLE;
        }

        // 4. Local revocation cache (Kafka-fed) — hot path avoids REST latency when revoked
        if (consentCacheService.isConsentRevoked(tenantId.toString(), subjectCpid)) {
            log.info("Consent DENY (cached revocation): actor={} subject={} resourceType={} tenant={}",
                    actorId, subjectCpid, resourceType, tenantId);
            return ConsentOutcome.DENY;
        }

        // 5. Call tshepo-consent-service
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl(consentServiceBaseUrl + "/v1/consent/evaluate")
                    .queryParam("tenantId", tenantId)
                    .queryParam("actorId", actorId)
                    .queryParam("subjectRef", subjectCpid)
                    .queryParam("purpose", purposeOfUse != null ? purposeOfUse : "TREATMENT")
                    .queryParam("scope", mapResourceTypeToScope(resourceType))
                    .toUriString();

            log.info("Evaluating consent: actor={} subject={} resourceType={} tenant={}",
                    actorId, subjectCpid, resourceType, tenantId);

            JsonNode response = restTemplate.getForObject(url, JsonNode.class);

            if (response == null) {
                log.warn("Consent service returned null response — fail-closed to DENY");
                return ConsentOutcome.DENY;
            }

            // The consent service wraps the result in ApiResponse { data: ConsentDecision }
            JsonNode data = response.has("data") ? response.get("data") : response;
            boolean permitted = data.has("permitted") && data.get("permitted").asBoolean(false);

            if (permitted) {
                log.info("Consent PERMIT: actor={} subject={} resourceType={}",
                        actorId, subjectCpid, resourceType);
                return ConsentOutcome.PERMIT;
            } else {
                String reason = data.has("reason") ? data.get("reason").asText("") : "DENIED";
                log.info("Consent DENY: actor={} subject={} resourceType={} reason={}",
                        actorId, subjectCpid, resourceType, reason);
                return ConsentOutcome.DENY;
            }
        } catch (Exception e) {
            // Fail-closed: if consent service is unreachable, DENY access
            log.warn("Consent service unreachable — fail-closed to DENY: actor={} subject={} resourceType={} error={}",
                    actorId, subjectCpid, resourceType, e.getMessage());
            return ConsentOutcome.DENY;
        }
    }

    /**
     * Maps a FHIR resource type to a consent scope string.
     * Clinical resources map to "clinical-data"; administrative to "admin-data".
     */
    private String mapResourceTypeToScope(String resourceType) {
        return switch (resourceType) {
            case "Patient", "Observation", "Condition", "Procedure",
                 "DiagnosticReport", "MedicationRequest", "MedicationAdministration",
                 "AllergyIntolerance", "Immunization", "CarePlan",
                 "Encounter", "ClinicalImpression", "Composition" -> "clinical-data";
            case "Claim", "ExplanationOfBenefit", "Coverage" -> "financial-data";
            default -> "clinical-data";
        };
    }
}
