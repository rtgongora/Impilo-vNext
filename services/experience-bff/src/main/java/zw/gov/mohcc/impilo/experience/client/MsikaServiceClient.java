package zw.gov.mohcc.impilo.experience.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

/**
 * Read-side client for the MSIKA product and service registry.
 *
 * <p>Returns raw upstream payloads so Experience can expose canonical BFF
 * routes without inventing a second registry envelope.</p>
 */
@Component
public class MsikaServiceClient {

    private static final Logger log = LoggerFactory.getLogger(MsikaServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public MsikaServiceClient(RestTemplate serviceRestTemplate,
                              ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.msikaBaseUrl();
    }

    public ResponseEntity<String> search(MultiValueMap<String, String> queryParams) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/search")
                .queryParams(queryParams)
                .toUriString();
        log.info("MSIKA: Searching registry with params={}", queryParams.keySet());
        return restTemplate.getForEntity(url, String.class);
    }

    public ResponseEntity<String> getItem(String itemId) {
        String url = baseUrl + "/v1/items/" + itemId;
        log.info("MSIKA: Fetching item={}", itemId);
        return restTemplate.getForEntity(url, String.class);
    }

    public ResponseEntity<String> listCatalogItems(String catalogId, MultiValueMap<String, String> queryParams) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/catalogs/" + catalogId + "/items")
                .queryParams(queryParams)
                .toUriString();
        log.info("MSIKA: Listing catalog items catalogId={} params={}", catalogId, queryParams.keySet());
        return restTemplate.getForEntity(url, String.class);
    }

    public ResponseEntity<String> listCatalogs(MultiValueMap<String, String> queryParams) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/catalogs")
                .queryParams(queryParams)
                .toUriString();
        log.info("MSIKA: Listing catalogs");
        return restTemplate.getForEntity(url, String.class);
    }

    public ResponseEntity<String> createCatalog(String requestBody) {
        log.info("MSIKA: Creating catalog");
        return postJson(baseUrl + "/v1/catalogs", requestBody);
    }

    public ResponseEntity<String> submitCatalogReview(String catalogId) {
        return postWithoutBody(baseUrl + "/v1/catalogs/" + catalogId + "/submit-review", "Submitting catalog review for");
    }

    public ResponseEntity<String> approveCatalog(String catalogId) {
        return postWithoutBody(baseUrl + "/v1/catalogs/" + catalogId + "/approve", "Approving catalog");
    }

    public ResponseEntity<String> publishCatalog(String catalogId) {
        return postWithoutBody(baseUrl + "/v1/catalogs/" + catalogId + "/publish", "Publishing catalog");
    }

    public ResponseEntity<String> listPendingMappings(MultiValueMap<String, String> queryParams) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/mappings/pending")
                .queryParams(queryParams)
                .toUriString();
        log.info("MSIKA: Listing pending mappings");
        return restTemplate.getForEntity(url, String.class);
    }

    public ResponseEntity<String> approveMapping(String mappingId) {
        return postWithoutBody(baseUrl + "/v1/mappings/" + mappingId + "/approve", "Approving mapping");
    }

    public ResponseEntity<String> rejectMapping(String mappingId) {
        return postWithoutBody(baseUrl + "/v1/mappings/" + mappingId + "/reject", "Rejecting mapping");
    }

    public ResponseEntity<String> importCsv(String catalogId, byte[] fileBytes, String filename) {
        log.info("MSIKA: Importing CSV for catalogId={}", catalogId);
        ByteArrayResource resource = new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/import/csv")
                .queryParam("catalogId", catalogId)
                .toUriString();
        return restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> postJson(String url, String requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(requestBody, headers), String.class);
    }

    private ResponseEntity<String> postWithoutBody(String url, String action) {
        log.info("MSIKA: {} at {}", action, url);
        return restTemplate.exchange(url, HttpMethod.POST, HttpEntity.EMPTY, String.class);
    }
}
