package zw.gov.mohcc.impilo.security.input;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class InputSanitizerTest {

    // --- requireNonBlank ---

    @Test
    void requireNonBlank_rejectsNull() {
        var ex = assertThrows(InputValidationException.class,
                () -> InputSanitizer.requireNonBlank(null, "name", 100));
        assertEquals("REQUIRED_FIELD_MISSING", ex.getErrorCode());
    }

    @Test
    void requireNonBlank_rejectsBlank() {
        assertThrows(InputValidationException.class,
                () -> InputSanitizer.requireNonBlank("   ", "name", 100));
    }

    @Test
    void requireNonBlank_rejectsTooLong() {
        var ex = assertThrows(InputValidationException.class,
                () -> InputSanitizer.requireNonBlank("a".repeat(101), "name", 100));
        assertEquals("FIELD_TOO_LONG", ex.getErrorCode());
    }

    @Test
    void requireNonBlank_stripsWhitespace() {
        assertEquals("hello", InputSanitizer.requireNonBlank("  hello  ", "name", 100));
    }

    // --- requireUuid ---

    @Test
    void requireUuid_acceptsValidUuid() {
        String uuid = "550e8400-e29b-41d4-a716-446655440000";
        assertEquals(uuid, InputSanitizer.requireUuid(uuid, "id"));
    }

    @Test
    void requireUuid_rejectsInvalidFormat() {
        assertThrows(InputValidationException.class,
                () -> InputSanitizer.requireUuid("not-a-uuid", "id"));
    }

    // --- requireSafeText ---

    @Test
    void requireSafeText_acceptsSafeInput() {
        assertEquals("John Doe", InputSanitizer.requireSafeText("John Doe", "name", 100));
    }

    // --- Injection detection ---

    @ParameterizedTest
    @ValueSource(strings = {
            "<script>alert(1)</script>",
            "javascript:void(0)",
            "onclick=alert(1)",
            "${jndi:ldap://evil}",
            "{{constructor.constructor}}",
            "1; DROP TABLE users",
            "admin'--"
    })
    void rejectsInjectionAttempts(String malicious) {
        var ex = assertThrows(InputValidationException.class,
                () -> InputSanitizer.requireNonBlank(malicious, "field", 4096));
        assertEquals("INJECTION_DETECTED", ex.getErrorCode());
    }

    @Test
    void rejectsNullByteInjection() {
        var ex = assertThrows(InputValidationException.class,
                () -> InputSanitizer.requireNonBlank("hello\0world", "field", 100));
        assertEquals("INJECTION_DETECTED", ex.getErrorCode());
    }

    // --- requireIdentifier ---

    @Test
    void requireIdentifier_acceptsValidCode() {
        assertEquals("TREATMENT_V2", InputSanitizer.requireIdentifier("TREATMENT_V2", "code"));
    }

    @Test
    void requireIdentifier_rejectsSpaces() {
        assertThrows(InputValidationException.class,
                () -> InputSanitizer.requireIdentifier("has space", "code"));
    }

    // --- requireEmail ---

    @Test
    void requireEmail_acceptsValid() {
        assertEquals("user@example.com", InputSanitizer.requireEmail("user@example.com", "email"));
    }

    @Test
    void requireEmail_rejectsInvalid() {
        assertThrows(InputValidationException.class,
                () -> InputSanitizer.requireEmail("not-an-email", "email"));
    }

    // --- requirePhone ---

    @Test
    void requirePhone_acceptsE164() {
        assertEquals("+263771234567", InputSanitizer.requirePhone("+263771234567", "phone"));
    }

    @Test
    void requirePhone_rejectsLocalFormat() {
        assertThrows(InputValidationException.class,
                () -> InputSanitizer.requirePhone("0771234567", "phone"));
    }

    // --- stripHtmlTags ---

    @Test
    void stripHtmlTags_removesAllTags() {
        assertEquals("hello world", InputSanitizer.stripHtmlTags("<b>hello</b> <i>world</i>"));
    }

    @Test
    void stripHtmlTags_handlesNull() {
        assertNull(InputSanitizer.stripHtmlTags(null));
    }

    // --- truncate ---

    @Test
    void truncate_shortStringUnchanged() {
        assertEquals("hello", InputSanitizer.truncate("hello", 10));
    }

    @Test
    void truncate_longStringTruncated() {
        String result = InputSanitizer.truncate("hello world", 6);
        assertEquals(6, result.length());
        assertTrue(result.endsWith("…"));
    }

    // --- optional fields ---

    @Test
    void optionalSafeText_returnsNullForBlank() {
        assertNull(InputSanitizer.optionalSafeText(null, "field", 100));
        assertNull(InputSanitizer.optionalSafeText("", "field", 100));
    }

    @Test
    void optionalUuid_returnsNullForBlank() {
        assertNull(InputSanitizer.optionalUuid(null, "field"));
    }
}
