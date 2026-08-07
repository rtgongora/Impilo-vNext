package zw.gov.mohcc.impilo.tuso.core;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the outcome of the ACT_AS_APPLICANT audit.
 *
 * <p>The duty briefly included {@code PROVIDER}, on the inference that a practitioner registering
 * their own practice is a real applicant. Measured, that flow does not exist on this path: every UI
 * consumer of the facility-application writes is in the {@code registry} zone, the
 * practitioner-facing regulatory applicant surface targets varapi, and the applicant is recorded on
 * {@code FacilityApplicationEntity} as free-text name/email/phone/organisation with no provider or
 * health id.</p>
 *
 * <p>This test exists because the inference was reasonable and someone will make it again. If a
 * sole-practitioner application path is genuinely built, the estate's own pattern for "the provider
 * acting for themselves" is an identity check — {@code PicNominationService.respond} requires
 * {@code ctx.actorId()} to equal the nominee's health id — not an actor-type widening.</p>
 */
class FacilityApplicantDutyAuditTest {

    private static Set<String> enforcedActorTypes(Class<?> owner, String field) throws Exception {
        Field f = owner.getDeclaredField(field);
        f.setAccessible(true);
        Object duty = f.get(null);
        Field types = duty.getClass().getDeclaredField("actorTypes");
        types.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<String> v = (Set<String>) types.get(duty);
        return v;
    }

    @Test
    void aClinicalSessionCannotActAsTheApplicantOnAFacilityApplication() throws Exception {
        assertThat(enforcedActorTypes(FacilityRegulatoryService.class, "ACT_AS_APPLICANT"))
                .as("createApplication / submitApplication / uploadDocument are registry-console acts")
                .doesNotContain("PROVIDER", "CITIZEN", "CAREGIVER");
    }

    @Test
    void aClinicalSessionCannotRespondAsTheApplicantToAnInformationRequest() throws Exception {
        assertThat(enforcedActorTypes(ApplicationGovernanceService.class, "ACT_AS_APPLICANT"))
                .doesNotContain("PROVIDER", "CITIZEN", "CAREGIVER");
    }

    /**
     * The applicant, inspector and committee duties enforce the same actor types, because actor
     * type cannot tell them apart — that is a role distinction and the KNOWN GAP in ActorTypeGuard.
     * They must stay separate constants so the distinction is still stated when roles arrive.
     */
    @Test
    void theApplicantAndInspectorDutiesAreDistinctEvenThoughTheyEnforceTheSameTypes() throws Exception {
        Set<String> applicant = enforcedActorTypes(FacilityRegulatoryService.class, "ACT_AS_APPLICANT");
        Set<String> inspector = enforcedActorTypes(FacilityRegulatoryService.class, "ACT_AS_INSPECTOR");
        assertThat(applicant).isEqualTo(inspector);

        Field a = FacilityRegulatoryService.class.getDeclaredField("ACT_AS_APPLICANT");
        Field i = FacilityRegulatoryService.class.getDeclaredField("ACT_AS_INSPECTOR");
        a.setAccessible(true);
        i.setAccessible(true);
        assertThat(a.get(null))
                .as("same enforced set, different duty — collapsing them would erase the distinction")
                .isNotEqualTo(i.get(null));
    }

    /**
     * The practitioner path that must keep working. {@code respond} carries no actor-type gate at
     * all; it checks that the caller IS the nominee. Narrowing REVIEW_PIC to exclude PROVIDER does
     * not touch it, and this test would go red if someone "tidied" an actor-type guard onto it.
     */
    @Test
    void aPractitionerRespondingToTheirOwnPicNominationIsGatedByIdentityNotActorType() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/zw/gov/mohcc/impilo/tuso/core/PicNominationService.java"));
        int start = source.indexOf("public PicNominationEntity respond(");
        assertThat(start).as("respond() must still exist").isGreaterThan(0);
        String body = source.substring(start, source.indexOf("\n    }", start));

        assertThat(body)
                .as("respond() is the practitioner acting for themselves — identity, not actor type")
                .doesNotContain("assertRole")
                .contains("ctx.actorId()");
    }
}
