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
import zw.gov.mohcc.impilo.mentalhealth.persistence.entity.ReferralEntity;
import zw.gov.mohcc.impilo.mentalhealth.persistence.entity.RestraintEventEntity;
import zw.gov.mohcc.impilo.mentalhealth.persistence.repository.ReferralRepository;
import zw.gov.mohcc.impilo.mentalhealth.persistence.repository.RestraintEventRepository;

/**
 * The restraint-REDUCTION register (W13). Proves the mandatory de-escalation-attempted record
 * (value-or-reason — a restraint row can never be written without it) and that every event is
 * queued for review until reviewed together (partial review is refused).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RestraintEventServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID REFERRAL = UUID.fromString("00000000-0000-4000-8000-000000000005");

    @Mock private ReferralRepository referralRepository;
    @Mock private RestraintEventRepository repository;
    @Mock private OutboxEventRepository outboxRepository;

    private RestraintEventService service;

    @BeforeEach
    void setUp() {
        service = new RestraintEventService(referralRepository, repository, outboxRepository, new ObjectMapper());
        when(referralRepository.findByIdAndTenantId(REFERRAL, TENANT)).thenReturn(Optional.of(new ReferralEntity()));
        doAnswer(inv -> inv.getArgument(0)).when(repository).save(any());
    }

    @Test
    @DisplayName("recording a restraint event with no de-escalation-attempted record is refused")
    void recordWithoutDeEscalationRefused() {
        assertThatThrownBy(() -> service.record(TENANT, REFERRAL, "PHYSICAL", "assaultive", null, "nurse-C"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("recording with a bad restraint type is refused")
    void recordBadTypeRefused() {
        assertThatThrownBy(() -> service.record(TENANT, REFERRAL, "SOLITARY", "assaultive", "verbal de-escalation tried", "nurse-C"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("a correct record succeeds and is unreviewed")
    void recordSucceeds() {
        var e = service.record(TENANT, REFERRAL, "PHYSICAL", "assaultive",
                "verbal de-escalation attempted x2, PRN offered and declined", "nurse-C");

        assertThat(e.getDeEscalationAttempted()).isNotBlank();
        assertThat(e.getReviewedAt()).isNull();
    }

    @Test
    @DisplayName("a partial review (missing outcome) is refused")
    void partialReviewRefused() {
        RestraintEventEntity e = restraintEvent();
        when(repository.findByIdAndTenantId(e.getId(), TENANT)).thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.review(e.getId(), TENANT, "dr-x", null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("a full review succeeds")
    void fullReviewSucceeds() {
        RestraintEventEntity e = restraintEvent();
        when(repository.findByIdAndTenantId(e.getId(), TENANT)).thenReturn(Optional.of(e));

        var reviewed = service.review(e.getId(), TENANT, "dr-x", "appropriate, plan updated");

        assertThat(reviewed.getReviewedBy()).isEqualTo("dr-x");
        assertThat(reviewed.getReviewedAt()).isNotNull();
        assertThat(reviewed.getReviewOutcome()).isEqualTo("appropriate, plan updated");
    }

    @Test
    @DisplayName("reviewing an already-reviewed event is refused")
    void doubleReviewRefused() {
        RestraintEventEntity e = restraintEvent();
        e.setReviewedBy("dr-x");
        e.setReviewedAt(OffsetDateTime.now());
        e.setReviewOutcome("appropriate");
        when(repository.findByIdAndTenantId(e.getId(), TENANT)).thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.review(e.getId(), TENANT, "dr-y", "second opinion"))
                .isInstanceOf(ResponseStatusException.class);
    }

    private RestraintEventEntity restraintEvent() {
        RestraintEventEntity e = new RestraintEventEntity();
        e.setId(UUID.randomUUID());
        e.setTenantId(TENANT);
        e.setReferralId(REFERRAL);
        e.setRestraintType("PHYSICAL");
        e.setReason("assaultive");
        e.setDeEscalationAttempted("verbal de-escalation attempted");
        e.setInitiatedBy("nurse-C");
        e.setInitiatedAt(OffsetDateTime.now());
        e.setCreatedAt(OffsetDateTime.now());
        return e;
    }
}
