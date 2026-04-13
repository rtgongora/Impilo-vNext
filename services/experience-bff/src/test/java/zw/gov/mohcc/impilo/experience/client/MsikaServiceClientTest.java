package zw.gov.mohcc.impilo.experience.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MsikaServiceClientTest {

    @Test
    void searchCallsMsikaRegistryEndpointWithQueryParameters() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        MsikaServiceClient client = new MsikaServiceClient(restTemplate, endpoints());
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("q", "bandage");
        params.add("kind", "PRODUCT");
        params.add("page", "0");

        server.expect(requestTo("http://msika/v1/search?q=bandage&kind=PRODUCT&page=0"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"data\":{\"items\":[]}}", MediaType.APPLICATION_JSON));

        assertEquals("{\"data\":{\"items\":[]}}", client.search(params).getBody());
        server.verify();
    }

    @Test
    void getItemCallsCanonicalItemEndpoint() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        MsikaServiceClient client = new MsikaServiceClient(restTemplate, endpoints());

        server.expect(requestTo("http://msika/v1/items/ITEM-001"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"data\":{\"id\":\"ITEM-001\"}}", MediaType.APPLICATION_JSON));

        assertEquals("{\"data\":{\"id\":\"ITEM-001\"}}", client.getItem("ITEM-001").getBody());
        server.verify();
    }

    @Test
    void listCatalogsCallsCanonicalCatalogEndpoint() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        MsikaServiceClient client = new MsikaServiceClient(restTemplate, endpoints());
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("status", "DRAFT");

        server.expect(requestTo("http://msika/v1/catalogs?status=DRAFT"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"items\":[]}", MediaType.APPLICATION_JSON));

        assertEquals("{\"items\":[]}", client.listCatalogs(params).getBody());
        server.verify();
    }

    @Test
    void createCatalogPostsJsonToCanonicalCatalogEndpoint() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        MsikaServiceClient client = new MsikaServiceClient(restTemplate, endpoints());

        server.expect(requestTo("http://msika/v1/catalogs"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"name\":\"Facility Pharmacy\",\"scope\":\"FACILITY\"}"))
                .andRespond(withSuccess("{\"catalogId\":\"cat-1\"}", MediaType.APPLICATION_JSON));

        assertEquals(
                "{\"catalogId\":\"cat-1\"}",
                client.createCatalog("{\"name\":\"Facility Pharmacy\",\"scope\":\"FACILITY\"}").getBody()
        );
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
                null, null, null, null, null, null, null, null, null, null
        );
    }
}
