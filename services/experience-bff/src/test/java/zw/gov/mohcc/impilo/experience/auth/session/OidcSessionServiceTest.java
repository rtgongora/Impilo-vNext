package zw.gov.mohcc.impilo.experience.auth.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class OidcSessionServiceTest {

    @Test
    void acceptsOnlyLocalJourneyDestinations() {
        assertEquals("/", OidcSessionService.safeReturnTo(null));
        assertEquals("/work/clinical?tab=queue", OidcSessionService.safeReturnTo("/work/clinical?tab=queue"));
    }

    @Test
    void rejectsOpenRedirectAndHeaderInjectionCandidates() {
        assertInvalid("https://attacker.example/path");
        assertInvalid("//attacker.example/path");
        assertInvalid("/\\attacker.example/path");
        assertInvalid("/safe\r\nLocation: https://attacker.example");
    }

    private static void assertInvalid(String candidate) {
        OidcSessionService.OidcProtocolException error = assertThrows(
                OidcSessionService.OidcProtocolException.class,
                () -> OidcSessionService.safeReturnTo(candidate));
        assertEquals("INVALID_RETURN_TO", error.code());
    }
}
