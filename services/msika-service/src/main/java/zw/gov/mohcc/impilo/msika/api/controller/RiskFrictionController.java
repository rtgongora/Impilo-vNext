package zw.gov.mohcc.impilo.msika.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.msika.api.dto.RiskFrictionView;
import zw.gov.mohcc.impilo.msika.core.ValidationService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

/**
 * Read-only risk-friction mapping lookup (authenticated). msika-flow consumes
 * this for order-time friction (max_qty_per_order, provider/facility context).
 */
@RestController
@RequestMapping("/v1/validation/risk-friction")
public class RiskFrictionController {

    private final ValidationService validationService;

    public RiskFrictionController(ValidationService validationService) {
        this.validationService = validationService;
    }

    @GetMapping("/{classification}")
    public ResponseEntity<ApiResponse<RiskFrictionView>> get(@PathVariable String classification) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(validationService.riskFriction(classification), correlationId));
    }
}
