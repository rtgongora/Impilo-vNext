package zw.gov.mohcc.impilo.vito.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.vito.core.patientshare.PatientShareCollaborationService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Unauthenticated entry lane for external providers using patient-mediated share token + OTP + session.
 * Session token must be presented on subsequent calls (header {@code X-Patient-Share-Session}).
 */
@RestController
@RequestMapping("/v1/public/patient-shares")
public class PublicPatientShareController {

    public static final String H_SESSION = "X-Patient-Share-Session";

    private final PatientShareCollaborationService collaborationService;

    public PublicPatientShareController(PatientShareCollaborationService collaborationService) {
        this.collaborationService = collaborationService;
    }

    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<PatientShareCollaborationService.ValidateArtifactResult>> validate(
            @RequestBody Map<String, String> body) {
        String token = body.get("shareToken");
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("shareToken required");
        }
        var res = collaborationService.validateArtifact(token.trim());
        return ResponseEntity.ok(ApiResponse.ok(res, correlationId()));
    }

    @GetMapping("/councils")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> councils(@RequestParam UUID tenantId) {
        var res = collaborationService.listCollaborationCouncils(tenantId);
        return ResponseEntity.ok(ApiResponse.ok(res, correlationId()));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<PatientShareCollaborationService.OtpVerifyResult>> verifyOtp(
            @RequestBody Map<String, String> body) {
        String token = body.get("shareToken");
        String otp = body.get("otp");
        if (token == null || otp == null) {
            throw new IllegalArgumentException("shareToken and otp required");
        }
        var res = collaborationService.verifyOtp(token.trim(), otp.trim());
        return ResponseEntity.ok(ApiResponse.ok(res, correlationId()));
    }

    @PostMapping("/verify-step-up")
    public ResponseEntity<ApiResponse<Void>> verifyStepUp(
            @RequestHeader(H_SESSION) String sessionToken,
            @RequestBody Map<String, String> body) {
        String code = body.get("stepUpCode");
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("stepUpCode required");
        }
        collaborationService.verifyStepUp(sessionToken.trim(), code.trim());
        return ResponseEntity.ok(ApiResponse.ok(null, correlationId()));
    }

    @PostMapping("/complete-identity")
    public ResponseEntity<ApiResponse<PatientShareCollaborationService.WorkspaceBootstrapResult>> complete(
            @RequestHeader(H_SESSION) String sessionToken,
            @RequestBody Map<String, Object> body) {
        var cmd = new PatientShareCollaborationService.CompleteIdentityCommand(
                longVal(body, "councilId"),
                stringReq(body, "councilRegistrationNumber"),
                stringReq(body, "givenName"),
                stringReq(body, "familyName"),
                stringVal(body, "professionOrCadre", null),
                stringVal(body, "email", null),
                stringVal(body, "phone", null),
                stringVal(body, "organisationHint", null));
        var res = collaborationService.completeIdentity(sessionToken.trim(), cmd);
        return ResponseEntity.ok(ApiResponse.ok(res, correlationId()));
    }

    @GetMapping("/workspace")
    public ResponseEntity<ApiResponse<PatientShareCollaborationService.WorkspaceContextResult>> workspace(
            @RequestHeader(H_SESSION) String sessionToken) {
        var res = collaborationService.getWorkspace(sessionToken.trim());
        return ResponseEntity.ok(ApiResponse.ok(res, correlationId()));
    }

    @PostMapping("/contributions")
    public ResponseEntity<ApiResponse<PatientShareCollaborationService.ContributionRecordedResult>> contribute(
            @RequestHeader(H_SESSION) String sessionToken,
            @RequestBody Map<String, String> body) {
        var cmd = new PatientShareCollaborationService.ContributionCommand(
                stringReq(body, "targetRecordType"),
                body.get("targetRecordId"),
                stringReq(body, "actionType"),
                body.get("summary"),
                body.get("payloadJson"));
        var res = collaborationService.addContribution(sessionToken.trim(), cmd);
        return ResponseEntity.ok(ApiResponse.ok(res, correlationId()));
    }

    private static String correlationId() {
        return TrustContextHolder.get() != null
                ? TrustContextHolder.get().correlationId().toString()
                : UUID.randomUUID().toString();
    }

    private static String stringReq(Map<String, ?> body, String k) {
        Object v = body.get(k);
        if (v == null || String.valueOf(v).isBlank()) {
            throw new IllegalArgumentException(k + " required");
        }
        return String.valueOf(v).trim();
    }

    private static String stringVal(Map<String, Object> body, String k, String def) {
        Object v = body.get(k);
        if (v == null) {
            return def;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? def : s;
    }

    private static long longVal(Map<String, Object> body, String k) {
        Object v = body.get(k);
        if (v instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(String.valueOf(v).trim());
    }
}
