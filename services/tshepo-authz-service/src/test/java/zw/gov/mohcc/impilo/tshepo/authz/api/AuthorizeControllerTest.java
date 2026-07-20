package zw.gov.mohcc.impilo.tshepo.authz.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import zw.gov.mohcc.impilo.tshepo.authz.core.PolicyEngine;
import zw.gov.mohcc.impilo.tshepo.authz.dto.AuthzInternalRequest;
import zw.gov.mohcc.impilo.tshepo.authz.session.SessionAssuranceRouter;
import zw.gov.mohcc.impilo.tshepo.authz.session.SessionInfo;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.AuthzResponse;
import zw.gov.mohcc.impilo.tshepo.contracts.headers.TrustHeaders;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Edge trust-hardening (TPL-1): the validated JWT identity must be AUTHORITATIVE over
 * client-supplied trust headers, so a header-injection attempt cannot impersonate another actor.
 */
@ExtendWith(MockitoExtension.class)
class AuthorizeControllerTest {

    @Mock private PolicyEngine policyEngine;
    @Mock private SessionAssuranceRouter sessionRouter;

    private AuthorizeController controller() {
        return new AuthorizeController(policyEngine, sessionRouter, new ObjectMapper(),
                new zw.gov.mohcc.impilo.tshepo.authz.service.IdentityIntrospectionClient(
                        new org.springframework.web.client.RestTemplate(),
                        new zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties()));
    }

    @Test
    void jwtIdentityOverridesSpoofedHeaders() {
        UUID tenant = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(sessionRouter.validateSession(anyString()))
                .thenReturn(new SessionInfo("authenticated-self", "CITIZEN",
                        List.of("CITIZEN"), tenant, 2, "sess-1", "iss"));
        when(policyEngine.evaluate(any())).thenReturn(AuthzResponse.deny("X", "y", 0));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("GET");
        req.setRequestURI("/internal/v1/mobile/citizen/immunizations");
        req.addHeader(TrustHeaders.TENANT_ID, "22222222-2222-2222-2222-222222222222"); // spoofed tenant
        req.addHeader(TrustHeaders.ACTOR_ID, "victim-other-person");                    // spoofed actor
        req.addHeader(TrustHeaders.ACTOR_TYPE, "PROVIDER");                             // spoofed type
        req.addHeader(TrustHeaders.PURPOSE_OF_USE, "TREATMENT");
        req.addHeader("authorization", "Bearer token");

        controller().authorize(req);

        ArgumentCaptor<AuthzInternalRequest> captor = ArgumentCaptor.forClass(AuthzInternalRequest.class);
        verify(policyEngine).evaluate(captor.capture());
        AuthzInternalRequest evaluated = captor.getValue();
        assertEquals("authenticated-self", evaluated.actorId(), "JWT sub must override spoofed X-Actor-ID");
        assertEquals("CITIZEN", evaluated.actorType(), "JWT actorType must override spoofed X-Actor-Type");
        assertEquals(tenant, evaluated.tenantId(), "JWT tenant must override spoofed X-Tenant-ID");
    }

    @Test
    void clientHeadersUsedWhenSessionLacksClaims_serviceToken() {
        // A service-to-service token carries no sub/type/tenant — headers remain the source.
        when(sessionRouter.validateSession(anyString()))
                .thenReturn(new SessionInfo(null, null, List.of("SERVICE"), null, 0, "s", "iss"));
        when(policyEngine.evaluate(any())).thenReturn(AuthzResponse.deny("X", "y", 0));

        UUID tenant = UUID.fromString("33333333-3333-3333-3333-333333333333");
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("POST");
        req.setRequestURI("/internal/v1/something");
        req.addHeader(TrustHeaders.TENANT_ID, tenant.toString());
        req.addHeader(TrustHeaders.ACTOR_ID, "svc-actor");
        req.addHeader(TrustHeaders.ACTOR_TYPE, "SERVICE");
        req.addHeader(TrustHeaders.PURPOSE_OF_USE, "SYSTEM");
        req.addHeader("authorization", "Bearer service-token");

        controller().authorize(req);

        ArgumentCaptor<AuthzInternalRequest> captor = ArgumentCaptor.forClass(AuthzInternalRequest.class);
        verify(policyEngine).evaluate(captor.capture());
        assertEquals("svc-actor", captor.getValue().actorId(), "header is the source when the session lacks sub");
        assertEquals(tenant, captor.getValue().tenantId());
    }
}
