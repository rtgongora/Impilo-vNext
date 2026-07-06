package zw.gov.mohcc.impilo.varapi.core.collaboration;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CouncilAdapterRegistrationEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.CouncilAdapterRegistrationRepository;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Default HTTP-calling implementation of the live council verification seam
 * (IATG Wave 2, Channel A). Honestly gated OFF: this bean only exists when
 * {@code varapi.council-regulatory.live-adapter-enabled=true}. When present it still only
 * calls out for a council that has an {@code enabled} {@link CouncilAdapterRegistrationEntity};
 * otherwise it returns {@link Result#NOT_CONFIGURED} so the caller falls through to the
 * local imported-registry path.
 *
 * <p><b>No fabrication:</b> any transport failure, timeout, non-2xx status, or unusable
 * body yields {@link CouncilVerificationOutcome#unreachable()} — an honest degraded
 * outcome, never a fabricated PASS. Positive results and the confidence score are taken
 * verbatim from what the authority returned.</p>
 */
@Component
@ConditionalOnProperty(name = "varapi.council-regulatory.live-adapter-enabled", havingValue = "true")
public class HttpCouncilRegistryAdapter implements CouncilRegistryAdapter {

    private static final Logger log = LoggerFactory.getLogger(HttpCouncilRegistryAdapter.class);

    private final CouncilAdapterRegistrationRepository registrationRepository;

    public HttpCouncilRegistryAdapter(CouncilAdapterRegistrationRepository registrationRepository) {
        this.registrationRepository = registrationRepository;
    }

    @Override
    public CouncilVerificationOutcome verify(String councilCode, String registrationNumber, PersonContext personContext) {
        if (personContext == null || personContext.tenantId() == null
                || councilCode == null || councilCode.isBlank()
                || registrationNumber == null || registrationNumber.isBlank()) {
            return CouncilVerificationOutcome.notConfigured();
        }

        Optional<CouncilAdapterRegistrationEntity> regOpt = registrationRepository
                .findFirstByTenantIdAndCouncilCodeIgnoreCaseAndEnabledTrue(personContext.tenantId(), councilCode);
        if (regOpt.isEmpty()) {
            return CouncilVerificationOutcome.notConfigured();
        }
        CouncilAdapterRegistrationEntity registration = regOpt.get();

        try {
            RestTemplate restTemplate = restTemplateFor(registration);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("councilCode", councilCode);
            body.put("registrationNumber", registrationNumber.trim());
            if (personContext.givenName() != null) {
                body.put("givenName", personContext.givenName());
            }
            if (personContext.familyName() != null) {
                body.put("familyName", personContext.familyName());
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            applyAuth(headers, registration);

            ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                    registration.getEndpointUrl(), new HttpEntity<>(body, headers), JsonNode.class);

            if (response.getStatusCode() == null || !response.getStatusCode().is2xxSuccessful()
                    || response.getBody() == null) {
                // No usable authoritative answer — honest degraded, never a fabricated result.
                return CouncilVerificationOutcome.unreachable();
            }

            JsonNode answer = response.getBody();
            boolean matched = answer.path("matched").asBoolean(false);
            if (!matched) {
                if (answer.path("formatInvalid").asBoolean(false)) {
                    return CouncilVerificationOutcome.formatInvalid();
                }
                return CouncilVerificationOutcome.noMatch();
            }

            BigDecimal confidence = readConfidence(answer);
            String matchedRef = answer.path("recordRef").isMissingNode() || answer.path("recordRef").isNull()
                    ? null : answer.path("recordRef").asText();
            boolean identityMatch = answer.path("identityMatch").asBoolean(false);

            return identityMatch
                    ? CouncilVerificationOutcome.identityMatch(confidence, matchedRef)
                    : CouncilVerificationOutcome.registryMatch(confidence, matchedRef);

        } catch (RestClientException e) {
            log.warn("Live council adapter call failed for council={} endpoint={}: {}",
                    councilCode, registration.getEndpointUrl(), e.getMessage());
            return CouncilVerificationOutcome.unreachable();
        }
    }

    /**
     * Builds a per-registration {@link RestTemplate} honouring the configured connect/read
     * timeouts. Exposed as a protected seam so tests can substitute a mock-bound template
     * (no real endpoint is ever contacted in tests).
     */
    protected RestTemplate restTemplateFor(CouncilAdapterRegistrationEntity registration) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(registration.getConnectTimeoutMs());
        factory.setReadTimeout(registration.getReadTimeoutMs());
        return new RestTemplate(factory);
    }

    private static void applyAuth(HttpHeaders headers, CouncilAdapterRegistrationEntity registration) {
        String authType = registration.getAuthType();
        if (authType == null || "NONE".equalsIgnoreCase(authType)) {
            return;
        }
        // authConfigJson carries opaque auth material; header wiring for concrete auth
        // schemes (API_KEY / BEARER) is added when a real authority is onboarded. Until
        // then NONE is the only honoured scheme and anything else calls unauthenticated.
        log.debug("Council adapter auth type {} configured but not yet wired; calling as NONE", authType);
    }

    private static BigDecimal readConfidence(JsonNode answer) {
        JsonNode node = answer.path("confidence");
        if (node.isMissingNode() || node.isNull() || !node.isNumber()) {
            return null;
        }
        return node.decimalValue();
    }
}
