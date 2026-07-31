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

    // ── Wave P-R2 — P7 safety-pause/sedation and P9 recovery/aftercare routes ──

    @Test
    void safetyPauseTemplateForwardsTheCodeFromTheQueryParameter() {
        ProceduresController controller = new ProceduresController(new StubClient() {
            @Override
            public ResponseEntity<String> safetyPauseTemplate(String templateCode) {
                assertEquals("SAFETY-PAUSE-SURGERY", templateCode);
                return ResponseEntity.ok("{\"templateCode\":\"SAFETY-PAUSE-SURGERY\"}");
            }
        });

        var response = controller.safetyPauseTemplate("SAFETY-PAUSE-SURGERY");

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void sedationLevelsReturnsTheDownstreamResponseVerbatim() {
        ProceduresController controller = new ProceduresController(new StubClient() {
            @Override
            public ResponseEntity<String> sedationLevels() {
                return ResponseEntity.ok("[{\"levelCode\":\"NO_SEDATION\"}]");
            }
        });

        var response = controller.sedationLevels();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("[{\"levelCode\":\"NO_SEDATION\"}]", response.getBody());
    }

    @Test
    void sedationLevelForwardsTheCodeFromTheQueryParameter() {
        ProceduresController controller = new ProceduresController(new StubClient() {
            @Override
            public ResponseEntity<String> sedationLevel(String levelCode) {
                assertEquals("MODERATE_SEDATION", levelCode);
                return ResponseEntity.ok("{\"levelCode\":\"MODERATE_SEDATION\"}");
            }
        });

        var response = controller.sedationLevel("MODERATE_SEDATION");

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void recoverySettingsReturnsTheDownstreamResponseVerbatim() {
        ProceduresController controller = new ProceduresController(new StubClient() {
            @Override
            public ResponseEntity<String> recoverySettings() {
                return ResponseEntity.ok("[{\"settingCode\":\"PACU\"}]");
            }
        });

        var response = controller.recoverySettings();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("[{\"settingCode\":\"PACU\"}]", response.getBody());
    }

    @Test
    void recoverySettingForwardsTheCodeFromTheQueryParameter() {
        ProceduresController controller = new ProceduresController(new StubClient() {
            @Override
            public ResponseEntity<String> recoverySetting(String settingCode) {
                assertEquals("PACU", settingCode);
                return ResponseEntity.ok("{\"settingCode\":\"PACU\"}");
            }
        });

        var response = controller.recoverySetting("PACU");

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void aftercareTemplateForwardsTheCodeFromTheQueryParameter() {
        ProceduresController controller = new ProceduresController(new StubClient() {
            @Override
            public ResponseEntity<String> aftercareTemplate(String templateCode) {
                assertEquals("AFTERCARE-THEATRE", templateCode);
                return ResponseEntity.ok("{\"templateCode\":\"AFTERCARE-THEATRE\"}");
            }
        });

        var response = controller.aftercareTemplate("AFTERCARE-THEATRE");

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    /**
     * A second failure-propagation control on the new surface, not just the old one — the same
     * guarantee must hold for every route this controller adds, not merely the one it started
     * with.
     */
    @Test
    void aDownstreamFailureOnTheNewSurfaceAlsoPropagates() {
        ProceduresController controller = new ProceduresController(new StubClient() {
            @Override
            public ResponseEntity<String> aftercareTemplate(String templateCode) {
                throw new org.springframework.web.client.ResourceAccessException(
                        "procedures-service unreachable");
            }
        });

        assertThrows(org.springframework.web.client.ResourceAccessException.class,
                () -> controller.aftercareTemplate("AFTERCARE-THEATRE"));
    }

    // ── Wave SB-3 — P14 analytics indicator catalogue routes ──

    @Test
    void analyticsIndicatorsReturnsTheDownstreamResponseVerbatim() {
        ProceduresController controller = new ProceduresController(new StubClient() {
            @Override
            public ResponseEntity<String> analyticsIndicators() {
                return ResponseEntity.ok("{\"total\":33,\"computed\":0,\"indicators\":[]}");
            }
        });

        var response = controller.analyticsIndicators();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("{\"total\":33,\"computed\":0,\"indicators\":[]}", response.getBody());
    }

    @Test
    void analyticsIndicatorForwardsTheCodeFromTheQueryParameter() {
        ProceduresController controller = new ProceduresController(new StubClient() {
            @Override
            public ResponseEntity<String> analyticsIndicator(String indicatorCode) {
                assertEquals("IND-PROC-001", indicatorCode);
                return ResponseEntity.ok("{\"indicatorCode\":\"IND-PROC-001\"}");
            }
        });

        var response = controller.analyticsIndicator("IND-PROC-001");

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    /** The propagation control again, on the analytics surface this wave adds. */
    @Test
    void aDownstreamFailureOnTheAnalyticsSurfaceAlsoPropagates() {
        ProceduresController controller = new ProceduresController(new StubClient() {
            @Override
            public ResponseEntity<String> analyticsIndicators() {
                throw new org.springframework.web.client.ResourceAccessException(
                        "procedures-service unreachable");
            }
        });

        assertThrows(org.springframework.web.client.ResourceAccessException.class,
                controller::analyticsIndicators);
    }

    // ── Wave W4 — P10 Clavien-Dindo grades and complication profiles ──

    @Test
    void clavienDindoGradesReturnsTheDownstreamResponseVerbatim() {
        ProceduresController controller = new ProceduresController(new StubClient() {
            @Override
            public ResponseEntity<String> clavienDindoGrades() {
                return ResponseEntity.ok("[{\"gradeCode\":\"I\",\"gradeLabel\":\"Grade I\"}]");
            }
        });

        var response = controller.clavienDindoGrades();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("[{\"gradeCode\":\"I\",\"gradeLabel\":\"Grade I\"}]", response.getBody());
    }

    @Test
    void complicationProfileForwardsTheCodeFromTheQueryParameter() {
        ProceduresController controller = new ProceduresController(new StubClient() {
            @Override
            public ResponseEntity<String> complicationProfile(String profileCode) {
                assertEquals("COMPLICATIONS-LAPAROTOMY", profileCode);
                return ResponseEntity.ok("{\"profileCode\":\"COMPLICATIONS-LAPAROTOMY\"}");
            }
        });

        var response = controller.complicationProfile("COMPLICATIONS-LAPAROTOMY");

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    /** Propagation control on the complication-profile surface — error must never become empty. */
    @Test
    void aDownstreamFailureOnTheComplicationSurfaceAlsoPropagates() {
        ProceduresController controller = new ProceduresController(new StubClient() {
            @Override
            public ResponseEntity<String> clavienDindoGrades() {
                throw new org.springframework.web.client.ResourceAccessException(
                        "procedures-service unreachable");
            }
        });

        assertThrows(org.springframework.web.client.ResourceAccessException.class,
                controller::clavienDindoGrades);
    }

    /** Overridable stub so each test only implements the one method it exercises. */
    private static class StubClient extends ProceduresServiceClient {
        StubClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints("http://procedures"));
        }
    }
}
