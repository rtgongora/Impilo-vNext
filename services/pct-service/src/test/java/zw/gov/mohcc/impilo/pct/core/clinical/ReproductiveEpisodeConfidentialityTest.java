package zw.gov.mohcc.impilo.pct.core.clinical;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.pct.persistence.entity.ContraceptiveAdministrationEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ContraceptiveEpisodeEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.PregnancyEpisodeEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.ContraceptiveAdministrationRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.ContraceptiveEpisodeRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.PregnancyDatingRevisionRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.PregnancyEpisodeRepository;
import zw.gov.mohcc.impilo.reproductive.dating.DatingBasis;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.visibility.VisibilityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static zw.gov.mohcc.impilo.pct.core.clinical.ReproductiveConfidentialityFixtures.SRH;
import static zw.gov.mohcc.impilo.pct.core.clinical.ReproductiveConfidentialityFixtures.policy;
import static zw.gov.mohcc.impilo.pct.core.clinical.ReproductiveConfidentialityFixtures.profileGranting;
import static zw.gov.mohcc.impilo.pct.core.clinical.ReproductiveConfidentialityFixtures.unresolvedPolicy;

/**
 * Contraceptive and pregnancy episodes carry the confidentiality stamp, and their reads honour it.
 *
 * <p>These two records were named in the decision table of
 * {@code docs/clinical-governance/rmnp/srh-confidentiality-stamping.md} §3 while the code stamped
 * neither and guarded neither. That is the specific failure these tests exist to prevent recurring:
 * a governance document describing behaviour no code implements is worse than a document that admits
 * the gap, because it is the one nobody goes back and checks.
 */
class ReproductiveEpisodeConfidentialityTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final String SUBJECT = "CPID-WOMAN-1";

    @AfterEach
    void clearContext() {
        VisibilityContextHolder.clear();
        TrustContextHolder.clear();
    }

    // ── contraceptive episodes ────────────────────────────────────────────────

    private static ContraceptiveEpisodeRepository episodeRepo() {
        ContraceptiveEpisodeRepository episodes = mock(ContraceptiveEpisodeRepository.class);
        when(episodes.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(episodes.findCurrentForMethod(any(), any(), any())).thenReturn(Optional.empty());
        when(episodes.findByTenantIdAndClientOfflineId(any(), any())).thenReturn(Optional.empty());
        return episodes;
    }

    private static ContraceptiveEpisodeEntity startedEpisode(LocalDate startedOn) {
        ContraceptiveEpisodeEntity e = new ContraceptiveEpisodeEntity();
        e.setTenantId(TENANT);
        e.setSubjectCpid(SUBJECT);
        e.setMethodCode("IMPLANT");
        e.setStartedOn(startedOn);
        e.setPregnancyCertainty("ESTABLISHED");
        e.setRecordedBy("nurse-1");
        return e;
    }

    private static ContraceptiveEpisodeEntity stamped(String sensitivityClass, String category) {
        ContraceptiveEpisodeEntity e = startedEpisode(LocalDate.of(2026, 1, 1));
        e.setContraceptiveEpisodeId(UUID.randomUUID());
        e.setStatus(ContraceptiveEpisodeEntity.STATUS_ACTIVE);
        e.setSensitivityClass(sensitivityClass);
        e.setConfidentialityCategory(category);
        return e;
    }

    @Test
    @DisplayName("starting a method stamps the SRH category — the row the decision table promised")
    void startingAMethodStampsTheCategory() {
        var service = new ContraceptiveEpisodeService(episodeRepo(),
                mock(ContraceptiveAdministrationRepository.class),
                policy(18, LocalDate.of(2012, 1, 1), false),
                new ConfidentialRecordGuard());

        var saved = service.start(startedEpisode(LocalDate.of(2026, 1, 1)));

        assertThat(saved.getConfidentialityCategory()).isEqualTo(SRH);
        assertThat(saved.getConfidentialityBasis())
                .isEqualTo(ConfidentialityStamper.BASIS_SUBJECT_UNDER_POLICY_AGE);
        assertThat(saved.getConfidentialityPolicyVersion()).isEqualTo("test-policy-1.0.0");
        assertThat(saved.getSensitivityClass())
                .as("SHADOW: the class is held at FULL_CLINICAL until the governance flip, or the "
                        + "nurse who just recorded the method could not see it")
                .isEqualTo(ConfidentialityStamper.CLASS_FULL_CLINICAL);
    }

    @Test
    @DisplayName("AGE IS TAKEN AT started_on: 17 then, 19 now, still class-eligible")
    void ageIsTakenAtTheStartDate() {
        // Born 2007. Method started 2024-07 when she was 17; today she is 19.
        var service = new ContraceptiveEpisodeService(episodeRepo(),
                mock(ContraceptiveAdministrationRepository.class),
                policy(18, LocalDate.of(2007, 6, 1), true),
                new ConfidentialRecordGuard());

        var atSeventeen = service.start(startedEpisode(LocalDate.of(2024, 7, 1)));
        assertThat(atSeventeen.getSensitivityClass())
                .as("the promise of confidentiality does not lapse on a birthday")
                .isEqualTo(ConfidentialityStamper.CLASS_SPECIALLY_PROTECTED);

        // The same woman starting a method today is over the threshold — proving the assertion above
        // is not passing for some age-independent reason.
        var atNineteen = service.start(startedEpisode(LocalDate.of(2026, 7, 1)));
        assertThat(atNineteen.getSensitivityClass())
                .isEqualTo(ConfidentialityStamper.CLASS_FULL_CLINICAL);
    }

    @Test
    @DisplayName("STAMPING FAILS OPEN: an unreachable CKP still records the category and accepts the write")
    void stampingFailsOpen() {
        var service = new ContraceptiveEpisodeService(episodeRepo(),
                mock(ContraceptiveAdministrationRepository.class),
                unresolvedPolicy(), new ConfidentialRecordGuard());

        var saved = service.start(startedEpisode(LocalDate.of(2026, 1, 1)));

        assertThat(saved.getConfidentialityCategory()).isEqualTo(SRH);
        assertThat(saved.getConfidentialityBasis())
                .isEqualTo(ConfidentialityStamper.BASIS_POLICY_UNAVAILABLE);
        assertThat(saved.getSensitivityClass()).isEqualTo(ConfidentialityStamper.CLASS_FULL_CLINICAL);
    }

    @Test
    @DisplayName("SHADOW is inert: a FULL_CLINICAL row with a category is returned to a requester granted nothing")
    void shadowStateReturnsEveryRow() {
        ContraceptiveEpisodeRepository episodes = episodeRepo();
        when(episodes.findCurrent(TENANT, SUBJECT))
                .thenReturn(List.of(stamped("FULL_CLINICAL", SRH), stamped("FULL_CLINICAL", null)));
        var service = new ContraceptiveEpisodeService(episodes,
                mock(ContraceptiveAdministrationRepository.class),
                unresolvedPolicy(), new ConfidentialRecordGuard());

        assertThat(service.current(TENANT, SUBJECT))
                .as("a category on a FULL_CLINICAL row is the expected pre-flip state, not a withhold")
                .hasSize(2);
    }

    @Test
    @DisplayName("POST-FLIP: no grant returns nothing, an SRH grant returns everything, an HIV grant still returns nothing")
    void postFlipGrantsAreCategoryScoped() {
        ContraceptiveEpisodeRepository episodes = episodeRepo();
        when(episodes.findCurrent(TENANT, SUBJECT))
                .thenReturn(List.of(stamped("SPECIALLY_PROTECTED", SRH)));
        var service = new ContraceptiveEpisodeService(episodes,
                mock(ContraceptiveAdministrationRepository.class),
                unresolvedPolicy(), new ConfidentialRecordGuard());

        assertThat(service.current(TENANT, SUBJECT)).isEmpty();

        VisibilityContextHolder.set(profileGranting("HIV"));
        assertThat(service.current(TENANT, SUBJECT))
                .as("an HIV grant must not open SRH — a whole-set grant is the only thing that does")
                .isEmpty();

        VisibilityContextHolder.set(profileGranting(SRH));
        assertThat(service.current(TENANT, SUBJECT)).hasSize(1);
    }

    @Test
    @DisplayName("EMERGENCY is never blocked: a null profile under EMERGENCY returns every row")
    void emergencyIsNeverBlocked() {
        ContraceptiveEpisodeRepository episodes = episodeRepo();
        when(episodes.findCurrent(TENANT, SUBJECT))
                .thenReturn(List.of(stamped("SPECIALLY_PROTECTED", SRH)));
        var service = new ContraceptiveEpisodeService(episodes,
                mock(ContraceptiveAdministrationRepository.class),
                unresolvedPolicy(), new ConfidentialRecordGuard());

        TrustContextHolder.set(new TrustContext(TENANT, "clinician-1", "PRACTITIONER", "EMERGENCY",
                null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));

        assertThat(service.current(TENANT, SUBJECT))
                .as("over-restricting kills people too: a teenager arriving unconscious must not be "
                        + "invisible to the clinician treating her")
                .hasSize(1);
    }

    @Test
    @DisplayName("COVERAGE FILTERS BEFORE IT COMPUTES: a withheld episode contributes no summary")
    void coverageFiltersBeforeComputing() {
        ContraceptiveEpisodeRepository episodes = episodeRepo();
        ContraceptiveAdministrationRepository administrations =
                mock(ContraceptiveAdministrationRepository.class);
        when(administrations.findForEpisode(any())).thenReturn(List.of());
        when(episodes.findCurrent(TENANT, SUBJECT)).thenReturn(List.of(
                stamped("SPECIALLY_PROTECTED", SRH), stamped("FULL_CLINICAL", null)));
        var service = new ContraceptiveEpisodeService(episodes, administrations,
                unresolvedPolicy(), new ConfidentialRecordGuard());

        var coverage = service.coverage(TENANT, SUBJECT, LocalDate.of(2026, 3, 1));

        assertThat(coverage)
                .as("a coverage summary derived from a withheld episode leaks its existence just as "
                        + "surely as returning the row would")
                .hasSize(1);
        assertThat(coverage.get(0).episode().getSensitivityClass()).isEqualTo("FULL_CLINICAL");
    }

    @Test
    @DisplayName("the retained-fragment worklist is guarded through the owning episode, which carries the stamp")
    void retainedFragmentWorklistIsGuardedThroughItsEpisode() {
        ContraceptiveEpisodeEntity protectedEpisode = stamped("SPECIALLY_PROTECTED", SRH);
        ContraceptiveEpisodeEntity ordinaryEpisode = stamped("FULL_CLINICAL", null);

        ContraceptiveEpisodeRepository episodes = episodeRepo();
        when(episodes.findAllById(any()))
                .thenReturn(List.of(protectedEpisode, ordinaryEpisode));
        ContraceptiveAdministrationRepository administrations =
                mock(ContraceptiveAdministrationRepository.class);
        when(administrations.findRetainedFragments(TENANT)).thenReturn(List.of(
                fragment(protectedEpisode.getContraceptiveEpisodeId()),
                fragment(ordinaryEpisode.getContraceptiveEpisodeId()),
                fragment(UUID.randomUUID())));

        var service = new ContraceptiveEpisodeService(episodes, administrations,
                unresolvedPolicy(), new ConfidentialRecordGuard());

        assertThat(service.retainedFragments(TENANT))
                .as("a tenant-wide worklist names women rather than answering about one, so it is the "
                        + "most disclosive read here — but an administration whose episode cannot be "
                        + "resolved is kept, because dropping it would hide a retained fragment")
                .hasSize(2);
    }

    private static ContraceptiveAdministrationEntity fragment(UUID episodeId) {
        ContraceptiveAdministrationEntity a = new ContraceptiveAdministrationEntity();
        a.setAdministrationId(UUID.randomUUID());
        a.setTenantId(TENANT);
        a.setContraceptiveEpisodeId(episodeId);
        a.setAdministrationType(ContraceptiveAdministrationEntity.TYPE_REMOVAL);
        a.setAdministeredOn(LocalDate.of(2026, 2, 1));
        a.setRetainedFragment(Boolean.TRUE);
        return a;
    }

    // ── pregnancy episodes ────────────────────────────────────────────────────

    private static PregnancyEpisodeService pregnancyService(ConfidentialCarePolicyProvider policy,
                                                            PregnancyEpisodeRepository episodes) {
        PregnancyDatingRevisionRepository revisions = mock(PregnancyDatingRevisionRepository.class);
        when(revisions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(revisions.countByPregnancyEpisodeId(any())).thenReturn(0);
        return new PregnancyEpisodeService(episodes, revisions, policy, new ConfidentialRecordGuard());
    }

    private static PregnancyEpisodeRepository pregnancyRepo() {
        PregnancyEpisodeRepository episodes = mock(PregnancyEpisodeRepository.class);
        when(episodes.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(episodes.findOngoing(any(), any())).thenReturn(Optional.empty());
        when(episodes.findByTenantIdAndClientOfflineId(any(), any())).thenReturn(Optional.empty());
        return episodes;
    }

    private static PregnancyEpisodeService.OpenPregnancyCommand booking(LocalDate lmp) {
        return new PregnancyEpisodeService.OpenPregnancyCommand(
                TENANT, SUBJECT, UUID.randomUUID(), "JRN-1", "ENC-1",
                List.of(DatingBasis.fromLmp(lmp, true)), lmp.plusMonths(2), null, "URINE_HCG",
                null, null, null, null, "midwife-1");
    }

    private static PregnancyEpisodeEntity stampedPregnancy(String sensitivityClass, String category) {
        PregnancyEpisodeEntity e = new PregnancyEpisodeEntity();
        e.setPregnancyEpisodeId(UUID.randomUUID());
        e.setTenantId(TENANT);
        e.setSubjectCpid(SUBJECT);
        e.setStatus(PregnancyEpisodeEntity.STATUS_ONGOING);
        e.setSensitivityClass(sensitivityClass);
        e.setConfidentialityCategory(category);
        return e;
    }

    @Test
    @DisplayName("opening a pregnancy stamps the SRH category at pregnancy_start_date, not today")
    void openingAPregnancyStampsTheCategory() {
        var service = pregnancyService(policy(18, LocalDate.of(2010, 1, 1), false), pregnancyRepo());

        var saved = service.open(booking(LocalDate.of(2026, 1, 1)));

        assertThat(saved.getConfidentialityCategory()).isEqualTo(SRH);
        assertThat(saved.getConfidentialityBasis())
                .isEqualTo(ConfidentialityStamper.BASIS_SUBJECT_UNDER_POLICY_AGE);
        assertThat(saved.getSensitivityClass())
                .as("the hardcoded FULL_CLINICAL is now the flip gate's output, not a literal")
                .isEqualTo(ConfidentialityStamper.CLASS_FULL_CLINICAL);
    }

    @Test
    @DisplayName("POST-FLIP: a protected pregnancy reads as absent, not as a 403")
    void protectedPregnancyReadsAsAbsent() {
        PregnancyEpisodeRepository episodes = pregnancyRepo();
        when(episodes.findOngoing(TENANT, SUBJECT))
                .thenReturn(Optional.of(stampedPregnancy("SPECIALLY_PROTECTED", SRH)));
        var service = pregnancyService(unresolvedPolicy(), episodes);

        assertThat(service.currentPregnancy(TENANT, SUBJECT))
                .as("a withheld record must be indistinguishable from one that does not exist")
                .isEmpty();

        VisibilityContextHolder.set(profileGranting(SRH));
        assertThat(service.currentPregnancy(TENANT, SUBJECT)).isPresent();
    }

    @Test
    @DisplayName("pregnant() stays UNGUARDED — a recorded, bounded leak, not an oversight")
    void pregnantStaysUnguarded() {
        PregnancyEpisodeRepository episodes = pregnancyRepo();
        when(episodes.findOngoing(TENANT, SUBJECT))
                .thenReturn(Optional.of(stampedPregnancy("SPECIALLY_PROTECTED", SRH)));
        var service = pregnancyService(unresolvedPolicy(), episodes);

        // No profile at all: currentPregnancy withholds, and pregnant() deliberately does not.
        assertThat(service.currentPregnancy(TENANT, SUBJECT)).isEmpty();
        assertThat(service.pregnant(TENANT, SUBJECT))
                .as("FormResolverService calls this with no request context; guarding it would blank "
                        + "clinical decision support outright. Documented in the stamping doc §8 with "
                        + "a named owner — do not route it through currentPregnancy()")
                .isTrue();
    }

    @Test
    @DisplayName("gestational age survives a withheld episode, because it returns a number and not the record")
    void gestationalAgeSurvivesAWithheldEpisode() {
        PregnancyEpisodeEntity dated = stampedPregnancy("SPECIALLY_PROTECTED", SRH);
        dated.setEstimatedDeliveryDate(LocalDate.of(2026, 10, 8));
        PregnancyEpisodeRepository episodes = pregnancyRepo();
        when(episodes.findOngoing(TENANT, SUBJECT)).thenReturn(Optional.of(dated));
        var service = pregnancyService(unresolvedPolicy(), episodes);

        assertThat(service.gestationalAgeDays(TENANT, SUBJECT, LocalDate.of(2026, 6, 1)))
                .as("gestation drives dose bands and referral thresholds; blanking it degrades care "
                        + "rather than protecting anyone")
                .isNotNull();
    }

    @Test
    @DisplayName("episode history is guarded per row, so a granted category survives an ungranted one")
    void historyIsGuardedPerRow() {
        PregnancyEpisodeRepository episodes = pregnancyRepo();
        when(episodes.findBySubject(TENANT, SUBJECT)).thenReturn(List.of(
                stampedPregnancy("SPECIALLY_PROTECTED", SRH),
                stampedPregnancy("FULL_CLINICAL", null)));
        var service = pregnancyService(unresolvedPolicy(), episodes);

        assertThat(service.history(TENANT, SUBJECT)).hasSize(1);

        VisibilityContextHolder.set(profileGranting(SRH));
        assertThat(service.history(TENANT, SUBJECT)).hasSize(2);
    }

    @Test
    @DisplayName("episodeCovering is guarded, so a linkage read cannot become an unguarded disclosure")
    void episodeCoveringIsGuarded() {
        PregnancyEpisodeRepository episodes = pregnancyRepo();
        when(episodes.findCoveringDate(any(), any(), any(), any()))
                .thenReturn(List.of(stampedPregnancy("SPECIALLY_PROTECTED", SRH)));
        var service = pregnancyService(unresolvedPolicy(), episodes);

        assertThat(service.episodeCovering(TENANT, SUBJECT, LocalDate.of(2026, 6, 1))).isEmpty();

        VisibilityContextHolder.set(profileGranting(SRH));
        assertThat(service.episodeCovering(TENANT, SUBJECT, LocalDate.of(2026, 6, 1))).isPresent();
    }
}
