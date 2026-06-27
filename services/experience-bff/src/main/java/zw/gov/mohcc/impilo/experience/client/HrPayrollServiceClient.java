package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
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
public class HrPayrollServiceClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public HrPayrollServiceClient(RestTemplate serviceRestTemplate,
                                  ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.hrPayrollBaseUrl();
    }

    public JsonNode getEmployees() {
        return get(baseUrl + "/internal/v1/hr/employees");
    }

    public JsonNode postEmployee(JsonNode body) {
        return exchange(HttpMethod.POST, baseUrl + "/internal/v1/hr/employees", body);
    }

    public JsonNode getContracts(String employeeId) {
        return get(UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/hr/contracts")
                .queryParam("employee_id", employeeId).toUriString());
    }

    // Leave + attendance are owned by Vashandi (read via VashandiServiceClient) — removed here.

    public JsonNode getPayrollRuns() {
        return get(baseUrl + "/internal/v1/hr/payroll/runs");
    }

    public JsonNode getPayslips(String runId) {
        return get(UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/hr/payroll/payslips")
                .queryParam("run_id", runId).toUriString());
    }

    public JsonNode getDeductions() {
        return get(baseUrl + "/internal/v1/hr/deductions");
    }

    public JsonNode postPayrollRun(JsonNode body) {
        return exchange(HttpMethod.POST, baseUrl + "/internal/v1/hr/payroll/runs", body);
    }

    public JsonNode postPayrollCalculate(String runId) {
        return postWithoutBody(baseUrl + "/internal/v1/hr/payroll/runs/" + runId + "/calculate");
    }

    public JsonNode postPayrollPay(String runId, JsonNode body) {
        JsonNode payload = body != null ? body : JsonNodeFactory.instance.objectNode();
        return exchange(HttpMethod.POST, baseUrl + "/internal/v1/hr/payroll/runs/" + runId + "/pay", payload);
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

    private JsonNode postWithoutBody(String url) {
        HttpHeaders h = new HttpHeaders();
        HttpEntity<Void> e = new HttpEntity<>(h);
        return restTemplate.exchange(url, HttpMethod.POST, e, JsonNode.class).getBody();
    }
}
