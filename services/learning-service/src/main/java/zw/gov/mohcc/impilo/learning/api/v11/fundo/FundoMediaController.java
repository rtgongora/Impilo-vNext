package zw.gov.mohcc.impilo.learning.api.v11.fundo;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.learning.fundo.FundoMediaWatchService;

/**
 * Recorded-media surface for the native Fundo v1.1 contract (LEARNING_RECORDING
 * W4): watch progress, chapters, bookmarks, enrolment-gated playback references
 * and the replay authoring queue.
 *
 * <p>Authz follows the estate model: Envoy ext_authz → TSHEPO fronts the route;
 * tenancy comes from {@code X-Tenant-ID} via {@code RequestContextHolder}; the
 * service layer enforces enrolment membership (the enrolment must belong to the
 * tenant and the asset to the enrolment's course) on every learner-scoped call.
 * Every meaningful mutation is audited through the {@code lrn_event_outbox}
 * v1.1 events emitted by {@code FundoMediaWatchService}.</p>
 */
@RestController
@RequestMapping("/internal/v1/learning/v11")
public class FundoMediaController {

    private final FundoMediaWatchService mediaWatchService;

    public FundoMediaController(FundoMediaWatchService mediaWatchService) {
        this.mediaWatchService = mediaWatchService;
    }

    // ── watch progress ───────────────────────────────────────────────────────

