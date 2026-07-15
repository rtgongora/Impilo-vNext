package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.GuidanceServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicGatewayGuidanceBffControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static PublicGatewayGuidanceBffController controller(RestTemplate restTemplate) {
        return new PublicGatewayGuidanceBffController(
                new GuidanceServiceClient(restTemplate, ServiceClientConfig.testServiceEndpoints()));
    }

    @Test
    void explainStepProxiesOnlyTheServiceSidePublicController() {
        CapturingRestTemplate restTemplate = new CapturingRestTemplate(
                "{\"data\":{\"stepKey\":\"signin-to-book\",\"why\":\"w\"}}");
        var response = controller(restTemplate).explainStep("signin-to-book");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(restTemplate.lastGetUrl);
        // Public-lane ADR rule: the BFF may only call a Public*Controller endpoint.
        assertTrue(restTemplate.lastGetUrl.contains("/v1/public/guidance/explain-steps/signin-to-book"),
                "unexpected downstream url: " + restTemplate.lastGetUrl);
        assertEquals("signin-to-book", response.getBody().path("data").path("stepKey").asText());
    }

    @Test
    void explainStepEncodesUntrustedKeys() {
        CapturingRestTemplate restTemplate = new CapturingRestTemplate("{\"data\":{}}");
        controller(restTemplate).explainStep("../internal/secret");

        assertNotNull(restTemplate.lastGetUrl);
        assertTrue(restTemplate.lastGetUrl.contains("..%2Finternal%2Fsecret"),
                "path must be percent-encoded: " + restTemplate.lastGetUrl);
    }

    @Test
    void unknownStepPassesThrough404() {
        RestTemplate notFound = new RestTemplate() {
            @Override
            public <T> ResponseEntity<T> getForEntity(URI url, Class<T> responseType) {
                throw org.springframework.web.client.HttpClientErrorException.NotFound.create(
                        org.springframework.http.HttpStatus.NOT_FOUND, "not found",
                        org.springframework.http.HttpHeaders.EMPTY, new byte[0], null);
            }
        };
        var response = controller(notFound).explainStep("no-such-step");
        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void listAndEducationFailClosedAs502WhenGuidanceUnavailable() {
        ThrowingRestTemplate restTemplate = new ThrowingRestTemplate();
        assertEquals(502, controller(restTemplate).listExplainSteps().getStatusCode().value());
        assertEquals(502, controller(restTemplate).education("all", "", null, 0, 20).getStatusCode().value());
        assertEquals(502, controller(restTemplate).educationCategories().getStatusCode().value());
        assertEquals(502, controller(restTemplate).educationArticle("any-id").getStatusCode().value());
    }

    @Test
    void educationProxiesTheServiceSidePublicEducationEndpoint() {
        CapturingRestTemplate restTemplate = new CapturingRestTemplate("{\"data\":[]}");
        var response = controller(restTemplate).education("prevention", "", null, 0, 10);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(restTemplate.lastGetUrl);
        assertTrue(restTemplate.lastGetUrl.contains("/v1/public/guidance/education"),
                "unexpected downstream url: " + restTemplate.lastGetUrl);
        assertTrue(restTemplate.lastGetUrl.contains("domain=prevention"));
    }

    @Test
    void educationPassesTheCategoryFilterDownstream() {
        CapturingRestTemplate restTemplate = new CapturingRestTemplate("{\"data\":[]}");
        var response = controller(restTemplate).education("all", "vaccination", null, 0, 10);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(restTemplate.lastGetUrl);
        assertTrue(restTemplate.lastGetUrl.contains("category=vaccination"),
                "category filter must reach the downstream url: " + restTemplate.lastGetUrl);
    }

    @Test
    void educationPassesTheTextQueryDownstream() {
        CapturingRestTemplate restTemplate = new CapturingRestTemplate("{\"data\":[]}");
        var response = controller(restTemplate).education("all", "", "fever", 0, 10);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(restTemplate.lastGetUrl);
        assertTrue(restTemplate.lastGetUrl.contains("/v1/public/guidance/education"),
                "unexpected downstream url: " + restTemplate.lastGetUrl);
        assertTrue(restTemplate.lastGetUrl.contains("q=fever"),
                "text query must reach the downstream url: " + restTemplate.lastGetUrl);
    }

    @Test
    void educationOmitsBlankTextQueryDownstream() {
        CapturingRestTemplate restTemplate = new CapturingRestTemplate("{\"data\":[]}");
        controller(restTemplate).education("all", "", "   ", 0, 10);

        assertNotNull(restTemplate.lastGetUrl);
        assertTrue(!restTemplate.lastGetUrl.contains("q="),
                "a blank query must not be forwarded: " + restTemplate.lastGetUrl);
    }

    @Test
    void educationCategoriesProxiesTheServiceSideTopicIndex() {
        CapturingRestTemplate restTemplate = new CapturingRestTemplate(
                "{\"data\":[{\"category\":\"vaccination\",\"count\":1}]}");
        var response = controller(restTemplate).educationCategories();

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(restTemplate.lastGetUrl);
        assertTrue(restTemplate.lastGetUrl.contains("/v1/public/guidance/education/categories"),
                "unexpected downstream url: " + restTemplate.lastGetUrl);
        assertEquals("vaccination", response.getBody().path("data").path(0).path("category").asText());
    }

    @Test
    void educationArticleProxiesAndEncodesUntrustedIds() {
        CapturingRestTemplate restTemplate = new CapturingRestTemplate(
                "{\"data\":{\"id\":\"a\",\"title\":\"t\",\"body\":\"b\"}}");
        var response = controller(restTemplate).educationArticle("../internal/secret");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(restTemplate.lastGetUrl);
        assertTrue(restTemplate.lastGetUrl.contains("/v1/public/guidance/education/"),
                "unexpected downstream url: " + restTemplate.lastGetUrl);
        assertTrue(restTemplate.lastGetUrl.contains("..%2Finternal%2Fsecret"),
                "path must be percent-encoded: " + restTemplate.lastGetUrl);
    }

    @Test
    void unknownArticlePassesThrough404() {
        RestTemplate notFound = new RestTemplate() {
            @Override
            public <T> ResponseEntity<T> getForEntity(URI url, Class<T> responseType) {
                throw org.springframework.web.client.HttpClientErrorException.NotFound.create(
                        org.springframework.http.HttpStatus.NOT_FOUND, "not found",
                        org.springframework.http.HttpHeaders.EMPTY, new byte[0], null);
            }
        };
        var response = controller(notFound).educationArticle("00000000-0000-0000-0000-000000000000");
        assertEquals(404, response.getStatusCode().value());
    }

    // ── Test doubles (mirrors PublicHealthControllerTest conventions) ─────────────────

    private static final class ThrowingRestTemplate extends RestTemplate {
        @Override
        public <T> ResponseEntity<T> getForEntity(String url, Class<T> responseType, Object... uriVariables) {
            throw new org.springframework.web.client.ResourceAccessException("guidance unavailable");
        }

        @Override
        public <T> ResponseEntity<T> getForEntity(URI url, Class<T> responseType) {
            throw new org.springframework.web.client.ResourceAccessException("guidance unavailable");
        }
    }

    private static final class CapturingRestTemplate extends RestTemplate {
        String lastGetUrl;
        private final String body;

        CapturingRestTemplate(String body) {
            this.body = body;
        }

        @SuppressWarnings("unchecked")
        private <T> ResponseEntity<T> respond() {
            try {
                return (ResponseEntity<T>) ResponseEntity.ok(MAPPER.readTree(body));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public <T> ResponseEntity<T> getForEntity(String url, Class<T> responseType, Object... uriVariables) {
            this.lastGetUrl = url;
            return respond();
        }

        @Override
        public <T> ResponseEntity<T> getForEntity(URI url, Class<T> responseType) {
            this.lastGetUrl = url.toString();
            return respond();
        }

        @Override
        public <T> ResponseEntity<T> exchange(URI url, org.springframework.http.HttpMethod method,
                HttpEntity<?> requestEntity, Class<T> responseType) {
            this.lastGetUrl = url.toString();
            return respond();
        }

        @Override
        public <T> ResponseEntity<T> exchange(URI url, org.springframework.http.HttpMethod method,
                HttpEntity<?> requestEntity, org.springframework.core.ParameterizedTypeReference<T> responseType) {
            this.lastGetUrl = url.toString();
            return respond();
        }
    }
}
