package zw.gov.mohcc.impilo.referral;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@EnableScheduling
public class ReferralServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReferralServiceApplication.class, args);
    }

    @RestController
    static class HealthController {
        @GetMapping("/internal/v1/health")
        String health() {
            return "ok";
        }
    }
}
