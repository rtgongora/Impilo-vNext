package zw.gov.mohcc.impilo.mushex.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.mushex.service.FraudDetectionService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;

import java.util.List;

@RestController
@RequestMapping("/mushex/v1/fraud")
public class FraudController {

    private final FraudDetectionService fraudDetectionService;

    public FraudController(FraudDetectionService fraudDetectionService) {
        this.fraudDetectionService = fraudDetectionService;
    }

    @GetMapping("/flags")
    public ResponseEntity<ApiResponse<PagedResponse<Object>>> getFlags(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        Pageable pageable = PageRequest.of(page, size);
        Page<?> result = fraudDetectionService.getFlags(ctx.tenantId(), pageable);

        @SuppressWarnings("unchecked")
        List<Object> content = (List<Object>) (List<?>) result.getContent();
        PagedResponse<Object> paged = PagedResponse.of(
                content, page, size, result.getTotalElements());

        return ResponseEntity.ok(ApiResponse.ok(paged, correlationId));
    }
}
