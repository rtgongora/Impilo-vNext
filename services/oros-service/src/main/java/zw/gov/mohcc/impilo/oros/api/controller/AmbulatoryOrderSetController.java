package zw.gov.mohcc.impilo.oros.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.oros.core.AmbulatoryOrderSetService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/order-sets")
public class AmbulatoryOrderSetController {

    private final AmbulatoryOrderSetService service;

    public AmbulatoryOrderSetController(AmbulatoryOrderSetService service) {
        this.service = service;
    }

    private static String cid() {
        return TrustContextHolder.require().correlationId().toString();
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(service.listDefinitions(), cid()));
    }

    @GetMapping("/{setId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> get(@PathVariable UUID setId) {
        return ResponseEntity.ok(ApiResponse.ok(service.getDefinition(setId), cid()));
    }

    @PostMapping("/{setId}/place")
    public ResponseEntity<ApiResponse<Map<String, Object>>> place(
            @PathVariable UUID setId,
            @RequestBody Map<String, Object> body) {
        String patientCpid = String.valueOf(body.getOrDefault("patient_cpid", body.get("patientCpid")));
        if (patientCpid == null || patientCpid.isBlank() || "null".equals(patientCpid)) {
            throw new IllegalArgumentException("patient_cpid is required");
        }
        String encounterRef = body.get("encounter_ref") != null
                ? String.valueOf(body.get("encounter_ref"))
                : body.get("encounterRef") != null ? String.valueOf(body.get("encounterRef")) : null;
        String notes = body.get("notes") == null ? null : String.valueOf(body.get("notes"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.batchPlace(setId, patientCpid, encounterRef, notes), cid()));
    }
}
