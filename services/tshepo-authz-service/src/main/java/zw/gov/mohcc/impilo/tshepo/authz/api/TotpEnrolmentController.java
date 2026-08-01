package zw.gov.mohcc.impilo.tshepo.authz.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Compatibility tombstone for Tshepo's retired authenticator endpoints.
 * Keycloak is the sole owner of TOTP secrets, passkeys and recovery codes. Keeping
 * an explicit 410 response avoids silently breaking old callers while ensuring
 * Tshepo can never receive or generate authenticator material.
 */
@Deprecated(forRemoval = true)
@RestController
@RequestMapping("/v1/step-up/totp")
public class TotpEnrolmentController {

    @PostMapping("/enrolment")
    public ResponseEntity<Map<String, Object>> enrol() {
        return retired();
    }

    @PostMapping("/enrolment/confirm")
    public ResponseEntity<Map<String, Object>> confirm() {
        return retired();
    }

    @PostMapping("/enrolment/revoke")
    public ResponseEntity<Map<String, Object>> revoke() {
        return retired();
    }

    private ResponseEntity<Map<String, Object>> retired() {
        return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
                "code", "TSHEPO_AUTHENTICATOR_RETIRED",
                "message", "Use the Keycloak-native OIDC enrollment or step-up action",
                "action", "/internal/v1/auth/oidc/action"));
    }
}
