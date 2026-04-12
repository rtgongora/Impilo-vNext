package zw.gov.mohcc.impilo.air.api;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.air.api.dto.ModelDtos.CreateVersionRequest;
import zw.gov.mohcc.impilo.air.api.dto.ModelDtos.VersionResponse;
import zw.gov.mohcc.impilo.air.service.RegistryModelVersionService;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;

@RestController
@RequestMapping("/internal/v1/ai-registry/models/{modelId}/versions")
public class ModelVersionController {

    private final RegistryModelVersionService versionService;

    public ModelVersionController(RegistryModelVersionService versionService) {
        this.versionService = versionService;
    }

    @GetMapping
    public ResponseEntity<List<VersionResponse>> list(@PathVariable UUID modelId) {
        return ResponseEntity.ok(versionService.list(modelId, RequestContextHolder.require()));
    }

    @PostMapping
    public ResponseEntity<VersionResponse> create(
            @PathVariable UUID modelId,
            @Valid @RequestBody CreateVersionRequest body,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {
        VersionResponse r = versionService.create(modelId, body, RequestContextHolder.require(), idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(r);
    }
}
