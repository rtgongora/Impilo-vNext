package zw.gov.mohcc.impilo.experience.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.CostaServiceClient;

import java.util.Map;
import java.util.UUID;

/**
 * BFF facade for the managed (rich, governed) budget surface. Stateless
 * pass-through to COSTA (the budget system of record); preserves request /
 * correlation headers and returns the upstream status on failure (safe partial
 * behaviour — the caller sees COSTA's error envelope rather than a 500 here).
 */
@RestController
@RequestMapping("/internal/v1/finance/budgets")
public class ManagedBudgetBffController {

    private static final Logger log = LoggerFactory.getLogger(ManagedBudgetBffController.class);
    private static final String BASE = "/internal/v1/finance/budgets";

    private final CostaServiceClient costaClient;

    public ManagedBudgetBffController(CostaServiceClient costaClient) {
        this.costaClient = costaClient;
    }

    // ----- budget lifecycle -------------------------------------------------

    @PostMapping(value = "/managed", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> create(@RequestBody Map<String, Object> body,
                                         @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
                                         @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return post(BASE + "/managed", body, rid, cid);
    }

    @GetMapping("/managed")
    public ResponseEntity<String> list(@RequestParam(name = "facility_id", required = false) UUID facilityId,
                                       @RequestParam(name = "year", required = false) Integer year,
                                       @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
                                       @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        UriComponentsBuilder b = UriComponentsBuilder.fromPath(BASE + "/managed");
        if (facilityId != null) b.queryParam("facility_id", facilityId);
        if (year != null) b.queryParam("year", year);
        return get(b.toUriString(), rid, cid);
    }

    @GetMapping("/managed/{budgetId}")
    public ResponseEntity<String> get(@PathVariable UUID budgetId,
                                      @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
                                      @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return get(BASE + "/managed/" + budgetId, rid, cid);
    }

    @PostMapping(value = "/managed/{budgetId}/lines", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> addLine(@PathVariable UUID budgetId, @RequestBody Map<String, Object> body,
                                          @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
                                          @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return post(BASE + "/managed/" + budgetId + "/lines", body, rid, cid);
    }

    @PostMapping("/managed/{budgetId}/{transition:submit|review|approve|activate|freeze|close}")
    public ResponseEntity<String> transition(@PathVariable UUID budgetId, @PathVariable String transition,
                                             @RequestBody(required = false) Map<String, Object> body,
                                             @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
                                             @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return post(BASE + "/managed/" + budgetId + "/" + transition, body == null ? Map.of() : body, rid, cid);
    }

    // ----- execution --------------------------------------------------------

    @GetMapping("/managed/{budgetId}/{resource:commitments|actuals|recommendations|revisions|versions|lines}")
    public ResponseEntity<String> listResource(@PathVariable UUID budgetId, @PathVariable String resource,
                                               @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
                                               @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return get(BASE + "/managed/" + budgetId + "/" + resource, rid, cid);
    }

    @PostMapping(value = "/managed/{budgetId}/{resource:commitments|actuals}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createResource(@PathVariable UUID budgetId, @PathVariable String resource,
                                                 @RequestBody Map<String, Object> body,
                                                 @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
                                                 @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return post(BASE + "/managed/" + budgetId + "/" + resource, body, rid, cid);
    }

    @GetMapping("/managed/{budgetId}/variance")
    public ResponseEntity<String> variance(@PathVariable UUID budgetId,
                                           @RequestParam(name = "as_of", required = false) String asOf,
                                           @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
                                           @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        UriComponentsBuilder b = UriComponentsBuilder.fromPath(BASE + "/managed/" + budgetId + "/variance");
        if (asOf != null) b.queryParam("as_of", asOf);
        return get(b.toUriString(), rid, cid);
    }

    @GetMapping("/managed/{budgetId}/forecast")
    public ResponseEntity<String> forecast(@PathVariable UUID budgetId,
                                           @RequestParam(name = "method", required = false) String method,
                                           @RequestParam(name = "as_of", required = false) String asOf,
                                           @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
                                           @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        UriComponentsBuilder b = UriComponentsBuilder.fromPath(BASE + "/managed/" + budgetId + "/forecast");
        if (method != null) b.queryParam("method", method);
        if (asOf != null) b.queryParam("as_of", asOf);
        return get(b.toUriString(), rid, cid);
    }

    @PostMapping(value = "/availability/check", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> availability(@RequestBody Map<String, Object> body,
                                               @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
                                               @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return post(BASE + "/availability/check", body, rid, cid);
    }

    // ----- passthrough helpers ---------------------------------------------

    private ResponseEntity<String> get(String pathAndQuery, String rid, String cid) {
        try {
            return withHeaders(costaClient.costaInternalGet(pathAndQuery), rid, cid);
        } catch (HttpStatusCodeException ex) {
            log.warn("managed budget GET {} upstream failed: {}", pathAndQuery, ex.getStatusCode());
            return withHeaders(ex, rid, cid);
        }
    }

    private ResponseEntity<String> post(String path, Object body, String rid, String cid) {
        try {
            return withHeaders(costaClient.costaInternalPost(path, body), rid, cid);
        } catch (HttpStatusCodeException ex) {
            log.warn("managed budget POST {} upstream failed: {}", path, ex.getStatusCode());
            return withHeaders(ex, rid, cid);
        }
    }

    private ResponseEntity<String> withHeaders(ResponseEntity<String> upstream, String rid, String cid) {
        MediaType ct = upstream.getHeaders().getContentType();
        return ResponseEntity.status(upstream.getStatusCode())
                .contentType(ct != null ? ct : MediaType.APPLICATION_JSON)
                .header(CompanionHeaders.REQUEST_ID, rid)
                .header(CompanionHeaders.CORRELATION_ID, cid)
                .body(upstream.getBody());
    }

    private ResponseEntity<String> withHeaders(HttpStatusCodeException ex, String rid, String cid) {
        MediaType ct = ex.getResponseHeaders() != null ? ex.getResponseHeaders().getContentType() : null;
        return ResponseEntity.status(ex.getStatusCode())
                .contentType(ct != null ? ct : MediaType.APPLICATION_JSON)
                .header(CompanionHeaders.REQUEST_ID, rid)
                .header(CompanionHeaders.CORRELATION_ID, cid)
                .body(ex.getResponseBodyAsString());
    }
}
