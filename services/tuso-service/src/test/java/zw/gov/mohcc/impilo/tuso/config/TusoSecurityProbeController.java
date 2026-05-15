package zw.gov.mohcc.impilo.tuso.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class TusoSecurityProbeController {

    @GetMapping("/v1/internal/security-probe")
    String probe() {
        return "ok";
    }
}
