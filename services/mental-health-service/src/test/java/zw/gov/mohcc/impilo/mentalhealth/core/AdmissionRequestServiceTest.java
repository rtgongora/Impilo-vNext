package zw.gov.mohcc.impilo.mentalhealth.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;

import zw.gov.mohcc.impilo.mentalhealth.events.OutboxEventRepository;
import zw.gov.mohcc.impilo.mentalhealth.persistence.entity.AdmissionRequestEntity;
import zw.gov.mohcc.impilo.mentalhealth.persistence.entity.ReferralEntity;
import zw.gov.mohcc.impilo.mentalhealth.persistence.repository.AdmissionRequestRepository;
import zw.gov.mohcc.impilo.mentalhealth.persistence.repository.ReferralRepository;

/**
 * Raises admission to PCT; never decides it (W13, CC-2). Proves: raise creates a RAISED request
 * with no pct_admission_id, and only {@link AdmissionRequestService#acknowledge} — called back
 * once PCT's OWN admission-handshake has recorded a decision — ever sets it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdmissionRequestServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID REFERRAL = UUID.fromString("00000000-0000-4000-8000-000000000005");

    @Mock private ReferralRepository referralRepository;
    @Mock private AdmissionRequestRepository repository;
    @Mock private OutboxEventRepository outboxRepository;

    private AdmissionRequestService service;

    @BeforeEach
    void setUp() {
        service = new AdmissionRequestService(referralRepository, repository, outboxRepository, new ObjectMapper());
        when(referralRepository.findByIdAndTenantId(REFERRAL, TENANT)).thenReturn(Optional.of(new ReferralEntity()));
        doAnswer(inv -> inv.getArgument(0)).when(repository).save(any());
    }

    @Test
    @DisplayName("raising with no reason is refused")
    void raiseWithoutReasonRefused() {
        assertThatThrownBy(() -> service.raise(TENANT, REFERRAL, null, "dr-x", null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("a correct raise is RAISED with no pct_admission_id — it never decides")
    void raiseNeverDecides() {
        var r = service.raise(TENANT, REFERRAL, "ongoing risk requires inpatient admission", "dr-x", null);

        assertThat(r.getStatus()).isEqualTo("RAISED");
        assertThat(r.getPctAdmissionId()).isNull();
    }

    @Test
    @DisplayName("acknowledging without a pct_admission_id is refused")
    void acknowledgeWithoutPctIdRefused() {
        AdmissionRequestEntity r = admissionRequest();
        when(repository.findByIdAndTenantId(r.getId(), TENANT)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.acknowledge(r.getId(), TENANT, null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("acknowledging with pct's own admission id correlates but does not fabricate a decision")
    void acknowledgeCorrelates() {
        AdmissionRequestEntity r = admissionRequest();
        when(repository.findByIdAndTenantId(r.getId(), TENANT)).thenReturn(Optional.of(r));

        var ack = service.acknowledge(r.getId(), TENANT, "ADM-PCT-001");

        assertThat(ack.getStatus()).isEqualTo("ACKNOWLEDGED");
        assertThat(ack.getPctAdmissionId()).isEqualTo("ADM-PCT-001");
    }

    private AdmissionRequestEntity admissionRequest() {
        AdmissionRequestEntity r = new AdmissionRequestEntity();
        r.setId(UUID.randomUUID());
        r.setTenantId(TENANT);
        r.setReferralId(REFERRAL);
        r.setReason("ongoing risk requires inpatient admission");
        r.setRequestedBy("dr-x");
        r.setRequestedAt(OffsetDateTime.now());
        r.setStatus("RAISED");
        r.setCreatedAt(OffsetDateTime.now());
        return r;
    }
}
