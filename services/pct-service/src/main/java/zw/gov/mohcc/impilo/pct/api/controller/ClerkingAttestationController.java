package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pct.core.clinical.ClerkingAttestationService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/clerking/visit-attestations")
public class ClerkingAttestationController {

    private final ClerkingAttestationService service;

    public ClerkingAttestationController(ClerkingAttestationService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(
            @RequestParam(name = "subject_cpid") String subjectCpid,
            @RequestParam(name = "encounter_id") String encounterId) {
        return ResponseEntity.ok(ApiResponse.ok(
                service.listForVisit(subjectCpid, encounterId),
                TrustContextHolder.require().correlationId().toString()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> attest(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                service.attest(body),
                TrustContextHolder.require().correlationId().toString()));
    }
}
