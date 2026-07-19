package zw.gov.mohcc.impilo.experience.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * D7 shaping advice behaviour: log mode observes without mutating; enforce
 * mode redacts raw trust-core identifiers (HID/CRID shapes) while leaving
 * cpid and non-UUID values untouched.
 */
@ExtendWith(MockitoExtension.class)
class IdentifierExposureShapingAdviceTest {

    private static final String RAW_UUID = "123e4567-e89b-42d3-a456-426614174000";

    private final ObjectMapper mapper = new ObjectMapper();

    @Mock private ServerHttpRequest request;
    @Mock private ServerHttpResponse response;

    private Object write(IdentifierExposureShapingAdvice advice, Object body) {
        when(request.getURI()).thenReturn(URI.create("http://bff/internal/v1/probe"));
        return advice.beforeBodyWrite(body, null, MediaType.APPLICATION_JSON, null, request, response);
    }

    @Test
    @DisplayName("enforce redacts a raw healthId in a JsonNode body, leaves cpid alone")
    void enforceRedactsJsonNode() {
        IdentifierExposureShapingAdvice advice = new IdentifierExposureShapingAdvice("enforce");
        ObjectNode body = mapper.createObjectNode()
                .put("healthId", RAW_UUID)
                .put("cpid", RAW_UUID)
                .put("name", "Test");

        ObjectNode out = (ObjectNode) write(advice, body);

        assertEquals("[REDACTED-D7]", out.get("healthId").asText());
        assertEquals(RAW_UUID, out.get("cpid").asText(), "cpid is deliberately out of scope");
        assertEquals("Test", out.get("name").asText());
    }

    @Test
    @DisplayName("enforce redacts nested map bodies (crid) and tolerates immutable maps")
    void enforceRedactsMaps() {
        IdentifierExposureShapingAdvice advice = new IdentifierExposureShapingAdvice("enforce");
        Map<String, Object> inner = new HashMap<>();
        inner.put("crid", RAW_UUID);
        Map<String, Object> body = new HashMap<>();
        body.put("subject", inner);

        write(advice, body);
        assertEquals("[REDACTED-D7]", inner.get("crid"));

        // Immutable map: no exception, body intact, leak still logged.
        Map<String, Object> immutable = Map.of("healthId", RAW_UUID);
        assertDoesNotThrow(() -> write(advice, immutable));
        assertEquals(RAW_UUID, immutable.get("healthId"));
    }

    @Test
    @DisplayName("log mode never mutates the body")
    void logModeObservesOnly() {
        IdentifierExposureShapingAdvice advice = new IdentifierExposureShapingAdvice("log");
        ObjectNode body = mapper.createObjectNode().put("impiloHealthId", RAW_UUID);

        ObjectNode out = (ObjectNode) write(advice, body);
        assertEquals(RAW_UUID, out.get("impiloHealthId").asText());
    }

    @Test
    @DisplayName("non-UUID values under sensitive keys pass (masked/alias forms are fine)")
    void nonUuidValuesPass() {
        IdentifierExposureShapingAdvice advice = new IdentifierExposureShapingAdvice("enforce");
        ObjectNode body = mapper.createObjectNode().put("healthId", "***masked***");

        ObjectNode out = (ObjectNode) write(advice, body);
        assertEquals("***masked***", out.get("healthId").asText());
    }

    @Test
    @DisplayName("off mode opts the advice out entirely")
    void offModeDoesNotSupport() {
        IdentifierExposureShapingAdvice advice = new IdentifierExposureShapingAdvice("off");
        assertFalse(advice.supports(null, null));
        assertTrue(new IdentifierExposureShapingAdvice("log").supports(null, null));
    }
}
