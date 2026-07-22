package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.pct.core.MdtBoardService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.Map;

/**
 * TM-B15 (B15-1): MDT / specialist-board endpoints. The board is a pct-owned clinical record
 * (spec profile #9); recommendations only — orders stay on the referral's own rails.
 */
@RestController
@RequestMapping("/v1/mdt")
public class MdtController {

    private final MdtBoardService mdtBoardService;

    public MdtController(MdtBoardService mdtBoardService) {
        this.mdtBoardService = mdtBoardService;
    }

    @PostMapping("/sessions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createSession(
            @RequestBody(required = false) Map<String, Object> request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(
                mdtBoardService.createSession(request == null ? Map.of() : request), correlationId));
    }

    @GetMapping("/sessions/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBoard(
            @PathVariable String id,
            @RequestParam(value = "viewer", required = false) String viewer) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(mdtBoardService.getBoard(id, viewer), correlationId));
    }

    @PostMapping("/sessions/{id}/cases")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addCase(
            @PathVariable String id,
            @RequestBody Map<String, Object> request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(mdtBoardService.addCase(id, request), correlationId));
    }

    @PostMapping("/cases/{caseItemId}/outcome")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordOutcome(
            @PathVariable String caseItemId,
            @RequestBody Map<String, Object> request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(mdtBoardService.recordOutcome(caseItemId, request), correlationId));
    }
}
