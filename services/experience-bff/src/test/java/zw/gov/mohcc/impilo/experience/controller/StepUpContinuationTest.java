package zw.gov.mohcc.impilo.experience.controller;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import zw.gov.mohcc.impilo.experience.auth.session.OidcSessionService;
import zw.gov.mohcc.impilo.experience.auth.session.WebAuthSessionProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A step-up is raised <em>by</em> a challenge, so it is the one flow certain to have a continuation
 * to carry — and it was the one flow that dropped it. {@code OidcSessionService.begin} has always
 * accepted a continuation id and {@code GET /authorize} has always taken the query parameter; the
 * step-up entry point simply called the five-argument overload.
 *
 * <p>The visible consequence: a user challenged mid-journey re-authenticated successfully and
 * landed nowhere near what they had been doing.</p>
 */
class StepUpContinuationTest {

    private OidcSessionService sessions;
    private OidcSessionController controller;

    @BeforeEach
    void setUp() {
        sessions = mock(OidcSessionService.class);
        controller = new OidcSessionController(sessions, new WebAuthSessionProperties());
        when(sessions.begin(any(), any(), any(), any(), any(), any()))
                .thenReturn(URI.create("https://idp/auth"));
    }

    @Test
    @DisplayName("step-up forwards the continuation to the authorize round trip")
    void stepUpForwardsContinuation() {
        controller.stepUp(new MockHttpServletRequest("POST", "/internal/v1/auth/oidc/step-up"), null,
                Map.of("returnTo", "/patients/1/notes",
                       "requiredAcr", "urn:impilo:aal2",
                       "continuation", "cont-abc"));

        verify(sessions).begin(eq("/patients/1/notes"), eq("urn:impilo:aal2"), isNull(),
                isNull(), isNull(), eq("cont-abc"));
        verify(sessions, never()).begin(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("a step-up without a continuation still works — this is additive, not required")
    void stepUpWithoutContinuationStillWorks() {
        controller.stepUp(new MockHttpServletRequest("POST", "/internal/v1/auth/oidc/step-up"), null,
                Map.of("returnTo", "/home", "requiredAcr", "urn:impilo:aal2"));

        verify(sessions).begin(eq("/home"), eq("urn:impilo:aal2"), isNull(), isNull(), isNull(),
                isNull());
    }

    @Test
    @DisplayName("the shell sends the continuation it was issued")
    void shellForwardsContinuation() throws Exception {
        // The BFF half is worthless if the browser never sends the parameter. beginOidcLogin
        // accepted returnTo/loginHint/acr and silently omitted continuation, so the round trip was
        // broken at the client end regardless of what the server accepted.
        Path shell = Path.of("../../ui/one-ui-shell/src/lib/auth/web-session.ts");
        assertThat(Files.isRegularFile(shell))
                .as("shell source not found at %s — this assertion would pass vacuously",
                        shell.toAbsolutePath())
                .isTrue();
        String src = Files.readString(shell);

        int begin = src.indexOf("export function beginOidcLogin");
        assertThat(begin).as("beginOidcLogin is gone or renamed").isGreaterThan(-1);
        // Slice to the NEXT export, not the next line-leading brace: the parameter type literal
        // closes with `}): void {` at column zero, which truncated this to the signature alone.
        int end = src.indexOf("\nexport ", begin + 1);
        String body = src.substring(begin, end > -1 ? end : src.length());

        assertThat(body)
                .as("beginOidcLogin must put the continuation on the authorize query string")
                .contains("query.set(\"continuation\"");
    }
}
