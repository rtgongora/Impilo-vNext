package zw.gov.mohcc.impilo.msikaflow.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import zw.gov.mohcc.impilo.msikaflow.api.controller.InternalFulfillmentController;
import zw.gov.mohcc.impilo.msikaflow.api.controller.InternalSelectionDeliveryController;
import zw.gov.mohcc.impilo.msikaflow.core.FulfilmentDispatchService;
import zw.gov.mohcc.impilo.msikaflow.core.FulfillmentService;
import zw.gov.mohcc.impilo.msikaflow.core.OrderStateMachine;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the callback rule is bound to the real URLs, through the real filter chain.
 *
 * <p>The sibling {@code InternalCallbackAuthorizationTest} proves the decision logic in
 * isolation, which would still pass if the request matchers named a path that does not
 * exist. That failure mode is not theoretical and it is not loud: an unmatched pattern falls
 * through to {@code anyRequest().authenticated()}, which keeps refusing anonymous callers —
 * so the unauthenticated case still looks correct — while quietly admitting <em>any</em>
 * authenticated principal to a courier callback. Only the wrong-caller assertions below
 * distinguish the two.
 */
@WebMvcTest(controllers = {InternalFulfillmentController.class, InternalSelectionDeliveryController.class})
@Import(SecurityConfig.class)
class InternalCallbackFilterChainTest {

    private static final String ORDER_CALLBACK =
            "/internal/v1/msika-flow/orders/ORD-1/dispatch-status";
    private static final String SELECTION_CALLBACK =
            "/internal/v1/msika-flow/selections/SEL-1/delivery-status";

    @Autowired
    private MockMvc mockMvc;

    @MockBean private FulfillmentService fulfillmentService;
    @MockBean private OrderStateMachine orderStateMachine;
    @MockBean private FulfilmentDispatchService fulfilmentDispatchService;
    @MockBean private JwtDecoder jwtDecoder;

    private static MockHttpServletRequestBuilder callback(String path) {
        return post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Tenant-ID", "11111111-1111-1111-1111-111111111111")
                .header("X-Pod-ID", "national-spine")
                .header("X-Request-ID", "req-1")
                .header("X-Correlation-ID", "corr-1")
                .header("X-Actor-ID", "nhume-service")
                .header("X-Actor-Type", "SYSTEM")
                .content("{\"status\":\"DELIVERED\",\"deliveryId\":\"D-1\"}");
    }

    @Test
    void orderCallback_withNoCredential_isRefused() throws Exception {
        mockMvc.perform(callback(ORDER_CALLBACK)).andExpect(status().isUnauthorized());
    }

    @Test
    void selectionCallback_withNoCredential_isRefused() throws Exception {
        mockMvc.perform(callback(SELECTION_CALLBACK)).andExpect(status().isUnauthorized());
    }

    @Test
    void orderCallback_fromSomeOtherAuthenticatedService_isRefused() throws Exception {
        mockMvc.perform(callback(ORDER_CALLBACK)
                        .with(jwt().jwt(j -> j.claim("azp", "vashandi-workforce-service"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void selectionCallback_fromSomeOtherAuthenticatedService_isRefused() throws Exception {
        mockMvc.perform(callback(SELECTION_CALLBACK)
                        .with(jwt().jwt(j -> j.claim("azp", "vashandi-workforce-service"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void orderCallback_fromNhume_reachesTheHandler() throws Exception {
        mockMvc.perform(callback(ORDER_CALLBACK)
                .with(jwt().jwt(j -> j.claim("azp", "nhume-service"))));

        // Reaching the service is the proof that security admitted the call. Asserting on the
        // status instead would pass for the wrong reason: a refusal and a handler blowing up on
        // a stubbed-out collaborator are both "not 200".
        verify(fulfillmentService).handleDispatchStatus(eq("ORD-1"), eq("DELIVERED"), eq("D-1"),
                any(), any(), any());
    }

    @Test
    void selectionCallback_fromNhume_reachesTheHandler() throws Exception {
        when(fulfilmentDispatchService.onDeliveryStatus(any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        mockMvc.perform(callback(SELECTION_CALLBACK)
                .with(jwt().jwt(j -> j.claim("azp", "nhume-service"))));

        verify(fulfilmentDispatchService).onDeliveryStatus(eq("SEL-1"), eq("DELIVERED"), eq("D-1"),
                any(), any(), any());
    }

    @Test
    void aRefusedCallbackNeverReachesTheHandlerAtAll() throws Exception {
        mockMvc.perform(callback(ORDER_CALLBACK)
                .with(jwt().jwt(j -> j.claim("azp", "vashandi-workforce-service"))));
        mockMvc.perform(callback(SELECTION_CALLBACK));

        verifyNoInteractions(fulfillmentService, fulfilmentDispatchService);
    }
}
