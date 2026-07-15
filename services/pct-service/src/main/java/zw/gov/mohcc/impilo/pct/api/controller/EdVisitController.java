package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.pct.core.EdVisitService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/ed")
public class EdVisitController {

    private final EdVisitService edVisitService;

    public EdVisitController(EdVisitService edVisitService) {
        this.edVisitService = edVisitService;
    }

    @PostMapping("/visits")
    public ResponseEntity<ApiResponse<Map<String, Object>>> openVisit(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = CompanionHeaders.TRAUMA_EPISODE_ID, required = false) String traumaEpisodeId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(edVisitService.openVisit(withTraumaEpisode(body, traumaEpisodeId)), correlationId));
    }

    /** Fold the X-Trauma-Episode-ID header into the payload (body value wins if already present). */
    private static Map<String, Object> withTraumaEpisode(Map<String, Object> body, String traumaEpisodeId) {
        if (traumaEpisodeId == null || traumaEpisodeId.isBlank()) return body;
        Map<String, Object> merged = new LinkedHashMap<>(body != null ? body : Map.of());
        merged.putIfAbsent("traumaEpisodeId", traumaEpisodeId);
        return merged;
    }

    @GetMapping("/visits")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listVisits(
            @RequestParam(required = false) UUID facilityId,
            @RequestParam(required = false) String status) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(edVisitService.listVisits(facilityId, status), correlationId));
    }

    @GetMapping("/visits/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getVisit(@PathVariable UUID id) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(edVisitService.getVisit(id), correlationId));
    }

    @GetMapping("/triage/discriminators")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> triageDiscriminators() {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(edVisitService.triageDiscriminatorCatalog(), correlationId));
    }

    @PostMapping("/triage/score")
    public ResponseEntity<ApiResponse<Map<String, Object>>> scoreTriage(@RequestBody Map<String, Object> body) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(edVisitService.scoreTriage(body), correlationId));
    }

    @PostMapping("/visits/{id}/triage")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordTriage(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(edVisitService.recordStructuredTriage(id, body), correlationId));
    }

    @PostMapping("/visits/{id}/zone")
    public ResponseEntity<ApiResponse<Map<String, Object>>> assignZone(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(edVisitService.assignZone(id, body), correlationId));
    }

    @PostMapping("/visits/{id}/encounter")
    public ResponseEntity<ApiResponse<Map<String, Object>>> startEncounter(
            @PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(
                edVisitService.startEdEncounter(id, body != null ? body : Map.of()), correlationId));
    }

    @PostMapping("/visits/{id}/protocol")
    public ResponseEntity<ApiResponse<Map<String, Object>>> bindProtocol(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(edVisitService.bindProtocol(id, body), correlationId));
    }

    @GetMapping("/visits/{id}/protocol-suggestions")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> protocolSuggestions(@PathVariable UUID id) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(edVisitService.protocolSuggestions(id), correlationId));
    }

    @PostMapping("/visits/{id}/trauma/activate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> activateTrauma(
            @PathVariable UUID id, @RequestBody Map<String, Object> body,
            @RequestHeader(value = CompanionHeaders.TRAUMA_EPISODE_ID, required = false) String traumaEpisodeId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(
                edVisitService.activateTrauma(id, withTraumaEpisode(body, traumaEpisodeId)), correlationId));
    }

    @PostMapping("/visits/{id}/trauma/survey")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordTraumaSurvey(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(edVisitService.recordTraumaSurvey(id, body), correlationId));
    }

    @PostMapping("/visits/{id}/trauma/end")
    public ResponseEntity<ApiResponse<Map<String, Object>>> endTrauma(
            @PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(
                edVisitService.endTrauma(id, body != null ? body : Map.of()), correlationId));
    }

    @PostMapping("/visits/{id}/page")
    public ResponseEntity<ApiResponse<Map<String, Object>>> requestPage(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(edVisitService.requestClinicalPage(id, body), correlationId));
    }

    @PostMapping("/pages/{pageId}/delivered")
    public ResponseEntity<ApiResponse<Map<String, Object>>> markPageDelivered(
            @PathVariable UUID pageId, @RequestBody Map<String, Object> body) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(edVisitService.markPageDelivered(pageId, body), correlationId));
    }

    @PostMapping("/visits/{id}/disposition")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordDisposition(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(edVisitService.recordDisposition(id, body), correlationId));
    }

    @PostMapping("/emergency-cases")
    public ResponseEntity<ApiResponse<Map<String, Object>>> openEmergencyCase(@RequestBody Map<String, Object> body) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(edVisitService.openEmergencyCase(body), correlationId));
    }

    @PostMapping("/emergency-cases/{caseId}/reconcile")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reconcileEmergencyCase(
            @PathVariable UUID caseId, @RequestBody Map<String, Object> body) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(edVisitService.reconcileEmergencyCase(caseId, body), correlationId));
    }
}
