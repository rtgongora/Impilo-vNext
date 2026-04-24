package zw.gov.mohcc.impilo.mushex.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.mushex.api.dto.RemittanceTransferRequests;
import zw.gov.mohcc.impilo.mushex.domain.entity.RemittanceRequestEntity;
import zw.gov.mohcc.impilo.mushex.service.RemittanceTransferService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;

@RestController
@RequestMapping("/mushex/v1/remittance-transfers")
public class RemittanceTransferController {

    private final RemittanceTransferService remittanceTransferService;

    public RemittanceTransferController(RemittanceTransferService remittanceTransferService) {
        this.remittanceTransferService = remittanceTransferService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RemittanceRequestEntity>>> list(@RequestParam String senderRef) {
        var ctx = TrustContextHolder.require();
        List<RemittanceRequestEntity> rows = remittanceTransferService.listForSender(senderRef);
        return ResponseEntity.ok(ApiResponse.ok(rows, ctx.correlationId().toString()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RemittanceRequestEntity>> create(
            @Valid @RequestBody RemittanceTransferRequests.CreateRemittanceTransferRequest request) {
        var ctx = TrustContextHolder.require();
        RemittanceRequestEntity r = remittanceTransferService.createRequest(
                request.remittanceCode(),
                request.senderRef(),
                request.recipientRef(),
                request.recipientType(),
                request.originCountry(),
                request.destinationCountry(),
                request.domesticOrInternational(),
                request.remittancePurpose(),
                request.healthContextJson(),
                request.amount(),
                request.currency());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(r, ctx.correlationId().toString()));
    }
}
