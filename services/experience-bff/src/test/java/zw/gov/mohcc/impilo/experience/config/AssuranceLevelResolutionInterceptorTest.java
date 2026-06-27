package zw.gov.mohcc.impilo.experience.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.IdentityAssuranceServiceClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AssuranceLevelResolutionInterceptor} — the BFF side of the LOA-propagation
 * fix (G-CZO-01). Proves it surfaces the caller's CURRENT identity-assurance level for citizen
 * actors only, and is fail-safe.
 */
@ExtendWith(MockitoExtension.class)
class AssuranceLevelResolutionInterceptorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock private IdentityAssuranceServiceClient identityAssuranceClient;
    @Mock private ObjectProvider<IdentityAssuranceServiceClient> clientProvider;

    private AssuranceLevelResolutionInterceptor interceptor() {
        return new AssuranceLevelResolutionInterceptor(clientProvider);
    }

    private static MockHttpServletRequest citizenRequest() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(CompanionHeaders.ACTOR_ID, "cpid-12345");
        req.addHeader(CompanionHeaders.ACTOR_TYPE, "CITIZEN");
        return req;
    }

    @Test
    void citizenWithLoa3_setsResolvedAttribute() throws Exception {
        when(clientProvider.getIfAvailable()).thenReturn(identityAssuranceClient);
        when(identityAssuranceClient.getAssuranceStatus())
                .thenReturn(MAPPER.readTree("{\"currentLevel\":\"LOA3\"}"));
        MockHttpServletRequest req = citizenRequest();

        boolean proceed = interceptor().preHandle(req, new MockHttpServletResponse(), new Object());

        assertTrue(proceed);
        assertEquals("LOA3",
                req.getAttribute(AssuranceLevelResolutionInterceptor.RESOLVED_ASSURANCE_LEVEL_ATTR),
                "Citizen's current assurance level must be exposed for propagation");
    }

    @Test
    void providerActor_doesNotResolve() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(CompanionHeaders.ACTOR_ID, "provider-1");
        req.addHeader(CompanionHeaders.ACTOR_TYPE, "PROVIDER");

        interceptor().preHandle(req, new MockHttpServletResponse(), new Object());

        assertNull(req.getAttribute(AssuranceLevelResolutionInterceptor.RESOLVED_ASSURANCE_LEVEL_ATTR));
        verifyNoInteractions(identityAssuranceClient);
    }

    @Test
    void noActorId_doesNotResolve() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(CompanionHeaders.ACTOR_TYPE, "CITIZEN");

        interceptor().preHandle(req, new MockHttpServletResponse(), new Object());

        assertNull(req.getAttribute(AssuranceLevelResolutionInterceptor.RESOLVED_ASSURANCE_LEVEL_ATTR));
        verifyNoInteractions(identityAssuranceClient);
    }

    @Test
    void identityAssuranceUnreachable_failsSafeWithoutAttribute() {
        when(clientProvider.getIfAvailable()).thenReturn(identityAssuranceClient);
        when(identityAssuranceClient.getAssuranceStatus())
                .thenThrow(new RuntimeException("connection refused"));
        MockHttpServletRequest req = citizenRequest();

        boolean proceed = interceptor().preHandle(req, new MockHttpServletResponse(), new Object());

        assertTrue(proceed, "Resolution failure must not block the request");
        assertNull(req.getAttribute(AssuranceLevelResolutionInterceptor.RESOLVED_ASSURANCE_LEVEL_ATTR),
                "Fail-safe: no attribute means policy falls back to the ACR level");
    }
}
