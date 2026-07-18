package zw.gov.mohcc.impilo.companion.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the patient-context guard (Identity Contract §7.2): a write
 * carrying X-Subject-ID is only valid with a patient-context token whose
 * {@code sub_ref} claim matches the header.
 */
@DisplayName("SubjectContextFilter")
class SubjectContextFilterTest {

    private static final String CPID = "cccccccc-cccc-4ccc-8ccc-cccccccccccc";

    private static String tokenWithSubRef(String subRef) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"EdDSA\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"scope\":\"patient-context\",\"sub_ref\":\"" + subRef + "\"}").getBytes());
        return header + "." + payload + ".sig";
    }

    @Test
    @DisplayName("matching sub_ref validates")
    void matchingSubRef_valid() {
        assertNull(SubjectContextFilter.validate(CPID, tokenWithSubRef(CPID)));
    }

    @Test
    @DisplayName("missing token is a violation")
    void missingToken_violation() {
        assertEquals("MISSING_TOKEN", SubjectContextFilter.validate(CPID, null));
        assertEquals("MISSING_TOKEN", SubjectContextFilter.validate(CPID, " "));
    }

    @Test
    @DisplayName("mismatched sub_ref is a violation — a caller cannot smuggle a different subject")
    void mismatchedSubRef_violation() {
        assertEquals("SUBJECT_MISMATCH",
                SubjectContextFilter.validate(CPID, tokenWithSubRef("dddddddd-dddd-4ddd-8ddd-dddddddddddd")));
    }

    @Test
    @DisplayName("garbage token is a violation")
    void garbageToken_violation() {
        assertEquals("UNPARSEABLE_TOKEN", SubjectContextFilter.validate(CPID, "not-a-jws"));
        assertEquals("UNPARSEABLE_TOKEN", SubjectContextFilter.validate(CPID, "a.!!!!.c"));
    }

    @Test
    @DisplayName("mode parsing tolerates unknown values (falls back to OFF)")
    void modeParsing() {
        assertEquals(SubjectContextFilter.Mode.OFF, new SubjectContextFilter(null).mode());
        assertEquals(SubjectContextFilter.Mode.OFF, new SubjectContextFilter("bogus").mode());
        assertEquals(SubjectContextFilter.Mode.LOG, new SubjectContextFilter("log").mode());
        assertEquals(SubjectContextFilter.Mode.ENFORCE, new SubjectContextFilter("ENFORCE").mode());
    }
}
