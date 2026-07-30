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

import java.util.UUID;

/**
 * surgery-service client — the surgical episode spine (S1), general surgical assessment (S2)
 * and surgical decision-making record (S3). Base URL config
 * {@code impilo.services.surgery-base-url} / {@code SURGERY_BASE_URL}, default port 8396.
 *
 * <p>Unlike its sibling {@link ProceduresServiceClient}, surgery-service IS a store — the
 * episode, assessment and decision rows are its own. Writes therefore exist here, and every
 * write sends an explicit {@code application/json} content type: {@code RestTemplate} with a
 * raw String body defaults to text/plain, which surgery-service's {@code @RequestBody} binding
 * rejects with 415 (the bug {@code postJson} on {@code TelemonitoringServiceClient} exists to
 * avoid).</p>
 *
 * <p>Episode ids are validated as UUIDs at the BFF controller boundary (Spring binds
 * {@code UUID} path variables), which is also what keeps the ext_authz resource-type
 * derivation stable — the PDP skips UUID path segments, so the fixed words "episodes",
 * "assessment", "decision", "transition", "link-procedure-episode" are always the derived
 * resource_type V302 pins on.</p>
 *
 * <p>All payloads pass through verbatim; failures propagate as real exceptions (see
 * {@code SurgeryController} for the empty/unknown/unavailable contract).</p>
 */
@Component
public class SurgeryServiceClient {

    private static final Logger log = LoggerFactory.getLogger(SurgeryServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public SurgeryServiceClient(RestTemplate serviceRestTemplate,
                                ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.surgeryBaseUrl();
    }

    /** Open a surgical episode — POST /internal/v1/surgery/episodes */
    public ResponseEntity<String> openEpisode(String requestBody) {
        log.info("Surgery: opening surgical episode");
        return exchangeJson(HttpMethod.POST, baseUrl + "/internal/v1/surgery/episodes", requestBody);
    }

    /** Episodes for a subject — GET /internal/v1/surgery/episodes?subjectCpid=... */
    public ResponseEntity<String> episodesForSubject(String subjectCpid) {
        log.info("Surgery: listing episodes for subject");
        String url = UriComponentsBuilder.fromUriString(baseUrl + "/internal/v1/surgery/episodes")
                .queryParam("subjectCpid", subjectCpid)
                .toUriString();
        return restTemplate.getForEntity(url, String.class);
    }

    /** One episode — GET /internal/v1/surgery/episodes/{id} */
    public ResponseEntity<String> episode(UUID id) {
        log.info("Surgery: fetching episode {}", id);
        return restTemplate.getForEntity(baseUrl + "/internal/v1/surgery/episodes/" + id, String.class);
    }

    /** Link the executing procedure episode — POST .../{id}/link-procedure-episode */
    public ResponseEntity<String> linkProcedureEpisode(UUID id, String requestBody) {
        log.info("Surgery: linking procedure episode onto {}", id);
        return exchangeJson(HttpMethod.POST,
                baseUrl + "/internal/v1/surgery/episodes/" + id + "/link-procedure-episode", requestBody);
    }

    /** Episode state transition — POST .../{id}/transition */
    public ResponseEntity<String> transition(UUID id, String requestBody) {
        log.info("Surgery: transitioning episode {}", id);
        return exchangeJson(HttpMethod.POST,
                baseUrl + "/internal/v1/surgery/episodes/" + id + "/transition", requestBody);
    }

    /** Record/refine the general surgical assessment — PUT .../{episodeId}/assessment (S2) */
    public ResponseEntity<String> recordAssessment(UUID episodeId, String requestBody) {
        log.info("Surgery: recording assessment for episode {}", episodeId);
        return exchangeJson(HttpMethod.PUT,
                baseUrl + "/internal/v1/surgery/episodes/" + episodeId + "/assessment", requestBody);
    }

    /** Read the general surgical assessment — GET .../{episodeId}/assessment */
    public ResponseEntity<String> assessment(UUID episodeId) {
        log.info("Surgery: fetching assessment for episode {}", episodeId);
        return restTemplate.getForEntity(
                baseUrl + "/internal/v1/surgery/episodes/" + episodeId + "/assessment", String.class);
    }

    /** Record/refine the surgical decision-making record — PUT .../{episodeId}/decision (S3) */
    public ResponseEntity<String> recordDecision(UUID episodeId, String requestBody) {
        log.info("Surgery: recording decision for episode {}", episodeId);
        return exchangeJson(HttpMethod.PUT,
                baseUrl + "/internal/v1/surgery/episodes/" + episodeId + "/decision", requestBody);
    }

    /** Read the surgical decision-making record — GET .../{episodeId}/decision */
    public ResponseEntity<String> decision(UUID episodeId) {
        log.info("Surgery: fetching decision for episode {}", episodeId);
        return restTemplate.getForEntity(
                baseUrl + "/internal/v1/surgery/episodes/" + episodeId + "/decision", String.class);
    }

    /** Reopen for a return to theatre — POST .../{episodeId}/reopen (V010, demonstration 9) */
    public ResponseEntity<String> reopen(UUID episodeId, String requestBody) {
        log.info("Surgery: reopening episode {} for a return to theatre", episodeId);
        return exchangeJson(HttpMethod.POST,
                baseUrl + "/internal/v1/surgery/episodes/" + episodeId + "/reopen", requestBody);
    }

    /** Every specialty on a shared case — GET .../{episodeId}/specialties (V011, demonstration 4) */
    public ResponseEntity<String> specialties(UUID episodeId) {
        log.info("Surgery: listing specialties on episode {}", episodeId);
        return restTemplate.getForEntity(
                baseUrl + "/internal/v1/surgery/episodes/" + episodeId + "/specialties", String.class);
    }

    /** Add a joining specialty — POST .../{episodeId}/specialties */
    public ResponseEntity<String> addSpecialty(UUID episodeId, String requestBody) {
        log.info("Surgery: adding a specialty to episode {}", episodeId);
        return exchangeJson(HttpMethod.POST,
                baseUrl + "/internal/v1/surgery/episodes/" + episodeId + "/specialties", requestBody);
    }

    /** Hand the lead over — POST .../{episodeId}/specialties/lead */
    public ResponseEntity<String> transferLead(UUID episodeId, String requestBody) {
        log.info("Surgery: transferring the lead specialty on episode {}", episodeId);
        return exchangeJson(HttpMethod.POST,
                baseUrl + "/internal/v1/surgery/episodes/" + episodeId + "/specialties/lead", requestBody);
    }

    /**
     * Remove a joining specialty — DELETE .../{episodeId}/specialties?specialty=...
     *
     * <p>The specialty is a query parameter, never a path segment: as a segment it would become
     * the ext_authz derived resource type and V303's rows could never match it. Encoded through
     * {@code UriComponentsBuilder} for the same reason the subject-cpid list route is.</p>
     */
    public ResponseEntity<String> removeSpecialty(UUID episodeId, String specialty) {
        log.info("Surgery: removing a specialty from episode {}", episodeId);
        String url = UriComponentsBuilder
                .fromUriString(baseUrl + "/internal/v1/surgery/episodes/" + episodeId + "/specialties")
                .queryParam("specialty", specialty)
                .toUriString();
        return restTemplate.exchange(url, HttpMethod.DELETE, HttpEntity.EMPTY, String.class);
    }

    private ResponseEntity<String> exchangeJson(HttpMethod method, String url, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url, method, new HttpEntity<>(body, headers), String.class);
    }
}
