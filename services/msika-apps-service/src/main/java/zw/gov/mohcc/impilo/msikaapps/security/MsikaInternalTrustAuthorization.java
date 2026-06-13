package zw.gov.mohcc.impilo.msikaapps.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.ServletRequestAttributes;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

/**
 * Preview-safe first-party trust for marketplace launcher S2S calls from experience-bff.
 *
 * <p>When {@code impilo.security.disable-oauth-for-tests=true}, allows the launcher
 * endpoint for INTERNAL calls from {@code experience-bff} that carry the v1.1 companion
 * header quartet. Production remains JWT-only via {@code isAuthenticated()}.</p>
 */
@Component("msikaInternalTrustAuthorization")
public class MsikaInternalTrustAuthorization {

    static final String TRUSTED_BFF_SERVICE_ID = "experience-bff";

    private final boolean disableOauthForTests;

    public MsikaInternalTrustAuthorization(
            @Value("${impilo.security.disable-oauth-for-tests:false}") boolean disableOauthForTests) {
        this.disableOauthForTests = disableOauthForTests;
    }

    /**
     * Allows marketplace launcher access for trusted BFF internal S2S in preview/test only.
     */
    public boolean allowLauncher() {
        if (!disableOauthForTests) {
            return false;
        }
        return hasTrustedBffInternalHeaders();
    }

    private boolean hasTrustedBffInternalHeaders() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return false;
        }
        if (!"INTERNAL".equalsIgnoreCase(request.getHeader(CompanionHeaders.ACCESS_MODE))) {
            return false;
        }
        if (!TRUSTED_BFF_SERVICE_ID.equalsIgnoreCase(request.getHeader(CompanionHeaders.SERVICE_ID))) {
            return false;
        }
        for (String header : CompanionHeaders.HARD_REQUIRED) {
            String value = request.getHeader(header);
            if (value == null || value.isBlank()) {
                return false;
            }
        }
        try {
            zw.gov.mohcc.impilo.companion.context.RequestContextHolder.require();
            return true;
        } catch (IllegalStateException ex) {
            return false;
        }
    }

    private static HttpServletRequest currentRequest() {
        var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            return servletAttrs.getRequest();
        }
        return null;
    }
}
