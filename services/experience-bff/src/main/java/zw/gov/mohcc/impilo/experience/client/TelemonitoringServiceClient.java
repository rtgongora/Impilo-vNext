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
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

/**
 * OF-B23/B27/B28 — telemonitoring-service client (plans, programmes, device
 * assignments, readings, alert episodes) for the Wave OF-C monitoring surfaces.
 *
 * <p>Read/act proxy only: telemonitoring-service keeps plan + alert-episode
 * truth (registry: {@code must-not-own-task-source-of-truth} — PCT keeps task
 * SoR). Base URL config {@code impilo.services.telemonitoring-base-url} /
 * {@code TELEMONITORING_BASE_URL}, default port 8394.</p>
 *
 * <p>All payloads pass through verbatim (CPID-only by upstream construction —
 * the service never stores PII). Alert actions carry the caller's actor via the
 * standard trust-header interceptor; idempotency keys are set explicitly per
 * write so a caller-supplied key survives any BFF-side fan-out.</p>
 */
@Component
public class TelemonitoringServiceClient {

    private static final Logger log = LoggerFactory.getLogger(TelemonitoringServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public TelemonitoringServiceClient(RestTemplate serviceRestTemplate,
                                       ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.telemonitoringBaseUrl();
    }

    // ── Monitoring plans (OF-B22 lifecycle surface) ──

    /** Plans for one patient (CPID) — the upstream list is patient-scoped by contract. */
    public ResponseEntity<String> listPlansByPatient(String patientCpid) {
        log.info("Telemonitoring: listing plans for patient");
        String url = UriComponentsBuilder.fromUriString(baseUrl + "/v1/monitoring-plans")
                .queryParam("patientCpid", patientCpid)
                .toUriString();
        return restTemplate.getForEntity(url, String.class);
    }

    public ResponseEntity<String> getPlan(String planId) {
        log.info("Telemonitoring: fetching plan={}", planId);
        return restTemplate.getForEntity(baseUrl + "/v1/monitoring-plans/" + planId, String.class);
    }

    /** Record a completed clinical review — re-arms the review-cadence timer. */
    public ResponseEntity<String> recordReview(String planId, String requestBody, String idempotencyKey) {
        log.info("Telemonitoring: recording review on plan={}", planId);
        return postJson(baseUrl + "/v1/monitoring-plans/" + planId + "/reviews",
                requestBody != null ? requestBody : "{}", idempotencyKey);
    }

    /** Threshold-profile version history (current version = highest version). */
    public ResponseEntity<String> listThresholdProfiles(String planId) {
        log.info("Telemonitoring: listing threshold profiles for plan={}", planId);
        return restTemplate.getForEntity(
                baseUrl + "/v1/monitoring-plans/" + planId + "/threshold-profiles", String.class);
    }

    // ── Programme catalogue (§14.1) ──

    public ResponseEntity<String> listProgrammes() {
        log.info("Telemonitoring: listing active programmes");
        return restTemplate.getForEntity(baseUrl + "/v1/monitoring-programmes", String.class);
    }

    public ResponseEntity<String> getProgramme(String code) {
        log.info("Telemonitoring: fetching programme={}", code);
        return restTemplate.getForEntity(baseUrl + "/v1/monitoring-programmes/" + code, String.class);
    }

    // ── Device assignments (OF-B24) ──

    /** Assignment listing — exactly one of planId / patientCpid per upstream contract. */
    public ResponseEntity<String> listDeviceAssignments(String planId, String patientCpid) {
        log.info("Telemonitoring: listing device assignments (planId={})", planId);
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl + "/v1/device-assignments");
        if (planId != null && !planId.isBlank()) builder.queryParam("planId", planId);
        if (patientCpid != null && !patientCpid.isBlank()) builder.queryParam("patientCpid", patientCpid);
        return restTemplate.getForEntity(builder.toUriString(), String.class);
    }

    public ResponseEntity<String> getDeviceAssignment(String assignmentId) {
        log.info("Telemonitoring: fetching device assignment={}", assignmentId);
        return restTemplate.getForEntity(baseUrl + "/v1/device-assignments/" + assignmentId, String.class);
    }

