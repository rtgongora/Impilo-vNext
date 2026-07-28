package zw.gov.mohcc.impilo.experience.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

/**
 * procedures-service client — the Clinical Procedures Pipeline platform layer (catalogue,
 * appropriateness/duplication, competence and privilege resolution). Base URL config
 * {@code impilo.services.procedures-base-url} / {@code PROCEDURES_BASE_URL}, default port 8395.
 *
 * <p><b>Read/evaluate only, by construction.</b> procedures-service is engine-not-store
 * (ADR-SURGERY-AND-PROCEDURES-SERVICE-BOUNDARIES decision 2): it evaluates readiness,
 * competence and appropriateness and persists none of it. This client therefore has no write
 * methods and never will while that ADR holds — a write method here would be a place for a
 * second copy of a fact the catalogue, VARAPI or the executing service already owns.</p>
 *
 * <p>All payloads pass through verbatim as the caller's responsibility to interpret; see
 * {@code ProceduresController} for the empty/unknown/unavailable distinction this estate has
 * been bitten by before when a client collapsed them.</p>
 */
@Component
public class ProceduresServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ProceduresServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public ProceduresServiceClient(RestTemplate serviceRestTemplate,
                                   ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.proceduresBaseUrl();
    }

    /** Catalogue search — GET /internal/v1/procedures/catalogue?specialty=&category=&q= */
    public ResponseEntity<String> searchCatalogue(String specialty, String category, String q) {
        log.info("Procedures: searching catalogue (specialty={}, category={})", specialty, category);
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(baseUrl + "/internal/v1/procedures/catalogue");
        if (specialty != null && !specialty.isBlank()) builder.queryParam("specialty", specialty);
        if (category != null && !category.isBlank()) builder.queryParam("category", category);
        if (q != null && !q.isBlank()) builder.queryParam("q", q);
        return restTemplate.getForEntity(builder.toUriString(), String.class);
    }

    /**
     * Catalogue detail — GET .../catalogue/{code} on procedures-service.
     *
     * <p>Exposed to callers of THIS client at a fixed path
     * ({@code /internal/v1/procedures/catalogue-detail?code=...}), not as a REST path variable —
     * see {@code ProceduresController} for why: a free-text procedure code as the final Envoy
     * path segment defeats the ext_authz PDP's resource-type derivation, which only skips
     * UUID-shaped segments. This method calls procedures-service's own (already-published,
     * unauthenticated-at-this-hop) REST shape directly; only the BFF's OWN external contract
     * needed to change.</p>
     */
    public ResponseEntity<String> catalogueDetail(String definitionCode) {
        log.info("Procedures: fetching catalogue entry={}", definitionCode);
        return restTemplate.getForEntity(
                baseUrl + "/internal/v1/procedures/catalogue/" + definitionCode, String.class);
    }

    /**
     * Appropriateness and duplication check — POST /internal/v1/procedures/appropriateness/evaluate.
     *
     * <p>Explicit application/json content type. {@code RestTemplate.postForEntity} with a raw
     * String body defaults to text/plain, and procedures-service's {@code @RequestBody} binding
     * would reject that with 415 — the same class of bug {@code postJson} on
     * {@link TelemonitoringServiceClient} already exists to avoid.</p>
     */
    public ResponseEntity<String> evaluateAppropriateness(String requestBody) {
        log.info("Procedures: evaluating appropriateness");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(
                baseUrl + "/internal/v1/procedures/appropriateness/evaluate",
                HttpMethod.POST, new HttpEntity<>(requestBody, headers), String.class);
    }

    /** Competence resolution — GET /internal/v1/procedures/competence?providerId=&definitionCode= */
    public ResponseEntity<String> resolveCompetence(String providerId, String definitionCode) {
        log.info("Procedures: resolving competence for provider (definition={})", definitionCode);
        String url = UriComponentsBuilder.fromUriString(baseUrl + "/internal/v1/procedures/competence")
                .queryParam("providerId", providerId)
                .queryParam("definitionCode", definitionCode)
                .toUriString();
        return restTemplate.getForEntity(url, String.class);
    }
}
