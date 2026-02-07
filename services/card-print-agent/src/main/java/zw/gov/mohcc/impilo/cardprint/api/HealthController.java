package zw.gov.mohcc.impilo.cardprint.api;

import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {
    @GetMapping("/status")
    public Map<String, String> status() {
        return Map.of("service", "card-print-agent", "status", "UP");
    }
}
