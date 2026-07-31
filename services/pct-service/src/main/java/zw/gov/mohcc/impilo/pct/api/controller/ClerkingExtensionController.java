package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pct.core.clinical.ClerkingExtensionService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.Map;

/**
 * NEW_PCT clerking extensions (V117). Safeguarding is under /safeguarding — never /ehr/social-history.
 */
@RestController
@RequestMapping("/v1/clerking")
public class ClerkingExtensionController {

    private final ClerkingExtensionService service;

    public ClerkingExtensionController(ClerkingExtensionService service) {
        this.service = service;
    }

    private static String cid() {
        return TrustContextHolder.require().correlationId().toString();
    }

    @GetMapping("/presenting-concerns")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listPresenting(
            @RequestParam(name = "subject_cpid") String subjectCpid) {
        return ResponseEntity.ok(ApiResponse.ok(service.listPresentingConcerns(subjectCpid), cid()));
    }

    @PostMapping("/presenting-concerns")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordPresenting(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.recordPresentingConcern(body), cid()));
    }

    @GetMapping("/systems-reviews")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listRos(
            @RequestParam(name = "subject_cpid") String subjectCpid) {
        return ResponseEntity.ok(ApiResponse.ok(service.listSystemsReviews(subjectCpid), cid()));
    }

    @PostMapping("/systems-reviews")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordRos(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.recordSystemsReview(body), cid()));
    }

    @GetMapping("/prior-icu")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listIcu(
            @RequestParam(name = "subject_cpid") String subjectCpid) {
        return ResponseEntity.ok(ApiResponse.ok(service.listPriorIcu(subjectCpid), cid()));
    }

    @PostMapping("/prior-icu")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordIcu(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.recordPriorIcu(body), cid()));
    }

    @GetMapping("/infection-exposures")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listExposure(
            @RequestParam(name = "subject_cpid") String subjectCpid) {
        return ResponseEntity.ok(ApiResponse.ok(service.listInfectionExposures(subjectCpid), cid()));
    }

    @PostMapping("/infection-exposures")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordExposure(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.recordInfectionExposure(body), cid()));
    }

    @GetMapping("/cognition-mood")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listCognition(
            @RequestParam(name = "subject_cpid") String subjectCpid) {
        return ResponseEntity.ok(ApiResponse.ok(service.listCognitionMood(subjectCpid), cid()));
    }

    @PostMapping("/cognition-mood")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordCognition(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.recordCognitionMood(body), cid()));
    }

    @GetMapping("/genetic-risks")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listGenetic(
            @RequestParam(name = "subject_cpid") String subjectCpid) {
        return ResponseEntity.ok(ApiResponse.ok(service.listGeneticRisks(subjectCpid), cid()));
    }

    @PostMapping("/genetic-risks")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordGenetic(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.recordGeneticRisk(body), cid()));
    }

    @GetMapping("/safeguarding")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listSafeguarding(
            @RequestParam(name = "subject_cpid") String subjectCpid) {
        return ResponseEntity.ok(ApiResponse.ok(service.listSafeguarding(subjectCpid), cid()));
    }

    @PostMapping("/safeguarding")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordSafeguarding(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.recordSafeguarding(body), cid()));
    }
}
