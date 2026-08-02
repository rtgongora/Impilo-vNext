package zw.gov.mohcc.impilo.tshepo.authz.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * Refuses to start when {@code tshepo.authz.opa-mode} is ENFORCE without cited parity evidence.
 *
 * <p>Promoting OPA from shadow to authoritative is a claim that its verdicts have been proven to
 * match the Java engine's. A mode string cannot carry that claim — anyone who can set an
 * environment variable could otherwise make an unvalidated policy corpus decide production
 * authorization, from a deployment that never ran a comparison. So ENFORCE additionally requires
 * {@code tshepo.authz.opa-parity-evidence-path} to name a file that exists at startup.</p>
 *
 * <p>Failing closed here means failing to <em>start</em>, not failing open at request time: a
 * misconfigured instance never serves a decision at all.</p>
 *
 * <p>This is deliberately a startup check rather than a request-path check. A request-path check
 * would be evaluated after the process is already accepting traffic, so the first request would
 * decide the outcome; a startup check makes the misconfiguration impossible to serve from.</p>
 */
@Component
public class OpaEnforceGate implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(OpaEnforceGate.class);

    static final Set<String> VALID_MODES = Set.of("OFF", "SHADOW", "ENFORCE");

    private final AuthzProperties properties;

    public OpaEnforceGate(AuthzProperties properties) {
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        validate(properties.getOpaMode(), properties.getOpaParityEvidencePath());
        // Same reasoning as the OPA mode: an unrecognised context-header mode must not degrade to
        // PASSTHROUGH, which would leave client-supplied facility and purpose flowing while the
        // configuration says the control is on.
        var contextMode = zw.gov.mohcc.impilo.tshepo.authz.core.ContextHeaderAuthority.Mode
                .parse(properties.getContextHeaderMode());
        log.info("Context-header mode {} — {}", contextMode, switch (contextMode) {
            case PASSTHROUGH -> "client-supplied operating context travels unchanged";
            case SHADOW -> "measuring client-vs-validated divergence, emitting nothing";
            case AUTHORITATIVE -> "emitting duty-token-validated context; client values are replaced";
        });
    }

    /** Package-visible so the gate can be exercised directly, without a Spring context. */
    static void validate(String rawMode, String evidencePath) {
        String mode = rawMode == null ? "OFF" : rawMode.trim().toUpperCase(Locale.ROOT);

        if (!VALID_MODES.contains(mode)) {
            throw new IllegalStateException(
                    "tshepo.authz.opa-mode is '" + rawMode + "'; expected one of " + VALID_MODES
                            + ". An unrecognised mode is refused rather than silently treated as OFF, "
                            + "because a typo would otherwise disable the comparison invisibly.");
        }

        if (!"ENFORCE".equals(mode)) {
            log.info("OPA mode {} — Java PolicyEngine is authoritative.", mode);
            return;
        }

        if (evidencePath == null || evidencePath.isBlank()) {
            throw new IllegalStateException(
                    "tshepo.authz.opa-mode=ENFORCE requires tshepo.authz.opa-parity-evidence-path "
                            + "to name the parity evidence justifying the promotion. It is empty. "
                            + "OPA must not become authoritative on the strength of a mode flag.");
        }

        Path path = Path.of(evidencePath);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException(
                    "tshepo.authz.opa-mode=ENFORCE cites parity evidence at '" + evidencePath
                            + "' but no such file exists. Refusing to start: an unproven policy "
                            + "corpus must not decide authorization.");
        }

        log.warn("OPA mode ENFORCE — OPA is authoritative, parity evidence: {}", evidencePath);
    }
}
