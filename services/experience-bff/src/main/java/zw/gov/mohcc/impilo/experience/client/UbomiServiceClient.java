package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

/**
 * HTTP client for the Ubomi vital events (birth / death notifications,
 * civil verification) sovereign service.
 */
@Component
public class UbomiServiceClient {

    private static final Logger log = LoggerFactory.getLogger(UbomiServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public UbomiServiceClient(RestTemplate serviceRestTemplate,
                              ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.ubomiBaseUrl();
    }

    public JsonNode listBirthNotifications(int page, int size) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/births")
                .queryParam("page", page)
                .queryParam("size", size)
                .build()
                .encode()
                .toUriString();
        log.info("UBOMI: listBirthNotifications operation [page={}, size={}]", page, size);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode submitBirthNotification(JsonNode body) {
        String url = baseUrl + "/v1/births";
        log.info("UBOMI: submitBirthNotification operation");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    public JsonNode approveBirthNotification(long notificationId) {
        String url = baseUrl + "/v1/births/" + notificationId + "/approve";
        log.info("UBOMI: approveBirthNotification operation [notificationId={}]", notificationId);
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, HttpEntity.EMPTY, JsonNode.class);
        return extractData(response);
    }

    public JsonNode listDeathNotifications(int page, int size) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/deaths")
                .queryParam("page", page)
                .queryParam("size", size)
                .build()
                .encode()
                .toUriString();
        log.info("UBOMI: listDeathNotifications operation [page={}, size={}]", page, size);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode submitDeathNotification(JsonNode body) {
        String url = baseUrl + "/v1/deaths";
        log.info("UBOMI: submitDeathNotification operation");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    public JsonNode certifyDeathNotification(long notificationId) {
        String url = baseUrl + "/v1/deaths/" + notificationId + "/certify";
        log.info("UBOMI: certifyDeathNotification operation [notificationId={}]", notificationId);
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, HttpEntity.EMPTY, JsonNode.class);
        return extractData(response);
    }

    public JsonNode verifyRegistration(String registrationNumber) {
        String url = baseUrl + "/v1/verifications/" + registrationNumber;
        log.info("UBOMI: verifyRegistration operation [registrationNumber={}]", registrationNumber);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode markDeathPackageReady(long notificationId) {
        String url = baseUrl + "/v1/deaths/" + notificationId + "/package-ready";
        log.info("UBOMI: markDeathPackageReady operation [notificationId={}]", notificationId);
        return extractData(restTemplate.postForEntity(url, HttpEntity.EMPTY, JsonNode.class));
    }

    public JsonNode registerDeath(long notificationId, java.util.Map<String, Object> body) {
        String url = baseUrl + "/v1/deaths/" + notificationId + "/register";
        log.info("UBOMI: registerDeath operation [notificationId={}]", notificationId);
        return extractData(restTemplate.postForEntity(url, new HttpEntity<>(body), JsonNode.class));
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
