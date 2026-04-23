package zw.gov.mohcc.impilo.vito.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.vito.core.patientshare.PatientSharePolicyDeniedException;

import java.util.UUID;

@RestControllerAdvice(assignableTypes = {PatientShareController.class, PublicPatientShareController.class})
public class PatientShareControllerAdvice {

    @ExceptionHandler(PatientSharePolicyDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> policyDenied(PatientSharePolicyDeniedException ex) {
        String cid = TrustContextHolder.get() != null
                ? TrustContextHolder.get().correlationId().toString()
                : UUID.randomUUID().toString();
        return ResponseEntity.status(403)
                .body(ApiResponse.error("PATIENT_SHARE_POLICY_DENIED", ex.getMessage(), 403, cid));
    }
}
