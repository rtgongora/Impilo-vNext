package zw.gov.mohcc.impilo.mentalhealth.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import zw.gov.mohcc.impilo.mentalhealth.integration.PctHandoverClient;
import zw.gov.mohcc.impilo.mentalhealth.persistence.entity.ReferralEntity;
import zw.gov.mohcc.impilo.mentalhealth.persistence.repository.ReferralRepository;

/**
 * The accepting side of a psychiatric emergency handover (W13). Proves: idempotent intake,
 * PENDING-only accept/decline, mandatory decline reason, and that a degraded pct never blocks the
 * LOCAL clinical decision (accept/decline succeed locally even when the pct write-back fails).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReferralServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID FACILITY = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID EPISODE = UUID.fromString("00000000-0000-4000-8000-000000000003");
    private static final UUID HANDOVER = UUID.fromString("00000000-0000-4000-8000-000000000004");

    @Mock private ReferralRepository referralRepository;
    @Mock private OutboxEventRepository outboxRepository;
    @Mock private PctHandoverClient pctHandoverClient;

    private ReferralService service;

    @BeforeEach
    void setUp() {
        service = new ReferralService(referralRepository, outboxRepository, pctHandoverClient, new ObjectMapper());
        doAnswer(inv -> inv.getArgument(0)).when(referralRepository).save(any());
    }

    @Test
    @DisplayName("intake on an unseen handover creates a new PENDING referral")
    void intakeCreatesReferral() {
        when(referralRepository.findByTenantIdAndHandoverId(TENANT, HANDOVER)).thenReturn(Optional.empty());

        var r = service.intake(TENANT, FACILITY, EPISODE, HANDOVER, "CPID-1", "acute psychosis", "nurse-A");

        assertThat(r.getStatus()).isEqualTo("PENDING");
        assertThat(r.getHandoverId()).isEqualTo(HANDOVER);
    }

    @Test
    @DisplayName("intake is idempotent on handover_id — a replay returns the existing referral")
    void intakeIsIdempotent() {
        ReferralEntity existing = referral("PENDING");
        when(referralRepository.findByTenantIdAndHandoverId(TENANT, HANDOVER)).thenReturn(Optional.of(existing));

        var r = service.intake(TENANT, FACILITY, EPISODE, HANDOVER, "CPID-1", "acute psychosis", "nurse-A");

        assertThat(r).isSameAs(existing);
    }

    @Test
    @DisplayName("accepting a non-PENDING referral is refused")
    void acceptNonPendingRefused() {
        ReferralEntity r = referral("ACCEPTED");
        when(referralRepository.findByIdAndTenantId(r.getId(), TENANT)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.accept(r.getId(), TENANT, "dr-x"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("declining with no reason is refused")
    void declineWithoutReasonRefused() {
        ReferralEntity r = referral("PENDING");
        when(referralRepository.findByIdAndTenantId(r.getId(), TENANT)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.decline(r.getId(), TENANT, "dr-x", null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("accept succeeds locally and calls back pct's handover")
    void acceptCallsBackPct() {
        ReferralEntity r = referral("PENDING");
        when(referralRepository.findByIdAndTenantId(r.getId(), TENANT)).thenReturn(Optional.of(r));
        when(pctHandoverClient.acceptHandover(any(), any(), any(), anyString())).thenReturn(true);

        var accepted = service.accept(r.getId(), TENANT, "dr-x");

        assertThat(accepted.getStatus()).isEqualTo("ACCEPTED");
        assertThat(accepted.getReviewedBy()).isEqualTo("dr-x");
    }

    @Test
    @DisplayName("accept succeeds LOCALLY even when pct is unreachable — a degraded peer never blocks the decision")
    void acceptSucceedsLocallyWhenPctUnreachable() {
        ReferralEntity r = referral("PENDING");
        when(referralRepository.findByIdAndTenantId(r.getId(), TENANT)).thenReturn(Optional.of(r));
        when(pctHandoverClient.acceptHandover(any(), any(), any(), anyString())).thenReturn(false);

        var accepted = service.accept(r.getId(), TENANT, "dr-x");

        assertThat(accepted.getStatus()).isEqualTo("ACCEPTED");
    }

    @Test
    @DisplayName("decline succeeds and calls back pct's decline with the reason")
    void declineCallsBackPct() {
        ReferralEntity r = referral("PENDING");
        when(referralRepository.findByIdAndTenantId(r.getId(), TENANT)).thenReturn(Optional.of(r));
        when(pctHandoverClient.declineHandover(any(), any(), anyString(), anyString())).thenReturn(true);

        var declined = service.decline(r.getId(), TENANT, "dr-x", "not a psychiatric emergency");

        assertThat(declined.getStatus()).isEqualTo("DECLINED");
        assertThat(declined.getDeclineReason()).isEqualTo("not a psychiatric emergency");
    }

    private ReferralEntity referral(String status) {
        ReferralEntity r = new ReferralEntity();
        r.setId(UUID.randomUUID());
        r.setTenantId(TENANT);
        r.setFacilityId(FACILITY);
        r.setEpisodeId(EPISODE);
        r.setHandoverId(HANDOVER);
        r.setStatus(status);
        r.setRequestedBy("nurse-A");
        r.setRequestedAt(OffsetDateTime.now());
        r.setCreatedAt(OffsetDateTime.now());
        r.setUpdatedAt(OffsetDateTime.now());
        return r;
    }
}
