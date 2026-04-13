package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

@Component
public class ProcurementServiceClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public ProcurementServiceClient(RestTemplate serviceRestTemplate,
                                    ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.procurementBaseUrl();
    }

    public JsonNode getSuppliers() {
        return get(baseUrl + "/internal/v1/procurement/suppliers");
    }

    public JsonNode getRequisitions() {
        return get(baseUrl + "/internal/v1/procurement/requisitions");
    }

    public JsonNode getPurchaseOrders() {
        return get(baseUrl + "/internal/v1/procurement/purchase-orders");
    }

    public JsonNode getGrn() {
        return get(baseUrl + "/internal/v1/procurement/grn");
    }

    public JsonNode getInvoices() {
        return get(baseUrl + "/internal/v1/procurement/invoices");
    }

    public JsonNode getRfqs() {
        return get(baseUrl + "/internal/v1/procurement/rfqs");
    }

    public JsonNode getRfqResponses(String rfqId) {
        return get(baseUrl + "/internal/v1/procurement/rfqs/" + rfqId + "/responses");
    }

    public JsonNode postGrn(JsonNode body) {
        return exchange(HttpMethod.POST, baseUrl + "/internal/v1/procurement/grn", body);
    }

    private JsonNode get(String url) {
        ResponseEntity<JsonNode> r = restTemplate.getForEntity(url, JsonNode.class);
        return r.getBody();
    }

    private JsonNode exchange(HttpMethod method, String url, JsonNode body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<JsonNode> e = new HttpEntity<>(body, h);
        return restTemplate.exchange(url, method, e, JsonNode.class).getBody();
    }
}
