package zw.gov.mohcc.impilo.sharedkernel.schema;

import java.util.Objects;

/**
 * Validates that events carry a valid schema_version field.
 * Part of the v1.1 companion spec enforcement surface.
 */
public final class SchemaValidator {

    /**
     * Require that a schema version is present and valid (>= 1).
     *
     * @param schemaVersion the schema_version from the event envelope
     * @param eventType     the event_type (included in error messages for diagnostics)
     * @throws SchemaValidationException if schemaVersion is null or less than 1
     */
    public void requireSchemaVersion(Integer schemaVersion, String eventType) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        if (schemaVersion == null) {
            throw new SchemaValidationException(
                    eventType + ": schema_version missing/invalid — value was null");
        }
        if (schemaVersion < 1) {
            throw new SchemaValidationException(
                    eventType + ": schema_version missing/invalid — value was " + schemaVersion);
        }
    }
}
