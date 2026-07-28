package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The student registration journey's three sides, proxied to varapi (NCZ-W1C).
 *
 * <p>The lane split from varapi is preserved rather than flattened. It would be convenient to offer
 * one "sections" endpoint that takes an application id and works out who is asking; that
 * convenience is exactly what would put a training institution one bug away from a student's
 * identity documents. The contributor path here takes an invitation id and has no way to name an
 * application, same as the service behind it.</p>
 *
 * <p>4xx from varapi is surfaced, not swallowed. A fee that is NOT_CONFIGURED, a section outside a
 * contributor's scope, an expired invitation — each is a thing the person on the screen needs to be
 * told, and a BFF that turns them all into 502 makes an honest refusal look like an outage.</p>
 */
@RestController
@RequestMapping("/api/v1/student-registration")
public class StudentRegistrationController {

    private static final Logger log = LoggerFactory.getLogger(StudentRegistrationController.class);

    private final RestTemplate restTemplate;
    private final String varapiBaseUrl;

    public StudentRegistrationController(
            RestTemplate serviceRestTemplate,
            @Value("${impilo.services.varapi-base-url:http://localhost:8083}") String varapiBaseUrl) {
        this.restTemplate = serviceRestTemplate;
        this.varapiBaseUrl = varapiBaseUrl.endsWith("/")
                ? varapiBaseUrl.substring(0, varapiBaseUrl.length() - 1) : varapiBaseUrl;
    }

    // ── The applicant ────────────────────────────────────────────────────────────────────────

    @GetMapping("/applications/{applicationId}/sections")
    public ResponseEntity<JsonNode> sections(@RequestHeader Map<String, String> headers,
                                             @PathVariable String applicationId) {
        return relay(HttpMethod.GET, "/v1/internal/student-applications/"
                + applicationId + "/sections", headers, null);
    }

    @PostMapping("/applications/{applicationId}/sections/{sectionKey}/resubmit")
    public ResponseEntity<JsonNode> resubmit(@RequestHeader Map<String, String> headers,
                                             @PathVariable String applicationId,
                                             @PathVariable String sectionKey,
                                             @RequestBody(required = false) JsonNode body) {
        return relay(HttpMethod.POST, "/v1/internal/student-applications/"
                + applicationId + "/sections/" + sectionKey + "/resubmit", headers, body);
    }

    // ── The contributor: invitation-addressed, by design ─────────────────────────────────────

    @GetMapping("/contributions/{inviteId}/sections")
    public ResponseEntity<JsonNode> contributorSections(@RequestHeader Map<String, String> headers,
                                                        @PathVariable String inviteId) {
        return relay(HttpMethod.GET, "/v1/internal/student-applications/contributor/"
                + inviteId + "/sections", headers, null);
    }

    @PostMapping("/contributions/{inviteId}/sections/{sectionKey}")
    public ResponseEntity<JsonNode> contributorCompletes(@RequestHeader Map<String, String> headers,
                                                         @PathVariable String inviteId,
                                                         @PathVariable String sectionKey,
                                                         @RequestBody(required = false) JsonNode body) {
        return relay(HttpMethod.POST, "/v1/internal/student-applications/contributor/"
                + inviteId + "/sections/" + sectionKey, headers, body);
    }

    // ── The regulator ────────────────────────────────────────────────────────────────────────

    @PostMapping("/applications/{applicationId}/sections/{sectionKey}/return")
    public ResponseEntity<JsonNode> returnSection(@RequestHeader Map<String, String> headers,
                                                  @PathVariable String applicationId,
                                                  @PathVariable String sectionKey,
                                                  @RequestBody JsonNode body) {
        return relay(HttpMethod.POST, "/v1/internal/student-applications/"
                + applicationId + "/sections/" + sectionKey + "/return", headers, body);
    }

    @GetMapping("/fee-verdict")
    public ResponseEntity<JsonNode> feeVerdict(@RequestHeader Map<String, String> headers,
                                               @RequestParam String councilId,
                                               @RequestParam String feeCode) {
        return relay(HttpMethod.GET, "/v1/internal/student-applications/fee-verdict"
                + "?councilId=" + councilId + "&feeCode=" + feeCode, headers, null);
    }

    @PostMapping("/applications/{applicationId}/admit")
    public ResponseEntity<JsonNode> admit(@RequestHeader Map<String, String> headers,
                                          @PathVariable String applicationId,
                                          @RequestBody JsonNode body) {
        return relay(HttpMethod.POST, "/v1/internal/student-applications/"
                + applicationId + "/admit", headers, body);
    }

    // ── Relay ────────────────────────────────────────────────────────────────────────────────

    private ResponseEntity<JsonNode> relay(HttpMethod method, String path,
                                           Map<String, String> inbound, JsonNode body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        copy(inbound, headers, CompanionHeaders.TENANT_ID);
        copy(inbound, headers, CompanionHeaders.REQUEST_ID);
        copy(inbound, headers, CompanionHeaders.CORRELATION_ID);
        copy(inbound, headers, CompanionHeaders.ACTOR_ID);
        copy(inbound, headers, CompanionHeaders.PURPOSE_OF_USE);

        try {
            return restTemplate.exchange(varapiBaseUrl + path, method,
                    new HttpEntity<>(body, headers), JsonNode.class);
        } catch (HttpStatusCodeException ex) {
            // Surfaced, not swallowed. "This fee has not been set by the Council" is an answer the
            // applicant needs; turning it into a 502 would make an honest refusal look like a fault.
            log.info("student-registration {} {} -> {}", method, path, ex.getStatusCode());
            return ResponseEntity.status(ex.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(errorBody(ex));
        } catch (Exception ex) {
            log.error("student-registration {} {} failed", method, path, ex);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    private static void copy(Map<String, String> inbound, HttpHeaders headers, String name) {
        inbound.entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(name))
                .findFirst()
                .ifPresent(e -> headers.set(name, e.getValue()));
    }

    private JsonNode errorBody(HttpStatusCodeException ex) {
        String raw = ex.getResponseBodyAsString();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                    raw == null || raw.isBlank() ? "{}" : raw);
        } catch (Exception parseFailure) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("message", raw);
            return new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(fallback);
        }
    }
}
