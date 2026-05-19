package zw.gov.mohcc.impilo.ndila.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class NdilaHealthController {

    @GetMapping("/internal/v1/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "service", "ndila-service",
                "status", "UP",
                "doctrine",
                "Ndila is the spatial intelligence layer of Impilo vNext: "
                        + "it turns locations, routes, boundaries, catchments, "
                        + "movements and risks into actionable health system intelligence."));
    }
}
