package zw.gov.mohcc.impilo.procurement.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;

import java.util.Map;

@RestController
public class ProcHealthController {
    @GetMapping("/internal/v1/health")
    public ResponseEntity<Map<String, Object>> health() {
        var ctx = RequestContextHolder.get();
        return ResponseEntity.ok(Map.of("service", "procurement-service", "tenant", ctx != null ? ctx.tenantId() : null));
    }
}
