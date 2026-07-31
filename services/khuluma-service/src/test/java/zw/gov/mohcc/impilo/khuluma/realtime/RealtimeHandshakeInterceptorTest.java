package zw.gov.mohcc.impilo.khuluma.realtime;

import zw.gov.mohcc.impilo.realtime.RealtimeEvent;
import zw.gov.mohcc.impilo.realtime.RealtimeHub;
import zw.gov.mohcc.impilo.realtime.RealtimeInstance;
import zw.gov.mohcc.impilo.realtime.RealtimeSubscription;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RealtimeHandshakeInterceptorTest {

    private static final UUID TENANT = UUID.randomUUID();

    @Mock private WsTokenValidator validator;

    private boolean handshake(MockHttpServletRequest req, Map<String, Object> attrs) {
        var interceptor = new RealtimeHandshakeInterceptor(validator);
        var resp = new MockHttpServletResponse();
        return interceptor.beforeHandshake(new ServletServerHttpRequest(req),
                new ServletServerHttpResponse(resp), null, attrs);
    }

    @Test
    void trusted_headers_are_accepted_without_a_token() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Tenant-ID", TENANT.toString());
        req.addHeader("X-Actor-ID", "prov-1");
        Map<String, Object> attrs = new HashMap<>();

        assertThat(handshake(req, attrs)).isTrue();
        assertThat(attrs).containsEntry("khuluma.actorId", "prov-1");
    }

    @Test
    void a_valid_token_is_accepted_and_identity_comes_from_claims() {
        when(validator.validate("jwt")).thenReturn(new WsTokenValidator.WsIdentity(TENANT, "user-9", "CITIZEN"));
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer jwt");
        Map<String, Object> attrs = new HashMap<>();

        assertThat(handshake(req, attrs)).isTrue();
        assertThat(attrs).containsEntry("khuluma.actorId", "user-9");
        assertThat(attrs).containsEntry("khuluma.tenantId", TENANT);
    }

    @Test
    void unverified_query_param_identity_is_rejected_the_hole_is_closed() {
        lenient().when(validator.validate(null)).thenReturn(null);
        MockHttpServletRequest req = new MockHttpServletRequest();
        // The old hole: passing identity via query params with NO token.
        req.setParameter("tenantId", TENANT.toString());
        req.setParameter("actorId", "attacker");
        Map<String, Object> attrs = new HashMap<>();

        assertThat(handshake(req, attrs)).isFalse();
        assertThat(attrs).doesNotContainKey("khuluma.actorId");
    }

    @Test
    void no_credentials_is_rejected() {
        assertThat(handshake(new MockHttpServletRequest(), new HashMap<>())).isFalse();
    }
}