    /**
     * The assignment gate — resolved assignment WITH calibration posture
     * (OK/DEGRADED/UNVERIFIED/UNTRACKED). This is the only lane that carries
     * posture, so the command workspace's honest device badges read it.
     */
    public ResponseEntity<String> resolveDeviceAssignment(String deviceRef) {
        log.info("Telemonitoring: resolving assignment for device={}", deviceRef);
        return restTemplate.getForEntity(
                baseUrl + "/internal/v1/device-assignments/by-device/" + deviceRef, String.class);
    }

    // ── Readings (OF-B25 read surface — newest first, paged) ──

    public ResponseEntity<String> readingsByPlan(String planId, int page, int size) {
        log.info("Telemonitoring: listing readings for plan={} page={}", planId, page);
        String url = UriComponentsBuilder.fromUriString(baseUrl + "/internal/v1/readings/by-plan/" + planId)
                .queryParam("page", page)
                .queryParam("size", size)
                .toUriString();
        return restTemplate.getForEntity(url, String.class);
    }

    public ResponseEntity<String> readingsByDevice(String deviceRef, int page, int size) {
        log.info("Telemonitoring: listing readings for device={} page={}", deviceRef, page);
        String url = UriComponentsBuilder.fromUriString(baseUrl + "/internal/v1/readings/by-device/" + deviceRef)
                .queryParam("page", page)
                .queryParam("size", size)
                .toUriString();
        return restTemplate.getForEntity(url, String.class);
    }

    // ── Alert episodes (OF-B26 — §14.6 accountable closure, §14.7 ladder) ──

    public ResponseEntity<String> listAlertEpisodes(String planId, String status, String deviceRef) {
        log.info("Telemonitoring: listing alert episodes (status={})", status);
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl + "/v1/alert-episodes");
        if (planId != null && !planId.isBlank()) builder.queryParam("planId", planId);
        if (status != null && !status.isBlank()) builder.queryParam("status", status);
        if (deviceRef != null && !deviceRef.isBlank()) builder.queryParam("deviceRef", deviceRef);
        return restTemplate.getForEntity(builder.toUriString(), String.class);
    }

    public ResponseEntity<String> getAlertEpisode(String episodeId) {
        log.info("Telemonitoring: fetching alert episode={}", episodeId);
        return restTemplate.getForEntity(baseUrl + "/v1/alert-episodes/" + episodeId, String.class);
    }

    public ResponseEntity<String> acknowledgeEpisode(String episodeId, String requestBody, String idempotencyKey) {
        log.info("Telemonitoring: acknowledging episode={}", episodeId);
        return postJson(baseUrl + "/v1/alert-episodes/" + episodeId + "/acknowledge",
                requestBody != null ? requestBody : "{}", idempotencyKey);
    }

    public ResponseEntity<String> startEpisodeProgress(String episodeId, String idempotencyKey) {
        log.info("Telemonitoring: starting progress on episode={}", episodeId);
        return postJson(baseUrl + "/v1/alert-episodes/" + episodeId + "/start", "{}", idempotencyKey);
    }

    public ResponseEntity<String> recordLadderStep(String episodeId, String requestBody, String idempotencyKey) {
        log.info("Telemonitoring: recording ladder step on episode={}", episodeId);
        return postJson(baseUrl + "/v1/alert-episodes/" + episodeId + "/ladder-steps",
                requestBody, idempotencyKey);
    }

    public ResponseEntity<String> recordAssumptionOfCare(String episodeId, String requestBody, String idempotencyKey) {
        log.info("Telemonitoring: recording assumption of care on episode={}", episodeId);
        return postJson(baseUrl + "/v1/alert-episodes/" + episodeId + "/assumption-of-care",
                requestBody, idempotencyKey);
    }

    /** §14.6 accountable closure — upstream refuses without reviewer + assessment + action + outcome. */
    public ResponseEntity<String> resolveEpisode(String episodeId, String requestBody, String idempotencyKey) {
        log.info("Telemonitoring: resolving episode={}", episodeId);
        return postJson(baseUrl + "/v1/alert-episodes/" + episodeId + "/resolve",
                requestBody != null ? requestBody : "{}", idempotencyKey);
    }

    private ResponseEntity<String> postJson(String url, String requestBody, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            headers.set(CompanionHeaders.IDEMPOTENCY_KEY, idempotencyKey);
        }
        return restTemplate.exchange(url, HttpMethod.POST,
                new HttpEntity<>(requestBody, headers), String.class);
    }
}
