package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.ProceduresServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Proves the controller is a pure pass-through — no shape it invents, no failure it swallows.
 *
 * <p>No try/catch is exercised here deliberately: this controller relies on
 * {@code BffGlobalExceptionHandler}'s existing catch-all to turn a downstream failure into a
 * real non-2xx status, the same mechanism every other BFF proxy controller in this codebase
 * relies on. What matters at the controller layer is narrower — that a downstream exception
 * actually PROPAGATES rather than being caught and quietly converted to an empty 200, which is
 * the specific failure mode the ADR calls out (a live BFF 502 once rendered as "no conditions"
 * for every patient because a client swallowed the error).</p>
 */
class ProceduresControllerTest {

    @Test
    void searchCatalogueForwardsFiltersAndReturnsTheDownstreamResponseVerbatim() {
        ProceduresController controller = new ProceduresController(new StubClient() {
            @Override
            public ResponseEntity<String> searchCatalogue(String specialty, String category, String q) {
                assertEquals("SURGERY", specialty);
                assertEquals("THEATRE", category);
                return ResponseEntity.ok("{\"items\":[{\"definitionCode\":\"PROC-LAPAROTOMY\"}]}");
            }
        });

        var response = controller.searchCatalogue("SURGERY", "THEATRE", null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("{\"items\":[{\"definitionCode\":\"PROC-LAPAROTOMY\"}]}", response.getBody());
    }

    @Test
    void catalogueDetailForwardsTheCodeFromTheQueryParameter() {
        ProceduresController controller = new ProceduresController(new StubClient() {
            @Override
            public ResponseEntity<String> catalogueDetail(String definitionCode) {
                assertEquals("PROC-LAPAROTOMY", definitionCode);
                return ResponseEntity.ok("{\"definitionCode\":\"PROC-LAPAROTOMY\"}");
            }
        });

        var response = controller.catalogueDetail("PROC-LAPAROTOMY");

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    /**
     * The failure-does-not-render-as-empty guarantee, at the boundary this controller actually
     * controls: a downstream exception is never caught here and converted to a 200. It must
     * reach BffGlobalExceptionHandler (proven separately to exist and to catch generic
     * Exception — see BffGlobalExceptionHandler itself) rather than being absorbed.
     */
    @Test
    void aDownstreamFailurePropagatesRatherThanBeingSwallowedIntoAnEmptyResponse() {
        ProceduresController controller = new ProceduresController(new StubClient() {
            @Override
            public ResponseEntity<String> searchCatalogue(String specialty, String category, String q) {
                throw new org.springframework.web.client.ResourceAccessException(
                        "procedures-service unreachable");
            }
        });

        assertThrows(org.springframework.web.client.ResourceAccessException.class,
                () -> controller.searchCatalogue(null, null, null));
    }

    @Test
    void evaluateAppropriatenessForwardsTheRawJsonBody() {
        String body = "{\"definitionCode\":\"PROC-LAPAROTOMY\",\"side\":\"LEFT\"}";
        ProceduresController controller = new ProceduresController(new StubClient() {
            @Override
            public ResponseEntity<String> evaluateAppropriateness(String requestBody) {
                assertEquals(body, requestBody);
                return ResponseEntity.ok("{\"outcome\":\"APPROPRIATE\"}");
            }
        });

        var response = controller.evaluateAppropriateness(body);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void resolveCompetenceForwardsBothParameters() {
        ProceduresController controller = new ProceduresController(new StubClient() {
            @Override
            public ResponseEntity<String> resolveCompetence(String providerId, String definitionCode) {
                assertEquals("prov-1", providerId);
                assertEquals("PROC-LAPAROTOMY", definitionCode);
                return ResponseEntity.ok("{\"capacity\":\"INDEPENDENT\"}");
            }
        });

        var response = controller.resolveCompetence("prov-1", "PROC-LAPAROTOMY");

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    /** Overridable stub so each test only implements the one method it exercises. */
    private static class StubClient extends ProceduresServiceClient {
        StubClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints("http://procedures"));
        }
    }
}
