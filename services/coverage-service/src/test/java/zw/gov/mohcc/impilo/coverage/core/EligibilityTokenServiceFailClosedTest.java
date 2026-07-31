package zw.gov.mohcc.impilo.coverage.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EligibilityTokenServiceFailClosedTest {

    private static final String STRONG = "coverage-test-ruvimbo-token-secret-32b!!";

    @Test
    void failsClosedOnBlankOrPlaceholder() {
        assertThrows(IllegalStateException.class, () -> new EligibilityTokenService(""));
        assertThrows(IllegalStateException.class, () -> new EligibilityTokenService(null));
        assertThrows(IllegalStateException.class, () -> new EligibilityTokenService("too-short"));
        assertThrows(IllegalStateException.class,
                () -> new EligibilityTokenService("ruvimbo-dev-eligibility-secret-change-me"));
    }

    @Test
    void issueAndVerifyWithStrongSecret() {
        EligibilityTokenService svc = new EligibilityTokenService(STRONG);
        var issued = svc.issue("dec-1", "cov-1", "cpid-1", "fac-1", "OPD", "RUVIMBO", 3600);
        assertNotNull(issued.token());
        var verified = svc.verify(issued.token());
        assertTrue(verified.valid());
    }
}
