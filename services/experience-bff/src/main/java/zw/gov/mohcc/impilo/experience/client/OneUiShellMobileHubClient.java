package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.BffProviderHubsProperties;

/**
 * Fetches provider hub section metadata from the web gateway (one-ui-shell).
 */
@Component
public class OneUiShellMobileHubClient {

    private static final Logger log = LoggerFactory.getLogger(OneUiShellMobileHubClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public OneUiShellMobileHubClient(RestTemplate serviceRestTemplate, BffProviderHubsProperties props) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = props.getOneUiShellBaseUrl();
    }

    /**
     * GET /api/mobile/provider/hubs/{hub}
     */
    public JsonNode getProviderHub(String hub) {
        String url = baseUrl + "/api/mobile/provider/hubs/" + hub;
        log.debug("one-ui-shell: provider hub {}", hub);
        return restTemplate.getForEntity(url, JsonNode.class).getBody();
    }
}

