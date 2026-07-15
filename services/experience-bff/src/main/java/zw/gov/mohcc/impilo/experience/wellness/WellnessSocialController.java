package zw.gov.mohcc.impilo.experience.wellness;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.experience.client.DocumentServiceClient;
import zw.gov.mohcc.impilo.experience.client.SimbaSocialServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Wellness-social composition the transparent proxy cannot do: reel playback resolves a document-service
 * signed URL from the stored object reference (Simba stores no bytes). Kept separate from the generic
 * community-service SocialController. Degrades gracefully if playback resolution fails.
 */
@RestController
@RequestMapping("/internal/v1/wellness/social/reels")
public class WellnessSocialController {

    private static final Logger log = LoggerFactory.getLogger(WellnessSocialController.class);

    private final SimbaSocialServiceClient simbaSocial;
    private final DocumentServiceClient documents;

    public WellnessSocialController(SimbaSocialServiceClient simbaSocial, DocumentServiceClient documents) {
        this.simbaSocial = simbaSocial;
        this.documents = documents;
    }

    @GetMapping("/{reelId}/playback")
    public ResponseEntity<Map<String, Object>> playback(@PathVariable("reelId") String reelId) {
        JsonNode reelResp = simbaSocial.getReel(reelId);
        JsonNode reel = reelResp != null && reelResp.has("data") ? reelResp.get("data") : reelResp;
        JsonNode media = reelResp != null && reelResp.has("media") ? reelResp.get("media") : null;
        if (media == null || !media.has("documentObjectId")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", Map.of("code", "REEL_MEDIA_MISSING", "message", "No media for this reel")));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reel", reel);
        data.put("playback_url", signedUrl(media.get("documentObjectId").asText()));
        if (media.has("thumbnailObjectId") && !media.get("thumbnailObjectId").isNull()) {
            data.put("thumbnail_url", signedUrl(media.get("thumbnailObjectId").asText()));
        }
        return ResponseEntity.ok(Map.of("data", data));
    }

    private String signedUrl(String objectId) {
        try {
            JsonNode signed = documents.getSignedUrl(UUID.fromString(objectId));
            if (signed != null && signed.has("url")) {
                return signed.get("url").asText();
            }
            if (signed != null && signed.has("signedUrl")) {
                return signed.get("signedUrl").asText();
            }
        } catch (Exception e) {
            log.warn("Reel playback signed-url resolution failed for object {}: {}", objectId, e.getMessage());
        }
        return null;
    }
}
