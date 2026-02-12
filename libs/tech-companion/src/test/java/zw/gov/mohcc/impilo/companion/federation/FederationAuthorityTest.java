package zw.gov.mohcc.impilo.companion.federation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FederationAuthorityTest {

    @Test
    void isNationalReturnsTrueForNational() {
        assertTrue(FederationAuthority.isNational("national"));
    }

    @Test
    void isNationalIsCaseInsensitive() {
        assertTrue(FederationAuthority.isNational("NATIONAL"));
        assertTrue(FederationAuthority.isNational("National"));
    }

    @Test
    void isNationalReturnsFalseForPrivatePod() {
        assertFalse(FederationAuthority.isNational("private-harare"));
    }

    @Test
    void requireNationalSucceedsForNational() {
        assertDoesNotThrow(() -> FederationAuthority.requireNational("national"));
    }

    @Test
    void requireNationalThrowsForPrivatePod() {
        var ex = assertThrows(FederationAuthorityViolationException.class,
                () -> FederationAuthority.requireNational("private-harare"));
        assertEquals("private-harare", ex.getPodId());
        assertEquals("FEDERATION_AUTHORITY_VIOLATION", ex.getErrorCode());
    }
}
