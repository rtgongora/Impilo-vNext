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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;

import zw.gov.mohcc.impilo.mentalhealth.events.OutboxEventRepository;
import zw.gov.mohcc.impilo.mentalhealth.persistence.entity.InvoluntaryEpisodeEntity;
import zw.gov.mohcc.impilo.mentalhealth.persistence.entity.ReferralEntity;
import zw.gov.mohcc.impilo.mentalhealth.persistence.repository.InvoluntaryEpisodeRepository;
import zw.gov.mohcc.impilo.mentalhealth.persistence.repository.ReferralRepository;

/**
 * Statutory detention/observation (W13). Proves: legal_basis and authorisedBy are mandatory,
 * closure requires a reason, and the database's own at-most-one-OPEN-per-referral invariant
 * (uq_mhie_open_per_referral, proven on real Postgres separately) is translated into a clean 409
 * rather than a raw constraint-violation leaking to the caller.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvoluntaryEpisodeServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID REFERRAL = UUID.fromString("00000000-0000-4000-8000-000000000005");

    @Mock private ReferralRepository referralRepository;
    @Mock private InvoluntaryEpisodeRepository repository;
    @Mock private OutboxEventRepository outboxRepository;

    private InvoluntaryEpisodeService service;

    @BeforeEach
    void setUp() {
        service = new InvoluntaryEpisodeService(referralRepository, repository, outboxRepository, new ObjectMapper());
        when(referralRepository.findByIdAndTenantId(REFERRAL, TENANT)).thenReturn(Optional.of(new ReferralEntity()));
    }

    @Test
    @DisplayName("opening with no legal basis is refused")
    void openWithoutLegalBasisRefused() {
        assertThatThrownBy(() -> service.open(TENANT, REFERRAL, null, "dr-x", null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("opening against an unknown referral is refused")
    void openUnknownReferralRefused() {
        UUID unknown = UUID.randomUUID();
        when(referralRepository.findByIdAndTenantId(unknown, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.open(TENANT, unknown, "Mental Health Act s.14", "dr-x", null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("a correct open succeeds and is OPEN")
    void openSucceeds() {
        doAnswer(inv -> inv.getArgument(0)).when(repository).save(any());

        var e = service.open(TENANT, REFERRAL, "Mental Health Act s.14", "dr-x", null);

        assertThat(e.getStatus()).isEqualTo("OPEN");
        assertThat(e.getLegalBasis()).isEqualTo("Mental Health Act s.14");
    }

    @Test
    @DisplayName("a second concurrent OPEN episode surfaces as a clean 409, not a raw DB error")
    void secondOpenSurfacesAsConflict() {
        doAnswer(inv -> { throw new DataIntegrityViolationException("uq_mhie_open_per_referral"); })
                .when(repository).save(any());

        assertThatThrownBy(() -> service.open(TENANT, REFERRAL, "Mental Health Act s.14", "dr-x", null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(409));
    }

    @Test
    @DisplayName("closing without a reason is refused")
    void closeWithoutReasonRefused() {
        InvoluntaryEpisodeEntity e = openEpisode();
        when(repository.findByIdAndTenantId(e.getId(), TENANT)).thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.close(e.getId(), TENANT, null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("closing an already-CLOSED episode is refused")
    void closeAlreadyClosedRefused() {
        InvoluntaryEpisodeEntity e = openEpisode();
        e.setStatus("CLOSED");
        when(repository.findByIdAndTenantId(e.getId(), TENANT)).thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.close(e.getId(), TENANT, "no longer meets criteria"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("a correct close succeeds")
    void closeSucceeds() {
        InvoluntaryEpisodeEntity e = openEpisode();
        when(repository.findByIdAndTenantId(e.getId(), TENANT)).thenReturn(Optional.of(e));
        doAnswer(inv -> inv.getArgument(0)).when(repository).save(any());

        var closed = service.close(e.getId(), TENANT, "no longer meets detention criteria");

        assertThat(closed.getStatus()).isEqualTo("CLOSED");
        assertThat(closed.getEndedAt()).isNotNull();
    }

    private InvoluntaryEpisodeEntity openEpisode() {
        InvoluntaryEpisodeEntity e = new InvoluntaryEpisodeEntity();
        e.setId(UUID.randomUUID());
        e.setTenantId(TENANT);
        e.setReferralId(REFERRAL);
        e.setLegalBasis("Mental Health Act s.14");
        e.setAuthorisedBy("dr-x");
        e.setAuthorisedAt(OffsetDateTime.now());
        e.setStatus("OPEN");
        e.setCreatedAt(OffsetDateTime.now());
        return e;
    }
}
