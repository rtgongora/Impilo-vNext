package zw.gov.mohcc.impilo.analyticspipeline;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class AnalyticsPipelineServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AnalyticsPipelineServiceApplication.class, args);
    }

    @RestController
    static class HealthController {
        @GetMapping("/internal/v1/health")
        String health() {
            return "ok";
        }
    }
}
