package zw.gov.mohcc.impilo.experience.wellness;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.DocumentServiceClient;
import zw.gov.mohcc.impilo.experience.client.SimbaSocialServiceClient;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reel upload composition. Writes the reel media (and optional thumbnail) to MinIO via the
 * document-service, then registers the object reference + creates the reel in Simba. Simba never sees
 * bytes. Playback resolves the signed URL via {@link WellnessSocialController}.
 */
@RestController
@RequestMapping("/internal/v1/wellness/social/reels")
public class WellnessReelUploadController {

    private static final Logger log = LoggerFactory.getLogger(WellnessReelUploadController.class);

    private final DocumentServiceClient documents;
    private final SimbaSocialServiceClient simbaSocial;

    public WellnessReelUploadController(DocumentServiceClient documents, SimbaSocialServiceClient simbaSocial) {
        this.documents = documents;
        this.simbaSocial = simbaSocial;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> upload(
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "thumbnail", required = false) MultipartFile thumbnail,
            @RequestPart(value = "caption", required = false) String caption,
            @RequestPart(value = "fundo_content_ref", required = false) String fundoContentRef,
            @RequestPart(value = "visibility", required = false) String visibility) throws IOException {
        if (actorId == null || actorId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "NO_PERSON_ANCHOR", "message", "X-Actor-ID is required")));
        }

        // 1) MinIO write (document-service owns the pipeline).
        String mime = file.getContentType() != null ? file.getContentType() : "video/mp4";
        JsonNode stored = documents.uploadObject(file.getBytes(), file.getOriginalFilename(), mime);
        String objectId = objectId(stored);
        if (objectId == null) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of("code", "MEDIA_STORE_FAILED", "message", "Media upload did not return an object id")));
        }

        String thumbObjectId = null;
        if (thumbnail != null && !thumbnail.isEmpty()) {
            JsonNode thumbStored = documents.uploadObject(thumbnail.getBytes(),
                    thumbnail.getOriginalFilename(),
                    thumbnail.getContentType() != null ? thumbnail.getContentType() : "image/jpeg");
            thumbObjectId = objectId(thumbStored);
        }

        // 2) Register the media asset reference in Simba (never bytes).
        Map<String, Object> mediaReq = new HashMap<>();
        mediaReq.put("owner_cpid", actorId);
        mediaReq.put("document_object_id", objectId);
        mediaReq.put("thumbnail_object_id", thumbObjectId);
        mediaReq.put("content_type", mime);
        mediaReq.put("media_kind", "VIDEO");
        JsonNode media = simbaSocial.registerMediaAsset(mediaReq);
        String mediaAssetId = media != null && media.has("mediaAssetId") ? media.get("mediaAssetId").asText() : null;

        // 3) Create the reel.
        Map<String, Object> reelReq = new HashMap<>();
        reelReq.put("creator_cpid", actorId);
        reelReq.put("media_asset_id", mediaAssetId);
        reelReq.put("caption", caption);
        reelReq.put("fundo_content_ref", fundoContentRef);
        reelReq.put("visibility", visibility);
        if ("PROVIDER".equalsIgnoreCase(actorType) && providerId != null) {
            reelReq.put("verified_badge", "PROVIDER");
        }
        JsonNode reel = simbaSocial.createReel(reelReq);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reel", reel);
        data.put("media", media);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", data));
    }

    private static String objectId(JsonNode stored) {
        if (stored == null) {
            return null;
        }
        if (stored.has("id")) {
            return stored.get("id").asText();
        }
        if (stored.has("objectId")) {
            return stored.get("objectId").asText();
        }
        return null;
    }
}
