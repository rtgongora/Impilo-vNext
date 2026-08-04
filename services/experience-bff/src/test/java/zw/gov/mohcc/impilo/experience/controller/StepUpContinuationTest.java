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
    @DisplayName("the shell still owns an executed proof that it sends the continuation")
    void shellForwardsContinuation() throws Exception {
        // The BFF half is worthless if the browser never sends the parameter — that intent is
        // preserved. What changed is how it is proven.
        //
        // This assertion used to slice beginOidcLogin's body (from its `export` to the next
        // `\nexport `) and search that slice for `query.set("continuation"`. The query
        // construction was later extracted, deliberately, into the shared buildOidcLoginUrl so
        // the windowed sign-in and the full-page redirect cannot drift apart on acr or the
        // continuation id. The call moved one function past the slice boundary and this test
        // went red while the behaviour was, and remained, correct.
        //
        // A Java test reading TypeScript source can only ever assert an implementation detail.
        // The behavioural proof now lives where it can be executed —
        // ui/one-ui-shell/src/lib/auth/__tests__/web-session-oidc-login.test.ts — which builds the
        // authorize URL and asserts the continuation survives to the query string, for both the
        // shared builder and the redirect path. Deleting the continuation line fails 4 of its 7
        // tests.
        //
        // What remains here is the cross-language guard that survives refactoring: the shell
        // module must still wire the continuation onto the authorize query somewhere, and the
        // executed proof must still exist.
        Path shell = Path.of("../../ui/one-ui-shell/src/lib/auth/web-session.ts");
        assertThat(Files.isRegularFile(shell))
                .as("shell source not found at %s — this assertion would pass vacuously",
                        shell.toAbsolutePath())
                .isTrue();
        String src = Files.readString(shell);

        assertThat(src)
                .as("beginOidcLogin is gone or renamed — the shell login entry point moved")
                .contains("export function beginOidcLogin");
        // Module-scoped, not function-scoped: which exported function performs the wiring is an
        // implementation choice; that the module performs it at all is the contract.
        assertThat(src)
                .as("the shell must put the continuation on the authorize query string")
                .contains("query.set(\"continuation\"");

        Path executedProof = Path.of(
                "../../ui/one-ui-shell/src/lib/auth/__tests__/web-session-oidc-login.test.ts");
        assertThat(Files.isRegularFile(executedProof))
                .as("the executed continuation proof is missing at %s — this guard is the only "
                        + "thing standing between a silent deletion and an unnoticed regression",
                        executedProof.toAbsolutePath())
                .isTrue();
    }
}
