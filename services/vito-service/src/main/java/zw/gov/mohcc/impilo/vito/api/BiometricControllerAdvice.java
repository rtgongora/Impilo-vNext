package zw.gov.mohcc.impilo.vito.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.vito.core.biometric.BiometricPolicyDeniedException;

@RestControllerAdvice(basePackages = "zw.gov.mohcc.impilo.vito.api")
public class BiometricControllerAdvice {

    @ExceptionHandler(BiometricPolicyDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> policyDenied(BiometricPolicyDeniedException ex) {
        String cid = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.status(403)
                .body(ApiResponse.error("BIOMETRIC_POLICY_DENIED", ex.getMessage(), 403, cid));
    }
}
