package zw.gov.mohcc.impilo.tshepo.keys.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.tshepo.keys.core.ManagedSecretService;

import java.util.Map;
import java.util.UUID;

/**
 * W4a: managed-secret custody API (INTERNAL, service-to-service only — behind Envoy,
 * never public). Callers register a named secret and later resolve it by ref; the
 * value is AES-256-GCM encrypted at rest under the service KEK. Every resolve is
 * audited (logged with actor/purpose; a durable audit event rides the standard chain
 * at the gateway). This is the custody source de-id dataset secrets swap onto.
 */
@RestController
@RequestMapping("/v1/secrets")
public class SecretController {

    private static final Logger log = LoggerFactory.getLogger(SecretController.class);

    private final ManagedSecretService managedSecretService;

    public SecretController(ManagedSecretService managedSecretService) {
        this.managedSecretService = managedSecretService;
    }

    /** Register or rotate a named secret. Body: {tenantId, secretRef, purpose?, value}. */
    @PostMapping
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterSecretRequest request) {
        var saved = managedSecretService.registerOrRotate(
                request.tenantId(), request.secretRef(), request.purpose(), request.value());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "secretRef", saved.getSecretRef(),
                "status", saved.getStatus(),
                "rotated", saved.getRotatedAt() != null));
    }

    /** Resolve a named secret to plaintext (INTERNAL callers only). 404 if absent. */
    @GetMapping("/{secretRef}")
    public ResponseEntity<Map<String, Object>> resolve(
            @PathVariable String secretRef,
            @RequestParam UUID tenantId,
            @RequestHeader(value = "X-Actor-Id", required = false) String actorId) {
        return managedSecretService.resolve(tenantId, secretRef)
                .map(value -> {
                    log.info("Managed secret resolved [ref={}, tenant={}, actor={}]",
                            secretRef, tenantId, actorId != null ? actorId : "service");
                    return ResponseEntity.ok(Map.<String, Object>of("secretRef", secretRef, "value", value));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        Map.of("error", "SECRET_NOT_FOUND", "secretRef", secretRef)));
    }

    public record RegisterSecretRequest(
            @NotNull UUID tenantId,
            @NotBlank String secretRef,
            String purpose,
            @NotBlank String value) {}
}
