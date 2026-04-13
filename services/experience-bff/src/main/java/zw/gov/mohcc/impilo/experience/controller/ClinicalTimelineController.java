package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.util.*;

/**
 * Clinical timeline endpoints.
 * GET /internal/v1/timeline?patient_id= — list timeline for patient (paged, desc by occurred_at).
 * GET /internal/v1/timeline?encounter_id= — list timeline for encounter (list, no pagination).
 */
@RestController
@RequestMapping("/internal/v1/timeline")
public class ClinicalTimelineController {

    private static final Logger log = LoggerFactory.getLogger(ClinicalTimelineController.class);

    private final PctServiceClient pctClient;

    public ClinicalTimelineController(PctServiceClient pctClient) {
        this.pctClient = pctClient;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listTimeline(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, name = "patient_id") String patientId,
            @RequestParam(required = false, name = "encounter_id") String encounterId) {
        throw new UnsupportedOperationException("Endpoint pending migration to sovereign service");
    }
}
