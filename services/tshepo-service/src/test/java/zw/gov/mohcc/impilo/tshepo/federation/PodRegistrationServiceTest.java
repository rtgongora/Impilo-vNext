package zw.gov.mohcc.impilo.tshepo.federation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.tshepo.federation.core.*;
import zw.gov.mohcc.impilo.tshepo.federation.persistence.*;
import zw.gov.mohcc.impilo.tshepo.persistence.EventOutboxEntity;
import zw.gov.mohcc.impilo.tshepo.persistence.EventOutboxRepository;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Wave 21 — Pod Registration Service tests.
 *
 * Covers:
 * - Pod registration handshake (Phase 1)
 * - Authority scope enforcement (Phase 3)
 * - Pod revocation and reinstatement (Phase 4)
 * - Heartbeat lifecycle
 * - Outbox event publishing
 */
@DisplayName("Wave 21 — PodRegistrationService")
class PodRegistrationServiceTest {

    private PodRegistrationRepository podRepo;
    private EventOutboxRepository outboxRepo;
    private PodRevocationCacheService revocationCache;
    private PodRegistrationService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        podRepo = mock(PodRegistrationRepository.class);
        outboxRepo = mock(EventOutboxRepository.class);
        revocationCache = new PodRevocationCacheService();
        objectMapper = new ObjectMapper();
        service = new PodRegistrationService(podRepo, outboxRepo, revocationCache, objectMapper);
    }

    private PodRegistrationRequest validRequest() {
        return new PodRegistrationRequest(
                UUID.randomUUID(),
                "Facility-Pod-Harare-Central",
                "-----BEGIN CERTIFICATE-----\nMIIBxx...\n-----END CERTIFICATE-----",
                List.of("PATIENT_REGISTRY", "CLINICAL_CAPTURE"),
                "Harare",
                List.of(UUID.randomUUID()),
                List.of("PATIENT", "ENCOUNTER", "OBSERVATION"),
                72
        );
    }

    @Nested
    @DisplayName("Phase 1: Pod Registration Handshake")
    class RegistrationHandshake {

        @Test
        @DisplayName("Registers a new pod with valid request")
        void registersNewPod() {
            PodRegistrationRequest request = validRequest();
            when(podRepo.existsByPodId(request.podId())).thenReturn(false);
            when(podRepo.save(any())).thenAnswer(inv -> {
                PodRegistrationEntity e = inv.getArgument(0);
                e.setId(1L);
                if (e.getRegistrationId() == null) e.setRegistrationId(UUID.randomUUID());
                return e;
            });

            PodRegistrationEntity result = service.registerPod(request);

            assertThat(result.getPodId()).isEqualTo(request.podId());
            assertThat(result.getPodName()).isEqualTo("Facility-Pod-Harare-Central");
            assertThat(result.getStatus()).isEqualTo(PodStatus.ACTIVE);
            assertThat(result.isMergeAuthority()).isFalse();
            assertThat(result.getRevocationPriority()).isEqualTo("HIGH");
            assertThat(result.getHeartbeatIntervalSeconds()).isEqualTo(300);
            assertThat(result.getMaxOfflineHours()).isEqualTo(48); // capped from requested 72

            // Verify outbox event published
            verify(outboxRepo).save(argThat(e ->
                    "impilo.federation.pod.registered.v1".equals(e.getEventType())));
        }

        @Test
        @DisplayName("Rejects duplicate pod registration")
        void rejectsDuplicateRegistration() {
            PodRegistrationRequest request = validRequest();
            when(podRepo.existsByPodId(request.podId())).thenReturn(true);

            assertThatThrownBy(() -> service.registerPod(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already registered");
        }

        @Test
        @DisplayName("Rejects registration without certificate")
        void rejectsNoCertificate() {
            PodRegistrationRequest request = new PodRegistrationRequest(
                    UUID.randomUUID(), "test-pod", "", List.of(), "Test", List.of(),
                    List.of("PATIENT"), null);
            when(podRepo.existsByPodId(request.podId())).thenReturn(false);

            assertThatThrownBy(() -> service.registerPod(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("certificate");
        }

        @Test
        @DisplayName("Pods never receive merge authority")
        void podsNeverGetMergeAuthority() {
            PodRegistrationRequest request = validRequest();
            when(podRepo.existsByPodId(request.podId())).thenReturn(false);
            when(podRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PodRegistrationEntity result = service.registerPod(request);

            assertThat(result.isMergeAuthority()).isFalse();
        }

        @Test
        @DisplayName("Registration has 30-day validity period")
        void registrationHasValidityPeriod() {
            PodRegistrationRequest request = validRequest();
            when(podRepo.existsByPodId(request.podId())).thenReturn(false);
            when(podRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PodRegistrationEntity result = service.registerPod(request);

            assertThat(result.getIssuedAt()).isNotNull();
            assertThat(result.getExpiresAt()).isNotNull();
            // Verify ~30 days apart
            long daysValid = java.time.Duration.between(result.getIssuedAt(), result.getExpiresAt()).toDays();
            assertThat(daysValid).isEqualTo(30);
        }
    }

    @Nested
    @DisplayName("Phase 3: Authority Scope Enforcement")
    class AuthorityScope {

        @Test
        @DisplayName("Scopes requested data classes to allowed set")
        void scopesDataClasses() {
            PodRegistrationRequest request = new PodRegistrationRequest(
                    UUID.randomUUID(), "test-pod",
                    "-----BEGIN CERTIFICATE-----\ntest\n-----END CERTIFICATE-----",
                    List.of(), "Test", List.of(),
                    List.of("PATIENT", "ENCOUNTER", "BILLING", "CLASSIFIED_INTEL"),
                    null);
            when(podRepo.existsByPodId(request.podId())).thenReturn(false);
            when(podRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PodRegistrationEntity result = service.registerPod(request);

            // BILLING and CLASSIFIED_INTEL should be filtered out
            assertThat(result.getGrantedDataClasses())
                    .contains("PATIENT")
                    .contains("ENCOUNTER")
                    .doesNotContain("BILLING")
                    .doesNotContain("CLASSIFIED_INTEL");
        }

        @Test
        @DisplayName("Max offline hours capped at 48")
        void capsMaxOfflineHours() {
            PodRegistrationRequest request = new PodRegistrationRequest(
                    UUID.randomUUID(), "test-pod",
                    "-----BEGIN CERTIFICATE-----\ntest\n-----END CERTIFICATE-----",
                    List.of(), "Test", List.of(),
                    List.of("PATIENT"), 120); // requesting 120 hours
            when(podRepo.existsByPodId(request.podId())).thenReturn(false);
            when(podRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PodRegistrationEntity result = service.registerPod(request);

            assertThat(result.getMaxOfflineHours()).isEqualTo(48);
        }
    }

    @Nested
    @DisplayName("Phase 4: Pod Revocation")
    class PodRevocation {

        @Test
        @DisplayName("Revokes an active pod")
        void revokesActivePod() {
            UUID podId = UUID.randomUUID();
            PodRegistrationEntity pod = new PodRegistrationEntity();
            pod.setPodId(podId);
            pod.setPodName("test-pod");
            pod.setStatus(PodStatus.ACTIVE);

            when(podRepo.findByPodId(podId)).thenReturn(Optional.of(pod));
            when(podRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PodRegistrationEntity result = service.revokePod(podId, "admin", "SECURITY_BREACH");

            assertThat(result.getStatus()).isEqualTo(PodStatus.REVOKED);
            assertThat(result.getRevokedBy()).isEqualTo("admin");
            assertThat(result.getRevocationReason()).isEqualTo("SECURITY_BREACH");
            assertThat(result.getRevokedAt()).isNotNull();

            // Verify revocation cache updated
            assertThat(revocationCache.isRevoked(podId.toString())).isTrue();

            // Verify outbox event published
            verify(outboxRepo).save(argThat(e ->
                    "impilo.federation.pod.revoked.v1".equals(e.getEventType())));
        }

        @Test
        @DisplayName("Cannot revoke an already revoked pod")
        void cannotRevokeAlreadyRevoked() {
            UUID podId = UUID.randomUUID();
            PodRegistrationEntity pod = new PodRegistrationEntity();
            pod.setPodId(podId);
            pod.setStatus(PodStatus.REVOKED);

            when(podRepo.findByPodId(podId)).thenReturn(Optional.of(pod));

            assertThatThrownBy(() -> service.revokePod(podId, "admin", "REASON"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already revoked");
        }

        @Test
        @DisplayName("Reinstates a revoked pod")
        void reinstatesRevokedPod() {
            UUID podId = UUID.randomUUID();
            PodRegistrationEntity pod = new PodRegistrationEntity();
            pod.setPodId(podId);
            pod.setPodName("test-pod");
            pod.setStatus(PodStatus.REVOKED);
            pod.setRevokedAt(Instant.now());
            pod.setRevokedBy("admin");

            revocationCache.markRevoked(podId.toString());

            when(podRepo.findByPodId(podId)).thenReturn(Optional.of(pod));
            when(podRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PodRegistrationEntity result = service.reinstatePod(podId, "senior-admin");

            assertThat(result.getStatus()).isEqualTo(PodStatus.ACTIVE);
            assertThat(result.getRevokedAt()).isNull();
            assertThat(result.getRevokedBy()).isNull();

            // Verify revocation cache cleared
            assertThat(revocationCache.isRevoked(podId.toString())).isFalse();

            // Verify outbox event published
            verify(outboxRepo).save(argThat(e ->
                    "impilo.federation.pod.reinstated.v1".equals(e.getEventType())));
        }
    }

    @Nested
    @DisplayName("Heartbeat")
    class Heartbeat {

        @Test
        @DisplayName("Updates heartbeat for active pod")
        void updatesHeartbeatForActivePod() {
            UUID podId = UUID.randomUUID();
            PodRegistrationEntity pod = new PodRegistrationEntity();
            pod.setPodId(podId);
            pod.setStatus(PodStatus.ACTIVE);

            when(podRepo.findByPodId(podId)).thenReturn(Optional.of(pod));
            when(podRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Optional<PodRegistrationEntity> result = service.heartbeat(podId);

            assertThat(result).isPresent();
            assertThat(result.get().getLastHeartbeatAt()).isNotNull();
        }

        @Test
        @DisplayName("Rejects heartbeat from revoked pod")
        void rejectsHeartbeatFromRevokedPod() {
            UUID podId = UUID.randomUUID();
            PodRegistrationEntity pod = new PodRegistrationEntity();
            pod.setPodId(podId);
            pod.setStatus(PodStatus.REVOKED);

            when(podRepo.findByPodId(podId)).thenReturn(Optional.of(pod));

            Optional<PodRegistrationEntity> result = service.heartbeat(podId);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Registration Renewal")
    class Renewal {

        @Test
        @DisplayName("Renews active pod registration")
        void renewsActiveRegistration() {
            UUID podId = UUID.randomUUID();
            PodRegistrationEntity pod = new PodRegistrationEntity();
            pod.setPodId(podId);
            pod.setStatus(PodStatus.ACTIVE);
            pod.setExpiresAt(Instant.now().minusSeconds(100));

            when(podRepo.findByPodId(podId)).thenReturn(Optional.of(pod));
            when(podRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PodRegistrationEntity result = service.renewRegistration(podId);

            assertThat(result.getExpiresAt()).isAfter(Instant.now());
        }

        @Test
        @DisplayName("Cannot renew non-active pod")
        void cannotRenewNonActivePod() {
            UUID podId = UUID.randomUUID();
            PodRegistrationEntity pod = new PodRegistrationEntity();
            pod.setPodId(podId);
            pod.setStatus(PodStatus.REVOKED);

            when(podRepo.findByPodId(podId)).thenReturn(Optional.of(pod));

            assertThatThrownBy(() -> service.renewRegistration(podId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Only active");
        }
    }
}
