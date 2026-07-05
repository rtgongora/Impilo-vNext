package zw.gov.mohcc.impilo.learning.fundo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.learning.persistence.entity.CompletionRuleEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.EnrolmentEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.MediaAssetEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.MediaWatchProgressEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.CompletionRuleRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseLessonRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseModuleRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.EnrolmentRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.MediaAssetRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.MediaBookmarkRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.MediaChapterRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.MediaWatchProgressRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Watch-progress → completion matrix (W4): monotonic upserts, percent
 * derivation, threshold-milestone firing exactly once, lesson-attached vs
 * rule-gated completion wiring, and enrolment-membership guards.
 */
@ExtendWith(MockitoExtension.class)
class FundoMediaWatchServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID COURSE = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID ASSET = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    @Mock private MediaAssetRepository mediaAssetRepository;
    @Mock private MediaWatchProgressRepository watchProgressRepository;
    @Mock private MediaChapterRepository chapterRepository;
    @Mock private MediaBookmarkRepository bookmarkRepository;
    @Mock private EnrolmentRepository enrolmentRepository;
    @Mock private CompletionRuleRepository ruleRepository;
    @Mock private CourseModuleRepository moduleRepository;
    @Mock private CourseLessonRepository lessonRepository;
    @Mock private FundoProgressService progressService;
    @Mock private FundoOutboxAppender outbox;

    private FundoMediaWatchService service;
    private EnrolmentEntity enrolment;
    private MediaAssetEntity asset;

    @BeforeEach
    void setUp() {
        service = new FundoMediaWatchService(mediaAssetRepository, watchProgressRepository,
                chapterRepository, bookmarkRepository, enrolmentRepository, ruleRepository,
                moduleRepository, lessonRepository, progressService, outbox);

        enrolment = new EnrolmentEntity();
        enrolment.setId(UUID.randomUUID());
        enrolment.setTenantId(TENANT);
        enrolment.setCourseId(COURSE);
        enrolment.setSubjectType("PROVIDER");
        enrolment.setSubjectId("provider-1");
        enrolment.setStatus("IN_PROGRESS");
        lenient().when(enrolmentRepository.findByTenantIdAndId(TENANT, enrolment.getId()))
                .thenReturn(Optional.of(enrolment));

        asset = new MediaAssetEntity();
        asset.setId(ASSET);
        asset.setTenantId(TENANT);
        asset.setCourseId(COURSE);
        asset.setMediaType("VIDEO");
        asset.setDurationSeconds(1000);
        lenient().when(mediaAssetRepository.findByTenantIdAndId(TENANT, ASSET))
                .thenReturn(Optional.of(asset));

        lenient().when(watchProgressRepository.findByEnrolmentIdAndAssetId(enrolment.getId(), ASSET))
                .thenReturn(Optional.empty());
        lenient().when(watchProgressRepository.save(any(MediaWatchProgressEntity.class)))
                .thenAnswer(inv -> {
                    MediaWatchProgressEntity p = inv.getArgument(0);
                    if (p.getId() == null) p.setId(UUID.randomUUID());
                    return p;
                });
        lenient().when(ruleRepository.findByTenantIdAndCourseIdAndRuleType(TENANT, COURSE, "WATCH_THRESHOLD"))
                .thenReturn(Optional.empty());
        lenient().when(ruleRepository.findByTenantIdAndCourseIdOrderByCreatedAtAsc(TENANT, COURSE))
                .thenReturn(List.of());
    }

    private Map<String, Object> upsert(int position, int watched, Integer duration) {
        return service.upsertWatchProgress(TENANT, ASSET, new FundoMediaWatchService.WatchUpdate(
                enrolment.getId(), position, watched, duration));
    }

    // ── progress semantics ───────────────────────────────────────────────────

    @Test
    void upsertDerivesPercentFromWatchedOverDuration() {
        Map<String, Object> view = upsert(450, 450, 1000);

        assertThat(view.get("percent")).isEqualTo(45);
        assertThat(view.get("furthestPositionSeconds")).isEqualTo(450);
        assertThat(view.get("lastPositionSeconds")).isEqualTo(450);
        assertThat(view.get("thresholdPercent")).isEqualTo(90);
        assertThat(view.get("completedAt")).isNull();
    }

    @Test
    void watchedAndFurthestAreMonotonicButLastPositionScrubsBack() {
        MediaWatchProgressEntity existing = new MediaWatchProgressEntity();
        existing.setId(UUID.randomUUID());
        existing.setTenantId(TENANT);
        existing.setEnrolmentId(enrolment.getId());
        existing.setAssetId(ASSET);
        existing.setWatchedSeconds(600);
        existing.setFurthestPositionSeconds(700);
        existing.setDurationSeconds(1000);
        existing.setPercent(60);
        when(watchProgressRepository.findByEnrolmentIdAndAssetId(enrolment.getId(), ASSET))
                .thenReturn(Optional.of(existing));

        Map<String, Object> view = upsert(120, 500, null); // learner scrubbed back

        assertThat(view.get("watchedSeconds")).isEqualTo(600); // never shrinks
        assertThat(view.get("furthestPositionSeconds")).isEqualTo(700); // never shrinks
        assertThat(view.get("lastPositionSeconds")).isEqualTo(120); // resume point follows
        assertThat(view.get("percent")).isEqualTo(60);
    }

    @Test
    void durationFallsBackToAssetDuration() {
        Map<String, Object> view = upsert(500, 500, null);

        assertThat(view.get("durationSeconds")).isEqualTo(1000);
        assertThat(view.get("percent")).isEqualTo(50);
    }

    // ── milestone firing ─────────────────────────────────────────────────────

    @Test
    void milestoneFiresOnceAtDefaultThreshold() {
        Map<String, Object> view = upsert(900, 900, 1000);

        assertThat(view.get("percent")).isEqualTo(90);
        assertThat(view.get("completedAt")).isNotNull();
        verify(outbox).append(eq("FundoMediaWatch"), anyString(),
                eq(FundoNativeEventTypes.MEDIA_WATCH_MILESTONE), any());
    }

    @Test
    void milestoneDoesNotRefireOnceCompleted() {
        MediaWatchProgressEntity existing = new MediaWatchProgressEntity();
        existing.setId(UUID.randomUUID());
        existing.setTenantId(TENANT);
        existing.setEnrolmentId(enrolment.getId());
        existing.setAssetId(ASSET);
        existing.setWatchedSeconds(950);
        existing.setFurthestPositionSeconds(950);
        existing.setDurationSeconds(1000);
        existing.setPercent(95);
        existing.setCompletedAt(java.time.OffsetDateTime.now());
        when(watchProgressRepository.findByEnrolmentIdAndAssetId(enrolment.getId(), ASSET))
                .thenReturn(Optional.of(existing));

        upsert(990, 990, 1000);

        verify(outbox, never()).append(anyString(), anyString(),
                eq(FundoNativeEventTypes.MEDIA_WATCH_MILESTONE), any());
        verify(progressService, never()).recordProgress(any(), any());
    }

    @Test
    void customRuleThresholdOverridesDefault() {
        CompletionRuleEntity rule = new CompletionRuleEntity();
        rule.setRuleType("WATCH_THRESHOLD");
        rule.setThresholdValue(new BigDecimal("50"));
        when(ruleRepository.findByTenantIdAndCourseIdAndRuleType(TENANT, COURSE, "WATCH_THRESHOLD"))
                .thenReturn(Optional.of(rule));

        Map<String, Object> view = upsert(500, 500, 1000);

        assertThat(view.get("thresholdPercent")).isEqualTo(50);
        assertThat(view.get("completedAt")).isNotNull();
    }

    // ── completion wiring ────────────────────────────────────────────────────

    @Test
    void lessonAttachedAssetDrivesLessonProgressTo100() {
        UUID lessonId = UUID.randomUUID();
        asset.setSourceLessonId(lessonId);

        upsert(950, 950, 1000);

        ArgumentCaptor<FundoProgressService.ProgressUpdate> captor =
                ArgumentCaptor.forClass(FundoProgressService.ProgressUpdate.class);
        verify(progressService).recordProgress(eq(TENANT), captor.capture());
        assertThat(captor.getValue().lessonId()).isEqualTo(lessonId);
        assertThat(captor.getValue().enrolmentId()).isEqualTo(enrolment.getId());
        assertThat(captor.getValue().progressPercent()).isEqualTo(100);
        // Lesson path already runs completeIfEligible inside recordProgress.
        verify(progressService, never()).completeIfEligible(any(), any(), any(), anyString());
    }

    @Test
    void lessonLessAssetOnRuledCourseRunsCompleteIfEligible() {
        when(ruleRepository.findByTenantIdAndCourseIdOrderByCreatedAtAsc(TENANT, COURSE))
                .thenReturn(List.of(new CompletionRuleEntity()));

        upsert(950, 950, 1000);

        verify(progressService).completeIfEligible(eq(TENANT), eq(enrolment), any(), eq("WATCH_THRESHOLD"));
        verify(progressService, never()).recordProgress(any(), any());
    }

    @Test
    void lessonLessAssetOnRulelessCourseNeverAutoCompletes() {
        upsert(1000, 1000, 1000);

        // completeIfEligible on a rule-less course would complete unconditionally —
        // a watch milestone alone must never do that.
        verify(progressService, never()).completeIfEligible(any(), any(), any(), anyString());
        verify(progressService, never()).recordProgress(any(), any());
    }

    // ── guards ───────────────────────────────────────────────────────────────

    @Test
    void rejectsAssetFromAnotherCourse() {
        asset.setCourseId(UUID.randomUUID());

        assertThatThrownBy(() -> upsert(10, 10, 1000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("asset_not_in_enrolment_course");
    }

    @Test
    void rejectsInactiveEnrolment() {
        enrolment.setStatus("CANCELLED");

        assertThatThrownBy(() -> upsert(10, 10, 1000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("enrolment_not_active");
    }

    @Test
    void bookmarkDeleteRequiresOwningEnrolment() {
        var bookmark = new zw.gov.mohcc.impilo.learning.persistence.entity.MediaBookmarkEntity();
        bookmark.setId(UUID.randomUUID());
        bookmark.setTenantId(TENANT);
        bookmark.setEnrolmentId(UUID.randomUUID()); // someone else's
        bookmark.setAssetId(ASSET);
        when(bookmarkRepository.findByTenantIdAndId(TENANT, bookmark.getId()))
                .thenReturn(Optional.of(bookmark));

        assertThatThrownBy(() -> service.deleteBookmark(TENANT, bookmark.getId(), enrolment.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bookmark_not_owned_by_enrolment");
    }
}
