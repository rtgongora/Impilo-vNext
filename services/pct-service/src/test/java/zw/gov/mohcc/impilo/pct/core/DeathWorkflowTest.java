package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import zw.gov.mohcc.impilo.pct.api.dto.*;
import zw.gov.mohcc.impilo.pct.core.death.CauseOfDeathValidator;
import zw.gov.mohcc.impilo.pct.core.death.MedicoLegalScreener;
import zw.gov.mohcc.impilo.pct.core.death.PublicHealthScreener;
import zw.gov.mohcc.impilo.pct.persistence.entity.BodyCustodyEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.DeathAuditEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.DeathCaseEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.FieldBodyManagementEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.VerbalAutopsyEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.*;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the WS#8 death-pathway spine in {@link DeathWorkflow}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeathWorkflowTest {

    @Mock private DeathCaseRepository deathCaseRepository;
    @Mock private JourneyRepository journeyRepository;
    @Mock private JourneyStateMachine journeyStateMachine;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private BodyCustodyRepository bodyCustodyRepository;
    @Mock private DeathAuditRepository deathAuditRepository;
    @Mock private VerbalAutopsyRepository verbalAutopsyRepository;
    @Mock private FieldBodyManagementRepository fieldBodyManagementRepository;

    private DeathWorkflow workflow;
    private final ObjectMapper om = new ObjectMapper();

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID FACILITY = UUID.randomUUID();
    private static final UUID CORR = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        workflow = new DeathWorkflow(deathCaseRepository, journeyRepository, journeyStateMachine,
                outboxRepository, om, bodyCustodyRepository, deathAuditRepository,
                verbalAutopsyRepository, fieldBodyManagementRepository,
                new MedicoLegalScreener(), new PublicHealthScreener(), new CauseOfDeathValidator());
        when(deathCaseRepository.save(any(DeathCaseEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(bodyCustodyRepository.save(any(BodyCustodyEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(deathAuditRepository.save(any(DeathAuditEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(verbalAutopsyRepository.save(any(VerbalAutopsyEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(fieldBodyManagementRepository.save(any(FieldBodyManagementEntity.class))).thenAnswer(i -> i.getArgument(0));
    }

    private TrustContext ctx(String actorType) {
        return new TrustContext(TENANT, "actor-1", actorType, "TREATMENT", null, CORR, FACILITY,
                null, null, AccessMode.INTERNAL);
    }

    private MockedStatic<TrustContextHolder> trust(String actorType) {
        MockedStatic<TrustContextHolder> m = mockStatic(TrustContextHolder.class);
        m.when(TrustContextHolder::require).thenReturn(ctx(actorType));
        return m;
    }

    private ConfirmDeathRequest natural() {
        return new ConfirmDeathRequest(null, OffsetDateTime.now(), "INPATIENT", "Ward 3",
                false, "Dr. A", "CPID-1", "KNOWN", OffsetDateTime.now().minusDays(5),
                "NATURAL", false, FACILITY.toString(), "FACILITY", null, null);
    }

    // 1. Authorised provider confirms a natural death; it persists with no coroner referral.
    @Test
    void natural_death_confirmation_persists() {
        try (var t = trust("DOCTOR")) {
            DeathCaseEntity dc = workflow.confirmDeath(natural());
            assertThat(dc.getConfirmedByRole()).isEqualTo("DOCTOR");
            assertThat(dc.isCoronerReferralRequired()).isFalse();
            verify(deathCaseRepository).save(any());
        }
    }

    // 1b. Unauthorised role is blocked from confirming a death.
    @Test
    void unauthorised_role_cannot_confirm() {
        try (var t = trust("CITIZEN")) {
            assertThatThrownBy(() -> workflow.confirmDeath(natural()))
                    .isInstanceOf(SecurityException.class);
        }
    }

    // 2. Death within 24h of admission triggers the medico-legal path + blocks body release.
    @Test
    void within_24h_triggers_medico_legal() {
        OffsetDateTime now = OffsetDateTime.now();
        ConfirmDeathRequest req = new ConfirmDeathRequest(null, now, "INPATIENT", null, true, null,
                "CPID-2", "KNOWN", now.minusHours(6), "NATURAL", false, FACILITY.toString(), "FACILITY", null, null);
        try (var t = trust("DOCTOR")) {
            DeathCaseEntity dc = workflow.confirmDeath(req);
            assertThat(dc.isCoronerReferralRequired()).isTrue();
            assertThat(dc.getMedicoLegalTriggers()).contains("WITHIN_24H_OF_ADMISSION");
            assertThat(dc.isBodyReleaseBlocked()).isTrue();
        }
    }

    // 2b. Theatre / procedure death triggers a medico-legal referral.
    @Test
    void theatre_death_triggers_referral() {
        ConfirmDeathRequest req = new ConfirmDeathRequest(null, OffsetDateTime.now(), "THEATRE", null,
                true, null, "CPID-3", "KNOWN", null, "NATURAL", false, FACILITY.toString(), "FACILITY", null, null);
        try (var t = trust("DOCTOR")) {
            assertThat(workflow.confirmDeath(req).getMedicoLegalTriggers()).contains("DURING_SURGERY_OR_PROCEDURE");
        }
    }

    // 2c. Brought-in-dead unknown body mints a temporary deceased identity + medico-legal screen, and
    // resolves to the BROUGHT_IN_DEAD source + BROUGHT_TO_FACILITY disposition (not a community death).
    @Test
    void brought_in_dead_unknown_creates_temporary_identity() {
        ConfirmDeathRequest req = new ConfirmDeathRequest(null, OffsetDateTime.now(), null,
                null, false, null, null, "UNKNOWN", null, "UNDETERMINED", false, FACILITY.toString(),
                null, null, null);
        try (var t = trust("MEDICAL_OFFICER")) {
            DeathCaseEntity dc = workflow.confirmBroughtInDead(req);
            assertThat(dc.getTemporaryIdentityRef()).startsWith("TMP-DEC-");
            assertThat(dc.getMedicoLegalTriggers()).contains("UNKNOWN_IDENTITY");
            assertThat(dc.isCoronerReferralRequired()).isTrue();
            assertThat(dc.getSourceContext()).isEqualTo("BROUGHT_IN_DEAD");
            assertThat(dc.getPlaceOfDeathContext()).isEqualTo("BROUGHT_IN_DEAD");
            assertThat(dc.getBodyDispositionContext()).isEqualTo("BROUGHT_TO_FACILITY");
            assertThat(dc.getCauseOfDeathBasis()).isEqualTo("MEDICO_LEGAL_PENDING");
        }
    }

    // 3. Public-health screen flags an under-five death and requires review.
    @Test
    void under_five_triggers_review() {
        DeathCaseEntity dc = baseCase();
        when(deathCaseRepository.findByTenantIdAndCaseId(TENANT, dc.getId())).thenReturn(Optional.of(dc));
        try (var t = trust("DOCTOR")) {
            DeathCaseEntity r = workflow.screenPublicHealth(dc.getId(),
                    new PublicHealthScreenRequest(3, null, false, false, false, false, false));
            assertThat(r.getPublicHealthFlags()).contains("UNDER_FIVE");
            assertThat(r.isDeathReviewRequired()).isTrue();
        }
    }

    // 4. Natural-death certification by an eligible certifier persists as SIGNED.
    @Test
    void eligible_certifier_signs() {
        DeathCaseEntity dc = baseCase();
        when(deathCaseRepository.findByTenantIdAndCaseId(TENANT, dc.getId())).thenReturn(Optional.of(dc));
        try (var t = trust("DOCTOR")) {
            DeathCaseEntity r = workflow.certifyCauseOfDeath(dc.getId(),
                    new CertifyCauseOfDeathRequest("Hypovolaemic shock", "Ruptured aneurysm",
                            "Abdominal aortic aneurysm", null, "NATURAL", null, "MDCN-12345", true));
            assertThat(r.getCertificationStatus()).isEqualTo("SIGNED");
            assertThat(r.getCertCompositionRef()).isNotNull();
        }
    }

    // 4b. Ineligible certifier role is hard-blocked.
    @Test
    void ineligible_certifier_blocked() {
        DeathCaseEntity dc = baseCase();
        when(deathCaseRepository.findByTenantIdAndCaseId(TENANT, dc.getId())).thenReturn(Optional.of(dc));
        try (var t = trust("NURSE")) {
            assertThatThrownBy(() -> workflow.certifyCauseOfDeath(dc.getId(),
                    new CertifyCauseOfDeathRequest("Sepsis", null, "Pneumonia", null, "NATURAL", null, "L-1", true)))
                    .isInstanceOf(SecurityException.class);
        }
    }

    // 4c. Garbage cause yields SOFT warnings but does not block a draft.
    @Test
    void garbage_cause_warns_softly() {
        DeathCaseEntity dc = baseCase();
        when(deathCaseRepository.findByTenantIdAndCaseId(TENANT, dc.getId())).thenReturn(Optional.of(dc));
        try (var t = trust("DOCTOR")) {
            DeathCaseEntity r = workflow.certifyCauseOfDeath(dc.getId(),
                    new CertifyCauseOfDeathRequest("Cardiac arrest", null, null, null, "NATURAL", null, "L-1", false));
            assertThat(r.getCertificationStatus()).isEqualTo("DRAFT");
            assertThat(r.getCertWarnings()).isNotNull();
        }
    }

    // 4d. Certification is blocked while an open coroner referral is present.
    @Test
    void certification_blocked_under_open_coroner_referral() {
        DeathCaseEntity dc = baseCase();
        dc.setCoronerReferralRequired(true);
        dc.setCoronerReferralStatus("PENDING_REFERRAL");
        when(deathCaseRepository.findByTenantIdAndCaseId(TENANT, dc.getId())).thenReturn(Optional.of(dc));
        try (var t = trust("DOCTOR")) {
            assertThatThrownBy(() -> workflow.certifyCauseOfDeath(dc.getId(),
                    new CertifyCauseOfDeathRequest("X", null, "Y", null, "NATURAL", null, "L-1", true)))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // 5. Post-mortem / legal hold blocks body release.
    @Test
    void hold_blocks_body_release() {
        DeathCaseEntity dc = baseCase();
        when(deathCaseRepository.findByTenantIdAndCaseId(TENANT, dc.getId())).thenReturn(Optional.of(dc));
        BodyCustodyEntity custody = new BodyCustodyEntity();
        custody.setCustodyId(UUID.randomUUID());
        custody.setCaseId(dc.getId());
        custody.setLegalHold(true);
        when(bodyCustodyRepository.findByTenantIdAndCaseId(TENANT, dc.getId())).thenReturn(Optional.of(custody));
        try (var t = trust("MORTUARY_OFFICER")) {
            assertThatThrownBy(() -> workflow.releaseBody(dc.getId(),
                    new BodyReleaseRequest("Next of kin", "SPOUSE", "NID-1")))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // 5b. Release succeeds to a named recipient once holds are clear.
    @Test
    void release_to_named_recipient_when_clear() {
        DeathCaseEntity dc = baseCase();
        when(deathCaseRepository.findByTenantIdAndCaseId(TENANT, dc.getId())).thenReturn(Optional.of(dc));
        BodyCustodyEntity custody = new BodyCustodyEntity();
        custody.setCustodyId(UUID.randomUUID());
        custody.setCaseId(dc.getId());
        when(bodyCustodyRepository.findByTenantIdAndCaseId(TENANT, dc.getId())).thenReturn(Optional.of(custody));
        try (var t = trust("MORTUARY_OFFICER")) {
            BodyCustodyEntity r = workflow.releaseBody(dc.getId(),
                    new BodyReleaseRequest("Jane Doe", "DAUGHTER", "NID-9"));
            assertThat(r.getStatus()).isEqualTo("RELEASED");
            assertThat(r.getReleasedToName()).isEqualTo("Jane Doe");
        }
    }

    // 6. CRVS package cannot be staged with missing required fields (no signed cause).
    @Test
    void crvs_package_blocked_when_incomplete() {
        DeathCaseEntity dc = baseCase(); // no signed cause yet
        when(deathCaseRepository.findByTenantIdAndCaseId(TENANT, dc.getId())).thenReturn(Optional.of(dc));
        try (var t = trust("DOCTOR")) {
            assertThatThrownBy(() -> workflow.stageCrvsPackage(dc.getId()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("signedCauseOfDeath");
        }
    }

    // 6b. CRVS package stages READY once the spine is complete.
    @Test
    void crvs_package_ready_when_complete() {
        DeathCaseEntity dc = baseCase();
        dc.setCertificationStatus("SIGNED");
        when(deathCaseRepository.findByTenantIdAndCaseId(TENANT, dc.getId())).thenReturn(Optional.of(dc));
        try (var t = trust("REGISTRY_CLERK")) {
            DeathCaseEntity r = workflow.stageCrvsPackage(dc.getId());
            assertThat(r.getCivilRegistrationStatus()).isEqualTo("PACKAGE_READY");
        }
    }

    // 7. Inpatient disposition=DEATH opens a death case (WS#6 seam), idempotently.
    @Test
    void inpatient_discharge_opens_case() {
        when(deathCaseRepository.findByTenantIdAndJourneyId(TENANT, "J-1")).thenReturn(Optional.empty());
        when(journeyRepository.findByJourneyId("J-1")).thenReturn(Optional.empty());
        try (var t = trust("DOCTOR")) {
            DeathCaseEntity r = workflow.openFromInpatientDischarge("J-1", OffsetDateTime.now());
            assertThat(r.getSourceContext()).isEqualTo("INPATIENT_DISCHARGE");
            assertThat(r.getPlaceOfDeathContext()).isEqualTo("INPATIENT");
        }
    }

    // 8. An audit event is written on every status change (confirm writes CONFIRMED).
    @Test
    void audit_event_written_on_confirm() {
        try (var t = trust("DOCTOR")) {
            workflow.confirmDeath(natural());
            verify(deathAuditRepository, atLeastOnce()).save(any(DeathAuditEntity.class));
        }
    }

    // ── Community / field death pathway refinement ──

    // 9. A community/field death REPORT opens an unverified case (status REPORTED, no asserted cause).
    // A known-identity natural death has no medico-legal trigger, so the basis is UNKNOWN_UNCERTIFIED.
    @Test
    void community_report_opens_unverified_case() {
        CommunityDeathReportRequest req = new CommunityDeathReportRequest(
                OffsetDateTime.now(), "COMMUNITY_FIELD", "COMMUNITY_HOME", "Village 7", "CPID-77", "KNOWN",
                "NATURAL", false, null, "Health worker", "CHW", "+263...", FACILITY.toString());
        try (var t = trust("CHW")) {
            DeathCaseEntity dc = workflow.reportCommunityDeath(req);
            assertThat(dc.getStatus()).isEqualTo("REPORTED");
            assertThat(dc.getSourceContext()).isEqualTo("COMMUNITY_FIELD");
            assertThat(dc.getBodyDispositionContext()).isEqualTo("NOT_BROUGHT_TO_FACILITY");
            assertThat(dc.getCauseOfDeathBasis()).isEqualTo("UNKNOWN_UNCERTIFIED");
            assertThat(dc.isCoronerReferralRequired()).isFalse();
            assertThat(dc.getConfirmedByRole()).isNull(); // a report is not a confirmation
        }
    }

    // 9d. An unidentified body in a community report is a medico-legal trigger (mints a temp identity).
    @Test
    void community_report_unknown_identity_is_medico_legal() {
        CommunityDeathReportRequest req = new CommunityDeathReportRequest(
                OffsetDateTime.now(), "COMMUNITY_FIELD", "COMMUNITY_HOME", "Village 7", null, "UNKNOWN",
                "NATURAL", false, null, "Health worker", "CHW", "+263...", FACILITY.toString());
        try (var t = trust("CHW")) {
            DeathCaseEntity dc = workflow.reportCommunityDeath(req);
            assertThat(dc.getTemporaryIdentityRef()).startsWith("TMP-DEC-");
            assertThat(dc.isCoronerReferralRequired()).isTrue();
            assertThat(dc.getCauseOfDeathBasis()).isEqualTo("MEDICO_LEGAL_PENDING");
        }
    }

    // 9b. A community report cannot claim a facility / brought-in-dead source.
    @Test
    void community_report_rejects_facility_source() {
        CommunityDeathReportRequest req = new CommunityDeathReportRequest(
                OffsetDateTime.now(), "FACILITY", null, null, "CPID-9", "KNOWN", "NATURAL", false,
                null, "X", "FAMILY", null, FACILITY.toString());
        try (var t = trust("CHW")) {
            assertThatThrownBy(() -> workflow.reportCommunityDeath(req))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // 9c. A red-flag manner on a community report routes it to the medico-legal pathway.
    @Test
    void community_report_red_flag_routes_medico_legal() {
        CommunityDeathReportRequest req = new CommunityDeathReportRequest(
                OffsetDateTime.now(), "COMMUNITY_FIELD", null, null, null, "UNKNOWN", "HOMICIDE", false,
                null, "Neighbour", "COMMUNITY_LEADER", null, FACILITY.toString());
        try (var t = trust("SURVEILLANCE_OFFICER")) {
            DeathCaseEntity dc = workflow.reportCommunityDeath(req);
            assertThat(dc.isCoronerReferralRequired()).isTrue();
            assertThat(dc.getCauseOfDeathBasis()).isEqualTo("MEDICO_LEGAL_PENDING");
            assertThat(dc.getMedicoLegalTriggers()).contains("UNNATURAL_MANNER");
        }
    }

    // 10. A verbal autopsy assigns a PROBABLE cause without a medical certification.
    @Test
    void verbal_autopsy_assigns_probable_cause() {
        DeathCaseEntity dc = baseCase();
        dc.setCertificationStatus("NOT_STARTED");
        dc.setCauseOfDeathBasis("UNKNOWN_UNCERTIFIED");
        when(deathCaseRepository.findByTenantIdAndCaseId(TENANT, dc.getId())).thenReturn(Optional.of(dc));
        try (var t = trust("NURSE")) {
            VerbalAutopsyEntity va = workflow.recordVerbalAutopsy(dc.getId(), new VerbalAutopsyRequest(
                    "WHO_VA_2022", "SPOUSE", OffsetDateTime.now(), "Fever for a week, then died at home.",
                    "Sepsis", "Malaria", "1F40", "PHYSICIAN_REVIEW", null, true));
            assertThat(va.getStatus()).isEqualTo("COMPLETED");
            assertThat(dc.getCauseOfDeathBasis()).isEqualTo("VERBAL_AUTOPSY_PROBABLE");
            assertThat(dc.getCodUnderlying()).isEqualTo("Malaria");
            // A verbal autopsy is NEVER a medical certification.
            assertThat(dc.getCertificationStatus()).isEqualTo("NOT_STARTED");
        }
    }

    // 10b. A verbal autopsy may NOT be attached to a medically certified death (never mixed).
    @Test
    void verbal_autopsy_blocked_on_certified_death() {
        DeathCaseEntity dc = baseCase();
        dc.setCertificationStatus("SIGNED");
        dc.setCauseOfDeathBasis("MEDICALLY_CERTIFIED");
        when(deathCaseRepository.findByTenantIdAndCaseId(TENANT, dc.getId())).thenReturn(Optional.of(dc));
        try (var t = trust("DOCTOR")) {
            assertThatThrownBy(() -> workflow.recordVerbalAutopsy(dc.getId(), new VerbalAutopsyRequest(
                    null, "CHILD", OffsetDateTime.now(), "n", "a", "b", null, "PHYSICIAN_REVIEW", null, true)))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // 10c. Red flags surfaced during a verbal autopsy route the case to the medico-legal pathway.
    @Test
    void verbal_autopsy_red_flags_route_medico_legal() {
        DeathCaseEntity dc = baseCase();
        dc.setCauseOfDeathBasis("UNKNOWN_UNCERTIFIED");
        when(deathCaseRepository.findByTenantIdAndCaseId(TENANT, dc.getId())).thenReturn(Optional.of(dc));
        try (var t = trust("NURSE")) {
            VerbalAutopsyEntity va = workflow.recordVerbalAutopsy(dc.getId(), new VerbalAutopsyRequest(
                    null, "SIBLING", OffsetDateTime.now(), "Signs of violence.", "x", "y", null,
                    "PHYSICIAN_REVIEW", List.of("SIGNS_OF_VIOLENCE"), true));
            assertThat(dc.isCoronerReferralRequired()).isTrue();
            assertThat(dc.getCauseOfDeathBasis()).isEqualTo("MEDICO_LEGAL_PENDING");
            // No probable cause assigned when red-flagged.
            assertThat(va.getProbableUnderlyingCause()).isNull();
        }
    }

    // 11. Field body management records a non-facility disposition (safe burial) — no mortuary custody.
    @Test
    void field_body_management_safe_burial() {
        DeathCaseEntity dc = baseCase();
        dc.setSourceContext("ISOLATION_FIELD_SITE");
        when(deathCaseRepository.findByTenantIdAndCaseId(TENANT, dc.getId())).thenReturn(Optional.of(dc));
        try (var t = trust("PUBLIC_HEALTH_OFFICER")) {
            FieldBodyManagementEntity fbm = workflow.recordFieldBodyManagement(dc.getId(),
                    new FieldBodyManagementRequest("SAFE_AND_DIGNIFIED_BURIAL", true, "EVD", true, true,
                            "Burial team A", true, true, null, null, "Village 3", OffsetDateTime.now(), null));
            assertThat(fbm.getDispositionType()).isEqualTo("SAFE_AND_DIGNIFIED_BURIAL");
            assertThat(dc.getBodyDispositionContext()).isEqualTo("FIELD_SAFE_BURIAL");
            verify(bodyCustodyRepository, never()).save(any()); // no mandatory mortuary custody
        }
    }

    // 11b. A non-police field disposition is blocked while an open coroner referral is present.
    @Test
    void field_body_management_blocked_under_open_coroner() {
        DeathCaseEntity dc = baseCase();
        dc.setCoronerReferralRequired(true);
        dc.setCoronerReferralStatus("PENDING_REFERRAL");
        when(deathCaseRepository.findByTenantIdAndCaseId(TENANT, dc.getId())).thenReturn(Optional.of(dc));
        try (var t = trust("PUBLIC_HEALTH_OFFICER")) {
            assertThatThrownBy(() -> workflow.recordFieldBodyManagement(dc.getId(),
                    new FieldBodyManagementRequest("FAMILY_RELEASE", false, null, false, null, null,
                            null, null, "Kin", "SON", null, OffsetDateTime.now(), null)))
                    .isInstanceOf(IllegalStateException.class);
            // But a police handover IS permitted under an open referral.
            FieldBodyManagementEntity fbm = workflow.recordFieldBodyManagement(dc.getId(),
                    new FieldBodyManagementRequest("POLICE_HANDOVER", false, null, false, null, null,
                            null, null, null, null, null, OffsetDateTime.now(), null));
            assertThat(dc.getBodyDispositionContext()).isEqualTo("POLICE_CUSTODY");
        }
    }

    // 12. A community death CRVS package stages READY on a verbal-autopsy probable cause (no signed cert).
    @Test
    void crvs_package_ready_with_verbal_autopsy_probable() {
        DeathCaseEntity dc = baseCase();
        dc.setPatientCpid(null);
        dc.setTemporaryIdentityRef("TMP-DEC-ABCD1234");
        dc.setCauseOfDeathBasis("VERBAL_AUTOPSY_PROBABLE");
        when(deathCaseRepository.findByTenantIdAndCaseId(TENANT, dc.getId())).thenReturn(Optional.of(dc));
        try (var t = trust("REGISTRY_CLERK")) {
            DeathCaseEntity r = workflow.stageCrvsPackage(dc.getId());
            assertThat(r.getCivilRegistrationStatus()).isEqualTo("PACKAGE_READY");
        }
    }

    private DeathCaseEntity baseCase() {
        DeathCaseEntity dc = new DeathCaseEntity();
        dc.setId(UUID.randomUUID());
        dc.setTenantId(TENANT);
        dc.setFacilityId(FACILITY);
        dc.setPatientCpid("CPID-X");
        dc.setDeathDatetime(OffsetDateTime.now());
        dc.setPlaceOfDeathContext("INPATIENT");
        dc.setCertificationStatus("NOT_STARTED");
        dc.setCivilRegistrationStatus("NOT_STARTED");
        return dc;
    }
}
