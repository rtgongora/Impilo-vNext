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
public class GeneralLedgerServiceClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public GeneralLedgerServiceClient(RestTemplate serviceRestTemplate,
                                      ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.generalLedgerBaseUrl();
    }

    public JsonNode getAccounts() {
        return get(baseUrl + "/internal/v1/gl/accounts");
    }

    public JsonNode postAccountsSeed() {
        return exchange(HttpMethod.POST, baseUrl + "/internal/v1/gl/accounts/seed", null);
    }

    public JsonNode getOpenPeriods() {
        return get(baseUrl + "/internal/v1/gl/periods/open");
    }

    public JsonNode getJournals(String periodId) {
        return get(UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/gl/journals")
                .queryParam("period_id", periodId).toUriString());
    }

    public JsonNode postJournal(JsonNode body) {
        return exchange(HttpMethod.POST, baseUrl + "/internal/v1/gl/journals", body);
    }

    public JsonNode postJournalPost(String entryId) {
        return exchange(HttpMethod.POST, baseUrl + "/internal/v1/gl/journals/" + entryId + "/post", null);
    }

    public JsonNode getTrialBalance(String periodId) {
        return get(UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/gl/trial-balance")
                .queryParam("period_id", periodId).toUriString());
    }

    public JsonNode getIncomeStatement(String periodId) {
        return get(UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/gl/financial-statements/income-statement")
                .queryParam("period_id", periodId).toUriString());
    }

    public JsonNode getBalanceSheet(String periodId) {
        return get(UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/gl/financial-statements/balance-sheet")
                .queryParam("period_id", periodId).toUriString());
    }

    public JsonNode getBudgets(int fiscalYear) {
        return get(UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/gl/budgets")
                .queryParam("fiscalYear", fiscalYear).toUriString());
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
