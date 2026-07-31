package zw.gov.mohcc.impilo.pct.core.clinical;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.pct.persistence.entity.PostnatalContactEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.PostnatalContactRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Postnatal-contact service behaviour and the "silence is not reassurance" default.
 *
 * <p>The doctrinal CHECKs — a danger-sign finding requires a completed screen, a referral carries its
 * reason, the vocabularies — are mutation-proven against real Postgres in the V436 probe (4 positive
 * controls, 6 rejections). Here: offline idempotency, the completed-action boolean defaults, and the
 * one that matters most — that an unrecorded screen leaves {@code dangerSignsPresent} null, never a
 * defaulted false that would read as "no danger signs".
 */
class PostnatalContactServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");

    private PostnatalContactRepository repo;
    private PostnatalContactService service;

    @BeforeEach
    void setUp() {
        repo = mock(PostnatalContactRepository.class);
        service = new PostnatalContactService(repo, inertConfidentiality(), new ConfidentialRecordGuard());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repo.findByTenantIdAndClientOfflineId(any(), any())).thenReturn(Optional.empty());
    }

    private static PostnatalContactEntity minimalContact() {
        PostnatalContactEntity c = new PostnatalContactEntity();
        c.setTenantId(TENANT);
        c.setMotherCpid("CPID-MOTHER");
        c.setContactTiming(PostnatalContactEntity.TIMING_DAY_3);
        c.setContactSetting(PostnatalContactEntity.SETTING_HOME);
        c.setContactedAt(OffsetDateTime.now());
        c.setRecordedBy("midwife");
        return c;
    }

    @Test
    @DisplayName("a contact is minted defaults; screening is false and danger-signs stays null")
    void recordsWithSafeDefaults() {
        var saved = service.record(minimalContact());
        assertThat(saved.getPostnatalContactId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo("RECORDED");
        assertThat(saved.getSensitivityClass()).isEqualTo("FULL_CLINICAL");
        // Completed-action booleans default false, so a null never reads as done.
        assertThat(saved.getScreeningComplete()).isFalse();
        assertThat(saved.getLactationSupportGiven()).isFalse();
        assertThat(saved.getContraceptionDiscussed()).isFalse();
        assertThat(saved.getReferralMade()).isFalse();
        // The one that must NOT be defaulted: an uncompleted screen leaves this null — "not screened",
        // never the reassuring "no danger signs".
        assertThat(saved.getDangerSignsPresent()).isNull();
    }

    @Test
    @DisplayName("a replayed offline packet returns the existing contact, not a second visit")
    void offlineReplayIsIdempotent() {
        var existing = minimalContact();
        existing.setPostnatalContactId(UUID.randomUUID());
        existing.setClientOfflineId("device-A");
        when(repo.findByTenantIdAndClientOfflineId(TENANT, "device-A")).thenReturn(Optional.of(existing));

        var incoming = minimalContact();
        incoming.setClientOfflineId("device-A");
        assertThat(service.record(incoming).getPostnatalContactId()).isEqualTo(existing.getPostnatalContactId());
    }

    @Test
    @DisplayName("a contact that no longer carries contraception content is cleared of its old stamp")
    void aStaleCategoryIsClearedRatherThanLeftBesideFullClinical() {
        var c = minimalContact();
        // As a re-record would arrive: the trio is still populated from an earlier version of this
        // contact, while the contraception content that justified it has been removed.
        c.setContraceptionDiscussed(Boolean.FALSE);
        c.setPostpartumContraceptionEpisodeId(null);
        c.setSensitivityClass("SPECIALLY_PROTECTED");
        c.setConfidentialityCategory("SEXUAL_REPRODUCTIVE_HEALTH");
        c.setConfidentialityBasis(ConfidentialityStamper.BASIS_SUBJECT_UNDER_POLICY_AGE);
        c.setConfidentialityPolicyVersion("old-policy-0.9.0");

        var saved = service.record(c);

        assertThat(saved.getSensitivityClass()).isEqualTo("FULL_CLINICAL");
        assertThat(saved.getConfidentialityCategory())
                .as("a stale category beside FULL_CLINICAL claims SRH content the row no longer holds, "
                        + "and the shadow-withhold signal would keep counting it")
                .isNull();
        assertThat(saved.getConfidentialityBasis()).isNull();
        assertThat(saved.getConfidentialityPolicyVersion()).isNull();
    }

    @Test
    @DisplayName("an explicitly completed screen with no danger signs is preserved, not overwritten")
    void completedScreenPreserved() {
        var c = minimalContact();
        c.setScreeningComplete(Boolean.TRUE);
        c.setDangerSignsPresent(Boolean.FALSE);
        var saved = service.record(c);
        assertThat(saved.getScreeningComplete()).isTrue();
        assertThat(saved.getDangerSignsPresent()).isFalse();
    }

    /**
     * A confidentiality provider that resolves nothing: no policy, no demographics. The stamper then
     * records POLICY_UNAVAILABLE and leaves the class at FULL_CLINICAL — which is exactly the
     * stamp-fails-open behaviour these tests should see by default.
     */
    private static ConfidentialCarePolicyProvider inertConfidentiality() {
        ConfidentialCarePolicyProvider p = mock(ConfidentialCarePolicyProvider.class);
        when(p.classStampingEnabled()).thenReturn(false);
        when(p.policyAsOf(any(), any())).thenReturn(new zw.gov.mohcc.impilo.reproductive.confidentiality.ConfidentialCarePolicy() {
            public java.util.Optional<Integer> confidentialFromGuardianAgeYears(
                    zw.gov.mohcc.impilo.reproductive.confidentiality.ConfidentialityCategory c) {
                return java.util.Optional.empty();
            }
            public java.util.Optional<Boolean> nonTerminationLossConfidentialFromGuardian() {
                return java.util.Optional.empty();
            }
            public String contentVersion() { return null; }
            public boolean ratified() { return false; }
        });
        when(p.subjectAt(any(), any(), any(), any()))
                .thenReturn(new ConfidentialityStamper.SubjectContext(null, false));
        return p;
    }

}
