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

    // ── Course-of-care (parity Wave A); gated by V304 ──

    /** Record/refine a prehabilitation item — PUT .../{episodeId}/prehab */
    public ResponseEntity<String> recordPrehab(UUID episodeId, String requestBody) {
        log.info("Surgery: recording prehab item for episode {}", episodeId);
        return exchangeJson(HttpMethod.PUT,
                baseUrl + "/internal/v1/surgery/episodes/" + episodeId + "/prehab", requestBody);
    }

    /** List prehabilitation items — GET .../{episodeId}/prehab */
    public ResponseEntity<String> prehab(UUID episodeId) {
        log.info("Surgery: listing prehab items for episode {}", episodeId);
        return restTemplate.getForEntity(
                baseUrl + "/internal/v1/surgery/episodes/" + episodeId + "/prehab", String.class);
    }

    /** Recognise a complication pathway — POST .../{episodeId}/complications */
    public ResponseEntity<String> recogniseComplication(UUID episodeId, String requestBody) {
        log.info("Surgery: recognising complication on episode {}", episodeId);
        return exchangeJson(HttpMethod.POST,
                baseUrl + "/internal/v1/surgery/episodes/" + episodeId + "/complications", requestBody);
    }

    /** List complication pathways — GET .../{episodeId}/complications */
    public ResponseEntity<String> complications(UUID episodeId) {
        log.info("Surgery: listing complications for episode {}", episodeId);
        return restTemplate.getForEntity(
                baseUrl + "/internal/v1/surgery/episodes/" + episodeId + "/complications", String.class);
    }

    /** Update a complication pathway — PUT .../{episodeId}/complications/{pathwayId} */
    public ResponseEntity<String> updateComplication(UUID episodeId, UUID pathwayId, String requestBody) {
        log.info("Surgery: updating complication {} on episode {}", pathwayId, episodeId);
        return exchangeJson(HttpMethod.PUT,
                baseUrl + "/internal/v1/surgery/episodes/" + episodeId + "/complications/" + pathwayId,
                requestBody);
    }

    /** Grade a complication — POST .../complications/{pathwayId}/grade */
    public ResponseEntity<String> gradeComplication(UUID episodeId, UUID pathwayId, String requestBody) {
        log.info("Surgery: grading complication {} on episode {}", pathwayId, episodeId);
        return exchangeJson(HttpMethod.POST,
                baseUrl + "/internal/v1/surgery/episodes/" + episodeId + "/complications/"
                        + pathwayId + "/grade",
                requestBody);
    }

    /** Disclose a complication — POST .../complications/{pathwayId}/disclose */
    public ResponseEntity<String> discloseComplication(UUID episodeId, UUID pathwayId, String requestBody) {
        log.info("Surgery: disclosing complication {} on episode {}", pathwayId, episodeId);
        return exchangeJson(HttpMethod.POST,
                baseUrl + "/internal/v1/surgery/episodes/" + episodeId + "/complications/"
                        + pathwayId + "/disclose",
                requestBody);
    }

    /** Close a complication — POST .../complications/{pathwayId}/close */
    public ResponseEntity<String> closeComplication(UUID episodeId, UUID pathwayId, String requestBody) {
        log.info("Surgery: closing complication {} on episode {}", pathwayId, episodeId);
        return exchangeJson(HttpMethod.POST,
                baseUrl + "/internal/v1/surgery/episodes/" + episodeId + "/complications/"
                        + pathwayId + "/close",
                requestBody);
    }

    /** Place a longitudinal object — POST .../{episodeId}/longitudinal-objects */
    public ResponseEntity<String> placeLongitudinalObject(UUID episodeId, String requestBody) {
        log.info("Surgery: placing longitudinal object on episode {}", episodeId);
        return exchangeJson(HttpMethod.POST,
                baseUrl + "/internal/v1/surgery/episodes/" + episodeId + "/longitudinal-objects",
                requestBody);
    }

    /** List longitudinal objects — GET .../{episodeId}/longitudinal-objects */
    public ResponseEntity<String> longitudinalObjects(UUID episodeId) {
        log.info("Surgery: listing longitudinal objects for episode {}", episodeId);
        return restTemplate.getForEntity(
                baseUrl + "/internal/v1/surgery/episodes/" + episodeId + "/longitudinal-objects",
                String.class);
    }

    /** Remove a longitudinal object — POST .../longitudinal-objects/{objectId}/remove */
    public ResponseEntity<String> removeLongitudinalObject(UUID episodeId, UUID objectId, String requestBody) {
        log.info("Surgery: removing longitudinal object {} on episode {}", objectId, episodeId);
        return exchangeJson(HttpMethod.POST,
                baseUrl + "/internal/v1/surgery/episodes/" + episodeId + "/longitudinal-objects/"
                        + objectId + "/remove",
                requestBody != null ? requestBody : "{}");
    }

    /** Revise a longitudinal object — POST .../longitudinal-objects/{objectId}/revise */
    public ResponseEntity<String> reviseLongitudinalObject(UUID episodeId, UUID objectId, String requestBody) {
        log.info("Surgery: revising longitudinal object {} on episode {}", objectId, episodeId);
        return exchangeJson(HttpMethod.POST,
                baseUrl + "/internal/v1/surgery/episodes/" + episodeId + "/longitudinal-objects/"
                        + objectId + "/revise",
                requestBody != null ? requestBody : "{}");
    }

    /** Record/refine follow-up — PUT .../{episodeId}/followup */
    public ResponseEntity<String> recordFollowup(UUID episodeId, String requestBody) {
        log.info("Surgery: recording follow-up for episode {}", episodeId);
        return exchangeJson(HttpMethod.PUT,
                baseUrl + "/internal/v1/surgery/episodes/" + episodeId + "/followup", requestBody);
    }

    /** Read follow-up — GET .../{episodeId}/followup */
    public ResponseEntity<String> followup(UUID episodeId) {
        log.info("Surgery: fetching follow-up for episode {}", episodeId);
        return restTemplate.getForEntity(
                baseUrl + "/internal/v1/surgery/episodes/" + episodeId + "/followup", String.class);
    }

    /** Append a waiting-list revalidation — POST .../{episodeId}/waitlist-revalidation */
    public ResponseEntity<String> revalidateWaitlist(UUID episodeId, String requestBody) {
        log.info("Surgery: appending waitlist revalidation for episode {}", episodeId);
        return exchangeJson(HttpMethod.POST,
                baseUrl + "/internal/v1/surgery/episodes/" + episodeId + "/waitlist-revalidation",
                requestBody);
    }

    /** List waiting-list revalidations — GET .../{episodeId}/waitlist-revalidation */
    public ResponseEntity<String> waitlistRevalidations(UUID episodeId) {
        log.info("Surgery: listing waitlist revalidations for episode {}", episodeId);
        return restTemplate.getForEntity(
                baseUrl + "/internal/v1/surgery/episodes/" + episodeId + "/waitlist-revalidation",
                String.class);
    }

    private ResponseEntity<String> exchangeJson(HttpMethod method, String url, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url, method, new HttpEntity<>(body, headers), String.class);
    }
}
