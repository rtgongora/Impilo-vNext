package zw.gov.mohcc.impilo.live.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventChatMessageEntity;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventEntity;
import zw.gov.mohcc.impilo.live.persistence.repository.*;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** Announcements: crew-gated broadcast messages on the moderated-chat vocabulary. */
@ExtendWith(MockitoExtension.class)
class AnnouncementServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID EVENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock private LiveEventService eventService;
    @Mock private LiveEventQuestionRepository questionRepository;
    @Mock private LiveEventChatMessageRepository chatRepository;
    @Mock private LiveEventPollRepository pollRepository;
    @Mock private LiveEventPollResponseRepository pollResponseRepository;
    @Mock private LiveEventResourceRepository resourceRepository;
    @Mock private LiveEventFeedbackRepository feedbackRepository;
    @Mock private StageService stageService;
    @Mock private LiveEventEmitter emitter;

    private InteractionService interactionService;
    private LiveEventEntity event;

    @BeforeEach
    void setUp() {
        interactionService = new InteractionService(eventService, questionRepository, chatRepository,
                pollRepository, pollResponseRepository, resourceRepository, feedbackRepository,
                stageService, emitter);
        event = new LiveEventEntity();
        event.setId(EVENT_ID);
        event.setTenantId(TENANT_ID);
        event.setTitle("National vaccination drive");
        event.setMode("PUBLIC_BROADCAST");
        event.setChatEnabled(false); // announcements must work even with chat locked
        lenient().when(eventService.get(TENANT_ID, EVENT_ID)).thenReturn(event);
        lenient().when(chatRepository.save(any())).thenAnswer(inv -> {
            LiveEventChatMessageEntity saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });
    }

    @Test
    void postAnnouncement_persistsAnnouncementKindAndFansOut() {
        LiveEventChatMessageEntity saved = interactionService.postAnnouncement(
                TENANT_ID, EVENT_ID, "host-1", "PROVIDER", "Doors open in 5 minutes");

        assertThat(saved.getKind()).isEqualTo("ANNOUNCEMENT");
        assertThat(saved.getStatus()).isEqualTo("VISIBLE");
        verify(stageService).assertCrew(event, "host-1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(emitter).emit(eq(TENANT_ID), eq("LIVE_EVENT"), eq(EVENT_ID.toString()),
                eq("impilo.live.announcement.published.v1"), anyString(), anyString(), payload.capture());
        assertThat(payload.getValue()).containsEntry("message", "Doors open in 5 minutes");
        assertThat(payload.getValue()).containsEntry("publishedBy", "host-1");
        assertThat(payload.getValue()).containsEntry("title", "National vaccination drive");
    }

    @Test
    void postAnnouncement_requiresCrew() {
        doThrow(LiveGovernanceException.forbidden("STAGE_CREW_REQUIRED", "crew only"))
                .when(stageService).assertCrew(event, "citizen-9");

        assertThatThrownBy(() -> interactionService.postAnnouncement(
                TENANT_ID, EVENT_ID, "citizen-9", "CITIZEN", "hi"))
                .isInstanceOf(LiveGovernanceException.class);
        verifyNoInteractions(emitter);
    }

    @Test
    void postAnnouncement_rejectsBlankMessage() {
        assertThatThrownBy(() -> interactionService.postAnnouncement(
                TENANT_ID, EVENT_ID, "host-1", "PROVIDER", "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listAnnouncements_filtersKindAndVisibility() {
        interactionService.listAnnouncements(TENANT_ID, EVENT_ID);

        verify(chatRepository).findByEventIdAndKindAndStatusOrderByCreatedAtDesc(
                EVENT_ID, "ANNOUNCEMENT", "VISIBLE");
    }
}
