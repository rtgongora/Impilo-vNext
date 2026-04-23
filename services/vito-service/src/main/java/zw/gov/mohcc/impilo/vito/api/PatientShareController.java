package zw.gov.mohcc.impilo.vito.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.vito.core.patientshare.PatientShareCollaborationService;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/clients/{healthId}/patient-shares")
public class PatientShareController {

    private final PatientShareCollaborationService collaborationService;

    public PatientShareController(PatientShareCollaborationService collaborationService) {
        this.collaborationService = collaborationService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PatientShareCollaborationService.CreateShareResult>> create(
            @PathVariable UUID healthId,
            @RequestBody Map<String, Object> body) {
        var cmd = mapToCreateCommand(body);
        var res = collaborationService.createShare(healthId, cmd);
        return ResponseEntity.ok(ApiResponse.ok(res, TrustContextHolder.require().correlationId().toString()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<java.util.List<PatientShareCollaborationService.ShareSummary>>> list(
            @PathVariable UUID healthId) {
        var res = collaborationService.listShares(healthId);
        return ResponseEntity.ok(ApiResponse.ok(res, TrustContextHolder.require().correlationId().toString()));
    }

    @PostMapping("/{shareRequestId}/revoke")
    public ResponseEntity<ApiResponse<Void>> revoke(
            @PathVariable UUID healthId,
            @PathVariable long shareRequestId,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        collaborationService.revokeShare(healthId, shareRequestId, reason != null ? reason : "REVOKED_BY_PATIENT");
        return ResponseEntity.ok(ApiResponse.ok(null, TrustContextHolder.require().correlationId().toString()));
    }

    @GetMapping("/{shareRequestId}/contributions")
    public ResponseEntity<ApiResponse<java.util.List<PatientShareCollaborationService.ContributionView>>> contributions(
            @PathVariable UUID healthId,
            @PathVariable long shareRequestId) {
        var res = collaborationService.listContributions(healthId, shareRequestId);
        return ResponseEntity.ok(ApiResponse.ok(res, TrustContextHolder.require().correlationId().toString()));
    }

    private static PatientShareCollaborationService.CreateShareCommand mapToCreateCommand(Map<String, Object> body) {
        String scope = stringVal(body, "scopeType", "LIMITED_CLINICAL_SUMMARY");
        int hours = intVal(body, "expiryHours", 72);
        return new PatientShareCollaborationService.CreateShareCommand(
                scope,
                stringVal(body, "purposeOfUse", null),
                stringVal(body, "shareType", null),
                hours,
                stringVal(body, "targetProviderHint", null),
                stringVal(body, "allowedActions", null),
                stringVal(body, "visibilityProfile", null),
                stringVal(body, "updatePermissions", null),
                stringVal(body, "notes", null),
                boolVal(body, "revocableFlag", true),
                boolVal(body, "oneTimeUse", true),
                stringVal(body, "scopeSummary", null),
                stringVal(body, "artifactType", null));
    }

    private static String stringVal(Map<String, Object> m, String k, String def) {
        Object v = m.get(k);
        if (v == null) {
            return def;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? def : s;
    }

    private static int intVal(Map<String, Object> m, String k, int def) {
        Object v = m.get(k);
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                return def;
            }
        }
        return def;
    }

    private static boolean boolVal(Map<String, Object> m, String k, boolean def) {
        Object v = m.get(k);
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return def;
    }
}
