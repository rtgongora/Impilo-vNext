package zw.gov.mohcc.impilo.learning.fundo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
import zw.gov.mohcc.impilo.learning.persistence.entity.EnrolmentEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.MediaAssetEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.MediaBookmarkEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.MediaChapterEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.MediaWatchProgressEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.CompletionRuleRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseLessonRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseModuleRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.EnrolmentRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.MediaAssetRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.MediaBookmarkRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.MediaChapterRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.MediaWatchProgressRepository;

/**
 * Recorded-media watch truth (LEARNING_RECORDING W4): per-enrolment watch
 * progress, chapters, bookmarks, playback resolution and the replay authoring
 * queue.
 *
 * <p><strong>Progress semantics:</strong> {@code watchedSeconds} and
 * {@code furthestPositionSeconds} are monotonic (max of stored vs reported) so
 * debounced client upserts are idempotent; {@code lastPositionSeconds} is the
 * resume point. {@code percent = watchedSeconds / durationSeconds} clamped to
 * 0..100.</p>
 *
 * <p><strong>Completion wiring:</strong> when percent first reaches the course's
 * WATCH_THRESHOLD (rule {@code threshold_value}, default {@value #DEFAULT_WATCH_THRESHOLD_PERCENT}):
 * an asset attached to a lesson drives that lesson's progress to 100 through
 * {@link FundoProgressService#recordProgress} (which reconciles the aggregate and
 * runs {@code completeIfEligible}); an unattached-to-lesson asset on a course
 * WITH configured completion rules re-runs {@code completeIfEligible} directly.
 * Courses WITHOUT rules are never auto-completed by a milestone alone — the
 * legacy lesson/aggregate paths stay authoritative (calling
 * {@code completeIfEligible} rule-less would complete unconditionally).</p>
 */
@Service
public class FundoMediaWatchService {

    private static final Logger log = LoggerFactory.getLogger(FundoMediaWatchService.class);

    /** Default WATCH_THRESHOLD percent when the course rule has no threshold_value. */
    public static final int DEFAULT_WATCH_THRESHOLD_PERCENT = 90;

    private static final List<String> INACTIVE_STATUSES = List.of("CANCELLED", "EXPIRED");

    private final MediaAssetRepository mediaAssetRepository;
    private final MediaWatchProgressRepository watchProgressRepository;
    private final MediaChapterRepository chapterRepository;
    private final MediaBookmarkRepository bookmarkRepository;
    private final EnrolmentRepository enrolmentRepository;
    private final CompletionRuleRepository ruleRepository;
    private final CourseModuleRepository moduleRepository;
    private final CourseLessonRepository lessonRepository;
    private final FundoProgressService progressService;
    private final FundoOutboxAppender outbox;

