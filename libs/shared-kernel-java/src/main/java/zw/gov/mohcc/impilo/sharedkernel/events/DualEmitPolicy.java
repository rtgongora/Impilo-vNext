package zw.gov.mohcc.impilo.sharedkernel.events;

/**
 * Resolves the active emit mode from the environment.
 * <p>
 * Precedence (highest → lowest):
 * <ol>
 *   <li>{@code EMIT_MODE} system property (e.g. {@code -DEMIT_MODE=V1_1_ONLY})</li>
 *   <li>{@code EMIT_MODE} environment variable</li>
 *   <li>Application config fallback (e.g. from application.yml)</li>
 *   <li>Default: {@link EmitMode#DUAL}</li>
 * </ol>
 */
public final class DualEmitPolicy {

    private static final String ENV_KEY = "EMIT_MODE";

    private final String configFallback;

    /** Create with no config fallback (original behavior). */
    public DualEmitPolicy() {
        this(null);
    }

    /**
     * Create with an application config fallback value.
     * @param configFallback value from application.yml (e.g. "V1_1_ONLY"), may be null
     */
    public DualEmitPolicy(String configFallback) {
        this.configFallback = configFallback;
    }

    /**
     * Resolve the current emit mode using full precedence chain.
     */
    public EmitMode mode() {
        // 1. System property
        String value = System.getProperty(ENV_KEY);
        if (value != null && !value.isBlank()) {
            return parseOrDefault(value);
        }
        // 2. Environment variable
        value = System.getenv(ENV_KEY);
        if (value != null && !value.isBlank()) {
            return parseOrDefault(value);
        }
        // 3. Application config fallback
        if (configFallback != null && !configFallback.isBlank()) {
            return parseOrDefault(configFallback);
        }
        // 4. Default
        return EmitMode.DUAL;
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

    private static EmitMode parseOrDefault(String value) {
        try {
            return EmitMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return EmitMode.DUAL;
        }
    }
}
