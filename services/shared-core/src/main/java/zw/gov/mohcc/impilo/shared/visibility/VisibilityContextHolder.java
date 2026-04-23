package zw.gov.mohcc.impilo.shared.visibility;

import zw.gov.mohcc.impilo.tshepo.contracts.dto.VisibilityProfile;

import java.util.Optional;

/**
 * Thread-local visibility profile propagated from gateway-injected obligation headers.
 */
public final class VisibilityContextHolder {

    private static final ThreadLocal<VisibilityProfile> CTX = new ThreadLocal<>();

    private VisibilityContextHolder() {
    }

    public static void set(VisibilityProfile profile) {
        CTX.set(profile);
    }

    public static void clear() {
        CTX.remove();
    }

    public static Optional<VisibilityProfile> current() {
        return Optional.ofNullable(CTX.get());
    }
}
