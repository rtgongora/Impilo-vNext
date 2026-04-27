package zw.gov.mohcc.impilo.costa.api.access;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.costa.api.dto.ServiceAccessDecisionCreateRequest;
import zw.gov.mohcc.impilo.costa.api.dto.ServiceAccessDecisionResponse;
import zw.gov.mohcc.impilo.costa.service.ServiceAccessDecisionService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/costa/v1/service-access-decisions")
public class ServiceAccessDecisionController {

    private final ServiceAccessDecisionService service;

    public ServiceAccessDecisionController(ServiceAccessDecisionService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ServiceAccessDecisionResponse>> create(
            @Valid @RequestBody ServiceAccessDecisionCreateRequest body) {
        var ctx = TrustContextHolder.require();
        ServiceAccessDecisionResponse created = service.create(body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(created, ctx.correlationId().toString()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ServiceAccessDecisionResponse>>> list(
            @RequestParam(name = "encounter_id", required = false) String encounterId) {
        var ctx = TrustContextHolder.require();
        List<ServiceAccessDecisionResponse> items = service.listRecent(encounterId);
        return ResponseEntity.ok(ApiResponse.ok(items, ctx.correlationId().toString()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ServiceAccessDecisionResponse>> get(@PathVariable("id") UUID id) {
        var ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(service.require(id), ctx.correlationId().toString()));
    }
}
