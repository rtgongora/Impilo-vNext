package zw.gov.mohcc.impilo.learning.fundo;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseLessonEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseModuleEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.MediaAssetEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.ScheduledLearningSessionEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseLessonRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseModuleRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.MediaAssetRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Replay adoption semantics (W4): document-object storage typing, idempotent
 * upsert on (tenant, live event), deterministic lesson attachment vs the
 * unattached authoring queue.
 */
@ExtendWith(MockitoExtension.class)
class FundoReplayMediaServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID COURSE = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID LIVE_EVENT = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final String OBJECT_ID = "11111111-1111-1111-1111-111111111111";

    @Mock private MediaAssetRepository mediaAssetRepository;
    @Mock private CourseModuleRepository moduleRepository;
    @Mock private CourseLessonRepository lessonRepository;
    @Mock private FundoOutboxAppender outbox;

    private FundoReplayMediaService service;
    private ScheduledLearningSessionEntity session;

    @BeforeEach
    void setUp() {
        service = new FundoReplayMediaService(mediaAssetRepository, moduleRepository,
                lessonRepository, outbox, new ObjectMapper());
        session = new ScheduledLearningSessionEntity();
        session.setId(UUID.randomUUID());
        session.setTenantId(TENANT);
        session.setCourseId(COURSE);
        session.setTitle("Cold chain refresher");
        session.setLiveEventId(LIVE_EVENT);
        lenient().when(mediaAssetRepository.save(any(MediaAssetEntity.class)))
                .thenAnswer(inv -> {
                    MediaAssetEntity a = inv.getArgument(0);
                    if (a.getId() == null) a.setId(UUID.randomUUID());
                    return a;
                });
        lenient().when(mediaAssetRepository.findByTenantIdAndLiveEventId(TENANT, LIVE_EVENT))
                .thenReturn(Optional.empty());
    }

    private FundoReplayMediaService.ReplayPublished replay() {
        return new FundoReplayMediaService.ReplayPublished(LIVE_EVENT, OBJECT_ID, 1800, null);
    }

    @Test
    void adoptCreatesDocumentObjectAssetWithCourseLinkage() {
        stubCourseVideoLessons(); // no lessons at all

        MediaAssetEntity asset = service.adoptReplay(session, replay());

        assertThat(asset.getStorageKind()).isEqualTo(MediaAssetEntity.STORAGE_KIND_DOCUMENT_OBJECT);
        assertThat(asset.getStorageRef()).isEqualTo(OBJECT_ID);
        assertThat(asset.getMediaType()).isEqualTo("VIDEO");
        assertThat(asset.getRecordingType()).isEqualTo("LIVE_REPLAY");
        assertThat(asset.getCourseId()).isEqualTo(COURSE);
        assertThat(asset.getLiveEventId()).isEqualTo(LIVE_EVENT);
        assertThat(asset.getDurationSeconds()).isEqualTo(1800);
        assertThat(asset.getTitle()).isEqualTo("Cold chain refresher — replay");
        assertThat(asset.getCreatedBy()).isEqualTo("impilo-live");
        verify(outbox).append(eq("FundoMediaAsset"), anyString(),
                eq(FundoNativeEventTypes.MEDIA_REPLAY_ADOPTED), any());
    }

    @Test
    void adoptAttachesWhenCourseHasExactlyOneVideoLesson() {
        UUID lessonId = UUID.randomUUID();
        stubCourseVideoLessons(videoLesson(lessonId), textLesson());

        MediaAssetEntity asset = service.adoptReplay(session, replay());

        assertThat(asset.getSourceLessonId()).isEqualTo(lessonId);
        assertThat(asset.isUnattached()).isFalse();
    }

    @Test
    void adoptQueuesWhenLessonIsAmbiguous() {
        stubCourseVideoLessons(videoLesson(UUID.randomUUID()), videoLesson(UUID.randomUUID()));

        MediaAssetEntity asset = service.adoptReplay(session, replay());

        assertThat(asset.getSourceLessonId()).isNull();
        assertThat(asset.isUnattached()).isTrue();
    }

    @Test
    void adoptPrefersExplicitLessonIdFromSessionMetadata() {
        UUID metadataLesson = UUID.randomUUID();
        session.setMetadataJson("{\"lessonId\":\"" + metadataLesson + "\",\"impiloLiveEventId\":\"x\"}");

        MediaAssetEntity asset = service.adoptReplay(session, replay());

        assertThat(asset.getSourceLessonId()).isEqualTo(metadataLesson);
        assertThat(asset.isUnattached()).isFalse();
    }

    @Test
    void redeliveryUpdatesExistingAssetInsteadOfDuplicating() {
        MediaAssetEntity existing = new MediaAssetEntity();
        existing.setId(UUID.randomUUID());
        existing.setTenantId(TENANT);
        existing.setLiveEventId(LIVE_EVENT);
        existing.setSourceLessonId(UUID.randomUUID()); // already attached by an author
        existing.setUnattached(false);
        existing.setCreatedBy("impilo-live");
        when(mediaAssetRepository.findByTenantIdAndLiveEventId(TENANT, LIVE_EVENT))
                .thenReturn(Optional.of(existing));

        MediaAssetEntity asset = service.adoptReplay(session, replay());

        assertThat(asset.getId()).isEqualTo(existing.getId());
        // Redelivery must not detach the author's explicit lesson attachment.
        assertThat(asset.getSourceLessonId()).isEqualTo(existing.getSourceLessonId());
        assertThat(asset.isUnattached()).isFalse();
        assertThat(asset.getStorageRef()).isEqualTo(OBJECT_ID);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void stubCourseVideoLessons(CourseLessonEntity... lessons) {
        CourseModuleEntity module = new CourseModuleEntity();
        module.setId(UUID.randomUUID());
        module.setCourseId(COURSE);
        lenient().when(moduleRepository.findByCourseIdOrderBySequenceNoAsc(COURSE))
                .thenReturn(List.of(module));
        lenient().when(lessonRepository.findByModuleIdInOrderBySequenceNoAsc(anyList()))
                .thenReturn(List.of(lessons));
    }

    private static CourseLessonEntity videoLesson(UUID id) {
        CourseLessonEntity l = new CourseLessonEntity();
        l.setId(id);
        l.setContentType("VIDEO");
        return l;
    }

    private static CourseLessonEntity textLesson() {
        CourseLessonEntity l = new CourseLessonEntity();
        l.setId(UUID.randomUUID());
        l.setContentType("TEXT");
        return l;
    }
}
