package zw.gov.mohcc.impilo.inpatient.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedureNoteEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Writes the signed operative note to the canonical clinical record (Butano FHIR layer) as a FHIR
 * Procedure + DocumentReference via {@code POST /internal/v1/fhir/resources}. Butano (HAPI FHIR) is
 * the SoR for the clinical record — inpatient only authors the resource and links the returned ref.
 * Best-effort: a Butano outage must not block signing (the operative note remains the local truth and
 * the link is reconciled later).
 */
@Service
public class ButanoProcedureClient {

    private static final Logger log = LoggerFactory.getLogger(ButanoProcedureClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public ButanoProcedureClient(RestTemplate inpatientRestTemplate,
                                 @Value("${inpatient.integration.butano.base-url:http://localhost:8289}") String baseUrl,
                                 ObjectMapper objectMapper) {
        this.restTemplate = inpatientRestTemplate;
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
    }

    /** Write a FHIR Procedure for the signed operative note. Returns the FHIR resource id or null. */
    public String writeProcedure(String cpid, String encounterRef, String procedureName, String procedureCode,
                                 ProcedureNoteEntity note) {
        Map<String, Object> proc = new LinkedHashMap<>();
        proc.put("resourceType", "Procedure");
        proc.put("status", "completed");
        proc.put("subject", Map.of("identifier", Map.of("value", cpid)));
        if (encounterRef != null) proc.put("encounter", Map.of("reference", "Encounter/" + encounterRef));
        Map<String, Object> code = new LinkedHashMap<>();
        code.put("text", procedureName);
        if (procedureCode != null) {
            code.put("coding", List.of(Map.of("code", procedureCode, "display", procedureName)));
        }
        proc.put("code", code);
        proc.put("performedDateTime", OffsetDateTime.now().toString());
        if (note.getComplications() != null) {
            proc.put("complication", List.of(Map.of("text", note.getComplications())));
        }
        if (note.getFindings() != null) {
            proc.put("note", List.of(Map.of("text", note.getFindings())));
        }
        return write("Procedure", proc);
    }

    /** Write a FHIR DocumentReference pointing at the operative note. Returns the FHIR resource id or null. */
    public String writeOperativeNoteDocument(String cpid, String encounterRef, ProcedureNoteEntity note) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("resourceType", "DocumentReference");
        doc.put("status", "current");
        doc.put("docStatus", "final");
        doc.put("type", Map.of("text", "Operative note"));
        doc.put("subject", Map.of("identifier", Map.of("value", cpid)));
        doc.put("date", OffsetDateTime.now().toString());
        if (encounterRef != null) doc.put("context", Map.of("encounter",
                List.of(Map.of("reference", "Encounter/" + encounterRef))));
        doc.put("description", note.getPerformedProcedure() != null
                ? note.getPerformedProcedure() : "Operative note");
        return write("DocumentReference", doc);
    }

    /**
     * Attach a specimen's pathology result to the clinical record as a FHIR DocumentReference (type =
     * Pathology report). Butano is the SoR — theatre only authors the resource and links the ref.
     * Returns the FHIR resource id or null.
     */
    public String attachPathology(String cpid, String encounterRef, String specimenLabel,
                                  String orosResultId, String summary) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("resourceType", "DocumentReference");
        doc.put("status", "current");
        doc.put("docStatus", "final");
        doc.put("type", Map.of("text", "Pathology report"));
        doc.put("subject", Map.of("identifier", Map.of("value", cpid)));
        doc.put("date", OffsetDateTime.now().toString());
        if (encounterRef != null) doc.put("context", Map.of("encounter",
                List.of(Map.of("reference", "Encounter/" + encounterRef))));
        doc.put("description", "Pathology: " + (specimenLabel != null ? specimenLabel : "theatre specimen")
                + (orosResultId != null ? " (OROS result " + orosResultId + ")" : ""));
        if (summary != null) {
            doc.put("content", List.of(Map.of("attachment", Map.of("contentType", "text/plain", "title", summary))));
        }
        return write("DocumentReference", doc);
    }

    @SuppressWarnings("unchecked")
    private String write(String resourceType, Map<String, Object> resource) {
        try {
            UUID tenantId = TrustContextHolder.require().tenantId();
            String resourceId = resourceType.toLowerCase() + "-" + UUID.randomUUID();
            Map<String, Object> req = new LinkedHashMap<>();
            req.put("tenantId", tenantId != null ? tenantId.toString() : null);
            req.put("resourceType", resourceType);
            req.put("resourceId", resourceId);
            req.put("fhirVersion", "R4");
            req.put("payload", objectMapper.writeValueAsString(resource));
            try {
                req.put("correlationId", TrustContextHolder.require().correlationId() != null
                        ? TrustContextHolder.require().correlationId().toString() : null);
            } catch (IllegalStateException ignored) { /* best-effort */ }
            ResponseEntity<Map> resp = restTemplate.postForEntity(baseUrl + "/internal/v1/fhir/resources",
                    new HttpEntity<>(req, trustHeaders()), Map.class);
            Map<String, Object> body = resp.getBody();
            if (body != null) {
                Object id = body.getOrDefault("resourceId", body.get("id"));
                log.info("INPATIENT-THEATRE: wrote FHIR {} → {}", resourceType, id);
                return id != null ? id.toString() : resourceId;
            }
            return resourceId;
        } catch (RestClientException | IllegalStateException | JsonProcessingException e) {
            log.warn("INPATIENT-THEATRE: Butano FHIR {} write unavailable (best-effort): {}",
                    resourceType, e.getMessage());
            return null;
        }
    }

    private HttpHeaders trustHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            TrustContext ctx = TrustContextHolder.require();
            if (ctx.tenantId() != null) headers.set(TrustContext.H_TENANT_ID, ctx.tenantId().toString());
            if (ctx.actorId() != null) headers.set(TrustContext.H_ACTOR_ID, ctx.actorId());
            if (ctx.correlationId() != null) headers.set(TrustContext.H_CORRELATION_ID, ctx.correlationId().toString());
            if (ctx.facilityId() != null) headers.set(TrustContext.H_FACILITY_ID, ctx.facilityId().toString());
        } catch (IllegalStateException ignored) {
            // best-effort
        }
        return headers;
    }
}
