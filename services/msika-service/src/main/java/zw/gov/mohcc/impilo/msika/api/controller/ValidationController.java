package zw.gov.mohcc.impilo.msika.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.msika.api.dto.ValidationRequest;
import zw.gov.mohcc.impilo.msika.api.dto.ValidationResult;
import zw.gov.mohcc.impilo.msika.core.ValidationService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

@RestController
@RequestMapping("/v1/validate")
public class ValidationController {

    private final ValidationService validationService;

    public ValidationController(ValidationService validationService) {
        this.validationService = validationService;
    }

    @PostMapping("/item")
    public ResponseEntity<ApiResponse<ValidationResult>> validateItem(@RequestBody ValidationRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        ValidationResult result = validationService.validateItem(request);
        return ResponseEntity.ok(ApiResponse.ok(result, correlationId));
    }

    /**
     * Pack validation. Optional JSON body of item ids validates that exact set
     * (existence, published-catalog membership, restriction composition); no
     * body validates the whole published pack for kind/tenant.
     */
    @PostMapping("/pack")
    public ResponseEntity<ApiResponse<ValidationResult>> validatePack(
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String tenantId,
            @RequestBody(required = false) java.util.List<String> itemIds) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        ValidationResult result = validationService.validatePack(kind, tenantId, itemIds);
        return ResponseEntity.ok(ApiResponse.ok(result, correlationId));
    }
}
