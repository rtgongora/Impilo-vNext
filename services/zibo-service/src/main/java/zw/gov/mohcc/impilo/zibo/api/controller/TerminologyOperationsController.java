package zw.gov.mohcc.impilo.zibo.api.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.zibo.core.MappingService;
import zw.gov.mohcc.impilo.zibo.core.TerminologyOperationsService;

/**
 * The FHIR terminology operations.
 *
 * <p>ZIBO had none of these. There was no {@code $lookup}, no {@code $expand} and no concept search
 * of any kind — which is why <b>13 of its 15 ValueSets resolved to nothing</b> and why the UI's own
 * terminology page says search is "intentionally not shown until the Experience BFF exposes a
 * canonical search contract". This is that contract.</p>
 *
 * <p>Routes are {@code /v1/**}, so they inherit the service's authentication and the
 * {@code RateLimitFilter} already registered there. The existing bespoke endpoints
 * ({@code /v1/validate/coding}, {@code /v1/artifacts/resolve}, {@code /v1/map}) are untouched and
 * remain the compatibility surface for the four live callers.</p>
 */
@RestController
@RequestMapping("/v1")
public class TerminologyOperationsController {

    private final TerminologyOperationsService operations;

    public TerminologyOperationsController(TerminologyOperationsService operations) {
        this.operations = operations;
    }

    private static String correlationId() {
        var ctx = TrustContextHolder.get();
        return ctx == null || ctx.correlationId() == null ? null : ctx.correlationId().toString();
    }

    /** {@code CodeSystem/$lookup} — what does this code mean? */
    @GetMapping("/CodeSystem/$lookup")
    public ResponseEntity<ApiResponse<TerminologyOperationsService.LookupResult>> lookup(
            @RequestParam String system,
            @RequestParam String code,
            @RequestParam(required = false) String version) {

        TerminologyOperationsService.LookupResult result = operations.lookup(system, code, version);
        if (!result.found()) {
            // 404 rather than an empty 200: "this code does not exist" and "the lookup failed" must
            // not look identical to a caller.
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.ok(result, correlationId()));
        }
        return ResponseEntity.ok(ApiResponse.ok(result, correlationId()));
    }

    /** {@code CodeSystem/$validate-code} — is this code real, and is the display right? */
    @GetMapping("/CodeSystem/$validate-code")
    public ResponseEntity<ApiResponse<TerminologyOperationsService.ValidateResult>> validateCode(
            @RequestParam String url,
            @RequestParam String code,
            @RequestParam(required = false) String display,
            @RequestParam(required = false) String version) {

        return ResponseEntity.ok(ApiResponse.ok(
                operations.validateCode(url, code, display, version), correlationId()));
    }

    /**
     * {@code ValueSet/$expand} — the operation the pickers need.
     *
     * <p>{@code filter} drives a type-ahead: it matches display text, designations (so Shona and
     * Ndebele terms are searchable, not decorative) and a code prefix, because a clinician typing
     * {@code N02BE01} is not typing a word.</p>
     */
    @GetMapping("/ValueSet/$expand")
    public ResponseEntity<ApiResponse<TerminologyOperationsService.Expansion>> expand(
            @RequestParam String url,
            @RequestParam(required = false) String version,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) Integer count,
            @RequestParam(required = false) Integer offset,
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly) {

        return ResponseEntity.ok(ApiResponse.ok(
                operations.expand(url, version, filter, count, offset, activeOnly), correlationId()));
    }

    /** {@code ValueSet/$validate-code} — is this code a member of this set? */
    @GetMapping("/ValueSet/$validate-code")
    public ResponseEntity<ApiResponse<TerminologyOperationsService.ValidateResult>> validateInValueSet(
            @RequestParam String url,
            @RequestParam String code,
            @RequestParam(required = false) String system,
            @RequestParam(required = false) String version) {

        return ResponseEntity.ok(ApiResponse.ok(
                operations.validateCodeInValueSet(url, system, code, version), correlationId()));
    }

    /** {@code ConceptMap/$translate}, FHIR-shaped. {@code /v1/map} and the oros alias still work. */
    @GetMapping("/ConceptMap/$translate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> translate(
            @RequestParam String system,
            @RequestParam String code,
            @RequestParam String targetsystem) {

        MappingService.MappingResult r = operations.translate(system, code, targetsystem);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("result", r.targetCode() != null);
        Map<String, Object> match = new LinkedHashMap<>();
        match.put("equivalence", r.equivalence());
        match.put("concept", Map.of(
                "system", String.valueOf(r.targetSystem()),
                "code", String.valueOf(r.targetCode()),
                "display", String.valueOf(r.targetDisplay())));
        body.put("match", r.targetCode() == null ? List.of() : List.of(match));
        return ResponseEntity.ok(ApiResponse.ok(body, correlationId()));
    }

    /**
     * A ValueSet that cannot be honestly expanded is a 501, not an empty result.
     *
     * <p>Returning the subset the index understands would hand a clinician a picker missing codes
     * with nothing to indicate it. When LOINC and ICD-11 arrive with filtered ValueSets, this
     * surfaces as an unimplemented operation and gets built.</p>
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(
            TerminologyOperationsService.UnsupportedComposeException.class)
    public ResponseEntity<Map<String, Object>> onUnsupportedCompose(
            TerminologyOperationsService.UnsupportedComposeException e) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(Map.of(
                "error", "unsupported_compose",
                "message", e.getMessage()));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> onNotFound(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "not_found",
                "message", e.getMessage()));
    }
}
