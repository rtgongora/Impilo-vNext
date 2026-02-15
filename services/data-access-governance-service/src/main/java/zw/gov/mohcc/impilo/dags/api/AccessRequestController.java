package zw.gov.mohcc.impilo.dags.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.dags.core.AccessRequestService;
import zw.gov.mohcc.impilo.dags.core.AccessRequestStatus;
import zw.gov.mohcc.impilo.dags.persistence.entity.AccessRequestEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;

@RestController
@RequestMapping("/internal/v1/access-requests")
public class AccessRequestController {

    private final AccessRequestService accessRequestService;

    public AccessRequestController(AccessRequestService accessRequestService) {
        this.accessRequestService = accessRequestService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AccessRequestEntity>> submitRequest(
            @RequestBody SubmitAccessRequest request) {
        TrustContext ctx = TrustContextHolder.require();

        AccessRequestEntity entity = accessRequestService.submit(
                ctx.tenantId(), ctx.actorId(), ctx.correlationId(),
                request.resourceType(), request.resourceId(),
                request.purpose(), request.justification());

        return ResponseEntity.status(201).body(
                ApiResponse.ok(entity, ctx.correlationId().toString()));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<AccessRequestEntity>> approve(
            @PathVariable Long id,
            @RequestBody(required = false) DecisionRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        String reason = request != null ? request.reason() : null;

        AccessRequestEntity entity = accessRequestService.approve(
                id, ctx.tenantId(), ctx.actorId(), ctx.correlationId(), reason);

        return ResponseEntity.ok(
                ApiResponse.ok(entity, ctx.correlationId().toString()));
    }

    @PostMapping("/{id}/deny")
    public ResponseEntity<ApiResponse<AccessRequestEntity>> deny(
            @PathVariable Long id,
            @RequestBody(required = false) DecisionRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        String reason = request != null ? request.reason() : null;

        AccessRequestEntity entity = accessRequestService.deny(
                id, ctx.tenantId(), ctx.actorId(), ctx.correlationId(), reason);

        return ResponseEntity.ok(
                ApiResponse.ok(entity, ctx.correlationId().toString()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<AccessRequestEntity>>> listRequests(
            @RequestParam(required = false) AccessRequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        TrustContext ctx = TrustContextHolder.require();

        Page<AccessRequestEntity> results = accessRequestService.list(
                ctx.tenantId(), status, PageRequest.of(page, size));

        return ResponseEntity.ok(ApiResponse.ok(
                PagedResponse.of(results.getContent(), page, size, results.getTotalElements()),
                ctx.correlationId().toString()));
    }

    public record SubmitAccessRequest(
            String resourceType,
            String resourceId,
            String purpose,
            String justification
    ) {}

    public record DecisionRequest(String reason) {}
}
