package zw.gov.mohcc.impilo.experience.khuluma;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The BFF defense-in-depth gate for Khuluma comms: core comms is open to any authenticated actor,
 * while binding a conversation to a patient requires a clinical-plane role (mirrors khuluma.rego +
 * the V017 policy_rule seed).
 */
class KhulumaAccessPolicyServiceTest {

    private final KhulumaAccessPolicyService policy = new KhulumaAccessPolicyService();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String... roles) {
        var authorities = List.of(roles).stream().map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken("sub-1", "n/a", List.copyOf(authorities)));
    }

    @Test
    void commsActor_allowedWhenAuthenticated_deniedWhenNot() {
        authenticateAs("ROLE_PATIENT");
        assertThatCode(policy::requireCommsActor).doesNotThrowAnyException();
        assertThatCode(policy::requireCallActor).doesNotThrowAnyException();

        SecurityContextHolder.clearContext();
        assertThatThrownBy(policy::requireCommsActor)
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
    }

    @Test
    void patientLink_requiresClinicalRole() {
        authenticateAs("ROLE_CLINICIAN");
        assertThatCode(policy::requireClinicalActorForPatientLink).doesNotThrowAnyException();
    }

    @Test
    void patientLink_deniedForNonClinicalActor() {
        authenticateAs("ROLE_PATIENT");
        assertThatThrownBy(policy::requireClinicalActorForPatientLink)
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void patientLink_unauthenticatedIs401() {
        assertThatThrownBy(policy::requireClinicalActorForPatientLink)
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
    }
}