    public FundoMediaWatchService(MediaAssetRepository mediaAssetRepository,
                                  MediaWatchProgressRepository watchProgressRepository,
                                  MediaChapterRepository chapterRepository,
                                  MediaBookmarkRepository bookmarkRepository,
                                  EnrolmentRepository enrolmentRepository,
                                  CompletionRuleRepository ruleRepository,
                                  CourseModuleRepository moduleRepository,
                                  CourseLessonRepository lessonRepository,
                                  FundoProgressService progressService,
                                  FundoOutboxAppender outbox) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.watchProgressRepository = watchProgressRepository;
        this.chapterRepository = chapterRepository;
        this.bookmarkRepository = bookmarkRepository;
        this.enrolmentRepository = enrolmentRepository;
        this.ruleRepository = ruleRepository;
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;
        this.progressService = progressService;
        this.outbox = outbox;
    }

    public record WatchUpdate(UUID enrolmentId, Integer positionSeconds,
                              Integer watchedSeconds, Integer durationSeconds) {}

    // ── watch progress ───────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> upsertWatchProgress(UUID tenantId, UUID assetId, WatchUpdate update) {
        EnrolmentEntity enrolment = requireActiveEnrolment(tenantId, update.enrolmentId());
        MediaAssetEntity asset = requireAssetForEnrolment(tenantId, assetId, enrolment);

        MediaWatchProgressEntity row = watchProgressRepository
                .findByEnrolmentIdAndAssetId(enrolment.getId(), assetId)
                .orElseGet(() -> {
                    MediaWatchProgressEntity p = new MediaWatchProgressEntity();
                    p.setTenantId(tenantId);
                    p.setEnrolmentId(enrolment.getId());
                    p.setAssetId(assetId);
                    return p;
                });

        int position = clampNonNegative(update.positionSeconds());
        row.setLastPositionSeconds(position);
        row.setFurthestPositionSeconds(Math.max(row.getFurthestPositionSeconds(), position));
        row.setWatchedSeconds(Math.max(row.getWatchedSeconds(), clampNonNegative(update.watchedSeconds())));

        Integer duration = update.durationSeconds() != null && update.durationSeconds() > 0
                ? update.durationSeconds()
                : row.getDurationSeconds() != null ? row.getDurationSeconds() : asset.getDurationSeconds();
        row.setDurationSeconds(duration);
        if (duration != null && duration > 0) {
            row.setPercent(Math.max(0, Math.min(100,
                    (int) Math.round(row.getWatchedSeconds() * 100.0 / duration))));
        }

        int threshold = watchThresholdPercent(tenantId, enrolment.getCourseId());
        boolean crossedMilestone = row.getCompletedAt() == null && row.getPercent() >= threshold;
        OffsetDateTime now = OffsetDateTime.now();
        if (crossedMilestone) {
            row.setCompletedAt(now);
        }
        watchProgressRepository.save(row);

        if (crossedMilestone) {
            emitMilestone(tenantId, enrolment, asset, row, threshold);
            driveCompletion(tenantId, enrolment, asset, now);
        }
        Map<String, Object> view = watchView(row);
        view.put("thresholdPercent", threshold);
        return view;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getWatchProgress(UUID tenantId, UUID assetId, UUID enrolmentId) {
        EnrolmentEntity enrolment = requireEnrolment(tenantId, enrolmentId);
        int threshold = watchThresholdPercent(tenantId, enrolment.getCourseId());
        Map<String, Object> view = watchProgressRepository
                .findByEnrolmentIdAndAssetId(enrolmentId, assetId)
                .map(FundoMediaWatchService::watchView)
                .orElseGet(() -> emptyWatchView(enrolmentId, assetId));
        view.put("thresholdPercent", threshold);
        return view;
    }

    /**
     * Milestone → completion wiring. Lesson-attached assets go through the
     * lesson-progress path; lesson-less assets only re-evaluate the policy when
     * the course actually has rules (see class doc for why).
     */
    private void driveCompletion(UUID tenantId, EnrolmentEntity enrolment,
                                 MediaAssetEntity asset, OffsetDateTime now) {
        if (asset.getSourceLessonId() != null) {
            progressService.recordProgress(tenantId, new FundoProgressService.ProgressUpdate(
                    enrolment.getId(), null, asset.getSourceLessonId(), 100, null));
            return;
        }
        boolean courseHasRules = !ruleRepository
                .findByTenantIdAndCourseIdOrderByCreatedAtAsc(tenantId, enrolment.getCourseId())
                .isEmpty();
        if (courseHasRules && !"COMPLETED".equals(enrolment.getStatus())) {
            progressService.completeIfEligible(tenantId, enrolment, now, "WATCH_THRESHOLD");
        }
    }

    private void emitMilestone(UUID tenantId, EnrolmentEntity enrolment, MediaAssetEntity asset,
                               MediaWatchProgressEntity row, int threshold) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", tenantId.toString());
        payload.put("enrolmentId", enrolment.getId().toString());
        payload.put("courseId", enrolment.getCourseId().toString());
        payload.put("assetId", asset.getId().toString());
        payload.put("lessonId", asset.getSourceLessonId() == null ? null : asset.getSourceLessonId().toString());
        payload.put("subjectType", enrolment.getSubjectType());
        payload.put("subjectId", enrolment.getSubjectId());
        payload.put("percent", row.getPercent());
        payload.put("thresholdPercent", threshold);
        payload.put("watchedSeconds", row.getWatchedSeconds());
        payload.put("durationSeconds", row.getDurationSeconds());
        outbox.append("FundoMediaWatch", row.getId().toString(),
                FundoNativeEventTypes.MEDIA_WATCH_MILESTONE, payload);
    }

    /** WATCH_THRESHOLD rule threshold for the course, or the 90 % default. */
    private int watchThresholdPercent(UUID tenantId, UUID courseId) {
        return ruleRepository.findByTenantIdAndCourseIdAndRuleType(tenantId, courseId, "WATCH_THRESHOLD")
                .map(r -> r.getThresholdValue())
                .filter(t -> t != null && t.compareTo(BigDecimal.ZERO) > 0)
                .map(BigDecimal::intValue)
                .orElse(DEFAULT_WATCH_THRESHOLD_PERCENT);
    }

    // ── playback resolution ──────────────────────────────────────────────────

    /**
     * Enrolment-gated playback reference. The BFF exchanges a
     * {@code DOCUMENT_OBJECT} storageRef for a signed URL against
     * document-service; learning-service itself never mints URLs.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> playbackRef(UUID tenantId, UUID assetId, UUID enrolmentId) {
        EnrolmentEntity enrolment = requireEnrolment(tenantId, enrolmentId);
        MediaAssetEntity asset = requireAssetForEnrolment(tenantId, assetId, enrolment);
        Map<String, Object> m = assetView(asset);
        m.put("enrolmentId", enrolmentId.toString());
        return m;
    }

    /** Newest asset attached to a lesson, or null-shaped view when none. */
    @Transactional(readOnly = true)
    public Map<String, Object> lessonMedia(UUID tenantId, UUID lessonId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("lessonId", lessonId.toString());
        data.put("asset", mediaAssetRepository
                .findFirstByTenantIdAndSourceLessonIdOrderByCreatedAtDesc(tenantId, lessonId)
                .map(FundoMediaWatchService::assetView)
                .orElse(null));
        return data;
    }

    // ── replay authoring queue ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> replayQueue(UUID tenantId) {
        List<Map<String, Object>> items = mediaAssetRepository
                .findByTenantIdAndUnattachedTrueOrderByCreatedAtDesc(tenantId).stream()
                .map(FundoMediaWatchService::assetView)
                .toList();
        return Map.of("items", items);
    }

    @Transactional
    public Map<String, Object> attach(UUID tenantId, UUID assetId, UUID lessonId, String actorId) {
        MediaAssetEntity asset = mediaAssetRepository.findByTenantIdAndId(tenantId, assetId)
                .orElseThrow(() -> new IllegalArgumentException("media_asset_not_found"));
        CourseLessonEntity lesson = lessonRepository.findById(lessonId)
                .filter(l -> tenantId.equals(l.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("lesson_not_found"));
        UUID lessonCourseId = courseIdOfLesson(lesson);
        if (asset.getCourseId() != null && lessonCourseId != null
                && !asset.getCourseId().equals(lessonCourseId)) {
            throw new IllegalStateException("lesson_not_in_asset_course");
        }
        asset.setSourceLessonId(lessonId);
        asset.setUnattached(false);
        if (asset.getCourseId() == null) {
            asset.setCourseId(lessonCourseId);
        }
        mediaAssetRepository.save(asset);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", tenantId.toString());
        payload.put("assetId", assetId.toString());
        payload.put("lessonId", lessonId.toString());
        payload.put("courseId", asset.getCourseId() == null ? null : asset.getCourseId().toString());
        payload.put("attachedBy", actorId);
        outbox.append("FundoMediaAsset", assetId.toString(),
                FundoNativeEventTypes.MEDIA_ATTACHED, payload);
        log.info("Media asset {} attached to lesson {} by {}", assetId, lessonId, actorId);
        return assetView(asset);
    }

    // ── chapters ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> listChapters(UUID tenantId, UUID assetId) {
        List<Map<String, Object>> items = chapterRepository
                .findByTenantIdAndAssetIdOrderByChapterOrderAscStartSecondsAsc(tenantId, assetId).stream()
                .map(FundoMediaWatchService::chapterView)
                .toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("assetId", assetId.toString());
        data.put("items", items);
        return data;
    }

    @Transactional
    public Map<String, Object> createChapter(UUID tenantId, UUID assetId, String actorId,
                                             String title, Integer startSeconds, Integer order) {
        mediaAssetRepository.findByTenantIdAndId(tenantId, assetId)
                .orElseThrow(() -> new IllegalArgumentException("media_asset_not_found"));
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title_required");
        }
        MediaChapterEntity chapter = new MediaChapterEntity();
        chapter.setTenantId(tenantId);
        chapter.setAssetId(assetId);
        chapter.setTitle(title.trim());
        chapter.setStartSeconds(clampNonNegative(startSeconds));
        chapter.setChapterOrder(order == null ? 0 : order);
        chapter.setCreatedBy(actorId);
        chapterRepository.save(chapter);
        return chapterView(chapter);
    }

    @Transactional
    public Map<String, Object> deleteChapter(UUID tenantId, UUID chapterId) {
        MediaChapterEntity chapter = chapterRepository.findByTenantIdAndId(tenantId, chapterId)
                .orElseThrow(() -> new IllegalArgumentException("chapter_not_found"));
        chapterRepository.delete(chapter);
        return Map.of("id", chapterId.toString(), "deleted", true);
    }

    // ── bookmarks ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> listBookmarks(UUID tenantId, UUID assetId, UUID enrolmentId) {
        requireEnrolment(tenantId, enrolmentId);
        List<Map<String, Object>> items = bookmarkRepository
                .findByTenantIdAndEnrolmentIdAndAssetIdOrderByPositionSecondsAsc(tenantId, enrolmentId, assetId)
                .stream()
                .map(FundoMediaWatchService::bookmarkView)
                .toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("assetId", assetId.toString());
        data.put("enrolmentId", enrolmentId.toString());
        data.put("items", items);
        return data;
    }

    @Transactional
    public Map<String, Object> createBookmark(UUID tenantId, UUID assetId, UUID enrolmentId,
                                              Integer positionSeconds, String note) {
        EnrolmentEntity enrolment = requireActiveEnrolment(tenantId, enrolmentId);
        requireAssetForEnrolment(tenantId, assetId, enrolment);
        MediaBookmarkEntity bookmark = new MediaBookmarkEntity();
        bookmark.setTenantId(tenantId);
        bookmark.setEnrolmentId(enrolmentId);
        bookmark.setAssetId(assetId);
        bookmark.setPositionSeconds(clampNonNegative(positionSeconds));
        bookmark.setNote(note == null || note.isBlank() ? null : note.trim());
        bookmarkRepository.save(bookmark);
        return bookmarkView(bookmark);
    }

    /** Delete requires the caller to present the owning enrolment (learner-private rows). */
    @Transactional
    public Map<String, Object> deleteBookmark(UUID tenantId, UUID bookmarkId, UUID enrolmentId) {
        MediaBookmarkEntity bookmark = bookmarkRepository.findByTenantIdAndId(tenantId, bookmarkId)
                .orElseThrow(() -> new IllegalArgumentException("bookmark_not_found"));
        if (enrolmentId == null || !bookmark.getEnrolmentId().equals(enrolmentId)) {
            throw new IllegalStateException("bookmark_not_owned_by_enrolment");
        }
        bookmarkRepository.delete(bookmark);
        return Map.of("id", bookmarkId.toString(), "deleted", true);
    }

    // ── shared guards ────────────────────────────────────────────────────────

    private EnrolmentEntity requireEnrolment(UUID tenantId, UUID enrolmentId) {
        if (enrolmentId == null) {
            throw new IllegalArgumentException("enrolment_required");
        }
        return enrolmentRepository.findByTenantIdAndId(tenantId, enrolmentId)
                .orElseThrow(() -> new IllegalArgumentException("enrolment_not_found"));
    }

    private EnrolmentEntity requireActiveEnrolment(UUID tenantId, UUID enrolmentId) {
        EnrolmentEntity enrolment = requireEnrolment(tenantId, enrolmentId);
        if (INACTIVE_STATUSES.contains(enrolment.getStatus())) {
            throw new IllegalStateException("enrolment_not_active");
        }
        return enrolment;
    }

    /**
     * Enrolment-membership gate on the asset: the asset must belong to the
     * enrolment's course (directly, or through its attached lesson's module).
     * Legacy assets with no course/lesson linkage stay reachable — they carry
     * no course claim to enforce.
     */
    private MediaAssetEntity requireAssetForEnrolment(UUID tenantId, UUID assetId, EnrolmentEntity enrolment) {
        MediaAssetEntity asset = mediaAssetRepository.findByTenantIdAndId(tenantId, assetId)
                .orElseThrow(() -> new IllegalArgumentException("media_asset_not_found"));
        UUID assetCourseId = asset.getCourseId();
        if (assetCourseId == null && asset.getSourceLessonId() != null) {
            assetCourseId = lessonRepository.findById(asset.getSourceLessonId())
                    .map(this::courseIdOfLesson)
                    .orElse(null);
        }
        if (assetCourseId != null && !assetCourseId.equals(enrolment.getCourseId())) {
            throw new IllegalStateException("asset_not_in_enrolment_course");
        }
        return asset;
    }

    private UUID courseIdOfLesson(CourseLessonEntity lesson) {
        return moduleRepository.findById(lesson.getModuleId())
                .map(CourseModuleEntity::getCourseId)
                .orElse(null);
    }

    private static int clampNonNegative(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    // ── views ────────────────────────────────────────────────────────────────

    static Map<String, Object> watchView(MediaWatchProgressEntity p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId() == null ? null : p.getId().toString());
        m.put("enrolmentId", p.getEnrolmentId().toString());
        m.put("assetId", p.getAssetId().toString());
        m.put("watchedSeconds", p.getWatchedSeconds());
        m.put("durationSeconds", p.getDurationSeconds());
        m.put("percent", p.getPercent());
        m.put("furthestPositionSeconds", p.getFurthestPositionSeconds());
        m.put("lastPositionSeconds", p.getLastPositionSeconds());
        m.put("completedAt", p.getCompletedAt() == null ? null : p.getCompletedAt().toString());
        m.put("updatedAt", p.getUpdatedAt() == null ? null : p.getUpdatedAt().toString());
        return m;
    }

    private static Map<String, Object> emptyWatchView(UUID enrolmentId, UUID assetId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", null);
        m.put("enrolmentId", enrolmentId.toString());
        m.put("assetId", assetId.toString());
        m.put("watchedSeconds", 0);
        m.put("durationSeconds", null);
        m.put("percent", 0);
        m.put("furthestPositionSeconds", 0);
        m.put("lastPositionSeconds", 0);
        m.put("completedAt", null);
        m.put("updatedAt", null);
        return m;
    }

    static Map<String, Object> assetView(MediaAssetEntity a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId().toString());
        m.put("title", a.getTitle());
        m.put("mediaType", a.getMediaType());
        m.put("status", a.getStatus());
        m.put("mimeType", a.getMimeType());
        m.put("storageKind", a.getStorageKind());
        m.put("storageRef", a.getStorageRef());
        m.put("recordingType", a.getRecordingType());
        m.put("durationSeconds", a.getDurationSeconds());
        m.put("courseId", a.getCourseId() == null ? null : a.getCourseId().toString());
        m.put("lessonId", a.getSourceLessonId() == null ? null : a.getSourceLessonId().toString());
        m.put("liveEventId", a.getLiveEventId() == null ? null : a.getLiveEventId().toString());
        m.put("unattached", a.isUnattached());
        m.put("transcriptAvailable", a.getTranscript() != null && !a.getTranscript().isBlank());
        m.put("transcript", a.getTranscript());
        m.put("captionRef", a.getCaptionRef());
        m.put("createdAt", a.getCreatedAt() == null ? null : a.getCreatedAt().toString());
        return m;
    }

    private static Map<String, Object> chapterView(MediaChapterEntity c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId().toString());
        m.put("assetId", c.getAssetId().toString());
        m.put("title", c.getTitle());
        m.put("startSeconds", c.getStartSeconds());
        m.put("order", c.getChapterOrder());
        return m;
    }

    private static Map<String, Object> bookmarkView(MediaBookmarkEntity b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.getId().toString());
        m.put("enrolmentId", b.getEnrolmentId().toString());
        m.put("assetId", b.getAssetId().toString());
        m.put("positionSeconds", b.getPositionSeconds());
        m.put("note", b.getNote());
        m.put("createdAt", b.getCreatedAt() == null ? null : b.getCreatedAt().toString());
        return m;
    }
}
