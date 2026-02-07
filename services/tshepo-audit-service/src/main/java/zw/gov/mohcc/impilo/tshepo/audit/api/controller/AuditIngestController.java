package zw.gov.mohcc.impilo.tshepo.audit.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tshepo.audit.api.dto.AuditEventRequest;
import zw.gov.mohcc.impilo.tshepo.audit.api.dto.AuditEventResponse;
import zw.gov.mohcc.impilo.tshepo.audit.core.AuditChainService;
import zw.gov.mohcc.impilo.tshepo.audit.persistence.entity.AuditEventEntity;

/**
 * Internal ingest endpoint for audit events from other TSHEPO services.
 * This endpoint is permitAll in SecurityConfig as it is called
 * service-to-service behind Envoy.
 */
@RestController
@RequestMapping("/v1/audit/events")
public class AuditIngestController {

    private static final Logger log = LoggerFactory.getLogger(AuditIngestController.class);

    private final AuditChainService auditChainService;

    public AuditIngestController(AuditChainService auditChainService) {
        this.auditChainService = auditChainService;
    }

    /**
     * POST /v1/audit/events — append an audit event to the hash chain.
     * Called internally by other TSHEPO services.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<AuditEventResponse>> ingestEvent(
            @Valid @RequestBody AuditEventRequest request) {
        log.info("Ingesting audit event: type={}, actor={}, correlationId={}",
                request.eventType(), request.actorId(), request.correlationId());

        AuditEventEntity saved = auditChainService.appendEvent(request);

        AuditEventResponse response = new AuditEventResponse(
                saved.getId(),
                saved.getTenantId(),
                saved.getEventType(),
                saved.getActorId(),
                saved.getActorType(),
                saved.getSubjectRef(),
                saved.getResourceType(),
                saved.getResourceId(),
                saved.getAction(),
                saved.getOutcome(),
                saved.getPurposeOfUse(),
                saved.getFacilityId(),
                saved.getCorrelationId(),
                saved.getDetail(),
                saved.getPreviousHash(),
                saved.getEntryHash(),
                saved.getSequenceNumber(),
                saved.getCreatedAt()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, request.correlationId().toString()));
    }
}
