package zw.gov.mohcc.impilo.experience.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MsikaFlowServiceClientTest {

    @Test
    void validateCartPostsJsonToMsikaFlow() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        MsikaFlowServiceClient client = new MsikaFlowServiceClient(restTemplate, endpoints());

        server.expect(requestTo("http://msika-flow/v1/cart/validate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"items\":[{\"msikaCoreCode\":\"ITEM-001\",\"qty\":2}],\"channel\":\"FACILITY\"}"))
                .andRespond(withSuccess("{\"data\":{\"valid\":true}}", MediaType.APPLICATION_JSON));

        assertEquals(
                "{\"data\":{\"valid\":true}}",
                client.validateCart("{\"items\":[{\"msikaCoreCode\":\"ITEM-001\",\"qty\":2}],\"channel\":\"FACILITY\"}").getBody()
        );
        server.verify();
    }

    @Test
    void createOrderPostsJsonToMsikaFlowOrders() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        MsikaFlowServiceClient client = new MsikaFlowServiceClient(restTemplate, endpoints());

        server.expect(requestTo("http://msika-flow/v1/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"orderType\":\"FACILITY_PROCUREMENT\",\"facilityId\":\"f100\",\"lines\":[]}"))
                .andRespond(withSuccess("{\"data\":{\"orderId\":\"ORDER-001\"}}", MediaType.APPLICATION_JSON));

        assertEquals(
                "{\"data\":{\"orderId\":\"ORDER-001\"}}",
                client.createOrder("{\"orderType\":\"FACILITY_PROCUREMENT\",\"facilityId\":\"f100\",\"lines\":[]}").getBody()
        );
        server.verify();
    }

    @Test
    void getVendorOrdersUsesCanonicalVendorQueueRoute() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        MsikaFlowServiceClient client = new MsikaFlowServiceClient(restTemplate, endpoints());
        MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        query.add("status", "ROUTED");

        server.expect(requestTo("http://msika-flow/v1/vendors/vendor-1/orders?status=ROUTED"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"content\":[]}", MediaType.APPLICATION_JSON));

        assertEquals("{\"content\":[]}", client.getVendorOrders("vendor-1", query).getBody());
        server.verify();
    }

    @Test
    void issuePickupTokenUsesCanonicalPickupIssueRoute() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        MsikaFlowServiceClient client = new MsikaFlowServiceClient(restTemplate, endpoints());

        server.expect(requestTo("http://msika-flow/v1/orders/ORDER-1/pickup/issue"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"pickupToken\":\"PICKUP-1\"}", MediaType.APPLICATION_JSON));

        assertEquals("{\"pickupToken\":\"PICKUP-1\"}", client.issuePickupToken("ORDER-1").getBody());
        server.verify();
    }

    @Test
    void listOpsReviewsUsesCanonicalPendingReviewsRoute() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        MsikaFlowServiceClient client = new MsikaFlowServiceClient(restTemplate, endpoints());
        MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        query.add("status", "PENDING");

        server.expect(requestTo("http://msika-flow/v1/ops/reviews/pending?status=PENDING"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"items\":[]}", MediaType.APPLICATION_JSON));

        assertEquals("{\"items\":[]}", client.listOpsReviews(query).getBody());
        server.verify();
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return new ServiceClientConfig.ServiceEndpoints(
                "http://pct", "http://oros", "http://pharmacy", "http://butano",
                "http://msika", "http://msika-flow", "http://mushex", "http://vito",
                "http://tuso", "http://varapi", "http://documents", "http://costa",
                "http://coverage", "http://surveillance", "http://campaigns", "http://indawo",
                "http://governance", "http://landela", "http://notifications",
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null
        );
    }
}
