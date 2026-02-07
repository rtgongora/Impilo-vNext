package zw.gov.mohcc.impilo.varapi.api.controller;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.core.FhirMappingService;

import java.util.UUID;

/**
 * FHIR R4-compliant REST API for exposing provider data as FHIR resources.
 *
 * Produces Practitioner, PractitionerRole, and Bundle resources in
 * application/fhir+json format. Used by BUTANO (HAPI FHIR) and other
 * interoperability consumers that require standards-based data exchange.
 *
 * All endpoints require a valid trust context provided by TSHEPO ext_authz.
 */
@RestController
@RequestMapping("/v1/fhir")
public class FhirController {

    private static final Logger log = LoggerFactory.getLogger(FhirController.class);
    private static final String FHIR_JSON = "application/fhir+json";
    private static final MediaType FHIR_JSON_MEDIA_TYPE = MediaType.parseMediaType(FHIR_JSON);

    private final FhirMappingService fhirMappingService;
    private final FhirContext fhirContext;

    public FhirController(FhirMappingService fhirMappingService) {
        this.fhirMappingService = fhirMappingService;
        this.fhirContext = FhirContext.forR4();
    }

    @GetMapping(value = "/practitioner/{providerPublicId}", produces = FHIR_JSON)
    public ResponseEntity<String> getPractitioner(
            @PathVariable String providerPublicId) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Fetching FHIR Practitioner [providerPublicId={}] correlationId={}",
                providerPublicId, ctx.correlationId());

        Practitioner practitioner = fhirMappingService.toPractitioner(providerPublicId);
        String json = fhirContext.newJsonParser().encodeResourceToString(practitioner);

        return ResponseEntity.ok()
                .contentType(FHIR_JSON_MEDIA_TYPE)
                .body(json);
    }

    @GetMapping(value = "/practitionerrole/{providerPublicId}", produces = FHIR_JSON)
    public ResponseEntity<String> getPractitionerRole(
            @PathVariable String providerPublicId,
            @RequestParam(required = false) Long facilityId,
            @RequestParam(required = false) UUID workspaceId) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Fetching FHIR PractitionerRole [providerPublicId={}, facilityId={}, workspaceId={}] correlationId={}",
                providerPublicId, facilityId, workspaceId, ctx.correlationId());

        PractitionerRole practitionerRole = fhirMappingService.toPractitionerRole(
                providerPublicId, facilityId, workspaceId);
        String json = fhirContext.newJsonParser().encodeResourceToString(practitionerRole);

        return ResponseEntity.ok()
                .contentType(FHIR_JSON_MEDIA_TYPE)
                .body(json);
    }

    @GetMapping(value = "/bundle/provider/{providerPublicId}", produces = FHIR_JSON)
    public ResponseEntity<String> getBundle(
            @PathVariable String providerPublicId) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Fetching FHIR Bundle [providerPublicId={}] correlationId={}",
                providerPublicId, ctx.correlationId());

        Bundle bundle = fhirMappingService.toBundle(providerPublicId);
        String json = fhirContext.newJsonParser().encodeResourceToString(bundle);

        return ResponseEntity.ok()
                .contentType(FHIR_JSON_MEDIA_TYPE)
                .body(json);
    }
}
