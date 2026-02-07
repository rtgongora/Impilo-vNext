package zw.gov.mohcc.impilo.butano.api.controller;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import org.hl7.fhir.r4.model.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.butano.core.IpsBundleGenerator;
import zw.gov.mohcc.impilo.butano.core.VisitSummaryGenerator;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

/**
 * REST controller for clinical summary generation.
 *
 * Provides endpoints for:
 * - IPS (International Patient Summary) generation by CPID
 * - Visit summary generation by Encounter ID
 *
 * Returns FHIR Bundles serialized as JSON with Content-Type application/fhir+json.
 */
@RestController
@RequestMapping("/v1/summary")
public class IpsSummaryController {

    private static final Logger log = LoggerFactory.getLogger(IpsSummaryController.class);
    private static final MediaType FHIR_JSON = MediaType.parseMediaType("application/fhir+json");

    private final IpsBundleGenerator ipsBundleGenerator;
    private final VisitSummaryGenerator visitSummaryGenerator;
    private final FhirContext fhirContext;

    public IpsSummaryController(IpsBundleGenerator ipsBundleGenerator,
                                VisitSummaryGenerator visitSummaryGenerator,
                                FhirContext fhirContext) {
        this.ipsBundleGenerator = ipsBundleGenerator;
        this.visitSummaryGenerator = visitSummaryGenerator;
        this.fhirContext = fhirContext;
    }

    /**
     * Generate an IPS (International Patient Summary) for the given CPID.
     *
     * @param cpid the Clinical Patient Identifier
     * @return FHIR Bundle of type DOCUMENT conforming to IPS structure
     */
    @GetMapping("/ips/{cpid}")
    public ResponseEntity<String> getIpsSummary(@PathVariable String cpid) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("IPS summary requested for CPID={} by actor={}", cpid, ctx.actorId());

        try {
            Bundle ipsBundle = ipsBundleGenerator.generate(cpid);
            String json = fhirContext.newJsonParser()
                    .setPrettyPrint(false)
                    .encodeResourceToString(ipsBundle);

            return ResponseEntity.ok()
                    .contentType(FHIR_JSON)
                    .header("X-Correlation-Id", ctx.correlationId().toString())
                    .body(json);

        } catch (IllegalArgumentException e) {
            log.warn("IPS generation failed for CPID={}: {}", cpid, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(toErrorJson("PATIENT_NOT_FOUND", e.getMessage(),
                            HttpStatus.NOT_FOUND.value(), ctx.correlationId().toString()));

        } catch (ResourceNotFoundException e) {
            log.warn("FHIR resource not found during IPS generation for CPID={}: {}", cpid, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(toErrorJson("RESOURCE_NOT_FOUND", e.getMessage(),
                            HttpStatus.NOT_FOUND.value(), ctx.correlationId().toString()));
        }
    }

    /**
     * Generate a visit summary Bundle for the given Encounter ID.
     *
     * @param encounterId the FHIR Encounter resource ID
     * @return FHIR Bundle of type COLLECTION with all encounter-associated resources
     */
    @GetMapping("/visit/{encounterId}")
    public ResponseEntity<String> getVisitSummary(@PathVariable String encounterId) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Visit summary requested for Encounter/{} by actor={}", encounterId, ctx.actorId());

        try {
            Bundle visitBundle = visitSummaryGenerator.generate(encounterId);
            String json = fhirContext.newJsonParser()
                    .setPrettyPrint(false)
                    .encodeResourceToString(visitBundle);

            return ResponseEntity.ok()
                    .contentType(FHIR_JSON)
                    .header("X-Correlation-Id", ctx.correlationId().toString())
                    .body(json);

        } catch (ResourceNotFoundException e) {
            log.warn("Encounter/{} not found: {}", encounterId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(toErrorJson("ENCOUNTER_NOT_FOUND", e.getMessage(),
                            HttpStatus.NOT_FOUND.value(), ctx.correlationId().toString()));
        }
    }

    private String toErrorJson(String code, String message, int status, String correlationId) {
        ApiResponse<Void> errorResponse = ApiResponse.error(code, message, status, correlationId);
        return "{\"success\":false,\"error\":{\"code\":\"" + code
                + "\",\"message\":\"" + escapeJson(message)
                + "\",\"status\":" + status
                + "},\"correlationId\":\"" + correlationId
                + "\",\"timestamp\":\"" + errorResponse.timestamp() + "\"}";
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
