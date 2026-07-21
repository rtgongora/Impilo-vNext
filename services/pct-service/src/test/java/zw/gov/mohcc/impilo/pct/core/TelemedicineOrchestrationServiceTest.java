package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.pct.core.telemedicine.TelemedicineProviderProperties;
import zw.gov.mohcc.impilo.pct.core.telemedicine.TelemedicineSessionProviderRouter;
import zw.gov.mohcc.impilo.pct.integration.LiveSessionIntegration;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ReferralEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.ReferralRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.TelehealthSessionRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelemedicineOrchestrationServiceTest {

    @Mock private ReferralRepository referralRepository;
    @Mock private TelehealthSessionRepository telehealthSessionRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private TelemetryService telemetryService;
    @Mock private TelemedicineSessionProviderRouter sessionProviderRouter;
    @Mock private TelemedicineProviderProperties providerProperties;
    @Mock private LiveSessionIntegration liveSessionIntegration;
    @Mock private VirtualPoolQueueService virtualPoolQueueService;
    @Mock private zw.gov.mohcc.impilo.pct.persistence.repository.ReferralTransitionRepository referralTransitionRepository;

    private TelemedicineOrchestrationService service;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        // Real guard in default SHADOW mode: it applies the status exactly as before
        // (never blocks) while recording the transition ledger — existing behavioural
        // assertions on the resulting status are unchanged.
        ReferralTransitionRecorder transitionRecorder =
                new ReferralTransitionRecorder(referralTransitionRepository, telemetryService);
        ReferralStateMachine referralStateMachine = new ReferralStateMachine(
                transitionRecorder,
                new zw.gov.mohcc.impilo.pct.core.telemedicine.ReferralTransitionGuardProperties());
        service = new TelemedicineOrchestrationService(
                referralRepository, telehealthSessionRepository, outboxRepository, telemetryService,
                sessionProviderRouter, providerProperties, liveSessionIntegration, virtualPoolQueueService,
                referralStateMachine, new ObjectMapper());
        tenantId = UUID.randomUUID();
    }

    private TrustContext context() {
        return new TrustContext(
                tenantId, "actor-1", "PROVIDER", "TREATMENT", null,
                UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL);
    }

    private ReferralEntity newReferral(UUID referralId, String status) {
        ReferralEntity referral = new ReferralEntity();
        referral.setReferralId(referralId);
        referral.setTenantId(tenantId);
        referral.setPatientCpid("CPID-1");
        referral.setStatus(status);
        referral.setStage(6);
        referral.setRoutingTarget("{}");
        referral.setAttachmentDocumentIds("[]");
        referral.setMessages("[]");
        referral.setResponses("[]");
        referral.setCompletionPayload("{}");
        return referral;
    }

    private long countValueEvents() {
        ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues().stream()
                .filter(e -> "TELECONSULT_COMPLETED".equals(e.getEventType()))
                .count();
    }

    @Test
    void completeReferral_emitsValueEventOnce() {
        try (MockedStatic<TrustContextHolder> mocked = org.mockito.Mockito.mockStatic(TrustContextHolder.class)) {
            mocked.when(TrustContextHolder::require).thenReturn(context());
            UUID referralId = UUID.randomUUID();
            ReferralEntity referral = newReferral(referralId, "RESPONDED");
            when(referralRepository.findByTenantIdAndReferralId(tenantId, referralId))
                    .thenReturn(Optional.of(referral));
            when(referralRepository.save(any(ReferralEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            service.completeReferral(referralId.toString(),
                    Map.of("closureNarrative", "Resolved remotely; advised follow-up in 2 weeks."));

            assertThat(countValueEvents()).isEqualTo(1L);
        }
    }

    @Test
    void attachRecording_writes_ref_to_the_matching_referral() {
        UUID referralId = UUID.randomUUID();
        ReferralEntity referral = newReferral(referralId, "COMPLETED");
        when(referralRepository.findByReferralId(referralId)).thenReturn(Optional.of(referral));
        when(referralRepository.save(any(ReferralEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean attached = service.attachRecording(referralId.toString(), "egr-77", "s3://bucket/rec.mp4");

        assertThat(attached).isTrue();
        assertThat(referral.getRecordingRef()).isEqualTo("egr-77");
        assertThat(referral.getRecordingStorageKey()).isEqualTo("s3://bucket/rec.mp4");
        // A recording_attached outbox event is emitted (tenant-explicit — no TrustContext needed).
        verify(outboxRepository, atLeastOnce()).save(any());
    }

    @Test
    void attachRecording_ignores_unknown_referral() {
        UUID referralId = UUID.randomUUID();
        when(referralRepository.findByReferralId(referralId)).thenReturn(Optional.empty());

        boolean attached = service.attachRecording(referralId.toString(), "egr-1", "k");

        assertThat(attached).isFalse();
        verify(referralRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void updateReferralStage_persists_appointment_backlink() {
        try (MockedStatic<TrustContextHolder> mocked = org.mockito.Mockito.mockStatic(TrustContextHolder.class)) {
            mocked.when(TrustContextHolder::require).thenReturn(context());
            UUID referralId = UUID.randomUUID();
            ReferralEntity referral = newReferral(referralId, "ACCEPTED");
            when(referralRepository.findByTenantIdAndReferralId(tenantId, referralId))
                    .thenReturn(Optional.of(referral));
            when(referralRepository.save(any(ReferralEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> result = service.updateReferralStage(referralId.toString(), Map.of(
                    "appointment_id", "appt-77",
                    "scheduled_at", "2026-08-01T09:30:00Z"));

            // G18: the referral is now the durable join to its scheduling-service appointment.
            assertThat(referral.getAppointmentId()).isEqualTo("appt-77");
            assertThat(referral.getScheduledAt()).isNotNull();
            assertThat(result.get("appointmentId")).isEqualTo("appt-77");
            assertThat(result.get("scheduledAt")).isNotNull();
        }
    }

    @Test
    void completeReferral_rejects_closure_without_an_audit_note() {
        try (MockedStatic<TrustContextHolder> mocked = org.mockito.Mockito.mockStatic(TrustContextHolder.class)) {
            mocked.when(TrustContextHolder::require).thenReturn(context());
            UUID referralId = UUID.randomUUID();
            ReferralEntity referral = newReferral(referralId, "RESPONDED");
            when(referralRepository.findByTenantIdAndReferralId(tenantId, referralId))
                    .thenReturn(Optional.of(referral));

            // No closure without audit: an empty completion (no actions/outcome/narrative) is rejected.
            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> service.completeReferral(referralId.toString(), Map.of()))
                    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                    .hasMessageContaining("completion note");
            // The referral must NOT be completed and no billable event emitted.
            assertThat(referral.getStatus()).isEqualTo("RESPONDED");
            verify(outboxRepository, org.mockito.Mockito.never()).save(any());
        }
    }

    @Test
    void completeReferral_persists_a_structured_auditable_note() {
        try (MockedStatic<TrustContextHolder> mocked = org.mockito.Mockito.mockStatic(TrustContextHolder.class)) {
            mocked.when(TrustContextHolder::require).thenReturn(context());
            UUID referralId = UUID.randomUUID();
            ReferralEntity referral = newReferral(referralId, "RESPONDED");
            when(referralRepository.findByTenantIdAndReferralId(tenantId, referralId))
                    .thenReturn(Optional.of(referral));
            when(referralRepository.save(any(ReferralEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            service.completeReferral(referralId.toString(), Map.of(
                    "actionsTaken", "Adjusted antihypertensive dose",
                    "patientOutcome", "Stable, BP controlled",
                    "closureNarrative", "Teleconsult complete; routine follow-up"));

            String payload = referral.getCompletionPayload();
            assertThat(payload).contains("completionNote");
            assertThat(payload).contains("Adjusted antihypertensive dose");
            assertThat(payload).contains("Stable, BP controlled");
            assertThat(payload).contains("completedBy");
        }
    }

    @Test
    void createReferral_persistsPoolRoutingWhenSupplied() {
        try (MockedStatic<TrustContextHolder> mocked = org.mockito.Mockito.mockStatic(TrustContextHolder.class)) {
            mocked.when(TrustContextHolder::require).thenReturn(context());
            ArgumentCaptor<ReferralEntity> captor = ArgumentCaptor.forClass(ReferralEntity.class);
            when(referralRepository.save(captor.capture())).thenAnswer(inv -> {
                ReferralEntity e = inv.getArgument(0);
                if (e.getReferralId() == null) e.setReferralId(UUID.randomUUID());
                return e;
            });

            service.createReferral(Map.of(
                    "patient_id", "CPID-9",
                    "reason", "Persistent cough",
                    "routing_kind", "POOL",
                    "routing_pool_id", "GENERAL_TELEMEDICINE"));

            ReferralEntity saved = captor.getValue();
            assertThat(saved.getRoutingKind()).isEqualTo("POOL");
            assertThat(saved.getRoutingPoolId()).isEqualTo("GENERAL_TELEMEDICINE");
            assertThat(saved.getRoutedAt()).isNotNull();
            assertThat(saved.getRoutingTarget()).contains("GENERAL_TELEMEDICINE");
            // Creation with routing does NOT skip the lifecycle: still DRAFT until submit.
            assertThat(saved.getStatus()).isEqualTo("DRAFT");
        }
    }

    @Test
    void createReferral_withoutRouting_leavesRoutingColumnsUnset() {
        try (MockedStatic<TrustContextHolder> mocked = org.mockito.Mockito.mockStatic(TrustContextHolder.class)) {
            mocked.when(TrustContextHolder::require).thenReturn(context());
            ArgumentCaptor<ReferralEntity> captor = ArgumentCaptor.forClass(ReferralEntity.class);
            when(referralRepository.save(captor.capture())).thenAnswer(inv -> {
                ReferralEntity e = inv.getArgument(0);
                if (e.getReferralId() == null) e.setReferralId(UUID.randomUUID());
                return e;
            });

            service.createReferral(Map.of("patient_id", "CPID-9", "reason", "Persistent cough"));

            ReferralEntity saved = captor.getValue();
            assertThat(saved.getRoutingKind()).isNull();
            assertThat(saved.getRoutingPoolId()).isNull();
            assertThat(saved.getRoutingTarget()).isEqualTo("{}");
        }
    }

    @Test
    void listPoolReferrals_defaultsToSubmittedStatus() {
        try (MockedStatic<TrustContextHolder> mocked = org.mockito.Mockito.mockStatic(TrustContextHolder.class)) {
            mocked.when(TrustContextHolder::require).thenReturn(context());
            ReferralEntity referral = newReferral(UUID.randomUUID(), "SUBMITTED");
            referral.setRoutingKind("POOL");
            referral.setRoutingPoolId("MATERNAL_NEWBORN");
            when(referralRepository.findByTenantIdAndRoutingPoolIdAndStatusInOrderByCreatedAtAsc(
                    tenantId, "MATERNAL_NEWBORN", List.of("SUBMITTED")))
                    .thenReturn(List.of(referral));

            List<Map<String, Object>> rows = service.listPoolReferrals("MATERNAL_NEWBORN", null);

            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).get("routingPoolId")).isEqualTo("MATERNAL_NEWBORN");
        }
    }

    @Test
    void listPoolReferrals_acceptsCommaSeparatedStatusIn() {
        try (MockedStatic<TrustContextHolder> mocked = org.mockito.Mockito.mockStatic(TrustContextHolder.class)) {
            mocked.when(TrustContextHolder::require).thenReturn(context());
            when(referralRepository.findByTenantIdAndRoutingPoolIdAndStatusInOrderByCreatedAtAsc(
                    tenantId, "SPECIALIST_POOL", List.of("SUBMITTED", "ACCEPTED")))
                    .thenReturn(List.of());

            List<Map<String, Object>> rows = service.listPoolReferrals("SPECIALIST_POOL", "submitted, accepted");

            assertThat(rows).isEmpty();
            verify(referralRepository).findByTenantIdAndRoutingPoolIdAndStatusInOrderByCreatedAtAsc(
                    tenantId, "SPECIALIST_POOL", List.of("SUBMITTED", "ACCEPTED"));
        }
    }

    @Test
    void completeReferral_isIdempotent_doesNotReEmitBillableEvent() {
        try (MockedStatic<TrustContextHolder> mocked = org.mockito.Mockito.mockStatic(TrustContextHolder.class)) {
            mocked.when(TrustContextHolder::require).thenReturn(context());
            UUID referralId = UUID.randomUUID();
            // Referral already COMPLETED (e.g. a redelivered or double-clicked completion).
            ReferralEntity referral = newReferral(referralId, "COMPLETED");
            when(referralRepository.findByTenantIdAndReferralId(tenantId, referralId))
                    .thenReturn(Optional.of(referral));

            Map<String, Object> result = service.completeReferral(referralId.toString(), Map.of());

            assertThat(result.get("status")).isEqualTo("COMPLETED");
            // No billable TELECONSULT_COMPLETED value event must be emitted on a re-completion.
            ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
            verify(outboxRepository, org.mockito.Mockito.never()).save(captor.capture());
            List<EventOutboxEntity> saved = captor.getAllValues();
            long billable = saved.stream().filter(e -> "TELECONSULT_COMPLETED".equals(e.getEventType())).count();
            assertThat(billable).isZero();
        }
    }
}
