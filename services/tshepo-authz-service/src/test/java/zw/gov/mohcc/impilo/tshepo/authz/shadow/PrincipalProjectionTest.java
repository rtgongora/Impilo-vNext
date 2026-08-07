package zw.gov.mohcc.impilo.tshepo.authz.shadow;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The rule this whole projection exists to hold: <b>a Keycloak role never selects a persona.</b>
 *
 * <p>Today's {@code deriveActorType()} does the opposite — it reaches for role names and, failing
 * to match any (lowercase literals against UPPERCASE realm roles), calls everyone a PROVIDER.
 * These tests pin the replacement behaviour before it is wired to anything.</p>
 */
class PrincipalProjectionTest {

    @Test
    void aRoleAloneNeverMakesSomeoneAnAdministrator() {
        // The exact failure mode to prevent: someone holds a privileged realm role but has no
        // proven appointment. They are a person using My Life, not an administrator.
        var p = PrincipalProjection.project(
                List.of("REGISTRY_ADMIN", "SYSTEM_ADMIN", "HIE_ADMIN"), null, null, false);

        assertEquals(PrincipalProjection.PrincipalClass.HUMAN, p.principalClass());
        assertEquals(PrincipalProjection.PERSONA_MY_LIFE, p.activePersona());
        assertEquals("CITIZEN", p.legacyActorType());
    }

    @Test
    void anOrdinaryCitizenIsMyLife() {
        var p = PrincipalProjection.project(List.of("CITIZEN"), null, null, false);
        assertEquals(PrincipalProjection.PERSONA_MY_LIFE, p.activePersona());
        assertEquals("CITIZEN", p.legacyActorType());
    }

    @Test
    void aProfessionalIdentityWithoutAWorkAnchorIsMyProfessionalNotWork() {
        var p = PrincipalProjection.project(List.of("CLINICIAN"), null, "PRV-1", false);
        assertEquals(PrincipalProjection.PERSONA_MY_PROFESSIONAL, p.activePersona());
        assertEquals("PROVIDER", p.legacyActorType());
    }

    @Test
    void myProfessionalWithoutAProviderIdIsNotAProvider() {
        // Claiming the persona is not the same as holding the identity.
        var p = PrincipalProjection.project(List.of("CLINICIAN"), null, null, false);
        assertEquals(PrincipalProjection.PERSONA_MY_LIFE, p.activePersona());
        assertEquals("CITIZEN", p.legacyActorType());
    }

    @Test
    void aProvenWorkModeBecomesThePersonaVerbatim() {
        // No parallel vocabulary: WorkMode already declares anchor kind, clinical access and
        // purpose of use, so it IS the persona.
        var p = PrincipalProjection.project(List.of("CLINICIAN"), "CLINICAL_CARE", "PRV-1", false);
        assertEquals("CLINICAL_CARE", p.activePersona());
        assertEquals("PROVIDER", p.legacyActorType());
    }

    @Test
    void anAdministrativeWorkModeProjectsToOperatorNotProvider() {
        var p = PrincipalProjection.project(List.of("SYSTEM_ADMIN"), "REGULATORY_OPERATIONS", null, false);
        assertEquals("REGULATORY_OPERATIONS", p.activePersona());
        assertEquals("OPERATOR", p.legacyActorType());
    }

    @Test
    void aProvenContextOutranksTheRolesEntirely() {
        // Roles say clinician; the proven context says facility management. The context wins,
        // because it is the thing that was proved.
        var p = PrincipalProjection.project(
                List.of("CLINICIAN", "DOCTOR"), "FACILITY_MANAGEMENT", "PRV-1", false);
        assertEquals("FACILITY_MANAGEMENT", p.activePersona());
        assertEquals("OPERATOR", p.legacyActorType());
    }

    @Test
    void aServiceCredentialIsNotAHuman() {
        var p = PrincipalProjection.project(List.of("SYSTEM_ADMIN"), null, null, true);
        assertEquals(PrincipalProjection.PrincipalClass.SERVICE, p.principalClass());
        assertEquals("SERVICE", p.legacyActorType());
    }

    @Test
    void noRolesAtAllStillYieldsAPrincipal() {
        // The current browser reality: the PDP sees an empty role list. That must not crash or
        // silently become a provider.
        var p = PrincipalProjection.project(List.of(), null, null, false);
        assertEquals(PrincipalProjection.PERSONA_MY_LIFE, p.activePersona());
        assertEquals("CITIZEN", p.legacyActorType());
    }
}
