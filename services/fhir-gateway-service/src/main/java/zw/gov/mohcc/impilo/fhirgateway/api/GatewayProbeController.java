package zw.gov.mohcc.impilo.fhirgateway.api;

import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1")
public class GatewayProbeController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthProbe() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "fhir-gateway",
                "timestamp", OffsetDateTime.now().toString()
        ));
    }

    @PostMapping("/test-command")
    public ResponseEntity<Map<String, Object>> testCommand(@RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "service", "fhir-gateway",
                "command", "test-command",
                "received", body != null ? body : Map.of(),
                "timestamp", OffsetDateTime.now().toString()
        ));
    }
}
