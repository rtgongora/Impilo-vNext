package zw.gov.mohcc.impilo.learning.fundo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseLessonEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseModuleEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.MediaAssetEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.ScheduledLearningSessionEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseLessonRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseModuleRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.MediaAssetRepository;

/**
 * Adopts published live replays into the Fundo media library (LEARNING_RECORDING
 * W4). Invoked by {@code LiveReplayConsumer} when live-service emits
 * {@code impilo.live.replay.published.v1} for a live event that is linked to a
 * scheduled learning session (V027 {@code live_event_id}) — standalone live
 * events are never adopted (layering law: learning only reacts to
 * {@code impilo.live.*} truth it can join to its own sessions).
 *
 * <p><strong>Storage:</strong> the created {@code lrn_media_asset} carries the
 * document-service object id in {@code storage_ref} with
 * {@code storage_kind=DOCUMENT_OBJECT} — document-service remains the artifact
 * SoR; playback URLs are minted per-request via its signed-url endpoint.</p>
 *
 * <p><strong>Idempotency:</strong> one asset per (tenant, live event) — the V029
 * partial unique index. Redeliveries update the existing row in place.</p>
 *
 * <p><strong>Lesson attachment</strong> is only performed when it resolves
 * deterministically:</p>
 * <ol>
 *   <li>the scheduled session's {@code metadata_json} names a {@code lessonId}, or</li>
 *   <li>the course has exactly ONE VIDEO-type lesson.</li>
 * </ol>
 * <p>Anything else lands in the unattached authoring queue
 * ({@code unattached=true}) for an explicit author decision via
 * {@code POST /v11/media/{assetId}/attach} — no guessing.</p>
 */
@Service
public class FundoReplayMediaService {

    private static final Logger log = LoggerFactory.getLogger(FundoReplayMediaService.class);

    static final String RECORDING_TYPE_LIVE_REPLAY = "LIVE_REPLAY";
    static final String CREATED_BY_IMPILO_LIVE = "impilo-live";

    private final MediaAssetRepository mediaAssetRepository;
    private final CourseModuleRepository moduleRepository;
    private final CourseLessonRepository lessonRepository;
    private final FundoOutboxAppender outbox;
    private final ObjectMapper objectMapper;

    public FundoReplayMediaService(MediaAssetRepository mediaAssetRepository,
                                   CourseModuleRepository moduleRepository,
                                   CourseLessonRepository lessonRepository,
                                   FundoOutboxAppender outbox,
                                   ObjectMapper objectMapper) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    /** Replay payload of {@code impilo.live.replay.published.v1} relevant to adoption. */
    public record ReplayPublished(UUID liveEventId, String documentObjectId,
                                  Integer durationSeconds, String title) {}

    /**
     * Create or update the media asset for a published replay.
     *
     * @return the adopted asset (never null; redeliveries return the existing row).
     */
    @Transactional
    public MediaAssetEntity adoptReplay(ScheduledLearningSessionEntity session, ReplayPublished replay) {
        UUID tenantId = session.getTenantId();
        MediaAssetEntity asset = mediaAssetRepository
                .findByTenantIdAndLiveEventId(tenantId, replay.liveEventId())
                .orElse(null);
        boolean created = asset == null;
        if (created) {
            asset = new MediaAssetEntity();
            asset.setTenantId(tenantId);
            asset.setMediaType("VIDEO");
            asset.setStatus("PUBLISHED");
            asset.setRecordingType(RECORDING_TYPE_LIVE_REPLAY);
            asset.setCreatedBy(CREATED_BY_IMPILO_LIVE);
            asset.setLiveEventId(replay.liveEventId());
        }
        asset.setTitle(resolveTitle(session, replay));
        asset.setMimeType("video/mp4");
        asset.setStorageRef(replay.documentObjectId());
        asset.setStorageKind(MediaAssetEntity.STORAGE_KIND_DOCUMENT_OBJECT);
        if (replay.durationSeconds() != null) {
            asset.setDurationSeconds(replay.durationSeconds());
        }
        asset.setCourseId(session.getCourseId());

        if (asset.getSourceLessonId() == null) {
            UUID lessonId = resolveLessonDeterministically(session);
            if (lessonId != null) {
                asset.setSourceLessonId(lessonId);
                asset.setUnattached(false);
            } else {
                asset.setUnattached(true);
            }
        }
        MediaAssetEntity saved = mediaAssetRepository.save(asset);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", tenantId.toString());
        payload.put("assetId", saved.getId().toString());
        payload.put("liveEventId", replay.liveEventId().toString());
        payload.put("sessionId", session.getId().toString());
        payload.put("courseId", session.getCourseId().toString());
        payload.put("lessonId", saved.getSourceLessonId() == null ? null : saved.getSourceLessonId().toString());
        payload.put("documentObjectId", replay.documentObjectId());
        payload.put("unattached", saved.isUnattached());
        payload.put("redelivery", !created);
        outbox.append("FundoMediaAsset", saved.getId().toString(),
                FundoNativeEventTypes.MEDIA_REPLAY_ADOPTED, payload);

        log.info("Replay {} adopted as media asset {} (course {}, lesson {}, unattached={})",
                replay.liveEventId(), saved.getId(), session.getCourseId(),
                saved.getSourceLessonId(), saved.isUnattached());
        return saved;
    }

    private String resolveTitle(ScheduledLearningSessionEntity session, ReplayPublished replay) {
        String base = replay.title() != null && !replay.title().isBlank()
                ? replay.title()
                : session.getTitle();
        if (base == null || base.isBlank()) {
            base = "Live session";
        }
        return base.endsWith(" — replay") ? base : base + " — replay";
    }

    /**
     * Deterministic lesson resolution: an explicit {@code lessonId} in the
     * session's metadata wins; otherwise a course with exactly one VIDEO lesson
     * is unambiguous. Everything else returns null (authoring queue).
     */
    private UUID resolveLessonDeterministically(ScheduledLearningSessionEntity session) {
        UUID fromMetadata = lessonIdFromMetadata(session.getMetadataJson());
        if (fromMetadata != null) {
            return fromMetadata;
        }
        List<UUID> moduleIds = moduleRepository
                .findByCourseIdOrderBySequenceNoAsc(session.getCourseId()).stream()
                .map(CourseModuleEntity::getId)
                .toList();
        if (moduleIds.isEmpty()) {
            return null;
        }
        List<CourseLessonEntity> videoLessons = lessonRepository
                .findByModuleIdInOrderBySequenceNoAsc(moduleIds).stream()
                .filter(l -> "VIDEO".equalsIgnoreCase(l.getContentType()))
                .toList();
        return videoLessons.size() == 1 ? videoLessons.get(0).getId() : null;
    }

    private UUID lessonIdFromMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return null;
        }
        try {
            JsonNode n = objectMapper.readTree(metadataJson);
            String raw = n.path("lessonId").asText(null);
            return raw == null || raw.isBlank() ? null : UUID.fromString(raw.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
