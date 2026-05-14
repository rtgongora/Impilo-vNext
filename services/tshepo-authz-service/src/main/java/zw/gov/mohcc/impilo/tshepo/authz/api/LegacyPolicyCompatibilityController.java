package zw.gov.mohcc.impilo.tshepo.authz.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.tshepo.authz.service.LegacyPolicyCompatibilityProxyService;

import java.util.Map;

/**
 * Temporary compatibility proxy so active consumers can migrate from tshepo-service to tshepo-authz-service
 * while legacy policy routes are retired in a controlled way.
 */
@RestController
@RequestMapping("/v1")
public class LegacyPolicyCompatibilityController {

    private final LegacyPolicyCompatibilityProxyService proxyService;

    public LegacyPolicyCompatibilityController(LegacyPolicyCompatibilityProxyService proxyService) {
        this.proxyService = proxyService;
    }

    @PostMapping("/biometric-policy/evaluate")
    public ResponseEntity<Map> biometricPolicyEvaluate(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        return proxyService.forwardPost("/v1/biometric-policy/evaluate", body, request);
    }

    @PostMapping("/patient-share-policy/evaluate")
    public ResponseEntity<Map> patientSharePolicyEvaluate(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        return proxyService.forwardPost("/v1/patient-share-policy/evaluate", body, request);
    }

    @PostMapping("/council-regulatory/evaluate")
    public ResponseEntity<Map> councilRegulatoryEvaluate(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        return proxyService.forwardPost("/v1/council-regulatory/evaluate", body, request);
    }
}
