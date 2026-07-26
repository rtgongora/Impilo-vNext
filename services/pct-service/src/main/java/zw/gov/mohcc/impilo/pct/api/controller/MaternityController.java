package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.pct.core.clinical.MaternityService;
import zw.gov.mohcc.impilo.pct.core.clinical.PartographProgressEngine;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Maternity SoR API: labour monitoring, the partograph, and cardiotocography.
 *
 * <p>Fulfils a BFF strangler contract that has been 404ing since it shipped. experience-bff calls
 * {@code /v1/labour-monitoring} and {@code /v1/maternity/**}; none of it existed, and the BFF's
 * GET turned the failure into an empty 200, so a ward reading a woman's labour record saw "no
 * observations" when the truth was "we could not ask". Both ends are now real.</p>
 *
 * <p>Paths and field names match what the BFF already sends, so no BFF or UI rework is needed
 * beyond the honesty fix on the failure branch.</p>
 */
@RestController
public class MaternityController {

    private final MaternityService maternityService;

    public MaternityController(MaternityService maternityService) {
        this.maternityService = maternityService;
    }

    // --- labour monitoring -----------------------------------------------------------------

    @GetMapping("/v1/labour-monitoring")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listLabourMonitoring(
            @RequestParam(name = "patientId", required = false) String patientId,
            @RequestParam(name = "patient_id", required = false) String patientIdSnake,
            @RequestParam(name = "encounterId", required = false) String encounterId) {
        String subject = require(patientId, patientIdSnake);
        return ResponseEntity.ok(ApiResponse.ok(
                MaternityService.observationsToMaps(maternityService.listObservations(subject, encounterId)),
                correlationId()));
    }

    @PostMapping("/v1/labour-monitoring")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordLabourMonitoring(
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                MaternityService.toMap(maternityService.recordObservation(body)), correlationId()));
    }

    // --- partograph ------------------------------------------------------------------------

    @PostMapping("/v1/maternity/partograph/sessions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> openPartograph(
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                MaternityService.toMap(maternityService.openPartograph(body)), correlationId()));
    }

    @GetMapping("/v1/maternity/partograph/sessions/active")
    public ResponseEntity<ApiResponse<Map<String, Object>>> activePartograph(
            @RequestParam(name = "patientId", required = false) String patientId,
            @RequestParam(name = "patient_id", required = false) String patientIdSnake) {
        String subject = require(patientId, patientIdSnake);
        return maternityService.activePartograph(subject)
                .map(session -> ResponseEntity.ok(ApiResponse.ok(
                        withProgress(MaternityService.toMap(session), session.getSessionId()), correlationId())))
                // No open partograph is a real answer, distinct from a failure to ask.
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.ok(
                        Map.of("patient_id", subject, "partograph_active", false), correlationId())));
    }

    @GetMapping("/v1/maternity/partograph/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPartograph(@PathVariable UUID sessionId) {
        Map<String, Object> out = withProgress(
                MaternityService.toMap(maternityService.requirePartograph(sessionId)), sessionId);
        out.put("points", MaternityService.observationsToMaps(maternityService.partographPoints(sessionId)));
        return ResponseEntity.ok(ApiResponse.ok(out, correlationId()));
    }

    @PostMapping("/v1/maternity/partograph/sessions/{sessionId}/points")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addPartographPoint(
            @PathVariable UUID sessionId, @RequestBody Map<String, Object> body) {
        Map<String, Object> point = MaternityService.toMap(
                maternityService.addPartographPoint(sessionId, body));
        // The assessment is returned with the write. A point that crosses the action line must
        // reach the midwife who just recorded it, not wait for someone to reopen the chart.
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("point", point);
        out.put("progress", progressMap(maternityService.assessPartograph(sessionId, OffsetDateTime.now())));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(out, correlationId()));
    }

    @PostMapping("/v1/maternity/partograph/sessions/{sessionId}/close")
    public ResponseEntity<ApiResponse<Map<String, Object>>> closePartograph(
            @PathVariable UUID sessionId, @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.ok(MaternityService.toMap(
                maternityService.closePartograph(sessionId, body == null ? Map.of() : body)), correlationId()));
    }

    // --- cardiotocography --------------------------------------------------------------------

    @PostMapping("/v1/maternity/ctg/sessions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> openCtgSession(
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                MaternityService.toMap(maternityService.openCtgSession(body)), correlationId()));
    }

    @GetMapping("/v1/maternity/ctg/sessions/active")
    public ResponseEntity<ApiResponse<Map<String, Object>>> activeCtgSession(
            @RequestParam(name = "patientId", required = false) String patientId,
            @RequestParam(name = "patient_id", required = false) String patientIdSnake) {
        String subject = require(patientId, patientIdSnake);
        return maternityService.activeCtgSession(subject)
                .map(session -> ResponseEntity.ok(ApiResponse.ok(
                        MaternityService.toMap(session), correlationId())))
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.ok(
                        Map.of("patient_id", subject, "ctg_active", false), correlationId())));
    }

    @GetMapping("/v1/maternity/ctg/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCtgSession(@PathVariable UUID sessionId) {
        Map<String, Object> out = MaternityService.toMap(maternityService.requireCtgSession(sessionId));
        out.put("annotations", maternityService.ctgAnnotations(sessionId)
                .stream().map(MaternityService::toMap).toList());
        return ResponseEntity.ok(ApiResponse.ok(out, correlationId()));
    }

    @PostMapping("/v1/maternity/ctg/sessions/{sessionId}/chunks")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addCtgChunk(
            @PathVariable UUID sessionId, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                maternityService.chunkToMap(maternityService.addCtgChunk(sessionId, body)), correlationId()));
    }

    @GetMapping("/v1/maternity/ctg/sessions/{sessionId}/chunks")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listCtgChunks(
            @PathVariable UUID sessionId,
            @RequestParam(name = "channel", required = false) String channel) {
        return ResponseEntity.ok(ApiResponse.ok(
                maternityService.ctgChunks(sessionId, channel).stream()
                        .map(maternityService::chunkToMap).toList(),
                correlationId()));
    }

    @PostMapping("/v1/maternity/ctg/sessions/{sessionId}/annotations")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addCtgAnnotation(
            @PathVariable UUID sessionId, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                MaternityService.toMap(maternityService.addCtgAnnotation(sessionId, body)), correlationId()));
    }

    // --- summary ------------------------------------------------------------------------------

    @GetMapping("/v1/maternity/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> summary(
            @RequestParam(name = "patientId", required = false) String patientId,
            @RequestParam(name = "patient_id", required = false) String patientIdSnake) {
        String subject = require(patientId, patientIdSnake);
        return ResponseEntity.ok(ApiResponse.ok(
                maternityService.summary(subject, OffsetDateTime.now()), correlationId()));
    }

    // --- helpers -------------------------------------------------------------------------------

    private Map<String, Object> withProgress(Map<String, Object> session, UUID sessionId) {
        Map<String, Object> out = new LinkedHashMap<>(session);
        out.put("progress", progressMap(maternityService.assessPartograph(sessionId, OffsetDateTime.now())));
        return out;
    }

    private static Map<String, Object> progressMap(PartographProgressEngine.Assessment a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", a.status().name());
        m.put("latest_dilation_cm", a.latestDilationCm());
        m.put("latest_dilation_at", a.latestDilationAt() == null ? null : String.valueOf(a.latestDilationAt()));
        m.put("expected_dilation_cm", a.expectedDilationCm());
        m.put("hours_behind_alert_line", a.hoursBehindAlertLine());
        m.put("alert_reference_at", a.alertReferenceAt() == null ? null : String.valueOf(a.alertReferenceAt()));
        m.put("alert_reference_dilation_cm", a.alertReferenceDilationCm());
        m.put("outstanding_observations", a.outstandingObservations());
        m.put("observations", a.observations());
        m.put("recommended_action", a.recommendedAction());
        m.put("content_version", a.contentVersion());
        m.put("content_source", a.contentSource());
        return m;
    }

    private static String require(String primary, String fallback) {
        String value = primary != null && !primary.isBlank() ? primary : fallback;
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patientId is required");
        }
        return value;
    }

    private static String correlationId() {
        return TrustContextHolder.require().correlationId().toString();
    }
}
