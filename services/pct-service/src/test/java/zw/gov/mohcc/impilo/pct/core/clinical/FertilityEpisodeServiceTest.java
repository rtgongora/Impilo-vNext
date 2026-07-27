package zw.gov.mohcc.impilo.pct.core.clinical;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.pct.persistence.entity.FertilityEpisodeEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.FertilityEpisodeRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Fertility care.
 *
 * <p>The two properties under test are the ones a well-meaning caller is most likely to cross: a
 * partner's record must not be linked without the partner's consent, and a finding must not be
 * recorded against an assessment that did not happen.
 */
class FertilityEpisodeServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");

    private FertilityEpisodeRepository episodes;
    private FertilityEpisodeService service;

    @BeforeEach
    void setUp() {
        episodes = mock(FertilityEpisodeRepository.class);
        service = new FertilityEpisodeService(episodes);
        when(episodes.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(episodes.findByTenantIdAndClientOfflineId(any(), any())).thenReturn(Optional.empty());
    }

    private static FertilityEpisodeEntity episode() {
        FertilityEpisodeEntity e = new FertilityEpisodeEntity();
        e.setTenantId(TENANT);
        e.setSubjectCpid("CPID-F");
        e.setStatus("ACTIVE");
        e.setOpenedOn(java.time.LocalDate.of(2026, 1, 1));
        e.setRecordedBy("test");
        return e;
    }

    @Test
    @DisplayName("a partner cannot be linked without recorded consent")
    void partnerLinkageNeedsConsent() {
        var e = episode();
        e.setPartnerCpid("PARTNER-1");

        assertThatThrownBy(() -> service.start(e))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attending together is not consent");
    }

    @Test
    @DisplayName("a partner with recorded consent is linked")
    void partnerLinkageWithConsentIsAccepted() {
        var e = episode();
        e.setPartnerCpid("PARTNER-1");
        e.setPartnerLinkageConsent(FertilityEpisodeEntity.CONSENT_GIVEN);

        assertThat(service.start(e).getFertilityEpisodeId()).isNotNull();
    }

    @Test
    @DisplayName("declined consent is a recorded answer, and the episode still opens without the link")
    void declinedConsentIsRecordedNotBlocking() {
        var e = episode();
        e.setPartnerCpid("PARTNER-1");
        e.setPartnerLinkageConsent(FertilityEpisodeEntity.CONSENT_DECLINED);

        assertThat(service.start(e).getPartnerLinkageConsent())
                .isEqualTo(FertilityEpisodeEntity.CONSENT_DECLINED);
    }

    @Test
    @DisplayName("male-factor findings cannot be recorded against no assessment")
    void maleFindingsNeedAnAssessment() {
        var e = episode();
        e.setMaleFactorFindings("Low count");

        assertThatThrownBy(() -> service.start(e))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("a claim that an assessment was made");
    }

    @Test
    @DisplayName("male-factor findings with an assessment are accepted")
    void maleFindingsWithAssessmentAreAccepted() {
        var e = episode();
        e.setMaleFactorAssessed(FertilityEpisodeEntity.ASSESSED);
        e.setMaleFactorFindings("Normal semen analysis");

        assertThat(service.start(e).getFertilityEpisodeId()).isNotNull();
    }

    @Test
    @DisplayName("the episode is anchored on whoever presented, not on a maternal column")
    void anyoneCanBeTheSubject() {
        // A man presenting alone for a fertility concern is a first-class subject, not a partner
        // hanging off a woman's record.
        var e = episode();
        e.setSubjectCpid("CPID-MALE");
        e.setMaleFactorAssessed(FertilityEpisodeEntity.ASSESSED);

        assertThat(service.start(e).getSubjectCpid()).isEqualTo("CPID-MALE");
    }
}
