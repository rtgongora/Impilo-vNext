package zw.gov.mohcc.impilo.varapi.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.varapi.api.dto.ProviderAccessRequestView;
import zw.gov.mohcc.impilo.varapi.api.dto.SubmitProviderAccessRequest;
import zw.gov.mohcc.impilo.varapi.core.ProviderAccessRequestService;

import java.util.List;

/**
 * Self-service provider-access requests (IATG Journey D). A submission always
 * persists a durable, applicant-queryable record with a named next actor; new
 * Provider IDs are never issued directly from this endpoint.
 */
@RestController
@RequestMapping("/v1/internal/providers/access-requests")
public class ProviderAccessRequestController {

    private final ProviderAccessRequestService service;

    public ProviderAccessRequestController(ProviderAccessRequestService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProviderAccessRequestView>> submit(
            @Valid @RequestBody SubmitProviderAccessRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        ProviderAccessRequestView view = ProviderAccessRequestView.of(service.submit(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(view, ctx.correlationId().toString()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProviderAccessRequestView>>> list() {
        TrustContext ctx = TrustContextHolder.require();
        List<ProviderAccessRequestView> views = service.listForApplicant().stream()
                .map(ProviderAccessRequestView::of)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(views, ctx.correlationId().toString()));
    }

    @GetMapping("/{publicId}")
    public ResponseEntity<ApiResponse<ProviderAccessRequestView>> get(@PathVariable String publicId) {
        TrustContext ctx = TrustContextHolder.require();
        ProviderAccessRequestView view = ProviderAccessRequestView.of(service.get(publicId));
        return ResponseEntity.ok(ApiResponse.ok(view, ctx.correlationId().toString()));
    }
}
