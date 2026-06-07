package zw.gov.mohcc.impilo.dags.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class InternalHealthController {

    @GetMapping("/internal/v1/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "service", "data-access-governance-service",
                "status", "UP"));
    }
}
