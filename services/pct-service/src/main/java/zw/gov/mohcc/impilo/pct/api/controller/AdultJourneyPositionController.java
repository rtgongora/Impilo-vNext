package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pct.core.clinical.AdultJourneyPositionService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.Map;

@RestController
@RequestMapping("/v1/medicine/journey-position")
public class AdultJourneyPositionController {

    private final AdultJourneyPositionService service;

    public AdultJourneyPositionController(AdultJourneyPositionService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> current(
            @RequestParam(name = "subject_cpid") String subjectCpid,
            @RequestParam(name = "journey_id", required = false) String journeyId,
            @RequestParam(name = "encounter_id", required = false) String encounterId) {
        return ResponseEntity.ok(ApiResponse.ok(
                service.current(subjectCpid, journeyId, encounterId),
                TrustContextHolder.require().correlationId().toString()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> record(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                service.record(body),
                TrustContextHolder.require().correlationId().toString()));
    }
}
