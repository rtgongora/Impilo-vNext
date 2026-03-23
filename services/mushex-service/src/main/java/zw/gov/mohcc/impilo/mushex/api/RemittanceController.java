package zw.gov.mohcc.impilo.mushex.api;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.mushex.api.dto.RemittanceClaimRequest;
import zw.gov.mohcc.impilo.mushex.domain.entity.RemittanceTokenEntity;
import zw.gov.mohcc.impilo.mushex.service.RemittanceService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

@RestController
@RequestMapping("/mushex/v1/remittance")
public class RemittanceController {

    private final RemittanceService remittanceService;

    public RemittanceController(RemittanceService remittanceService) {
        this.remittanceService = remittanceService;
    }

    @PostMapping("/claim")
    public ResponseEntity<ApiResponse<RemittanceTokenEntity>> claimRemittance(
            @Valid @RequestBody RemittanceClaimRequest request) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        RemittanceTokenEntity result = remittanceService.claimSlip(request.token(), request.otp(), null);

        return ResponseEntity.ok(ApiResponse.ok(result, correlationId));
    }
}
