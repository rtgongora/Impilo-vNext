package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.SurgeryServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pure pass-through for specialty catalogue + surgical analytics. Same empty-versus-unavailable
 * contract as {@link SurgeryControllerTest}: a downstream failure must propagate, never render
 * as an empty catalogue.
 */
class SurgerySpecialtyAnalyticsControllerTest {

    @Test
    void specialtyIndicationsForwardsTheSpecialtyQuery() {
        SurgerySpecialtyAnalyticsController controller = new SurgerySpecialtyAnalyticsController(new StubClient() {
            @Override
            public ResponseEntity<String> specialtyIndications(String specialty) {
                assertEquals("COLORECTAL", specialty);
                return ResponseEntity.ok("[{\"indicationCode\":\"IND-COL-01\"}]");
            }
        });

        var response = controller.specialtyIndications("COLORECTAL");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("[{\"indicationCode\":\"IND-COL-01\"}]", response.getBody());
    }

    @Test
    void specialtyTemplatesForwardsTheSpecialtyQuery() {
        SurgerySpecialtyAnalyticsController controller = new SurgerySpecialtyAnalyticsController(new StubClient() {
            @Override
            public ResponseEntity<String> specialtyTemplates(String specialty) {
                assertEquals("GENERAL_SURGERY", specialty);
                return ResponseEntity.ok("[{\"templateCode\":\"GS-HERNIA-OPEN\"}]");
            }
        });

        assertEquals("[{\"templateCode\":\"GS-HERNIA-OPEN\"}]",
                controller.specialtyTemplates("GENERAL_SURGERY").getBody());
    }

    @Test
    void analyticsIndicatorsAndDetailForwardVerbatim() {
        SurgerySpecialtyAnalyticsController controller = new SurgerySpecialtyAnalyticsController(new StubClient() {
            @Override
            public ResponseEntity<String> analyticsIndicators() {
                return ResponseEntity.ok("{\"total\":1,\"indicators\":[]}");
            }

            @Override
            public ResponseEntity<String> analyticsIndicator(String indicatorCode) {
                assertEquals("SSI_RATE", indicatorCode);
                return ResponseEntity.ok("{\"indicatorCode\":\"SSI_RATE\"}");
            }
        });

        assertEquals("{\"total\":1,\"indicators\":[]}", controller.analyticsIndicators().getBody());
        assertEquals("{\"indicatorCode\":\"SSI_RATE\"}",
                controller.analyticsIndicator("SSI_RATE").getBody());
    }

    @Test
    void aDownstreamFailureOnTheCataloguePropagatesRatherThanRenderingEmpty() {
        SurgerySpecialtyAnalyticsController controller = new SurgerySpecialtyAnalyticsController(new StubClient() {
            @Override
            public ResponseEntity<String> specialtyTemplates(String specialty) {
                throw new org.springframework.web.client.ResourceAccessException("surgery-service unreachable");
            }
        });

        assertThrows(org.springframework.web.client.ResourceAccessException.class,
                () -> controller.specialtyTemplates("COLORECTAL"));
    }

    @Test
    void aDownstreamFailureOnAnalyticsPropagatesRatherThanRenderingEmpty() {
        SurgerySpecialtyAnalyticsController controller = new SurgerySpecialtyAnalyticsController(new StubClient() {
            @Override
            public ResponseEntity<String> analyticsIndicators() {
                throw new org.springframework.web.client.ResourceAccessException("surgery-service unreachable");
            }
        });

        assertThrows(org.springframework.web.client.ResourceAccessException.class,
                () -> controller.analyticsIndicators());
    }

    private static class StubClient extends SurgeryServiceClient {
        StubClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints("http://surgery"));
        }
    }
}
