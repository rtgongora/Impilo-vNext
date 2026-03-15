package zw.gov.mohcc.impilo.offlineedge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.offlineedge.core.BreakGlassService;
import zw.gov.mohcc.impilo.offlineedge.domain.BreakGlassEventEntity;
import zw.gov.mohcc.impilo.offlineedge.repository.BreakGlassEventRepository;
import zw.gov.mohcc.impilo.offlineedge.repository.OutboxEventRepository;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Wave 22 tests: Break-glass activation, review, and escalation.
 */
class Wave22BreakGlassTest {

    private BreakGlassEventRepository breakGlassRepo;
    private OutboxEventRepository outboxRepo;
    private BreakGlassService service;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        breakGlassRepo = mock(BreakGlassEventRepository.class);
        outboxRepo = mock(OutboxEventRepository.class);
        service = new BreakGlassService(breakGlassRepo, outboxRepo, new ObjectMapper());
    }

    @Nested
    @DisplayName("Break-glass activation")
    class Activation {

        @Test
        @DisplayName("Activation creates event with PENDING_REVIEW status")
        void activationCreatesPendingReview() {
            when(breakGlassRepo.save(any(BreakGlassEventEntity.class)))
                    .thenAnswer(inv -> {
                        BreakGlassEventEntity e = inv.getArgument(0);
                        e.prePersist();
                        return e;
                    });

            BreakGlassEventEntity event = service.activate(
                    TENANT_ID, "nurse-001", "facility-A", "CPID-12345",
                    "Emergency: patient transferred", "SCOPE_EXTENSION",
                    "FACILITY-A", "FACILITY-A,FACILITY-B",
                    UUID.randomUUID(), "pod-1", "corr-1");

            assertThat(event.getStatus()).isEqualTo("PENDING_REVIEW");
            assertThat(event.getActorId()).isEqualTo("nurse-001");
            assertThat(event.getReason()).isEqualTo("Emergency: patient transferred");
            assertThat(event.getOverrideType()).isEqualTo("SCOPE_EXTENSION");
            assertThat(event.getReviewRequiredBy()).isAfter(OffsetDateTime.now().plusHours(23));
        }

        @Test
        @DisplayName("Activation requires reason")
        void activationRequiresReason() {
            assertThatThrownBy(() -> service.activate(
                    TENANT_ID, "nurse-001", "facility-A", "CPID-12345",
                    null, "SCOPE_EXTENSION", null, null,
                    null, "pod-1", "corr-1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reason is mandatory");
        }

        @Test
        @DisplayName("Activation requires non-blank reason")
        void activationRequiresNonBlankReason() {
            assertThatThrownBy(() -> service.activate(
                    TENANT_ID, "nurse-001", "facility-A", "CPID-12345",
                    "   ", "SCOPE_EXTENSION", null, null,
                    null, "pod-1", "corr-1"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Activation emits HIGH-priority outbox event")
        void activationEmitsHighPriorityEvent() {
            when(breakGlassRepo.save(any(BreakGlassEventEntity.class)))
                    .thenAnswer(inv -> {
                        BreakGlassEventEntity e = inv.getArgument(0);
                        e.prePersist();
                        return e;
                    });

            service.activate(TENANT_ID, "nurse-001", "facility-A", "CPID-12345",
                    "Emergency access", "SCOPE_EXTENSION",
                    "FACILITY-A", "FACILITY-A,FACILITY-B",
                    null, "pod-1", "corr-1");

            verify(outboxRepo).save(argThat(e ->
                    "impilo.offline.break_glass.activated.v1".equals(e.getEventType()) &&
                            e.getPayloadJson().contains("\"priority\":\"HIGH\"") &&
                            e.getPayloadJson().contains("nurse-001") &&
                            e.getPayloadJson().contains("Emergency access")
            ));
        }

        @Test
        @DisplayName("Activation defaults override type to SCOPE_EXTENSION")
        void activationDefaultsOverrideType() {
            when(breakGlassRepo.save(any(BreakGlassEventEntity.class)))
                    .thenAnswer(inv -> {
                        BreakGlassEventEntity e = inv.getArgument(0);
                        e.prePersist();
                        return e;
                    });

            BreakGlassEventEntity event = service.activate(
                    TENANT_ID, "nurse-001", "facility-A", "CPID-12345",
                    "Emergency", null, null, null,
                    null, "pod-1", "corr-1");

            assertThat(event.getOverrideType()).isEqualTo("SCOPE_EXTENSION");
        }
    }

    @Nested
    @DisplayName("Break-glass review")
    class Review {

        @Test
        @DisplayName("Review resolves event with APPROVED")
        void reviewApproves() {
            UUID eventId = UUID.randomUUID();
            BreakGlassEventEntity event = createPendingEvent(eventId);
            when(breakGlassRepo.findById(eventId)).thenReturn(Optional.of(event));
            when(breakGlassRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            BreakGlassEventEntity result = service.review(
                    eventId, "APPROVED", "manager-001", "Verified legitimate emergency",
                    "pod-1", "corr-1");

            assertThat(result.getStatus()).isEqualTo("APPROVED");
            assertThat(result.getReviewedBy()).isEqualTo("manager-001");
            assertThat(result.getReviewedAt()).isNotNull();
        }

        @Test
        @DisplayName("Review resolves event with ESCALATED")
        void reviewEscalates() {
            UUID eventId = UUID.randomUUID();
            BreakGlassEventEntity event = createPendingEvent(eventId);
            when(breakGlassRepo.findById(eventId)).thenReturn(Optional.of(event));
            when(breakGlassRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            BreakGlassEventEntity result = service.review(
                    eventId, "ESCALATED", "manager-001", "Needs investigation",
                    "pod-1", "corr-1");

            assertThat(result.getStatus()).isEqualTo("ESCALATED");
        }

        @Test
        @DisplayName("Review resolves event with FLAGGED")
        void reviewFlags() {
            UUID eventId = UUID.randomUUID();
            BreakGlassEventEntity event = createPendingEvent(eventId);
            when(breakGlassRepo.findById(eventId)).thenReturn(Optional.of(event));
            when(breakGlassRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            BreakGlassEventEntity result = service.review(
                    eventId, "FLAGGED", "manager-001", "Suspicious pattern",
                    "pod-1", "corr-1");

            assertThat(result.getStatus()).isEqualTo("FLAGGED");
        }

        @Test
        @DisplayName("Review rejects invalid resolution")
        void reviewRejectsInvalid() {
            UUID eventId = UUID.randomUUID();
            BreakGlassEventEntity event = createPendingEvent(eventId);
            when(breakGlassRepo.findById(eventId)).thenReturn(Optional.of(event));

            assertThatThrownBy(() -> service.review(
                    eventId, "INVALID", "manager-001", null, "pod-1", "corr-1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("INVALID");
        }

        @Test
        @DisplayName("Review rejects already-reviewed event")
        void reviewRejectsAlreadyReviewed() {
            UUID eventId = UUID.randomUUID();
            BreakGlassEventEntity event = createPendingEvent(eventId);
            event.setStatus("APPROVED");
            when(breakGlassRepo.findById(eventId)).thenReturn(Optional.of(event));

            assertThatThrownBy(() -> service.review(
                    eventId, "FLAGGED", "manager-001", null, "pod-1", "corr-1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already reviewed");
        }

        @Test
        @DisplayName("Review emits outbox event")
        void reviewEmitsOutbox() {
            UUID eventId = UUID.randomUUID();
            BreakGlassEventEntity event = createPendingEvent(eventId);
            when(breakGlassRepo.findById(eventId)).thenReturn(Optional.of(event));
            when(breakGlassRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.review(eventId, "APPROVED", "manager-001", "OK", "pod-1", "corr-1");

            verify(outboxRepo).save(argThat(e ->
                    "impilo.offline.break_glass.reviewed.v1".equals(e.getEventType()) &&
                            e.getPayloadJson().contains("APPROVED")
            ));
        }

        @Test
        @DisplayName("Review throws for non-existent event")
        void reviewNotFound() {
            UUID eventId = UUID.randomUUID();
            when(breakGlassRepo.findById(eventId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.review(
                    eventId, "APPROVED", "manager-001", null, "pod-1", "corr-1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not found");
        }
    }

    @Nested
    @DisplayName("Break-glass escalation")
    class Escalation {

        @Test
        @DisplayName("Overdue events are auto-escalated")
        void overdueEventsEscalated() {
            BreakGlassEventEntity overdue1 = createPendingEvent(UUID.randomUUID());
            overdue1.setReviewRequiredBy(OffsetDateTime.now().minusHours(1));

            BreakGlassEventEntity overdue2 = createPendingEvent(UUID.randomUUID());
            overdue2.setReviewRequiredBy(OffsetDateTime.now().minusMinutes(30));

            when(breakGlassRepo.findByStatusAndReviewRequiredByBefore(
                    eq("PENDING_REVIEW"), any(OffsetDateTime.class)))
                    .thenReturn(List.of(overdue1, overdue2));
            when(breakGlassRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.escalateOverdueReviews();

            verify(breakGlassRepo, times(2)).save(argThat(e ->
                    "ESCALATED".equals(e.getStatus())
            ));
        }

        @Test
        @DisplayName("No escalation when no overdue events")
        void noEscalationWhenNoneOverdue() {
            when(breakGlassRepo.findByStatusAndReviewRequiredByBefore(
                    eq("PENDING_REVIEW"), any(OffsetDateTime.class)))
                    .thenReturn(List.of());

            service.escalateOverdueReviews();

            verify(breakGlassRepo, never()).save(any());
        }
    }

    private BreakGlassEventEntity createPendingEvent(UUID eventId) {
        BreakGlassEventEntity event = new BreakGlassEventEntity();
        event.setEventId(eventId);
        event.setTenantId(TENANT_ID);
        event.setActorId("nurse-001");
        event.setFacilityId("facility-A");
        event.setPatientRef("CPID-12345");
        event.setReason("Emergency");
        event.setOverrideType("SCOPE_EXTENSION");
        event.setActivatedAt(OffsetDateTime.now().minusHours(2));
        event.setReviewRequiredBy(OffsetDateTime.now().plusHours(22));
        event.setStatus("PENDING_REVIEW");
        event.setCreatedAt(OffsetDateTime.now().minusHours(2));
        return event;
    }
}
