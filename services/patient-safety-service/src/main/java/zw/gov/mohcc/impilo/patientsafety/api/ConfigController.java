package zw.gov.mohcc.impilo.patientsafety.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.patientsafety.api.dto.ApiResponse;
import zw.gov.mohcc.impilo.patientsafety.core.ConfigService;

import java.util.List;
import java.util.Map;

/**
 * Reference configuration surface: external VigiMobile / VigiFlow eForm link-outs and the honest
 * adapter posture used by the workbench export-readiness / dispatch indicators.
 */
@RestController
@RequestMapping("/internal/v1/patient-safety/config")
public class ConfigController {

    private final ConfigService config;

    public ConfigController(ConfigService config) {
        this.config = config;
    }

    @GetMapping("/vigimobile-links")
    public ResponseEntity<ApiResponse<Map<String, Object>>> vigiMobileLinks() {
        return ResponseEntity.ok(ApiResponse.of(config.vigiMobileLinks()));
    }

    @GetMapping("/adapters")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> adapters() {
        return ResponseEntity.ok(ApiResponse.of(config.adapters()));
    }
}
