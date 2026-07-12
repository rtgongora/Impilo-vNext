package zw.gov.mohcc.impilo.ia.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
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
class ContactVerificationServiceTest {

    @Mock private AttestationRepository attestationRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private ContactVerificationService service;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ContactVerificationService(
                attestationRepository, outboxRepository, new ObjectMapper());
        when(attestationRepository.save(any(AttestationEntity.class))).thenAnswer(inv -> {
            AttestationEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(1L);
            return e;
        });
        when(attestationRepository.findByTenantIdAndActorId(any(), any())).thenReturn(List.of());
    }

    private AttestationEntity captureSaved() {
        ArgumentCaptor<AttestationEntity> captor = ArgumentCaptor.forClass(AttestationEntity.class);
        verify(attestationRepository).save(captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("Record CONTACT_VERIFIED — verifier seam, R1 rung")
    class Record {

        @Test
        void recordsVerifiedAttestationWithChannelEvidence() {
            service.recordContactVerified(tenantId, "acct-1", "experience-bff",
                    UUID.randomUUID(), "PHONE", "+263*******67", "OTP");

            AttestationEntity saved = captureSaved();
            assertThat(saved.getAttestationType()).isEqualTo(AttestationType.CONTACT_VERIFIED);
            assertThat(saved.getOutcome()).isEqualTo(AttestationOutcome.VERIFIED);
            assertThat(saved.getConfidence()).isEqualByComparingTo(BigDecimal.ONE);
            assertThat(saved.getActorId()).isEqualTo("acct-1");
            assertThat(saved.getRecordedBy()).isEqualTo("experience-bff");
            assertThat(saved.getEvidence()).contains("\"channel\":\"PHONE\"")
                    .contains("\"maskedValue\":\"+263*******67\"")
                    .contains("\"method\":\"OTP\"")
                    .contains("verifiedAt");
        }

        @Test
        void publishesOutboxEvent() {
            service.recordContactVerified(tenantId, "acct-1", "experience-bff",
                    UUID.randomUUID(), "EMAIL", "j***@example.org", null);

            ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
            verify(outboxRepository).save(captor.capture());
            assertThat(captor.getValue().getEventType()).isEqualTo("CONTACT_VERIFIED_RECORDED");
            assertThat(captor.getValue().getPayload()).contains("\"channel\":\"EMAIL\"");
        }

        @Test
        void defaultsMethodToOtp() {
            service.recordContactVerified(tenantId, "acct-1", "experience-bff",
                    UUID.randomUUID(), "PHONE", "+263*******67", null);
            assertThat(captureSaved().getEvidence()).contains("\"method\":\"OTP\"");
        }

        @Test
        void masksRawLookingValuesDefensively() {
            service.recordContactVerified(tenantId, "acct-1", "experience-bff",
                    UUID.randomUUID(), "PHONE", "+263771234567", "OTP");
            AttestationEntity saved = captureSaved();
            assertThat(saved.getEvidence()).doesNotContain("+263771234567");
            assertThat(saved.getEvidence()).contains("*");
        }

        @Test
        void idempotentPerAccountChannelAndMaskedValue() {
            AttestationEntity existing = new AttestationEntity();
            existing.setId(42L);
            existing.setTenantId(tenantId);
            existing.setActorId("acct-1");
            existing.setAttestationType(AttestationType.CONTACT_VERIFIED);
            existing.setOutcome(AttestationOutcome.VERIFIED);
            existing.setEvidence("{\"channel\":\"PHONE\",\"maskedValue\":\"+263*******67\",\"method\":\"OTP\"}");
            when(attestationRepository.findByTenantIdAndActorId(tenantId, "acct-1"))
                    .thenReturn(List.of(existing));

            AttestationEntity result = service.recordContactVerified(tenantId, "acct-1",
                    "experience-bff", UUID.randomUUID(), "PHONE", "+263*******67", "OTP");

            assertThat(result.getId()).isEqualTo(42L);
            verify(attestationRepository, never()).save(any());
            verify(outboxRepository, never()).save(any());
        }

        @Test
        void rejectsUnknownChannel() {
            assertThatThrownBy(() -> service.recordContactVerified(tenantId, "acct-1",
                    "experience-bff", UUID.randomUUID(), "FAX", "+263*******67", "OTP"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsBlankAccountAndValue() {
            assertThatThrownBy(() -> service.recordContactVerified(tenantId, " ",
                    "experience-bff", UUID.randomUUID(), "PHONE", "+263*******67", "OTP"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.recordContactVerified(tenantId, "acct-1",
                    "experience-bff", UUID.randomUUID(), "PHONE", " ", "OTP"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Read contact-verification status")
    class ReadStatus {

        @Test
        void reportsVerifiedChannels() {
            AttestationEntity verified = new AttestationEntity();
            verified.setId(1L);
            verified.setTenantId(tenantId);
            verified.setActorId("acct-1");
            verified.setAttestationType(AttestationType.CONTACT_VERIFIED);
            verified.setOutcome(AttestationOutcome.VERIFIED);
            verified.setEvidence("{\"channel\":\"PHONE\",\"maskedValue\":\"+263*******67\","
                    + "\"method\":\"OTP\",\"verifiedAt\":\"2026-07-12T10:00:00Z\"}");
            when(attestationRepository.findByTenantIdAndActorId(tenantId, "acct-1"))
                    .thenReturn(List.of(verified));

            var view = service.getContactVerification(tenantId, "acct-1");
            assertThat(view.contactVerified()).isTrue();
            assertThat(view.channels()).hasSize(1);
            assertThat(view.channels().get(0).channel()).isEqualTo("PHONE");
            assertThat(view.channels().get(0).maskedValue()).isEqualTo("+263*******67");
        }

        @Test
        void ignoresNonContactAndNonVerifiedAttestations() {
            AttestationEntity pendingContact = new AttestationEntity();
            pendingContact.setAttestationType(AttestationType.CONTACT_VERIFIED);
            pendingContact.setOutcome(AttestationOutcome.PENDING);
            pendingContact.setEvidence("{\"channel\":\"PHONE\"}");

            AttestationEntity device = new AttestationEntity();
            device.setAttestationType(AttestationType.DEVICE_BINDING);
            device.setOutcome(AttestationOutcome.VERIFIED);
            device.setEvidence("{}");

            when(attestationRepository.findByTenantIdAndActorId(tenantId, "acct-1"))
                    .thenReturn(List.of(pendingContact, device));

            var view = service.getContactVerification(tenantId, "acct-1");
            assertThat(view.contactVerified()).isFalse();
            assertThat(view.channels()).isEmpty();
        }
    }
}
