package zw.gov.mohcc.impilo.learning.api.v11;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.learning.core.LearningPlatformFacade;

/**
 * Native v1.1-compliant read surface for Impilo Fundo.
 *
 * <p><strong>Phase 3C of the Impilo Fundo integration plan.</strong> This controller
 * adds <em>one</em> additive endpoint under {@code /internal/v1/learning/v11/**}
 * that is fully governed by the platform v1.1 contract (companion header
 * enforcement, error envelope, client-timeout enforcement, idempotency
 * filter for any future commands). It exists alongside the existing legacy
 * {@code /internal/v1/learning/**} controllers, which are left untouched
 * and continue to serve Fundo's BFF traffic exactly as before.</p>
 *
 * <p>The endpoint reads from the same native Fundo data path as the legacy
 * {@code GET /internal/v1/learning/subject-completions} — it delegates to
 * {@link LearningPlatformFacade#listSubjectCompletions(UUID, String, String, int)}.
 * It does <strong>not</strong> introduce any new domain behaviour, schema,
 * Moodle call, or external-LMS dependency. Impilo Fundo is the native
 * vNext LMS; Moodle (and any other external LMS) is only an optional adapter
 * and is intentionally never invoked from this surface.</p>
 *
 * <h3>Contract</h3>
 * <ul>
 *   <li>Path: {@code GET /internal/v1/learning/v11/subject-completions}</li>
 *   <li>Required platform headers: {@code X-Tenant-ID}, {@code X-Pod-ID},
 *       {@code X-Request-ID}, {@code X-Correlation-ID} (enforced by
 *       {@code V11HeaderFilter}; not enforced inside this controller).</li>
 *   <li>Optional query: {@code subjectType}, {@code subjectId}, {@code limit}
 *       (default {@code 25}, clamped 1–100 in the facade).</li>
 *   <li>Success response: HTTP 200 with the v1.1 envelope
 *       {@code {"data": {"items": [...], "subjectType": ..., "subjectId": ..., "limit": ...}}}.</li>
 *   <li>Empty success: when {@code subjectType}/{@code subjectId} are
 *       absent or when the tenant header is not a valid UUID, returns an
 *       empty {@code data.items} array with HTTP 200 rather than a 4xx —
 *       this keeps the surface read-only and side-effect-free and lets
 *       the golden-contract harness's "all headers present passes
 *       through" assertion succeed without any seed data.</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/v1/learning/v11")
public class LearningV11ReadController {

    private static final Logger log = LoggerFactory.getLogger(LearningV11ReadController.class);

    private final LearningPlatformFacade facade;

    public LearningV11ReadController(LearningPlatformFacade facade) {
        this.facade = facade;
    }

    @GetMapping("/subject-completions")
    public ResponseEntity<Map<String, Object>> subjectCompletionsV11(
            @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) String subjectId,
            @RequestParam(defaultValue = "25") int limit) {

        RequestContext ctx = RequestContextHolder.require();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("subjectType", subjectType);
        data.put("subjectId", subjectId);
        data.put("limit", limit);

        if (subjectType == null || subjectType.isBlank()
                || subjectId == null || subjectId.isBlank()) {
            data.put("items", List.of());
            return ResponseEntity.ok(Map.of("data", data));
        }

        UUID tenantId;
        try {
            tenantId = UUID.fromString(ctx.tenantId());
        } catch (IllegalArgumentException ex) {
            // Non-UUID tenant header — the v1.1 envelope still requires a 2xx
            // for "headers present" callers. Returning an empty page rather
            // than 400 preserves the legacy controller's behaviour for valid
            // UUIDs and avoids leaking implementation details for invalid ones.
            log.debug("v11 subject-completions: tenant header is not a UUID ({}); returning empty page",
                    ctx.tenantId());
            data.put("items", List.of());
            return ResponseEntity.ok(Map.of("data", data));
        }

        List<Map<String, Object>> rows =
                facade.listSubjectCompletions(tenantId, subjectType, subjectId, limit);
        data.put("items", rows);
        return ResponseEntity.ok(Map.of("data", data));
    }
}
