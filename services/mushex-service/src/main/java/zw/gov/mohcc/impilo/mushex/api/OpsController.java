package zw.gov.mohcc.impilo.mushex.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.mushex.service.OpsService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;

@RestController
@RequestMapping("/mushex/v1/ops")
public class OpsController {

    private final OpsService opsService;

    public OpsController(OpsService opsService) {
        this.opsService = opsService;
    }

    @GetMapping("/reviews/pending")
    public ResponseEntity<ApiResponse<PagedResponse<Object>>> getPendingReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        Pageable pageable = PageRequest.of(page, size);
        Page<Object> result = opsService.getPendingReviews(ctx.tenantId(), pageable);

        PagedResponse<Object> paged = PagedResponse.of(
                result.getContent(), page, size, result.getTotalElements());

        return ResponseEntity.ok(ApiResponse.ok(paged, correlationId));
    }

    @PostMapping("/reviews/{id}/approve")
    public ResponseEntity<ApiResponse<Object>> approveReview(
            @PathVariable String id,
            @RequestBody(required = false) String notes) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        Object result = opsService.approveReview(id, ctx.actorId(), notes);

        return ResponseEntity.ok(ApiResponse.ok(result, correlationId));
    }
}
