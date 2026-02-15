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
import zw.gov.mohcc.impilo.ia.persistence.entity.AttestationEntity;
import zw.gov.mohcc.impilo.ia.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.ia.persistence.repository.AttestationRepository;
import zw.gov.mohcc.impilo.ia.persistence.repository.EventOutboxRepository;

@ExtendWith(MockitoExtension.class)
class AttestationServiceTest {

    @Mock private AttestationRepository attestationRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private AttestationService attestationService;

    @BeforeEach
    void setUp() {
        attestationService = new AttestationService(attestationRepository, outboxRepository);
    }

    @Nested
    @DisplayName("Record Attestation")
    class RecordAttestation {

        @Test
        void recordsAndSavesAttestation() {
            UUID tenantId = UUID.randomUUID();
            String actorId = "user-1";
            UUID correlationId = UUID.randomUUID();

            AttestationEntity saved = new AttestationEntity();
            saved.setId(1L);
            saved.setTenantId(tenantId);
            saved.setActorId(actorId);
            saved.setAttestationType(AttestationType.DEVICE_BINDING);
            saved.setOutcome(AttestationOutcome.VERIFIED);
            saved.setConfidence(new BigDecimal("0.9500"));
            saved.setRecordedBy(actorId);

            when(attestationRepository.save(any(AttestationEntity.class))).thenReturn(saved);

            AttestationEntity result = attestationService.recordAttestation(
                    tenantId, actorId, correlationId,
                    AttestationType.DEVICE_BINDING, "fp-abc-123",
                    "{\"key\":\"value\"}", AttestationOutcome.VERIFIED,
                    new BigDecimal("0.9500"));

            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getAttestationType()).isEqualTo(AttestationType.DEVICE_BINDING);
            assertThat(result.getOutcome()).isEqualTo(AttestationOutcome.VERIFIED);
        }

        @Test
        void publishesOutboxEventOnRecord() {
            UUID tenantId = UUID.randomUUID();
            AttestationEntity saved = new AttestationEntity();
            saved.setId(2L);
            saved.setTenantId(tenantId);
            saved.setActorId("admin");
            saved.setAttestationType(AttestationType.BIOMETRIC);
            saved.setOutcome(AttestationOutcome.VERIFIED);
            saved.setConfidence(new BigDecimal("0.8800"));
            saved.setRecordedBy("admin");

            when(attestationRepository.save(any(AttestationEntity.class))).thenReturn(saved);

            attestationService.recordAttestation(tenantId, "admin", UUID.randomUUID(),
                    AttestationType.BIOMETRIC, null, null,
                    AttestationOutcome.VERIFIED, new BigDecimal("0.8800"));

            ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
            verify(outboxRepository).save(captor.capture());

            EventOutboxEntity event = captor.getValue();
            assertThat(event.getAggregateType()).isEqualTo("ATTESTATION");
            assertThat(event.getEventType()).isEqualTo("ATTESTATION_RECORDED");
            assertThat(event.getAggregateId()).isEqualTo("2");
        }

        @Test
        void defaultsOutcomeToPendingWhenNull() {
            UUID tenantId = UUID.randomUUID();
            ArgumentCaptor<AttestationEntity> captor = ArgumentCaptor.forClass(AttestationEntity.class);

            AttestationEntity saved = new AttestationEntity();
            saved.setId(3L);
            saved.setTenantId(tenantId);
            saved.setActorId("admin");
            saved.setAttestationType(AttestationType.OTP);
            saved.setOutcome(AttestationOutcome.PENDING);
            saved.setConfidence(BigDecimal.ZERO);
            saved.setRecordedBy("admin");

            when(attestationRepository.save(captor.capture())).thenReturn(saved);

            attestationService.recordAttestation(tenantId, "admin", UUID.randomUUID(),
                    AttestationType.OTP, null, null, null, null);

            assertThat(captor.getValue().getOutcome()).isEqualTo(AttestationOutcome.PENDING);
            assertThat(captor.getValue().getConfidence()).isEqualTo(BigDecimal.ZERO);
        }

        @Test
        void defaultsEvidenceToEmptyJsonWhenNull() {
            UUID tenantId = UUID.randomUUID();
            ArgumentCaptor<AttestationEntity> captor = ArgumentCaptor.forClass(AttestationEntity.class);

            AttestationEntity saved = new AttestationEntity();
            saved.setId(4L);
            saved.setTenantId(tenantId);
            saved.setActorId("admin");
            saved.setAttestationType(AttestationType.SMARTCARD);
            saved.setOutcome(AttestationOutcome.PENDING);
            saved.setConfidence(BigDecimal.ZERO);
            saved.setRecordedBy("admin");

            when(attestationRepository.save(captor.capture())).thenReturn(saved);

            attestationService.recordAttestation(tenantId, "admin", UUID.randomUUID(),
                    AttestationType.SMARTCARD, null, null, null, null);

            assertThat(captor.getValue().getEvidence()).isEqualTo("{}");
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

            List<AttestationEntity> result = attestationService.listAttestations(tenantId);
            assertThat(result).hasSize(2);
        }
    }
}
