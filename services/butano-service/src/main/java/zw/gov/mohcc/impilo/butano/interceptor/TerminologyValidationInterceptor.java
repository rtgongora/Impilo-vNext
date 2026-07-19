package zw.gov.mohcc.impilo.butano.interceptor;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.Immunization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.butano.config.ButanoProperties;
import zw.gov.mohcc.impilo.butano.config.ButanoProperties.TerminologyMode;

import java.util.ArrayList;
import java.util.List;

/**
 * HAPI FHIR interceptor that validates coded elements against the ZIBO
 * terminology service before storage.
 *
 * <p>When terminology validation is enabled ({@code butano.terminology.validation-enabled=true}),
 * this interceptor extracts all coded elements (CodeableConcept/Coding) from
 * incoming resources and validates them against the national terminology service
 * (ZIBO — Terminology and Value Set Service, port 8085).</p>
 *
 * <h3>Validated resource types and code paths</h3>
 * <ul>
 *   <li>{@code Condition.code} — diagnosis codes (ICD-10, SNOMED CT)</li>
 *   <li>{@code Observation.code} — observation type codes (LOINC)</li>
 *   <li>{@code Observation.value[x]} — when value is a CodeableConcept</li>
 *   <li>{@code MedicationRequest.medicationCodeableConcept} — drug codes</li>
 *   <li>{@code Procedure.code} — procedure codes (CPT, SNOMED)</li>
 *   <li>{@code DiagnosticReport.code} — report type codes</li>
 *   <li>{@code ServiceRequest.code} — service/order codes</li>
 *   <li>{@code AllergyIntolerance.code} — allergy codes</li>
 *   <li>{@code Immunization.vaccineCode} — vaccine codes</li>
 *   <li>{@code Encounter.type} — encounter type codes</li>
 * </ul>
 *
 * <h3>Validation modes</h3>
 * <ul>
 *   <li><strong>STRICT</strong> — resources with invalid codes are rejected (HTTP 422)</li>
 *   <li><strong>LENIENT</strong> — invalid codes generate a warning log but are stored</li>
 * </ul>
 *
 * <h3>ZIBO API contract</h3>
 * <p>Calls {@code POST {zibo-base-url}/api/v1/terminology/validate} with a JSON body
 * containing {@code system} and {@code code}. Expects a response with {@code valid: true/false}
 * and optional {@code display} and {@code message} fields.</p>
 *
 * <p>When disabled ({@code butano.terminology.validation-enabled=false}), this interceptor
 * is a complete no-op with zero performance overhead.</p>
 */
