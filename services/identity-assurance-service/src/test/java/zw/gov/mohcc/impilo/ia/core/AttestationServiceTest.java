package zw.gov.mohcc.impilo.ia.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.ia.persistence.entity.AttestationEntity;
import zw.gov.mohcc.impilo.ia.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.ia.persistence.repository.AttestationRepository;
import zw.gov.mohcc.impilo.ia.persistence.repository.EventOutboxRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttestationServiceTest {

    @Mock private AttestationRepository attestationRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private AttestationService attestationService;

    @BeforeEach
    void setUp() {
        attestationService = new AttestationService(attestationRepository, outboxRepository);
        when(attestationRepository.save(any(AttestationEntity.class))).thenAnswer(inv -> {
            AttestationEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(1L);
            return e;
        });
    }

    private AttestationEntity captureSaved() {
        ArgumentCaptor<AttestationEntity> captor = ArgumentCaptor.forClass(AttestationEntity.class);
        verify(attestationRepository).save(captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("Record Attestation — outcome is computed server-side, never client-asserted")
    class RecordAttestation {

        @Test
        void evidencePresent_isPending_notVerified() {
            attestationService.recordAttestation(UUID.randomUUID(), "user-1", UUID.randomUUID(),
                    AttestationType.DEVICE_BINDING, "fp-abc-123", "{\"signedChallenge\":\"abc\"}");
            AttestationEntity saved = captureSaved();
            // A client cannot self-assert VERIFIED; a recorded claim awaits real verification.
            assertThat(saved.getOutcome()).isEqualTo(AttestationOutcome.PENDING);
            assertThat(saved.getConfidence()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void noEvidence_failsClosed() {
            attestationService.recordAttestation(UUID.randomUUID(), "user-1", UUID.randomUUID(),
                    AttestationType.BIOMETRIC, null, null);
            AttestationEntity saved = captureSaved();
            assertThat(saved.getOutcome()).isEqualTo(AttestationOutcome.FAILED);
            assertThat(saved.getEvidence()).isEqualTo("{}");
        }

        @Test
        void emptyJsonEvidence_failsClosed() {
            attestationService.recordAttestation(UUID.randomUUID(), "user-1", UUID.randomUUID(),
                    AttestationType.OTP, null, "{}");
            assertThat(captureSaved().getOutcome()).isEqualTo(AttestationOutcome.FAILED);
        }

        @Test
        void publishesOutboxEventOnRecord() {
            attestationService.recordAttestation(UUID.randomUUID(), "admin", UUID.randomUUID(),
                    AttestationType.BIOMETRIC, null, "{\"sample\":\"x\"}");
            ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
            verify(outboxRepository).save(captor.capture());
            assertThat(captor.getValue().getAggregateType()).isEqualTo("ATTESTATION");
            assertThat(captor.getValue().getEventType()).isEqualTo("ATTESTATION_RECORDED");
        }
    }

    @Nested
    @DisplayName("List Attestations")
    class ListAttestations {

        @Test
        void returnsAllAttestationsForTenant() {
            UUID tenantId = UUID.randomUUID();
            AttestationEntity a1 = new AttestationEntity();
            a1.setId(1L);
            AttestationEntity a2 = new AttestationEntity();
            a2.setId(2L);
            when(attestationRepository.findByTenantId(tenantId)).thenReturn(List.of(a1, a2));

            assertThat(attestationService.listAttestations(tenantId)).hasSize(2);
        }
    }
}
