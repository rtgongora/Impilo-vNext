package zw.gov.mohcc.impilo.pct.core.clinical;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.pct.persistence.entity.ContraceptiveAdministrationEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ContraceptiveEpisodeEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.ContraceptiveAdministrationRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.ContraceptiveEpisodeRepository;
import zw.gov.mohcc.impilo.reproductive.contraception.CoverageStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Contraceptive episodes, and the one question the record exists to answer honestly: is she
 * protected today.
 *
 * <p>The tests are built around the way that question goes wrong. It is not usually answered
 * "no" when the answer is yes — it is answered "yes" from a record that cannot actually tell,
 * because a start date is present and nobody asked what happened since.
 */
class ContraceptiveEpisodeServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final String CPID = "CPID-TEST-W";

    private ContraceptiveEpisodeRepository episodes;
    private ContraceptiveAdministrationRepository administrations;
    private ContraceptiveEpisodeService service;

    @BeforeEach
    void setUp() {
        episodes = mock(ContraceptiveEpisodeRepository.class);
        administrations = mock(ContraceptiveAdministrationRepository.class);
        service = new ContraceptiveEpisodeService(episodes, administrations,
                ReproductiveConfidentialityFixtures.unresolvedPolicy(),
                new ConfidentialRecordGuard());
        when(episodes.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(administrations.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(administrations.findForEpisode(any())).thenReturn(List.of());
        when(episodes.findCurrentForMethod(any(), any(), any())).thenReturn(Optional.empty());
        when(episodes.findByTenantIdAndClientOfflineId(any(), any())).thenReturn(Optional.empty());
    }

    private static ContraceptiveEpisodeEntity episode(String methodCode, LocalDate startedOn) {
        ContraceptiveEpisodeEntity e = new ContraceptiveEpisodeEntity();
        e.setContraceptiveEpisodeId(UUID.randomUUID());
        e.setTenantId(TENANT);
        e.setSubjectCpid(CPID);
        e.setMethodCode(methodCode);
        e.setStatus(ContraceptiveEpisodeEntity.STATUS_ACTIVE);
        e.setStartedOn(startedOn);
        e.setPregnancyCertainty("ESTABLISHED");
        e.setRecordedBy("test");
        return e;
    }

    private static ContraceptiveAdministrationEntity dose(UUID episodeId, LocalDate on) {
        ContraceptiveAdministrationEntity a = new ContraceptiveAdministrationEntity();
        a.setAdministrationId(UUID.randomUUID());
        a.setContraceptiveEpisodeId(episodeId);
        a.setTenantId(TENANT);
        a.setSubjectCpid(CPID);
        a.setAdministrationType(ContraceptiveAdministrationEntity.TYPE_INJECTION);
        a.setAdministeredOn(on);
        a.setRecordedBy("test");
        return a;
    }

    // ---------------------------------------------------------------- coverage honesty

    @Test
    @DisplayName("an injectable with no dose recorded is UNKNOWN, never covered from the start date")
    void aScheduledMethodWithNoDoseIsNotCovered() {
        var e = episode("INJECTABLE_DMPA_IM", LocalDate.of(2026, 1, 10));

        var coverage = service.coverageFor(e, LocalDate.of(2026, 7, 26));

        // Six months after starting, with no injection on file. Falling back to the start date
        // would report her covered because she once began the method.
        assertThat(coverage.status()).isEqualTo(CoverageStatus.UNKNOWN);
        assertThat(coverage.coverageUnknownWhy())
                .contains("No dose has been recorded")
                .contains("ask when she last had it");
    }

    @Test
    @DisplayName("an injectable four months past its dose is LAPSED, not covered")
    void aLateInjectableLapses() {
        var e = episode("INJECTABLE_DMPA_IM", LocalDate.of(2026, 1, 10));
        when(administrations.findForEpisode(eq(e.getContraceptiveEpisodeId())))
                .thenReturn(List.of(dose(e.getContraceptiveEpisodeId(), LocalDate.of(2026, 3, 1))));

        var coverage = service.coverageFor(e, LocalDate.of(2026, 7, 26));

        assertThat(coverage.status()).isEqualTo(CoverageStatus.LAPSED);
        assertThat(coverage.daysRemaining()).isNegative();
        assertThat(coverage.coverageUnknownWhy()).isNull();
    }

    @Test
    @DisplayName("an injectable inside its grace window is late but still protecting her")
    void aRecentlyLateInjectableIsStillProtecting() {
        var e = episode("INJECTABLE_DMPA_IM", LocalDate.of(2026, 1, 10));
        when(administrations.findForEpisode(eq(e.getContraceptiveEpisodeId())))
                .thenReturn(List.of(dose(e.getContraceptiveEpisodeId(), LocalDate.of(2026, 4, 20))));

        // 97 days later: past the 91-day interval, inside the 28-day late window. The distinction
        // is the difference between telling her she needs backup and telling her she does not.
        var coverage = service.coverageFor(e, LocalDate.of(2026, 7, 26));

        assertThat(coverage.status()).isEqualTo(CoverageStatus.LATE_WITHIN_GRACE);
    }

    @Test
    @DisplayName("condoms are NOT_APPLICABLE, never COVERED — the record cannot see whether they were used")
    void condomsAreNotAssertedAsCoverage() {
        var coverage = service.coverageFor(episode("MALE_CONDOM", LocalDate.of(2026, 1, 10)),
                LocalDate.of(2026, 7, 26));

        assertThat(coverage.status()).isEqualTo(CoverageStatus.NOT_APPLICABLE);
    }

    @Test
    @DisplayName("an unrecognised method code is UNKNOWN with a reason, not treated as no method")
    void anUnrecognisedMethodIsUnknownNotAbsent() {
        var coverage = service.coverageFor(episode("SOME_FUTURE_METHOD", LocalDate.of(2026, 1, 10)),
                LocalDate.of(2026, 7, 26));

        assertThat(coverage.status()).isEqualTo(CoverageStatus.UNKNOWN);
        assertThat(coverage.coverageUnknownWhy()).contains("Do not read this as protected");
    }

    @Test
    @DisplayName("every UNKNOWN carries a reason a clinician can act on")
    void unknownAlwaysExplainsItself() {
        for (String method : List.of("INJECTABLE_DMPA_IM", "SOME_FUTURE_METHOD",
                "LACTATIONAL_AMENORRHOEA", "OTHER")) {
            var coverage = service.coverageFor(episode(method, LocalDate.of(2026, 1, 10)),
                    LocalDate.of(2026, 7, 26));
            if (coverage.status() == CoverageStatus.UNKNOWN) {
                assertThat(coverage.coverageUnknownWhy())
                        .as("%s reports UNKNOWN with no explanation, which renders as a shrug", method)
                        .isNotBlank();
            }
        }
    }

    @Test
    @DisplayName("an implant inside its five years is covered, and past them is expired")
    void anExpiringMethodExpires() {
        var e = episode("IMPLANT_LEVONORGESTREL_2ROD", LocalDate.of(2021, 1, 10));

        assertThat(service.coverageFor(e, LocalDate.of(2025, 1, 10)).status())
                .isEqualTo(CoverageStatus.COVERED);
        assertThat(service.coverageFor(e, LocalDate.of(2026, 7, 26)).status())
                .isEqualTo(CoverageStatus.EXPIRED);
    }

    // ---------------------------------------------------------------- starting and stopping

    @Test
    @DisplayName("dual method use is allowed — starting condoms alongside an implant is not a duplicate")
    void dualMethodUseIsAllowed() {
        when(episodes.findCurrentForMethod(TENANT, CPID, "MALE_CONDOM")).thenReturn(Optional.empty());

        var saved = service.start(episode("MALE_CONDOM", LocalDate.of(2026, 7, 26)));

        assertThat(saved.getMethodCode()).isEqualTo("MALE_CONDOM");
    }

    @Test
    @DisplayName("starting a method she is already on raises a reconcilable conflict, not a 500")
    void startingTheSameMethodTwiceIsAReconcilableConflict() {
        var existing = episode("IMPLANT_ETONOGESTREL_1ROD", LocalDate.of(2026, 1, 10));
        when(episodes.findCurrentForMethod(TENANT, CPID, "IMPLANT_ETONOGESTREL_1ROD"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() ->
                service.start(episode("IMPLANT_ETONOGESTREL_1ROD", LocalDate.of(2026, 7, 26))))
                .isInstanceOf(ContraceptiveEpisodeService.DuplicateContraceptiveEpisodeException.class)
                .hasMessageContaining("already recorded as using")
                .extracting(ex -> ((ContraceptiveEpisodeService.DuplicateContraceptiveEpisodeException) ex)
                        .getExistingEpisodeId())
                .isEqualTo(existing.getContraceptiveEpisodeId());
    }

    @Test
    @DisplayName("a replayed offline packet returns the existing episode instead of a second one")
    void aReplayedOfflinePacketIsIdempotent() {
        var existing = episode("COMBINED_ORAL_PILL", LocalDate.of(2026, 7, 20));
        existing.setClientOfflineId("device-a-1");
        when(episodes.findByTenantIdAndClientOfflineId(TENANT, "device-a-1"))
                .thenReturn(Optional.of(existing));

        var incoming = episode("COMBINED_ORAL_PILL", LocalDate.of(2026, 7, 20));
        incoming.setClientOfflineId("device-a-1");

        assertThat(service.start(incoming).getContraceptiveEpisodeId())
                .isEqualTo(existing.getContraceptiveEpisodeId());
    }

    @Test
    @DisplayName("starting an implant stamps its removal-due date without being told")
    void startingALarcStampsWhenItRunsOut() {
        var saved = service.start(episode("IMPLANT_ETONOGESTREL_1ROD", LocalDate.of(2026, 7, 26)));

        // Three years for the one-rod etonogestrel implant. A device with no removal date is a
        // device nobody will call her back for.
        assertThat(saved.getExpectedEndOn()).isEqualTo(LocalDate.of(2029, 7, 26));
    }

    @Test
    @DisplayName("a method cannot be recorded as stopped without a reason")
    void stoppingRequiresAReason() {
        var existing = episode("INJECTABLE_DMPA_IM", LocalDate.of(2026, 1, 10));
        when(episodes.findById(existing.getContraceptiveEpisodeId()))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.discontinue(TENANT,
                existing.getContraceptiveEpisodeId(), LocalDate.of(2026, 7, 26), "  ", null, "test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("without a reason");
    }

    // ---------------------------------------------------------------- the retained-rod case

    @Test
    @DisplayName("a complete removal ends the episode")
    void aCompleteRemovalEndsTheEpisode() {
        var e = episode("IMPLANT_LEVONORGESTREL_2ROD", LocalDate.of(2022, 1, 10));
        when(episodes.findById(e.getContraceptiveEpisodeId())).thenReturn(Optional.of(e));

        var removal = new ContraceptiveAdministrationEntity();
        removal.setContraceptiveEpisodeId(e.getContraceptiveEpisodeId());
        removal.setTenantId(TENANT);
        removal.setSubjectCpid(CPID);
        removal.setAdministrationType(ContraceptiveAdministrationEntity.TYPE_REMOVAL);
        removal.setRemovalCompleteness(ContraceptiveAdministrationEntity.REMOVAL_COMPLETE);
        removal.setUnitsExpected((short) 2);
        removal.setUnitsRemoved((short) 2);
        removal.setAdministeredOn(LocalDate.of(2026, 7, 26));
        removal.setRecordedBy("test");

        service.record(removal);

        assertThat(e.getStatus()).isEqualTo(ContraceptiveEpisodeEntity.STATUS_COMPLETED);
        assertThat(e.getEndedOn()).isEqualTo(LocalDate.of(2026, 7, 26));
    }

    @Test
    @DisplayName("a partial removal does NOT end the episode — one rod is still in her arm")
    void aPartialRemovalLeavesTheEpisodeOpen() {
        var e = episode("IMPLANT_LEVONORGESTREL_2ROD", LocalDate.of(2022, 1, 10));
        when(episodes.findById(e.getContraceptiveEpisodeId())).thenReturn(Optional.of(e));

        var removal = new ContraceptiveAdministrationEntity();
        removal.setContraceptiveEpisodeId(e.getContraceptiveEpisodeId());
        removal.setTenantId(TENANT);
        removal.setSubjectCpid(CPID);
        removal.setAdministrationType(ContraceptiveAdministrationEntity.TYPE_REMOVAL);
        removal.setRemovalCompleteness(ContraceptiveAdministrationEntity.REMOVAL_PARTIAL);
        removal.setRetainedFragment(Boolean.TRUE);
        removal.setRetainedFragmentDetail("One rod palpable, referred for removal");
        removal.setUnitsExpected((short) 2);
        removal.setUnitsRemoved((short) 1);
        removal.setAdministeredOn(LocalDate.of(2026, 7, 26));
        removal.setRecordedBy("test");

        service.record(removal);

        // If this closed the episode she would be counted as having no method and offered one on
        // top of the rod still in her arm.
        assertThat(e.getStatus()).isEqualTo(ContraceptiveEpisodeEntity.STATUS_ACTIVE);
        assertThat(e.getEndedOn()).isNull();
        assertThat(removal.endsProtection()).isFalse();
    }

    @Test
    @DisplayName("a failed removal does not end the episode either")
    void aFailedRemovalLeavesTheEpisodeOpen() {
        var e = episode("IMPLANT_ETONOGESTREL_1ROD", LocalDate.of(2024, 1, 10));
        when(episodes.findById(e.getContraceptiveEpisodeId())).thenReturn(Optional.of(e));

        var removal = new ContraceptiveAdministrationEntity();
        removal.setContraceptiveEpisodeId(e.getContraceptiveEpisodeId());
        removal.setTenantId(TENANT);
        removal.setSubjectCpid(CPID);
        removal.setAdministrationType(ContraceptiveAdministrationEntity.TYPE_REMOVAL);
        removal.setRemovalCompleteness(ContraceptiveAdministrationEntity.REMOVAL_FAILED);
        removal.setRetainedFragment(Boolean.TRUE);
        removal.setRetainedFragmentDetail("Not palpable, imaging requested");
        removal.setAdministeredOn(LocalDate.of(2026, 7, 26));
        removal.setRecordedBy("test");

        service.record(removal);

        assertThat(e.getStatus()).isEqualTo(ContraceptiveEpisodeEntity.STATUS_ACTIVE);
    }

    @Test
    @DisplayName("a removal is not counted as the last dose when computing coverage")
    void aRemovalIsNotADose() {
        var e = episode("INJECTABLE_DMPA_IM", LocalDate.of(2026, 1, 10));
        var acts = new ArrayList<ContraceptiveAdministrationEntity>();
        acts.add(dose(e.getContraceptiveEpisodeId(), LocalDate.of(2026, 3, 1)));
        var removal = new ContraceptiveAdministrationEntity();
        removal.setContraceptiveEpisodeId(e.getContraceptiveEpisodeId());
        removal.setAdministrationType(ContraceptiveAdministrationEntity.TYPE_REMOVAL);
        removal.setAdministeredOn(LocalDate.of(2026, 7, 20));
        acts.add(removal);
        when(administrations.findForEpisode(e.getContraceptiveEpisodeId())).thenReturn(acts);

        var coverage = service.coverageFor(e, LocalDate.of(2026, 7, 26));

        // The March injection is the last dose. Treating the July removal as one would report a
        // woman whose implant came out as freshly protected.
        assertThat(coverage.lastAdministeredOn()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(coverage.status()).isEqualTo(CoverageStatus.LAPSED);
    }
}
