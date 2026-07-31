package zw.gov.mohcc.impilo.experience.auth.session;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.stereotype.Component;

/** Resolves explicit API bearer tokens first, then the opaque browser session cookie. */
@Component
public class SessionBearerTokenResolver implements BearerTokenResolver {
    private final DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
    private final OidcSessionService sessions;

    public SessionBearerTokenResolver(OidcSessionService sessions) { this.sessions = sessions; }

    @Override
    public String resolve(HttpServletRequest request) {
        String explicit = delegate.resolve(request);
        if (explicit != null) return explicit;
        String sessionId = cookie(request, WebAuthSessionStore.SESSION_COOKIE);
        return sessions.validAccessToken(sessionId).orElse(null);
    }

    public static String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies()).filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue).findFirst().orElse(null);
    }
}
