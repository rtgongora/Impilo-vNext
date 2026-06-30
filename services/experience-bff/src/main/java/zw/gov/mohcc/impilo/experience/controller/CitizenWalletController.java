package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;
import zw.gov.mohcc.impilo.experience.service.PiiAccessAuditService;
import zw.gov.mohcc.impilo.experience.service.WalletOverviewService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Unified Person Health Wallet — the person anchor experience surface (MY LIFE / MY HEALTH).
 *
 * <p>This controller is a thin <strong>composition / orchestration</strong> layer. It owns no
 * data: {@link WalletOverviewService} fans out to the sovereign owners (VITO, identity-assurance,
 * PCT/BUTANO, tshepo-consent, mvumo, Costa, notification-service, guidance/Nompilo) and returns a
 * single person-centred overview. Demographic corrections are routed to VITO's correction workflow
 * (no direct mutation of high-trust fields).</p>
 *
 * <p>Routes (web + mobile share these BFF endpoints):</p>
 * <ul>
 *   <li>GET  /internal/v1/citizen/wallet/overview        — aggregated wallet home</li>
 *   <li>POST /internal/v1/citizen/wallet/profile/correction — submit a demographic correction request</li>
 * </ul>
 *
 * <p>Every overview read is recorded via {@link PiiAccessAuditService} (actor, subject, fields,
 * purpose, channel) so cross-person and self clinical-adjacent access is auditable, per doctrine.</p>
 */
@RestController
@RequestMapping("/internal/v1/citizen/wallet")
public class CitizenWalletController {

    private final WalletOverviewService walletService;
    private final VitoServiceClient vitoClient;
    private final PiiAccessAuditService auditService;

    public CitizenWalletController(WalletOverviewService walletService,
                                   VitoServiceClient vitoClient,
                                   PiiAccessAuditService auditService) {
        this.walletService = walletService;
        this.vitoClient = vitoClient;
        this.auditService = auditService;
    }

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> overview(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestHeader(value = "X-Subject-ID", required = false) String subjectId,
            @RequestHeader(value = "X-Actor-Type", required = false) String actorType,
            @RequestHeader(value = "X-Purpose-Of-Use", required = false) String purposeOfUse,
            @RequestHeader(value = "X-Facility-ID", required = false) String facilityId,
            HttpServletRequest request) {

        // The wallet may be viewed for the signed-in person, or — under an active delegation — for a
        // dependant/subject (X-Subject-ID). The trust plane authorises the delegated subject; we
        // resolve which person's wallet to compose and audit the cross-person access explicitly.
        boolean delegated = subjectId != null && !subjectId.isBlank() && !subjectId.equals(actorId);
        String subjectPerson = delegated ? stripPrefix(subjectId) : actorId;

        Map<String, Object> data = walletService.buildOverview(subjectPerson);
        data.put("viewingAs", delegated ? "DELEGATE" : "SELF");
        if (delegated) {
            data.put("subjectId", subjectPerson);
        }

        auditService.recordAccess(
                tenantId,
                actorId,
                actorType != null ? actorType : "CITIZEN",
                subjectPerson,
                "PATIENT",
                "VIEW",
                new String[]{"identity", "trust", "records", "careTimeline", "consent", "dependants", "payments", "commsPreferences"},
                purposeOfUse != null ? purposeOfUse : (delegated ? "TREATMENT" : "SELF_SERVICE"),
                channel(request),
                "FULL",
                clientIp(request),
                request.getHeader("User-Agent"),
                facilityId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    /**
     * Submit a demographic correction request. Routed to VITO's correction workflow — high-trust
     * fields are NOT overwritten inline; the request is recorded for steward review. The correction
     * submission is itself audited as a profile-change request.
     */
    @PostMapping("/profile/correction")
    public ResponseEntity<Map<String, Object>> submitCorrection(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestHeader(value = "X-Actor-Type", required = false) String actorType,
            @RequestHeader(value = "X-Facility-ID", required = false) String facilityId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        JsonNode result = vitoClient.recordClientCorrection(actorId, body);

        auditService.recordAccess(
                tenantId,
                actorId,
                actorType != null ? actorType : "CITIZEN",
                actorId,
                "PATIENT",
                "CORRECTION_REQUEST",
                fieldsOf(body),
                "SELF_SERVICE",
                channel(request),
                "FULL",
                clientIp(request),
                request.getHeader("User-Agent"),
                facilityId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    private static String[] fieldsOf(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            return new String[]{"correction"};
        }
        return body.keySet().toArray(new String[0]);
    }

    private static String stripPrefix(String ref) {
        int idx = ref.indexOf('/');
        return idx >= 0 ? ref.substring(idx + 1) : ref;
    }

    private static String channel(HttpServletRequest request) {
        String src = request.getHeader("X-Request-Source");
        if (src != null && src.toUpperCase().contains("MOBILE")) {
            return "APP";
        }
        return "WEB";
    }

    private static String clientIp(HttpServletRequest request) {
        String fwd = request.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            return fwd.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
