package zw.gov.mohcc.impilo.simba.core;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.simba.persistence.entity.ConsentPreferenceEntity;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Allow/deny matrix for the wellness access guard, and that every decision is audited. */
class WellnessAccessGuardTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final String SUBJECT = "cpid-subject";
    private static final UUID REL = UUID.randomUUID();

    private WellnessAccessGuard.WellnessAccessContext ctx(String actorId, String actorType, String providerId) {
        return new WellnessAccessGuard.WellnessAccessContext(TENANT, actorId, actorType, providerId,
                null, null, "prog-1", "corr", "req");
    }

    private ConsentPreferenceEntity consent(boolean provider, boolean caregiver, boolean programme) {
        ConsentPreferenceEntity c = new ConsentPreferenceEntity();
        c.setTenantId(TENANT);
        c.setPersonCpid(SUBJECT);
        c.setProviderInvolvement(provider);
        c.setCaregiverInvolvement(caregiver);
        c.setProgrammeInvolvement(programme);
        return c;
    }

    // ── citizen-self ────────────────────────────────────────────────────────
    @Test
    void self_allowed_andAudited() {
        var coaching = mock(CoachingService.class);
        var consentSvc = mock(ConsentPreferenceService.class);
        var audit = mock(WellnessAuditService.class);
        var guard = new WellnessAccessGuard(coaching, consentSvc, audit);

        guard.requireSelf(ctx(SUBJECT, "CITIZEN", null), SUBJECT, "wellness.read");
        verify(audit, atLeastOnce()).record(eq(TENANT), eq(SUBJECT), anyString(), any(), eq(SUBJECT),
                anyString(), any(), any(), eq("wellness.read"), eq("ALLOW"), any(), anyString(), anyString());
    }

    @Test
    void self_crossPerson_denied() {
        var guard = new WellnessAccessGuard(mock(CoachingService.class),
                mock(ConsentPreferenceService.class), mock(WellnessAuditService.class));
        assertThatThrownBy(() -> guard.requireSelf(ctx("other", "CITIZEN", null), SUBJECT, "wellness.read"))
                .isInstanceOf(WellnessAccessGuard.WellnessAccessDeniedException.class);
    }

    // ── provider-client ─────────────────────────────────────────────────────
    @Test
    void provider_withAssignmentAndConsent_allowed() {
        var coaching = mock(CoachingService.class);
        var consentSvc = mock(ConsentPreferenceService.class);
        when(coaching.coachIsAssigned(TENANT, "prov-1", REL)).thenReturn(true);
        when(consentSvc.getOrDefault(TENANT, SUBJECT)).thenReturn(consent(true, false, false));
        var guard = new WellnessAccessGuard(coaching, consentSvc, mock(WellnessAuditService.class));

        guard.requireProviderClient(ctx("prov-1", "PROVIDER", "prov-1"), SUBJECT, REL, "wellness.provider.read");
    }

    @Test
    void provider_withoutAssignment_denied() {
        var coaching = mock(CoachingService.class);
        when(coaching.coachIsAssigned(TENANT, "prov-1", REL)).thenReturn(false);
        var guard = new WellnessAccessGuard(coaching, mock(ConsentPreferenceService.class),
                mock(WellnessAuditService.class));
        assertThatThrownBy(() -> guard.requireProviderClient(ctx("prov-1", "PROVIDER", "prov-1"),
                SUBJECT, REL, "wellness.provider.read"))
                .isInstanceOf(WellnessAccessGuard.WellnessAccessDeniedException.class);
    }

    @Test
    void provider_assignedButNoConsent_denied() {
        var coaching = mock(CoachingService.class);
        var consentSvc = mock(ConsentPreferenceService.class);
        when(coaching.coachIsAssigned(TENANT, "prov-1", REL)).thenReturn(true);
        when(consentSvc.getOrDefault(TENANT, SUBJECT)).thenReturn(consent(false, false, false));
        var guard = new WellnessAccessGuard(coaching, consentSvc, mock(WellnessAuditService.class));
        assertThatThrownBy(() -> guard.requireProviderClient(ctx("prov-1", "PROVIDER", "prov-1"),
                SUBJECT, REL, "wellness.provider.read"))
                .isInstanceOf(WellnessAccessGuard.WellnessAccessDeniedException.class);
    }

    // ── programme + caregiver ───────────────────────────────────────────────
    @Test
    void programme_withConsent_allowed_withoutConsent_denied() {
        var consentSvc = mock(ConsentPreferenceService.class);
        when(consentSvc.getOrDefault(TENANT, SUBJECT))
                .thenReturn(consent(false, false, true), consent(false, false, false));
        var guard = new WellnessAccessGuard(mock(CoachingService.class), consentSvc, mock(WellnessAuditService.class));

        guard.requireProgramme(ctx("op-1", "PROGRAMME_ADMIN", null), SUBJECT, "wellness.programme.read");
        assertThatThrownBy(() -> guard.requireProgramme(ctx("op-1", "PROGRAMME_ADMIN", null),
                SUBJECT, "wellness.programme.read"))
                .isInstanceOf(WellnessAccessGuard.WellnessAccessDeniedException.class);
    }

    @Test
    void caregiver_withoutConsent_denied() {
        var consentSvc = mock(ConsentPreferenceService.class);
        when(consentSvc.getOrDefault(TENANT, SUBJECT)).thenReturn(consent(false, false, false));
        var guard = new WellnessAccessGuard(mock(CoachingService.class), consentSvc, mock(WellnessAuditService.class));
        assertThatThrownBy(() -> guard.requireCaregiver(ctx("cg-1", "CAREGIVER", null),
                SUBJECT, "wellness.caregiver.read"))
                .isInstanceOf(WellnessAccessGuard.WellnessAccessDeniedException.class);
    }

    // ── aggregate ───────────────────────────────────────────────────────────
    @Test
    void aggregate_providerAllowed_citizenDenied() {
        var guard = new WellnessAccessGuard(mock(CoachingService.class),
                mock(ConsentPreferenceService.class), mock(WellnessAuditService.class));
        guard.requireAggregate(ctx("prov-1", "PROVIDER", "prov-1"), "wellness.aggregate");
        assertThatThrownBy(() -> guard.requireAggregate(ctx("cit-1", "CITIZEN", null), "wellness.aggregate"))
                .isInstanceOf(WellnessAccessGuard.WellnessAccessDeniedException.class);
    }

    // ── sensitive-category ──────────────────────────────────────────────────
    @Test
    void sensitive_selfAlwaysVisible_nonSelfGatedOnConsent() {
        var consentSvc = mock(ConsentPreferenceService.class);
        when(consentSvc.consentedSensitiveCategories(TENANT, SUBJECT)).thenReturn(Set.of("MENTAL"));
        var guard = new WellnessAccessGuard(mock(CoachingService.class), consentSvc, mock(WellnessAuditService.class));

        assertThat(guard.sensitiveCategoryVisible(ctx(SUBJECT, "CITIZEN", null), SUBJECT, "MENTAL")).isTrue();
        assertThat(guard.sensitiveCategoryVisible(ctx("prov-1", "PROVIDER", "prov-1"), SUBJECT, "MENTAL")).isTrue();
        assertThat(guard.sensitiveCategoryVisible(ctx("prov-1", "PROVIDER", "prov-1"), SUBJECT, "SRH")).isFalse();
    }
}
