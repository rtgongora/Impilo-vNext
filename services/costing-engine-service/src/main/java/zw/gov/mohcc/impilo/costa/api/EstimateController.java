package zw.gov.mohcc.impilo.costa.api;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.costa.api.dto.EstimateRequest;
import zw.gov.mohcc.impilo.costa.service.EstimateService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/costa/v1/estimate")
public class EstimateController {

    private final EstimateService estimateService;

    public EstimateController(EstimateService estimateService) {
        this.estimateService = estimateService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EstimateService.EstimateResult>> createEstimate(
            @Valid @RequestBody EstimateRequest request) {

        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        Map<String, Object> context = request.context() != null ? new HashMap<>(request.context()) : new HashMap<>();

        List<EstimateService.EstimateLineInput> items = request.items().stream()
                .map(i -> new EstimateService.EstimateLineInput(
                        i.msikaCode(), i.description(), i.kind(), i.qty(),
                        i.costMethod(), i.extras() != null ? i.extras() : Map.of()))
                .collect(Collectors.toList());

        EstimateService.EstimateResult result = estimateService.estimate(
                ctx.tenantId(), ctx.facilityId(), items,
                request.exemptionCategory(), request.insurancePlanId(), context);

        return ResponseEntity.ok(ApiResponse.ok(result, correlationId));
    }
}
