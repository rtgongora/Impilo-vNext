package zw.gov.mohcc.impilo.sharedkernel.events;

/**
 * Resolves the active emit mode from the environment.
 * <p>
 * Reads the {@code EMIT_MODE} environment variable (or system property as fallback).
 * Defaults to {@link EmitMode#DUAL} when unset, allowing safe dual-write migration.
 */
public final class DualEmitPolicy {

    private static final String ENV_KEY = "EMIT_MODE";

    /**
     * Resolve the current emit mode.
     * Precedence: system property → environment variable → DUAL.
     */
    public EmitMode mode() {
        String value = System.getProperty(ENV_KEY);
        if (value == null || value.isBlank()) {
            value = System.getenv(ENV_KEY);
        }
        if (value == null || value.isBlank()) {
            return EmitMode.DUAL;
        }
        try {
            return EmitMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return EmitMode.DUAL;
        }
    }

    /** Should the publisher emit legacy-format events? */
    public boolean emitLegacy() {
        EmitMode m = mode();
        return m == EmitMode.LEGACY_ONLY || m == EmitMode.DUAL;
    }

    /** Should the publisher emit v1.1-format events? */
    public boolean emitV11() {
        EmitMode m = mode();
        return m == EmitMode.V1_1_ONLY || m == EmitMode.DUAL;
    }
}
