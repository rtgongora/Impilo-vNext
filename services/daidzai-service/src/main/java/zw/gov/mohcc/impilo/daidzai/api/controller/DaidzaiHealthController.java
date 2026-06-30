package zw.gov.mohcc.impilo.daidzai.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal/v1")
public class DaidzaiHealthController {
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("service", "daidzai-service", "status", "UP");
    }
}
