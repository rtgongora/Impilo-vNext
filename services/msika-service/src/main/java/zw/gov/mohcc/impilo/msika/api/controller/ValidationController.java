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

    @PostMapping("/pack")
    public ResponseEntity<ApiResponse<ValidationResult>> validatePack(
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String tenantId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        ValidationResult result = validationService.validatePack(kind, tenantId);
        return ResponseEntity.ok(ApiResponse.ok(result, correlationId));
    }
}
