package zw.gov.mohcc.impilo.learning.api.v11.fundo;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.learning.fundo.FundoLearningProviderService;

/** Learning-provider & academy registry (C1; 3 regulated provider kinds). */
@RestController
@RequestMapping("/internal/v1/learning/v11")
public class FundoLearningProviderController {

    private static final UUID DEFAULT_TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final FundoLearningProviderService service;

    public FundoLearningProviderController(FundoLearningProviderService service) {
        this.service = service;
    }

    @PostMapping("/providers")
    public ResponseEntity<Map<String, Object>> createProvider(@RequestBody Map<String, Object> body) {
        return ok(service.createProvider(tenantId(), actorId(), body));
    }

    @GetMapping("/providers")
    public ResponseEntity<Map<String, Object>> listProviders(
            @RequestParam(required = false) String kind,
            @RequestParam(defaultValue = "100") int limit) {
        return ok(service.listProviders(tenantId(), kind, limit));
    }

    @GetMapping("/providers/{providerId}")
    public ResponseEntity<Map<String, Object>> getProvider(@PathVariable String providerId) {
        return ok(service.getProvider(tenantId(), providerId));
    }

    @PostMapping("/providers/{providerId}/spaces")
    public ResponseEntity<Map<String, Object>> createSpace(
            @PathVariable String providerId, @RequestBody Map<String, Object> body) {
        return ok(service.createSpace(tenantId(), providerId, actorId(), body));
    }

    @GetMapping("/providers/{providerId}/spaces")
    public ResponseEntity<Map<String, Object>> listSpaces(@PathVariable String providerId) {
        return ok(service.listSpaces(tenantId(), providerId));
    }

    private ResponseEntity<Map<String, Object>> ok(Object data) {
        return ResponseEntity.ok(Map.of("data", data == null ? Map.of() : data));
    }

    private UUID tenantId() {
        RequestContext ctx = RequestContextHolder.get();
        if (ctx == null) return DEFAULT_TENANT;
        try {
            return UUID.fromString(ctx.tenantId());
        } catch (Exception ex) {
            return DEFAULT_TENANT;
        }
    }

    private String actorId() {
        RequestContext ctx = RequestContextHolder.get();
        if (ctx == null || ctx.principal() == null || ctx.principal().getName() == null) {
            return "system";
        }
        return ctx.principal().getName();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handle(Exception ex) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", "FUNDO_PROVIDER_ERROR");
        error.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", error));
    }
}
