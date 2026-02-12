package zw.gov.mohcc.impilo.federation.error;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.companion.error.ErrorCodes;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelope;
import zw.gov.mohcc.impilo.companion.federation.FederationAuthorityViolationException;

import static org.junit.jupiter.api.Assertions.*;

class FederationConflictHandlerTest {

    @Test
    void denyCreatesCorrectEnvelope() {
        ErrorEnvelope envelope = FederationConflictHandler.deny(
                "private-harare", "Patient merge", "req-1", "corr-1");

        assertEquals(ErrorCodes.FEDERATION_AUTHORITY_VIOLATION, envelope.error().code());
        assertTrue(envelope.error().message().contains("Patient merge"));
        assertEquals("private-harare", envelope.error().details().get("pod_id"));
        assertEquals("req-1", envelope.error().request_id());
        assertEquals("corr-1", envelope.error().correlation_id());
    }

    @Test
    void assertNationalSucceedsForNational() {
        assertDoesNotThrow(() -> FederationConflictHandler.assertNational("national", "merge"));
    }

    @Test
    void assertNationalThrowsForPrivate() {
        assertThrows(FederationAuthorityViolationException.class,
                () -> FederationConflictHandler.assertNational("private-bulawayo", "merge"));
    }
}
