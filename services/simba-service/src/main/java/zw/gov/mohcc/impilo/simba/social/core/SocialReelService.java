package zw.gov.mohcc.impilo.simba.social.core;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.simba.core.SimbaEventEmitter;
import zw.gov.mohcc.impilo.simba.social.entity.MediaAssetEntity;
import zw.gov.mohcc.impilo.simba.social.entity.ReelEntity;
import zw.gov.mohcc.impilo.simba.social.repository.MediaAssetRepository;
import zw.gov.mohcc.impilo.simba.social.repository.ReelRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wellness reels. Simba holds only the document-service object REFERENCE (never bytes) — the BFF
 * writes the media to MinIO via DocumentServiceClient, then registers the object here. Media starts
 * PENDING and is marked READY when playback resolution succeeds. Feed returns visible+approved reels.
 */
@Service
public class SocialReelService {

    private final MediaAssetRepository mediaRepository;
    private final ReelRepository reelRepository;
    private final SimbaEventEmitter events;

    public SocialReelService(MediaAssetRepository mediaRepository,
                             ReelRepository reelRepository,
                             SimbaEventEmitter events) {
        this.mediaRepository = mediaRepository;
        this.reelRepository = reelRepository;
        this.events = events;
    }

    /** Registers a MinIO object as a media asset (no bytes). Called by the BFF after uploadObject. */
    @Transactional
    public MediaAssetEntity registerMediaAsset(UUID tenantId, String ownerCpid, String documentObjectId,
                                               String thumbnailObjectId, String contentType,
                                               String mediaKind, Integer durationSeconds) {
        MediaAssetEntity m = new MediaAssetEntity();
        m.setTenantId(tenantId);
        m.setOwnerCpid(ownerCpid);
        m.setDocumentObjectId(documentObjectId);
        m.setThumbnailObjectId(thumbnailObjectId);
        m.setContentType(contentType);
        if (mediaKind != null && !mediaKind.isBlank()) {
            m.setMediaKind(mediaKind);
        }
        m.setDurationSeconds(durationSeconds);
        return mediaRepository.save(m);
    }

    @Transactional
    public ReelEntity createReel(UUID tenantId, String creatorCpid, String creatorType, String providerId,
                                 UUID mediaAssetId, String caption, String fundoContentRef,
                                 String visibility, String verifiedBadge) {
        MediaAssetEntity media = mediaRepository.findByMediaAssetIdAndTenantId(mediaAssetId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Media asset not found"));
        // Caption must not carry clinical values (same sanitiser as posts).
        if (caption != null) {
            MilestoneSanitiser.requireSafe(caption);
        }

        ReelEntity r = new ReelEntity();
        r.setTenantId(tenantId);
        r.setCreatorCpid(creatorCpid);
        if (creatorType != null) r.setCreatorType(creatorType);
        r.setProviderId(providerId);
        r.setMediaAssetId(mediaAssetId);
        r.setCaption(caption);
        r.setFundoContentRef(fundoContentRef);
        if (visibility != null && !visibility.isBlank()) {
            r.setVisibility(visibility);
        }
        r.setVerifiedBadge(verifiedBadge);
        // Provider/programme reels are auto-approved; client reels start PENDING for light review only
        // when a sensitive area is implied — default APPROVED keeps the product usable.
        r = reelRepository.save(r);

        // Media transitions to READY once persisted (playback resolves the signed URL on demand).
        media.setProcessingStatus("READY");
        mediaRepository.save(media);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reel_id", r.getReelId().toString());
        payload.put("creator_cpid", creatorCpid);
        payload.put("media_asset_id", mediaAssetId.toString());
        events.emit(tenantId, "WELLNESS_SOCIAL_REEL", r.getReelId().toString(),
                "impilo.simba.wellness.social.reel.created.v1", creatorCpid,
                "simba:social-reel:" + r.getReelId(), payload);
        return r;
    }

    @Transactional(readOnly = true)
    public MediaAssetEntity media(UUID tenantId, UUID mediaAssetId) {
        return mediaRepository.findByMediaAssetIdAndTenantId(mediaAssetId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Media asset not found"));
    }

    @Transactional(readOnly = true)
    public ReelEntity get(UUID tenantId, UUID reelId) {
        return reelRepository.findByReelIdAndTenantId(reelId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Reel not found"));
    }

    public record ReelPage(List<ReelEntity> reels, Long nextCursor) { }

    @Transactional(readOnly = true)
    public ReelPage feed(UUID tenantId, Long cursor, int limit) {
        int capped = Math.max(1, Math.min(limit, 100));
        List<ReelEntity> rows = reelRepository.feed(tenantId, cursor, PageRequest.of(0, capped + 1));
        Long nextCursor = null;
        if (rows.size() > capped) {
            rows = rows.subList(0, capped);
            nextCursor = rows.get(rows.size() - 1).getId();
        }
        return new ReelPage(rows, nextCursor);
    }

    @Transactional
    public void incrementView(UUID tenantId, UUID reelId) {
        reelRepository.findByReelIdAndTenantId(reelId, tenantId).ifPresent(r -> {
            r.setViewCount(r.getViewCount() + 1);
            reelRepository.save(r);
        });
    }
}
