package zw.gov.mohcc.impilo.tshepo.consent.api;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;
import zw.gov.mohcc.impilo.tshepo.consent.dto.ConsentDirectiveDto;
import zw.gov.mohcc.impilo.tshepo.consent.dto.CreateConsentRequest;
import zw.gov.mohcc.impilo.tshepo.consent.dto.RevokeConsentRequest;
import zw.gov.mohcc.impilo.tshepo.consent.dto.UpdateConsentRequest;
import zw.gov.mohcc.impilo.tshepo.consent.service.ConsentCrudService;

import java.util.UUID;

/**
 * REST controller for CRUD operations on consent directives.
 *
 * <p>All mutations require authenticated access. The actor identity is extracted
 * from the trust context headers injected by Envoy/TSHEPO.</p>
 */
@RestController
@RequestMapping("/v1/consent")
public class ConsentController {

    private final ConsentCrudService consentService;

    public ConsentController(ConsentCrudService consentService) {
        this.consentService = consentService;
    }

    /**
     * Creates a new consent directive from a FHIR R4 Consent JSON resource.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ConsentDirectiveDto>> create(
            @Valid @RequestBody CreateConsentRequest request) {
        String actorId = resolveActorId();
        String correlationId = resolveCorrelationId();
        ConsentDirectiveDto dto = consentService.create(request, actorId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(dto, correlationId));
    }

    /**
     * Retrieves a consent directive by its ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ConsentDirectiveDto>> findById(@PathVariable UUID id) {
        String correlationId = resolveCorrelationId();
        ConsentDirectiveDto dto = consentService.findById(id);
        return ResponseEntity.ok(ApiResponse.ok(dto, correlationId));
    }

    /**
     * Updates an existing consent directive. Re-validates the FHIR Consent JSON.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ConsentDirectiveDto>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateConsentRequest request) {
        String actorId = resolveActorId();
        String correlationId = resolveCorrelationId();
        ConsentDirectiveDto dto = consentService.update(id, request, actorId);
        return ResponseEntity.ok(ApiResponse.ok(dto, correlationId));
    }

    /**
     * Revokes a consent directive (soft delete).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<ConsentDirectiveDto>> revoke(
            @PathVariable UUID id,
            @Valid @RequestBody RevokeConsentRequest request) {
        String actorId = resolveActorId();
        String correlationId = resolveCorrelationId();
        ConsentDirectiveDto dto = consentService.revoke(id, request.reason(), actorId);
        return ResponseEntity.ok(ApiResponse.ok(dto, correlationId));
    }

    /**
     * Lists consent directives for a patient, optionally filtered by status.
     */
    @GetMapping("/patient/{patientRef}")
    public ResponseEntity<ApiResponse<PagedResponse<ConsentDirectiveDto>>> findByPatient(
            @PathVariable String patientRef,
            @RequestParam UUID tenantId,
            @RequestParam(defaultValue = "ACTIVE") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String correlationId = resolveCorrelationId();
        Page<ConsentDirectiveDto> results = consentService.findByPatient(
                tenantId, patientRef, status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        PagedResponse<ConsentDirectiveDto> paged = PagedResponse.of(
                results.getContent(), results.getNumber(), results.getSize(), results.getTotalElements());
        return ResponseEntity.ok(ApiResponse.ok(paged, correlationId));
    }

    // --- Helpers ---

    private String resolveActorId() {
        var ctx = TrustContextHolder.get();
        if (ctx != null && ctx.actorId() != null) {
            return ctx.actorId();
        }
        return "unknown";
    }

    private String resolveCorrelationId() {
        var ctx = TrustContextHolder.get();
        if (ctx != null && ctx.correlationId() != null) {
            return ctx.correlationId().toString();
        }
        return UUID.randomUUID().toString();
    }
}
