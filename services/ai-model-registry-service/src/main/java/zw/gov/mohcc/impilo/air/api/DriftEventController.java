package zw.gov.mohcc.impilo.air.api;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.air.api.dto.ModelDtos.CreateDriftRequest;
import zw.gov.mohcc.impilo.air.api.dto.ModelDtos.DriftResponse;
import zw.gov.mohcc.impilo.air.service.DriftEventService;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;

@RestController
@RequestMapping("/internal/v1/ai-registry/drift-events")
public class DriftEventController {

    private final DriftEventService driftEventService;

    public DriftEventController(DriftEventService driftEventService) {
        this.driftEventService = driftEventService;
    }

    @PostMapping
    public ResponseEntity<DriftResponse> create(
            @Valid @RequestBody CreateDriftRequest body,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {
        DriftResponse r = driftEventService.record(body, RequestContextHolder.require(), idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(r);
    }

    @GetMapping
    public ResponseEntity<List<DriftResponse>> listByModel(@RequestParam("model_id") UUID modelId) {
        return ResponseEntity.ok(driftEventService.listByModel(modelId, RequestContextHolder.require()));
    }
}
