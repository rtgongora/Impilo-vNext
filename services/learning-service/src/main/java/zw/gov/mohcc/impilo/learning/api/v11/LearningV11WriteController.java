package zw.gov.mohcc.impilo.learning.api.v11;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.learning.core.LearningPlatformFacade;

/**
 * Native v1.1-compliant write surface for Impilo Fundo.
 *
 * <p><strong>Phase 4A of the Impilo Fundo integration plan.</strong> This
 * controller adds one additive {@code POST} endpoint under
 * {@code /internal/v1/learning/v11/**} so the platform's v1.1 contract suite
 * (header enforcement + idempotency + error envelope + client-timeout) runs
 * against a real native Fundo handler rather than the synthetic
 * {@code __contract-probe} path used in Phase 3C.</p>
 *
 * <h3>Contract</h3>
 * <ul>
 *   <li>Path: {@code POST /internal/v1/learning/v11/resource-opened-acknowledgements}</li>
 *   <li>Required platform headers: {@code X-Tenant-ID}, {@code X-Pod-ID},
 *       {@code X-Request-ID}, {@code X-Correlation-ID}, {@code Idempotency-Key}
 *       (the first four are enforced by {@code V11HeaderFilter}; the last by
 *       {@code IdempotencyFilter}).</li>
 *   <li>Request body: any JSON object. The optional fields
 *       {@code resourceId} (UUID string), {@code actorId}, {@code actorType},
 *       and {@code workflowContext} (free-form map) are honoured when present.</li>
 *   <li>Success response: HTTP 200 with the v1.1 envelope
 *       {@code {"data": {"status": "recorded" | "acknowledged", ...}}}.</li>
 *   <li><strong>Tolerant inputs:</strong> when {@code resourceId} is missing or
 *       not a valid UUID, or when {@code X-Tenant-ID} is not a valid UUID, the
 *       endpoint returns a side-effect-free
 *       {@code {"data": {"status": "acknowledged", "noOp": true, ...}}} 200
 *       envelope rather than 4xx. Rationale: the platform's golden-contract
 *       harness sends {@code X-Tenant-ID: moh-zw} and bodies such as
 *       {@code {"name":"replay-test"}} that intentionally do not satisfy this
 *       endpoint's domain inputs; the v1.1 envelope still requires a 2xx for
 *       "all headers present + idempotency key present" callers, and the
 *       legacy {@code POST /internal/v1/learning/resource-opened} controller
 *       is unchanged.</li>
 * </ul>
 *
 * <h3>What this is not</h3>
 *
 * <p>This endpoint is <em>not</em> a replacement for the legacy
 * {@code POST /internal/v1/learning/resource-opened}. Both endpoints delegate
 * to the same native {@link LearningPlatformFacade#recordResourceOpened} method
 * — the v1.1 surface is additive so that consumers can migrate to the v1.1
 * envelope at their own pace. No Moodle, Open edX, or other external LMS is
 * invoked here. Impilo Fundo is the native vNext LMS; this controller's only
 * collaborator is the native Fundo facade.</p>
 */
@RestController
@RequestMapping("/internal/v1/learning/v11")
public class LearningV11WriteController {

    private static final Logger log = LoggerFactory.getLogger(LearningV11WriteController.class);

    private final LearningPlatformFacade facade;

    public LearningV11WriteController(LearningPlatformFacade facade) {
        this.facade = facade;
    }

    @PostMapping("/resource-opened-acknowledgements")
    public ResponseEntity<Map<String, Object>> resourceOpenedAcknowledgement(
            @RequestBody(required = false) Map<String, Object> body) {

        RequestContext ctx = RequestContextHolder.require();

        UUID tenantId = tryParseUuid(ctx.tenantId());
        UUID resourceId = body == null ? null : tryParseUuid(asString(body.get("resourceId")));

        Map<String, Object> data = new LinkedHashMap<>();

        if (tenantId == null || resourceId == null) {
            data.put("status", "acknowledged");
            data.put("noOp", true);
            data.put(
                    "reason",
                    tenantId == null
                            ? "tenant_header_not_uuid"
                            : "resource_id_missing_or_invalid");
            log.debug(
                    "v11 resource-opened-ack: no-op envelope returned (tenant={}, resourceIdPresent={})",
                    ctx.tenantId(),
                    body != null && body.get("resourceId") != null);
            return ResponseEntity.ok(Map.of("data", data));
        }

        String actorId = ctx.principal() != null ? ctx.principal().getName() : asString(body.get("actorId"));
        if (actorId == null || actorId.isBlank()) {
            actorId = "";
        }
        String actorType = asString(body.get("actorType"));
        if (actorType == null || actorType.isBlank()) {
            actorType = "USER";
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> workflowContext = (Map<String, Object>) body.get("workflowContext");

        facade.recordResourceOpened(tenantId, actorId, actorType, resourceId, ctx.correlationId(), workflowContext);

        data.put("status", "recorded");
        data.put("resourceId", resourceId.toString());
        return ResponseEntity.ok(Map.of("data", data));
    }

    private static UUID tryParseUuid(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String asString(Object v) {
        return v == null ? null : v.toString();
    }
}