    @PostMapping("/media/{assetId}/watch-progress")
    public ResponseEntity<Map<String, Object>> upsertWatchProgress(
            @PathVariable String assetId,
            @RequestBody(required = false) Map<String, Object> body) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoV11Support.requireTenantOrNull(ctx);
        UUID asset = FundoV11Support.tryParseUuid(assetId);
        if (tenantId == null || body == null || asset == null) {
            return FundoV11Support.badRequest("INVALID_INPUT",
                    tenantId == null ? "Tenant header is not a valid UUID"
                            : asset == null ? "assetId must be a UUID" : "Request body is required");
        }
        UUID enrolmentId = FundoV11Support.tryParseUuid(asString(body.get("enrolmentId")));
        if (enrolmentId == null) {
            return FundoV11Support.badRequest("INVALID_INPUT", "enrolmentId is required and must be a UUID");
        }
        try {
            Map<String, Object> view = mediaWatchService.upsertWatchProgress(tenantId, asset,
                    new FundoMediaWatchService.WatchUpdate(
                            enrolmentId,
                            asInt(body.get("positionSeconds")),
                            asInt(body.get("watchedSeconds")),
                            asInt(body.get("durationSeconds"))));
            return FundoV11Support.dataEnvelope("watchProgress", view);
        } catch (IllegalArgumentException ex) {
            return FundoV11Support.notFound("NOT_FOUND", ex.getMessage());
        } catch (IllegalStateException ex) {
            return FundoV11Support.conflict("CONFLICT", ex.getMessage());
        }
    }

    @GetMapping("/media/{assetId}/watch-progress")
    public ResponseEntity<Map<String, Object>> getWatchProgress(
            @PathVariable String assetId,
            @RequestParam String enrolmentId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoV11Support.requireTenantOrNull(ctx);
        UUID asset = FundoV11Support.tryParseUuid(assetId);
        UUID enrolment = FundoV11Support.tryParseUuid(enrolmentId);
        if (tenantId == null || asset == null || enrolment == null) {
            return FundoV11Support.badRequest("INVALID_INPUT", "assetId and enrolmentId must be UUIDs");
        }
        try {
            return FundoV11Support.dataEnvelope("watchProgress",
                    mediaWatchService.getWatchProgress(tenantId, asset, enrolment));
        } catch (IllegalArgumentException ex) {
            return FundoV11Support.notFound("NOT_FOUND", ex.getMessage());
        }
    }

    // ── playback resolution ──────────────────────────────────────────────────

    @GetMapping("/media/{assetId}/playback-ref")
    public ResponseEntity<Map<String, Object>> playbackRef(
            @PathVariable String assetId,
            @RequestParam String enrolmentId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoV11Support.requireTenantOrNull(ctx);
        UUID asset = FundoV11Support.tryParseUuid(assetId);
        UUID enrolment = FundoV11Support.tryParseUuid(enrolmentId);
        if (tenantId == null || asset == null || enrolment == null) {
            return FundoV11Support.badRequest("INVALID_INPUT", "assetId and enrolmentId must be UUIDs");
        }
        try {
            return FundoV11Support.dataEnvelope("asset",
                    mediaWatchService.playbackRef(tenantId, asset, enrolment));
        } catch (IllegalArgumentException ex) {
            return FundoV11Support.notFound("NOT_FOUND", ex.getMessage());
        } catch (IllegalStateException ex) {
            return FundoV11Support.conflict("NOT_ENROLLED", ex.getMessage());
        }
    }

    @GetMapping("/lessons/{lessonId}/media")
    public ResponseEntity<Map<String, Object>> lessonMedia(@PathVariable String lessonId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoV11Support.requireTenantOrNull(ctx);
        UUID lesson = FundoV11Support.tryParseUuid(lessonId);
        if (tenantId == null || lesson == null) {
            return FundoV11Support.badRequest("INVALID_INPUT", "lessonId must be a UUID");
        }
        return FundoV11Support.dataEnvelope(mediaWatchService.lessonMedia(tenantId, lesson));
    }

    // ── replay authoring queue ───────────────────────────────────────────────

    @GetMapping("/media/replay-queue")
    public ResponseEntity<Map<String, Object>> replayQueue() {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoV11Support.requireTenantOrNull(ctx);
        if (tenantId == null) {
            return FundoV11Support.badRequest("INVALID_INPUT", "Tenant header is not a valid UUID");
        }
        return FundoV11Support.dataEnvelope(mediaWatchService.replayQueue(tenantId));
    }

    @PostMapping("/media/{assetId}/attach")
    public ResponseEntity<Map<String, Object>> attach(
            @PathVariable String assetId,
            @RequestBody(required = false) Map<String, Object> body) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoV11Support.requireTenantOrNull(ctx);
        UUID asset = FundoV11Support.tryParseUuid(assetId);
        UUID lessonId = body == null ? null : FundoV11Support.tryParseUuid(asString(body.get("lessonId")));
        if (tenantId == null || asset == null || lessonId == null) {
            return FundoV11Support.badRequest("INVALID_INPUT", "assetId and body.lessonId must be UUIDs");
        }
        try {
            return FundoV11Support.dataEnvelope("asset",
                    mediaWatchService.attach(tenantId, asset, lessonId, actorId(ctx)));
        } catch (IllegalArgumentException ex) {
            return FundoV11Support.notFound("NOT_FOUND", ex.getMessage());
        } catch (IllegalStateException ex) {
            return FundoV11Support.conflict("LESSON_NOT_IN_COURSE", ex.getMessage());
        }
    }

    // ── chapters ─────────────────────────────────────────────────────────────

    @GetMapping("/media/{assetId}/chapters")
    public ResponseEntity<Map<String, Object>> listChapters(@PathVariable String assetId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoV11Support.requireTenantOrNull(ctx);
        UUID asset = FundoV11Support.tryParseUuid(assetId);
        if (tenantId == null || asset == null) {
            return FundoV11Support.badRequest("INVALID_INPUT", "assetId must be a UUID");
        }
        return FundoV11Support.dataEnvelope(mediaWatchService.listChapters(tenantId, asset));
    }

    @PostMapping("/media/{assetId}/chapters")
    public ResponseEntity<Map<String, Object>> createChapter(
            @PathVariable String assetId,
            @RequestBody(required = false) Map<String, Object> body) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoV11Support.requireTenantOrNull(ctx);
        UUID asset = FundoV11Support.tryParseUuid(assetId);
        if (tenantId == null || asset == null || body == null) {
            return FundoV11Support.badRequest("INVALID_INPUT",
                    body == null ? "Request body is required" : "assetId must be a UUID");
        }
        try {
            return FundoV11Support.dataEnvelope("chapter", mediaWatchService.createChapter(
                    tenantId, asset, actorId(ctx),
                    asString(body.get("title")), asInt(body.get("startSeconds")), asInt(body.get("order"))));
        } catch (IllegalArgumentException ex) {
            return "title_required".equals(ex.getMessage())
                    ? FundoV11Support.badRequest("INVALID_INPUT", "title is required")
                    : FundoV11Support.notFound("NOT_FOUND", ex.getMessage());
        }
    }

    @DeleteMapping("/media/chapters/{chapterId}")
    public ResponseEntity<Map<String, Object>> deleteChapter(@PathVariable String chapterId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoV11Support.requireTenantOrNull(ctx);
        UUID chapter = FundoV11Support.tryParseUuid(chapterId);
        if (tenantId == null || chapter == null) {
            return FundoV11Support.badRequest("INVALID_INPUT", "chapterId must be a UUID");
        }
        try {
            return FundoV11Support.dataEnvelope(mediaWatchService.deleteChapter(tenantId, chapter));
        } catch (IllegalArgumentException ex) {
            return FundoV11Support.notFound("NOT_FOUND", ex.getMessage());
        }
    }

    // ── bookmarks ────────────────────────────────────────────────────────────

    @GetMapping("/media/{assetId}/bookmarks")
    public ResponseEntity<Map<String, Object>> listBookmarks(
            @PathVariable String assetId,
            @RequestParam String enrolmentId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoV11Support.requireTenantOrNull(ctx);
        UUID asset = FundoV11Support.tryParseUuid(assetId);
        UUID enrolment = FundoV11Support.tryParseUuid(enrolmentId);
        if (tenantId == null || asset == null || enrolment == null) {
            return FundoV11Support.badRequest("INVALID_INPUT", "assetId and enrolmentId must be UUIDs");
        }
        try {
            return FundoV11Support.dataEnvelope(mediaWatchService.listBookmarks(tenantId, asset, enrolment));
        } catch (IllegalArgumentException ex) {
            return FundoV11Support.notFound("NOT_FOUND", ex.getMessage());
        }
    }

    @PostMapping("/media/{assetId}/bookmarks")
    public ResponseEntity<Map<String, Object>> createBookmark(
            @PathVariable String assetId,
            @RequestBody(required = false) Map<String, Object> body) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoV11Support.requireTenantOrNull(ctx);
        UUID asset = FundoV11Support.tryParseUuid(assetId);
        if (tenantId == null || asset == null || body == null) {
            return FundoV11Support.badRequest("INVALID_INPUT",
                    body == null ? "Request body is required" : "assetId must be a UUID");
        }
        UUID enrolmentId = FundoV11Support.tryParseUuid(asString(body.get("enrolmentId")));
        if (enrolmentId == null) {
            return FundoV11Support.badRequest("INVALID_INPUT", "enrolmentId is required and must be a UUID");
        }
        try {
            return FundoV11Support.dataEnvelope("bookmark", mediaWatchService.createBookmark(
                    tenantId, asset, enrolmentId, asInt(body.get("positionSeconds")),
                    asString(body.get("note"))));
        } catch (IllegalArgumentException ex) {
            return FundoV11Support.notFound("NOT_FOUND", ex.getMessage());
        } catch (IllegalStateException ex) {
            return FundoV11Support.conflict("CONFLICT", ex.getMessage());
        }
    }

    @DeleteMapping("/media/bookmarks/{bookmarkId}")
    public ResponseEntity<Map<String, Object>> deleteBookmark(
            @PathVariable String bookmarkId,
            @RequestParam String enrolmentId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoV11Support.requireTenantOrNull(ctx);
        UUID bookmark = FundoV11Support.tryParseUuid(bookmarkId);
        UUID enrolment = FundoV11Support.tryParseUuid(enrolmentId);
        if (tenantId == null || bookmark == null || enrolment == null) {
            return FundoV11Support.badRequest("INVALID_INPUT", "bookmarkId and enrolmentId must be UUIDs");
        }
        try {
            return FundoV11Support.dataEnvelope(mediaWatchService.deleteBookmark(tenantId, bookmark, enrolment));
        } catch (IllegalArgumentException ex) {
            return FundoV11Support.notFound("NOT_FOUND", ex.getMessage());
        } catch (IllegalStateException ex) {
            return FundoV11Support.conflict("NOT_OWNED", ex.getMessage());
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static String actorId(RequestContext ctx) {
        if (ctx == null || ctx.principal() == null || ctx.principal().getName() == null) {
            return "system";
        }
        return ctx.principal().getName();
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static Integer asInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return (int) Math.round(Double.parseDouble(value.toString().trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
