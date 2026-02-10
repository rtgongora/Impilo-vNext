package zw.gov.mohcc.impilo.sharedkernel.schema;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SchemaValidatorTest {

    private final SchemaValidator validator = new SchemaValidator();

    @Test
    void throwsWhenSchemaVersionIsNull() {
        SchemaValidationException ex = assertThrows(SchemaValidationException.class,
                () -> validator.requireSchemaVersion(null, "vito.patient.created"));
        assertTrue(ex.getMessage().contains("vito.patient.created"));
        assertTrue(ex.getMessage().contains("schema_version missing/invalid"));
    }

    @Test
    void throwsWhenSchemaVersionIsZero() {
        SchemaValidationException ex = assertThrows(SchemaValidationException.class,
                () -> validator.requireSchemaVersion(0, "vito.patient.updated"));
        assertTrue(ex.getMessage().contains("vito.patient.updated"));
        assertTrue(ex.getMessage().contains("schema_version missing/invalid"));
    }

    @Test
    void throwsWhenSchemaVersionIsNegative() {
        assertThrows(SchemaValidationException.class,
                () -> validator.requireSchemaVersion(-1, "vito.patient.merged"));
    }

    @Test
    void acceptsValidSchemaVersion() {
        assertDoesNotThrow(() -> validator.requireSchemaVersion(1, "vito.patient.created"));
        assertDoesNotThrow(() -> validator.requireSchemaVersion(42, "oros.order.placed"));
    }

    @Test
    void throwsWhenEventTypeIsNull() {
        assertThrows(NullPointerException.class,
                () -> validator.requireSchemaVersion(1, null));
    }
}
