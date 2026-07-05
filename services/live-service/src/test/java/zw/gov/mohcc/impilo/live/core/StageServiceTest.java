package zw.gov.mohcc.impilo.live.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.live.core.StageService.StageTier;
import zw.gov.mohcc.impilo.live.domain.LiveEventStatus;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventEntity;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventRoleAssignmentEntity;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventStageRequestEntity;
import zw.gov.mohcc.impilo.live.persistence.repository.LiveEventRoleAssignmentRepository;
import zw.gov.mohcc.impilo.live.persistence.repository.LiveEventStageRequestRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StageServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID EVENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String ORGANISER = "organiser-1";
    private static final String AUDIENCE_USER = "citizen-9";

    @Mock private LiveEventService eventService;
    @Mock private LiveEventStageRequestRepository stageRequests;
    @Mock private LiveEventRoleAssignmentRepository roleAssignments;
    @Mock private LiveEventEmitter emitter;

    private StageService stageService;
    private LiveEventEntity event;

    @BeforeEach
    void setUp() {
        stageService = new StageService(eventService, stageRequests, roleAssignments, emitter);
        event = new LiveEventEntity();
        event.setId(EVENT_ID);
        event.setTenantId(TENANT_ID);
        event.setMode("PUBLIC_BROADCAST");
        event.setStatus(LiveEventStatus.LIVE.name());
        event.setOrganiserId(ORGANISER);
        event.setCreatedBy(ORGANISER);
        lenient().when(eventService.get(TENANT_ID, EVENT_ID)).thenReturn(event);
        lenient().when(stageRequests.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(roleAssignments.findByEventId(EVENT_ID)).thenReturn(List.of());
    }

    // ── request machine ─────────────────────────────────────────────

    @Test
    void requestStage_createsRequestedRowAndEmitsEvent() {
        when(stageRequests.findFirstByEventIdAndParticipantIdAndStatusInOrderByRequestedAtDesc(
                eq(EVENT_ID), eq(AUDIENCE_USER), anyList())).thenReturn(Optional.empty());

        LiveEventStageRequestEntity request =
                stageService.requestStage(TENANT_ID, EVENT_ID, AUDIENCE_USER, "CITIZEN");

        assertThat(request.getStatus()).isEqualTo("REQUESTED");
        assertThat(request.getParticipantId()).isEqualTo(AUDIENCE_USER);
        verify(emitter).emit(eq(TENANT_ID), eq("LIVE_STAGE_REQUEST"), eq(EVENT_ID.toString()),
                eq("impilo.live.stage.requested.v1"), anyString(), anyString(), any());
    }

    @Test
    void requestStage_isIdempotentOnOpenRequest() {
        LiveEventStageRequestEntity existing = request("REQUESTED");
        when(stageRequests.findFirstByEventIdAndParticipantIdAndStatusInOrderByRequestedAtDesc(
                eq(EVENT_ID), eq(AUDIENCE_USER), anyList())).thenReturn(Optional.of(existing));

        LiveEventStageRequestEntity result =
                stageService.requestStage(TENANT_ID, EVENT_ID, AUDIENCE_USER, "CITIZEN");

        assertThat(result).isSameAs(existing);
        verify(stageRequests, never()).save(any());
    }

    @Test
    void requestStage_rejectsNonBroadcastModes() {
        event.setMode("PROFESSIONAL_MEETING");

        assertThatThrownBy(() -> stageService.requestStage(TENANT_ID, EVENT_ID, AUDIENCE_USER, "CITIZEN"))
                .isInstanceOf(LiveGovernanceException.class)
                .hasMessageContaining("broadcast");
    }

    @Test
    void requestStage_rejectsEndedEvents() {
        event.setStatus(LiveEventStatus.ENDED.name());

        assertThatThrownBy(() -> stageService.requestStage(TENANT_ID, EVENT_ID, AUDIENCE_USER, "CITIZEN"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void approve_marksApprovedRecordsDeciderAndEmits() {
        LiveEventStageRequestEntity pending = request("REQUESTED");
        when(stageRequests.findById(pending.getId())).thenReturn(Optional.of(pending));

        LiveEventStageRequestEntity approved =
                stageService.approve(TENANT_ID, EVENT_ID, pending.getId(), ORGANISER);

        assertThat(approved.getStatus()).isEqualTo("APPROVED");
        assertThat(approved.getDecidedBy()).isEqualTo(ORGANISER);
        assertThat(approved.getDecidedAt()).isNotNull();
        verify(emitter).emit(eq(TENANT_ID), eq("LIVE_STAGE_REQUEST"), eq(EVENT_ID.toString()),
                eq("impilo.live.stage.approved.v1"), anyString(), anyString(), any());
    }

    @Test
    void approve_requiresCrewActor() {
        LiveEventStageRequestEntity pending = request("REQUESTED");

        assertThatThrownBy(() -> stageService.approve(TENANT_ID, EVENT_ID, pending.getId(), "random-user"))
                .isInstanceOf(LiveGovernanceException.class)
                .hasMessageContaining("host or producer");
        assertThatThrownBy(() -> stageService.approve(TENANT_ID, EVENT_ID, pending.getId(), null))
                .isInstanceOf(LiveGovernanceException.class);
    }

    @Test
    void deny_marksDenied() {
        LiveEventStageRequestEntity pending = request("REQUESTED");
        when(stageRequests.findById(pending.getId())).thenReturn(Optional.of(pending));

        LiveEventStageRequestEntity denied =
                stageService.deny(TENANT_ID, EVENT_ID, pending.getId(), ORGANISER);

        assertThat(denied.getStatus()).isEqualTo("DENIED");
    }

    @Test
    void demote_flipsApprovedRequestAndRemovesSpeakerAssignments() {
        LiveEventStageRequestEntity approved = request("APPROVED");
        when(stageRequests.findFirstByEventIdAndParticipantIdAndStatusInOrderByRequestedAtDesc(
                EVENT_ID, AUDIENCE_USER, List.of("APPROVED"))).thenReturn(Optional.of(approved));
        LiveEventRoleAssignmentEntity speakerAssignment = assignment(AUDIENCE_USER, "SPEAKER");
        when(roleAssignments.findByEventId(EVENT_ID)).thenReturn(List.of(speakerAssignment));

        Map<String, Object> out = stageService.demoteParticipant(TENANT_ID, EVENT_ID, AUDIENCE_USER, ORGANISER);

        assertThat(approved.getStatus()).isEqualTo("DEMOTED");
        assertThat(out.get("tier")).isEqualTo("AUDIENCE");
        verify(roleAssignments).deleteAll(List.of(speakerAssignment));
        verify(emitter).emit(eq(TENANT_ID), eq("LIVE_STAGE_REQUEST"), eq(EVENT_ID.toString()),
                eq("impilo.live.stage.demoted.v1"), anyString(), anyString(), any());
    }

    @Test
    void demote_withoutAnyGrantFails() {
        when(stageRequests.findFirstByEventIdAndParticipantIdAndStatusInOrderByRequestedAtDesc(
                EVENT_ID, AUDIENCE_USER, List.of("APPROVED"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stageService.demoteParticipant(TENANT_ID, EVENT_ID, AUDIENCE_USER, ORGANISER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── tier + effective-role resolution ────────────────────────────

    @Test
    void organiserResolvesToHostTier() {
        assertThat(stageService.resolveTier(event, ORGANISER)).isEqualTo(StageTier.HOST);
    }

    @Test
    void approvedStageRequestGrantsSpeakerTier() {
        when(stageRequests.findFirstByEventIdAndParticipantIdAndStatusInOrderByRequestedAtDesc(
                EVENT_ID, AUDIENCE_USER, List.of("APPROVED"))).thenReturn(Optional.of(request("APPROVED")));

        assertThat(stageService.resolveTier(event, AUDIENCE_USER)).isEqualTo(StageTier.SPEAKER);
    }

    @Test
    void unknownParticipantIsAudience() {
        when(stageRequests.findFirstByEventIdAndParticipantIdAndStatusInOrderByRequestedAtDesc(
                eq(EVENT_ID), eq(AUDIENCE_USER), anyList())).thenReturn(Optional.empty());

        assertThat(stageService.resolveTier(event, AUDIENCE_USER)).isEqualTo(StageTier.AUDIENCE);
    }

    @Test
    void effectiveRole_clampsUnapprovedSpeakerRequestToAudience() {
        when(stageRequests.findFirstByEventIdAndParticipantIdAndStatusInOrderByRequestedAtDesc(
                eq(EVENT_ID), eq(AUDIENCE_USER), anyList())).thenReturn(Optional.empty());

        // Legacy PRESENTER alias is also clamped — the exact escalation this wave closes.
        assertThat(stageService.resolveEffectiveRole(event, AUDIENCE_USER, "PRESENTER")).isEqualTo("AUDIENCE");
        assertThat(stageService.resolveEffectiveRole(event, AUDIENCE_USER, "SPEAKER")).isEqualTo("AUDIENCE");
        assertThat(stageService.resolveEffectiveRole(event, AUDIENCE_USER, "HOST")).isEqualTo("AUDIENCE");
    }

    @Test
    void effectiveRole_honoursApprovedSpeakerAndCrewChoices() {
        when(stageRequests.findFirstByEventIdAndParticipantIdAndStatusInOrderByRequestedAtDesc(
                EVENT_ID, AUDIENCE_USER, List.of("APPROVED"))).thenReturn(Optional.of(request("APPROVED")));

        assertThat(stageService.resolveEffectiveRole(event, AUDIENCE_USER, "SPEAKER")).isEqualTo("SPEAKER");
        // Crew may take any seat.
        assertThat(stageService.resolveEffectiveRole(event, ORGANISER, "SPEAKER")).isEqualTo("SPEAKER");
        assertThat(stageService.resolveEffectiveRole(event, ORGANISER, "ATTENDEE")).isEqualTo("AUDIENCE");
    }

    @Test
    void effectiveRole_moderatorCannotTradeUpToSpeaker() {
        when(roleAssignments.findByEventId(EVENT_ID))
                .thenReturn(List.of(assignment("mod-1", "MODERATOR")));

        assertThat(stageService.resolveEffectiveRole(event, "mod-1", "SPEAKER")).isEqualTo("MODERATOR");
        assertThat(stageService.resolveEffectiveRole(event, "mod-1", "MODERATOR")).isEqualTo("MODERATOR");
    }

    @Test
    void effectiveRole_leavesNonBroadcastModesUntouched() {
        event.setMode("WEBINAR_CPD"); // LEARNING_LIVE session mode — classroom roles are not stage-managed

        assertThat(stageService.resolveEffectiveRole(event, AUDIENCE_USER, "LEARNER")).isEqualTo("LEARNER");
    }

    // ── helpers ─────────────────────────────────────────────────────

    private LiveEventStageRequestEntity request(String status) {
        LiveEventStageRequestEntity request = new LiveEventStageRequestEntity();
        request.setId(UUID.randomUUID());
        request.setEventId(EVENT_ID);
        request.setParticipantId(AUDIENCE_USER);
        request.setParticipantType("CITIZEN");
        request.setStatus(status);
        return request;
    }

    private LiveEventRoleAssignmentEntity assignment(String userId, String role) {
        LiveEventRoleAssignmentEntity assignment = new LiveEventRoleAssignmentEntity();
        assignment.setId(UUID.randomUUID());
        assignment.setEventId(EVENT_ID);
        assignment.setUserId(userId);
        assignment.setRole(role);
        return assignment;
    }
}
