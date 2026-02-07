package zw.gov.mohcc.impilo.tshepo.authz.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.ConsentDecision;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST client for calling the tshepo-consent-service to evaluate consent.
 *
 * <p>For clinical resources (Patient, Encounter, Observation, DiagnosticReport,
 * MedicationRequest), the policy engine calls this service to verify that
 * the patient has granted consent for the requested access.</p>
 *
 * <p>Consent evaluation is skipped for EMERGENCY and BREAK_GLASS purposes
 * (those override consent but produce elevated audit records).</p>
 */
@Service
public class ConsentClient {

    private static final Logger log = LoggerFactory.getLogger(ConsentClient.class);

    private final RestTemplate restTemplate;
    private final AuthzProperties properties;

    public ConsentClient(RestTemplate restTemplate, AuthzProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    /**
     * Evaluate consent for a clinical resource access request.
     *
     * @param tenantId     tenant isolation
     * @param resourceType FHIR resource type (Patient, Encounter, etc.)
     * @param resourceId   resource identifier (CPID for patients)
     * @param actorId      the requesting actor
     * @param purposeOfUse declared purpose
     * @return consent decision (permitted or denied with reason)
     */
    public ConsentDecision evaluateConsent(UUID tenantId, String resourceType,
                                            String resourceId, String actorId,
                                            String purposeOfUse) {
        try {
            String url = properties.getConsentServiceUrl() + properties.getConsentEvaluatePath();

            Map<String, Object> body = new HashMap<>();
            body.put("tenantId", tenantId.toString());
            body.put("resourceType", resourceType);
            body.put("resourceId", resourceId);
            body.put("actorId", actorId);
            body.put("purposeOfUse", purposeOfUse);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<ConsentDecision> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    ConsentDecision.class
            );

            ConsentDecision decision = response.getBody();
            if (decision == null) {
                log.warn("Consent service returned null response for resource {} of actor {}",
                        resourceId, actorId);
                return ConsentDecision.noConsentFound();
            }

            return decision;

        } catch (Exception e) {
            log.error("Consent evaluation failed for resource={}, actor={}: {}",
                    resourceId, actorId, e.getMessage());
            // Fail-closed: if consent service is unavailable, deny access
            return ConsentDecision.deny("CONSENT_SERVICE_UNAVAILABLE");
        }
    }
}
