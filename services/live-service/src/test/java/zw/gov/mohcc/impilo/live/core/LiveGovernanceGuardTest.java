package zw.gov.mohcc.impilo.live.core;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.live.domain.LiveMode;
import zw.gov.mohcc.impilo.live.domain.OwningService;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LiveGovernanceGuardTest {

    private final LiveGovernanceGuard guard = new LiveGovernanceGuard();

    private LiveEventEntity clinical() {
        LiveEventEntity e = new LiveEventEntity();
        e.setTenantId(UUID.randomUUID());
        e.setMode(LiveMode.CLINICAL_SESSION.name());
        e.setOwningService(OwningService.TELEMEDICINE.name());
        e.setOwningEntityId("APPT-1");
        e.setClinicalContextRef("APPT-1");
        e.setClientId("CLIENT-1");
        e.setProviderId("PROV-1");
        e.setFacilityId("FAC-1");
        e.setConsentPolicy("REQUIRED");
        e.setVisibility("RESTRICTED");
        return e;
    }

    @Test
    void clinicalWithoutContextIsRejected() {
        LiveEventEntity e = clinical();
        e.setClinicalContextRef(null);
        e.setOwningEntityId(null);
        assertThatThrownBy(() -> guard.validateForCreate(e))
                .isInstanceOf(LiveGovernanceException.class)
                .satisfies(ex -> assertThat(((LiveGovernanceException) ex).getCode())
                        .isIn("CLINICAL_CONTEXT_REQUIRED", "MISSING_OWNING_ENTITY"));
    }

    @Test
    void clinicalWithoutClinicalIdentitiesIsRejected() {
        LiveEventEntity e = clinical();
        e.setClientId(null);
        assertThatThrownBy(() -> guard.validateForCreate(e))
                .isInstanceOf(LiveGovernanceException.class);
    }

    @Test
    void clinicalMustBeOwnedByTelemedicine() {
        LiveEventEntity e = clinical();
        e.setOwningService(OwningService.FUNDO.name());
        assertThatThrownBy(() -> guard.validateForCreate(e))
                .isInstanceOf(LiveGovernanceException.class)
                .satisfies(ex -> assertThat(((LiveGovernanceException) ex).getCode())
                        .isEqualTo("CLINICAL_OWNER_REQUIRED"));
    }

    @Test
    void clinicalRecordingDisabledByDefault() {
        LiveEventEntity e = clinical();
        e.setRecordingAllowed(true);
        e.setRecordingPolicy(null);
        guard.validateForCreate(e);
        assertThat(e.isRecordingAllowed()).isFalse();
        assertThat(e.getRecordingPolicy()).isEqualTo("DISABLED");
    }

    @Test
    void clinicalRecordingPermittedWhenPolicyExplicit() {
        LiveEventEntity e = clinical();
        e.setRecordingPolicy("ALLOWED");
        e.setRecordingAllowed(true);
        guard.validateForCreate(e);
        assertThat(e.isRecordingAllowed()).isTrue();
    }

    @Test
    void clinicalCannotBePublic() {
        LiveEventEntity e = clinical();
        e.setVisibility("PUBLIC");
        assertThatThrownBy(() -> guard.validateForCreate(e))
                .isInstanceOf(LiveGovernanceException.class)
                .satisfies(ex -> assertThat(((LiveGovernanceException) ex).getCode())
                        .isEqualTo("CLINICAL_VISIBILITY_INVALID"));
    }

    @Test
    void validClinicalPasses() {
        LiveEventEntity e = clinical();
        guard.validateForCreate(e);
        assertThat(e.getConsentPolicy()).isEqualTo("REQUIRED");
        assertThat(e.isConsentRequired()).isTrue();
    }

    @Test
    void publicBroadcastGetsSafeModerationDefaults() {
        LiveEventEntity e = new LiveEventEntity();
        e.setTenantId(UUID.randomUUID());
        e.setMode(LiveMode.PUBLIC_BROADCAST.name());
        e.setOwningService(OwningService.PUBLIC_HEALTH.name());
        e.setOwningEntityId("CAMP-1");
        e.setVisibility("PUBLIC");
        guard.validateForCreate(e);
        assertThat(e.getModerationPolicy()).isEqualTo("MODERATED");
        assertThat(e.isSpeakerVerificationRequired()).isTrue();
    }

    @Test
    void orphanOwnedEventIsRejected() {
        LiveEventEntity e = new LiveEventEntity();
        e.setTenantId(UUID.randomUUID());
        e.setMode(LiveMode.WEBINAR_CPD.name());
        e.setOwningService(OwningService.FUNDO.name());
        assertThatThrownBy(() -> guard.validateForCreate(e))
                .isInstanceOf(LiveGovernanceException.class)
                .satisfies(ex -> assertThat(((LiveGovernanceException) ex).getCode())
                        .isEqualTo("MISSING_OWNING_ENTITY"));
    }

    @Test
    void publicParticipantCannotJoinClinicalSession() {
        LiveEventEntity e = clinical();
        assertThatThrownBy(() -> guard.validateJoin(e, "ATTENDEE", "PUBLIC", false))
                .isInstanceOf(LiveGovernanceException.class)
                .satisfies(ex -> assertThat(((LiveGovernanceException) ex).getCode())
                        .isIn("RESTRICTED_EVENT_PUBLIC_DENIED", "CLINICAL_PUBLIC_DENIED"));
    }

    @Test
    void clinicalJoinRequiresConsent() {
        LiveEventEntity e = clinical();
        assertThatThrownBy(() -> guard.validateJoin(e, "ATTENDEE", "CLIENT", false))
                .isInstanceOf(LiveGovernanceException.class)
                .satisfies(ex -> assertThat(((LiveGovernanceException) ex).getCode())
                        .isEqualTo("CLINICAL_CONSENT_NOT_GRANTED"));
    }

    @Test
    void clinicalJoinSucceedsWithConsent() {
        LiveEventEntity e = clinical();
        guard.validateJoin(e, "ATTENDEE", "CLIENT", true);
    }

    @Test
    void publicParticipantCannotJoinRestrictedProfessionalEvent() {
        LiveEventEntity e = new LiveEventEntity();
        e.setMode(LiveMode.PROFESSIONAL_MEETING.name());
        e.setVisibility("RESTRICTED");
        assertThatThrownBy(() -> guard.validateJoin(e, "VIEWER", "PUBLIC", false))
                .isInstanceOf(LiveGovernanceException.class)
                .satisfies(ex -> assertThat(((LiveGovernanceException) ex).getCode())
                        .isEqualTo("RESTRICTED_EVENT_PUBLIC_DENIED"));
    }

    @Test
    void publicParticipantCanJoinPublicBroadcast() {
        LiveEventEntity e = new LiveEventEntity();
        e.setMode(LiveMode.PUBLIC_BROADCAST.name());
        e.setVisibility("PUBLIC");
        guard.validateJoin(e, "VIEWER", "PUBLIC", false);
    }

    @Test
    void legacyInferenceMapsClinicalKeywords() {
        assertThat(LiveMode.inferLegacy("Clinical case conference", "PROFESSIONAL", "PROVIDER"))
                .isEqualTo(LiveMode.CLINICAL_SESSION);
        assertThat(LiveMode.inferLegacy("CPD", "PROFESSIONAL", "PROVIDER"))
                .isEqualTo(LiveMode.WEBINAR_CPD);
        assertThat(LiveMode.inferLegacy("PUBLIC_HEALTH_EDUCATION", "CITIZEN", "FACILITY"))
                .isEqualTo(LiveMode.PUBLIC_BROADCAST);
        assertThat(LiveMode.inferLegacy("Programme sync", "PROFESSIONAL", "ENTERPRISE"))
                .isEqualTo(LiveMode.PROFESSIONAL_MEETING);
    }
}
