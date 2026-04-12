package zw.gov.mohcc.impilo.air.api;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.air.api.dto.ModelDtos.ApproveModelRequest;
import zw.gov.mohcc.impilo.air.api.dto.ModelDtos.CreateModelRequest;
import zw.gov.mohcc.impilo.air.api.dto.ModelDtos.ModelResponse;
import zw.gov.mohcc.impilo.air.api.dto.ModelDtos.UpdateModelRequest;
import zw.gov.mohcc.impilo.air.api.dto.ModelDtos.WithdrawModelRequest;
import zw.gov.mohcc.impilo.air.service.RegistryModelService;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;

@RestController
@RequestMapping("/internal/v1/ai-registry/models")
public class ModelController {

    private final RegistryModelService modelService;

    public ModelController(RegistryModelService modelService) {
        this.modelService = modelService;
    }

    @PostMapping
    public ResponseEntity<ModelResponse> create(
            @Valid @RequestBody CreateModelRequest body,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {
        ModelResponse r = modelService.create(body, RequestContextHolder.require(), idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(r);
    }

    @GetMapping
    public ResponseEntity<List<ModelResponse>> list() {
        return ResponseEntity.ok(modelService.list(RequestContextHolder.require()));
    }

    @GetMapping("/{modelId}")
    public ResponseEntity<ModelResponse> get(@PathVariable UUID modelId) {
        return ResponseEntity.ok(modelService.get(modelId, RequestContextHolder.require()));
    }

    @PutMapping("/{modelId}")
    public ResponseEntity<ModelResponse> update(
            @PathVariable UUID modelId,
            @Valid @RequestBody UpdateModelRequest body,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {
        return ResponseEntity.ok(modelService.update(modelId, body, RequestContextHolder.require(), idempotencyKey));
    }

    @PostMapping("/{modelId}/approve")
    public ResponseEntity<ModelResponse> approve(
            @PathVariable UUID modelId,
            @RequestBody(required = false) ApproveModelRequest body,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {
        return ResponseEntity.ok(modelService.approve(modelId, body, RequestContextHolder.require(), idempotencyKey));
    }

    @PostMapping("/{modelId}/withdraw")
    public ResponseEntity<ModelResponse> withdraw(
            @PathVariable UUID modelId,
            @RequestBody(required = false) WithdrawModelRequest body,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {
        return ResponseEntity.ok(modelService.withdraw(modelId, body, RequestContextHolder.require(), idempotencyKey));
    }
}