@Interceptor
@Component
public class TerminologyValidationInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TerminologyValidationInterceptor.class);

    private static final String VALIDATION_ENDPOINT = "/api/v1/terminology/validate";

    private final ButanoProperties properties;
    private final RestTemplate restTemplate;

    public TerminologyValidationInterceptor(ButanoProperties properties) {
        this.properties = properties;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Validates terminology on newly created resources.
     */
    // Hook params must use the pointcut's declared types (IBaseResource); HAPI
    // binds by type and passes null otherwise.
    @Hook(Pointcut.STORAGE_PRESTORAGE_RESOURCE_CREATED)
    public void onResourceCreated(IBaseResource theResource, RequestDetails requestDetails) {
        if (theResource instanceof Resource resource) {
            validateTerminology(requestDetails, resource);
        }
    }

    /**
     * Validates terminology on updated resources.
     */
    @Hook(Pointcut.STORAGE_PRESTORAGE_RESOURCE_UPDATED)
    public void onResourceUpdated(IBaseResource theOldResource, IBaseResource theNewResource, RequestDetails requestDetails) {
        if (theNewResource instanceof Resource newResource) {
            validateTerminology(requestDetails, newResource);
        }
    }

    // ── Core validation logic ───────────────────────────────────────────

    /**
     * Extracts coded elements from the resource and validates each against ZIBO.
     *
     * <p>If validation is disabled, this method returns immediately (no-op).
     * Otherwise, it collects all relevant CodeableConcepts from the resource,
     * validates each coding, and either rejects or warns based on the configured mode.</p>
     */
    private void validateTerminology(RequestDetails requestDetails, Resource resource) {
        if (!properties.getTerminology().isValidationEnabled()) {
            return;
        }

        List<CodingWithPath> codings = extractCodings(resource);
        if (codings.isEmpty()) {
            return;
        }

        List<String> violations = new ArrayList<>();
        String tenantId = getUserData(requestDetails, HeaderValidationInterceptor.UD_TENANT_ID);
        String correlationId = getUserData(requestDetails, HeaderValidationInterceptor.UD_CORRELATION_ID);

        for (CodingWithPath codingWithPath : codings) {
            Coding coding = codingWithPath.coding();
            String path = codingWithPath.path();

            if (!coding.hasSystem() || !coding.hasCode()) {
                // Codings without system+code are not validatable
                log.debug("Skipping terminology validation for {} — missing system or code", path);
                continue;
            }

            try {
                ZiboValidationResponse response = callZiboValidation(
                        coding.getSystem(), coding.getCode(), tenantId, correlationId);

                if (response != null && !response.valid()) {
                    String message = String.format(
                            "%s: code '%s' (system: %s) is not valid. %s",
                            path, coding.getCode(), coding.getSystem(),
                            response.message() != null ? response.message() : "Unknown reason.");
                    violations.add(message);
                }
            } catch (RestClientException e) {
                // ZIBO service is unavailable — degrade gracefully
                log.warn("ZIBO terminology service unavailable for validation of {} — " +
                         "system={}, code={}, error={}",
                        path, coding.getSystem(), coding.getCode(), e.getMessage());

                if (properties.getTerminology().getMode() == TerminologyMode.STRICT) {
                    violations.add(path + ": terminology validation service unavailable — " +
                            "cannot validate code '" + coding.getCode() + "' in STRICT mode.");
                }
                // In LENIENT mode, service unavailability is tolerated
            }
        }

        if (!violations.isEmpty()) {
            if (properties.getTerminology().getMode() == TerminologyMode.STRICT) {
                String errorMessage = "Terminology validation failed: " +
                        String.join("; ", violations);
                log.warn("STRICT terminology validation rejection for {}/{} — {}",
                        resource.fhirType(),
                        resource.getIdElement() != null ? resource.getIdElement().getIdPart() : "new",
                        errorMessage);
                throw new UnprocessableEntityException(errorMessage);
            } else {
                // LENIENT mode — log warnings but allow storage
                log.warn("LENIENT terminology validation warnings for {}/{} — {}",
                        resource.fhirType(),
                        resource.getIdElement() != null ? resource.getIdElement().getIdPart() : "new",
                        String.join("; ", violations));
            }
        }
    }

    // ── Coding extraction ───────────────────────────────────────────────

    /**
     * Extracts all validatable codings from a resource based on its type.
     *
     * <p>Returns a list of codings paired with their FHIR paths for error reporting.</p>
     */
    private List<CodingWithPath> extractCodings(Resource resource) {
        List<CodingWithPath> codings = new ArrayList<>();

        if (resource instanceof Condition condition) {
            extractFromCodeableConcept(codings, condition.getCode(), "Condition.code");
        } else if (resource instanceof Observation observation) {
            extractFromCodeableConcept(codings, observation.getCode(), "Observation.code");
            // value[x] may be a CodeableConcept
            if (observation.hasValueCodeableConcept()) {
                extractFromCodeableConcept(codings,
                        observation.getValueCodeableConcept(), "Observation.valueCodeableConcept");
            }
        } else if (resource instanceof MedicationRequest medicationRequest) {
            if (medicationRequest.hasMedicationCodeableConcept()) {
                extractFromCodeableConcept(codings,
                        medicationRequest.getMedicationCodeableConcept(),
                        "MedicationRequest.medicationCodeableConcept");
            }
        } else if (resource instanceof Procedure procedure) {
            extractFromCodeableConcept(codings, procedure.getCode(), "Procedure.code");
        } else if (resource instanceof DiagnosticReport diagnosticReport) {
            extractFromCodeableConcept(codings, diagnosticReport.getCode(), "DiagnosticReport.code");
        } else if (resource instanceof ServiceRequest serviceRequest) {
            extractFromCodeableConcept(codings, serviceRequest.getCode(), "ServiceRequest.code");
        } else if (resource instanceof AllergyIntolerance allergyIntolerance) {
            extractFromCodeableConcept(codings, allergyIntolerance.getCode(), "AllergyIntolerance.code");
        } else if (resource instanceof Immunization immunization) {
            extractFromCodeableConcept(codings, immunization.getVaccineCode(), "Immunization.vaccineCode");
        } else if (resource instanceof Encounter encounter) {
            if (encounter.hasType()) {
                for (int i = 0; i < encounter.getType().size(); i++) {
                    extractFromCodeableConcept(codings,
                            encounter.getType().get(i), "Encounter.type[" + i + "]");
                }
            }
        }

        return codings;
    }

    /**
     * Extracts individual Coding elements from a CodeableConcept.
     */
    private void extractFromCodeableConcept(List<CodingWithPath> codings,
                                             CodeableConcept codeableConcept,
                                             String basePath) {
        if (codeableConcept == null || !codeableConcept.hasCoding()) {
            return;
        }

        for (int i = 0; i < codeableConcept.getCoding().size(); i++) {
            Coding coding = codeableConcept.getCoding().get(i);
            String path = basePath + ".coding[" + i + "]";
            codings.add(new CodingWithPath(coding, path));
        }
    }

    // ── ZIBO service call ───────────────────────────────────────────────

    /**
     * Calls the ZIBO terminology validation endpoint.
     *
     * @param system         the code system URI
     * @param code           the code value
     * @param tenantId       the requesting tenant ID (for ZIBO context)
     * @param correlationId  the request correlation ID (for tracing)
     * @return the validation response, or null if the call fails in a non-throwing way
     */
    private ZiboValidationResponse callZiboValidation(String system, String code,
                                                       String tenantId, String correlationId) {
        String url = properties.getTerminology().getZiboBaseUrl() + VALIDATION_ENDPOINT;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (tenantId != null) {
            headers.set("X-Tenant-Id", tenantId);
        }
        if (correlationId != null) {
            headers.set("X-Correlation-Id", correlationId);
        }

        ZiboValidationRequest request = new ZiboValidationRequest(system, code);
        HttpEntity<ZiboValidationRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<ZiboValidationResponse> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, ZiboValidationResponse.class);

        return response.getBody();
    }

    // ── Helper types ────────────────────────────────────────────────────

    /**
     * Pairs a Coding with its FHIR path for error reporting.
     */
    private record CodingWithPath(Coding coding, String path) {}

    /**
     * Request body for the ZIBO terminology validation endpoint.
     */
    private record ZiboValidationRequest(String system, String code) {}

    /**
     * Response from the ZIBO terminology validation endpoint.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ZiboValidationResponse(
            boolean valid,
            String display,
            String message
    ) {}

    // ── Private helpers ─────────────────────────────────────────────────

    /**
     * Safely extracts a String value from request user data.
     */
    private String getUserData(RequestDetails requestDetails, String key) {
        Object value = requestDetails.getUserData().get(key);
        return value instanceof String s ? s : null;
    }
}
