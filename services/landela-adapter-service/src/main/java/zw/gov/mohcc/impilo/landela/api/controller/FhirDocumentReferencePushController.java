package zw.gov.mohcc.impilo.landela.api.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.landela.core.DocumentService;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Contract alias for FHIR DocumentReference push ({@code POST /v1/internal/documentreference/push}).
 *
 * <p>Existing document-scoped push remains at
 * {@code POST /v1/internal/documents/{documentId}/fhir-push}.</p>
 */
@RestController
@RequestMapping("/v1/internal/documentreference")
public class FhirDocumentReferencePushController {

    private final DocumentService documentService;
    private final String defaultFhirServerUrl;

    public FhirDocumentReferencePushController(
            DocumentService documentService,
            @Value("${landela.fhir.base-url:http://localhost:8090/fhir}") String defaultFhirServerUrl) {
        this.documentService = documentService;
        this.defaultFhirServerUrl = defaultFhirServerUrl;
    }

    @PostMapping("/push")
    public ResponseEntity<ApiResponse<Void>> pushDocumentReference(@RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        Object rawId = body.get("documentId");
        if (rawId == null || rawId.toString().isBlank()) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("BAD_REQUEST", "documentId is required", 400, ctx.correlationId().toString()));
        }

        UUID documentId = UUID.fromString(rawId.toString());
        String fhirServerUrl = body.containsKey("fhirServerUrl") && body.get("fhirServerUrl") != null
                ? body.get("fhirServerUrl").toString()
                : defaultFhirServerUrl;

        try {
            documentService.pushDocumentReference(documentId, fhirServerUrl, ctx);
            return ResponseEntity.status(201).body(ApiResponse.ok(null, ctx.correlationId().toString()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(
                    ApiResponse.error("NOT_FOUND", e.getMessage(), 404, ctx.correlationId().toString()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(502).body(
                    ApiResponse.error("FHIR_PUSH_FAILED", e.getMessage(), 502, ctx.correlationId().toString()));
        }
    }
}
