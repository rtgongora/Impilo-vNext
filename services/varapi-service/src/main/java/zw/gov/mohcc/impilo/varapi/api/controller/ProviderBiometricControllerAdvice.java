package zw.gov.mohcc.impilo.varapi.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.varapi.core.biometric.ProviderBiometricPolicyDeniedException;

@RestControllerAdvice(basePackageClasses = ProviderBiometricController.class)
public class ProviderBiometricControllerAdvice {

    @ExceptionHandler(ProviderBiometricPolicyDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> denied(ProviderBiometricPolicyDeniedException ex) {
        String cid = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.status(403)
                .body(ApiResponse.error("BIOMETRIC_POLICY_DENIED", ex.getMessage(), 403, cid));
    }
}
