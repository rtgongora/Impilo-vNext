package zw.gov.mohcc.impilo.simba.social.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.simba.social.core.SocialReelService;
import zw.gov.mohcc.impilo.simba.social.entity.MediaAssetEntity;
import zw.gov.mohcc.impilo.simba.social.entity.ReelEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Wellness reels. The BFF WellnessReelUploadController writes the media to MinIO (document-service)
 * and calls {@code POST /media-assets} here with the object id, then {@code POST /reels}. Simba stores
 * only the object reference — never bytes. Playback resolves the signed URL via the BFF.
 */
@RestController
@RequestMapping("/internal/v1/wellness/social")
public class SocialReelController {

    private final SocialReelService reelService;

    public SocialReelController(SocialReelService reelService) {
        this.reelService = reelService;
    }

    @PostMapping("/media-assets")
    public ResponseEntity<Map<String, Object>> registerMedia(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {
        String owner = actorId != null ? actorId : required(body, "owner_cpid");
        Integer duration = body.get("duration_seconds") == null ? null
                : Integer.valueOf(String.valueOf(body.get("duration_seconds")));
        MediaAssetEntity m = reelService.registerMediaAsset(tenantId, owner,
                required(body, "document_object_id"), str(body, "thumbnail_object_id"),
                str(body, "content_type"), str(body, "media_kind"), duration);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", m));
    }

    @PostMapping("/reels")
    public ResponseEntity<Map<String, Object>> createReel(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestBody Map<String, Object> body) {
        String creator = actorId != null ? actorId : required(body, "creator_cpid");
        ReelEntity r = reelService.createReel(tenantId, creator, actorType, providerId,
                UUID.fromString(required(body, "media_asset_id")), str(body, "caption"),
                str(body, "fundo_content_ref"), str(body, "visibility"), str(body, "verified_badge"));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", r));
    }

    @GetMapping("/reels")
    public Map<String, Object> feed(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestParam(value = "cursor", required = false) Long cursor,
            @RequestParam(value = "limit", required = false, defaultValue = "10") int limit) {
        SocialReelService.ReelPage page = reelService.feed(tenantId, cursor, limit);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("next_cursor", page.nextCursor());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", page.reels());
        out.put("meta", meta);
        return out;
    }

    @GetMapping("/reels/{reelId}")
    public Map<String, Object> get(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable("reelId") UUID reelId) {
        ReelEntity r = reelService.get(tenantId, reelId);
        reelService.incrementView(tenantId, reelId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", r);
        out.put("media", reelService.media(tenantId, r.getMediaAssetId()));
        return out;
    }

    private static String required(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return v.toString();
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : v.toString();
    }
}
