package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.CommunityServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SocialControllerTest {

    @Test
    void feed_returnsEnvelope() {
        SocialController controller = new SocialController(new StubCommunityClient());
        ResponseEntity<Map<String, Object>> response =
                controller.feed("home", null, 20, 0, "req-1", "corr-1");
        assertEquals(200, response.getStatusCode().value());
    }

    private static final class StubCommunityClient extends CommunityServiceClient {
        private static final ObjectMapper mapper = new ObjectMapper();

        StubCommunityClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints(), mapper);
        }

        @Override
        public JsonNode getFeed(String scope, String scopeId, int limit, int offset) {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(mapper.createObjectNode().put("id", "post-1"));
            return arr;
        }
    }
}
