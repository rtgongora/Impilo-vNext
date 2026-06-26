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

    private TelemedicineOrchestrationService service;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        service = new TelemedicineOrchestrationService(
                referralRepository, telehealthSessionRepository, outboxRepository, telemetryService,
                sessionProviderRouter, providerProperties, liveSessionIntegration, new ObjectMapper());
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

            service.completeReferral(referralId.toString(), Map.of());

            assertThat(countValueEvents()).isEqualTo(1L);
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
