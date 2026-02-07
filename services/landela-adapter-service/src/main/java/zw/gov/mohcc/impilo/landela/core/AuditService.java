package zw.gov.mohcc.impilo.landela.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.landela.persistence.entity.DocumentAuditEntity;
import zw.gov.mohcc.impilo.landela.persistence.repository.DocumentAuditRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Audit recording service for document operations.
 *
 * Every document access, modification, or lifecycle transition
 * is recorded as an immutable audit entry tied to the trust context.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final DocumentAuditRepository auditRepository;
    private final ObjectMapper objectMapper;

    public AuditService(DocumentAuditRepository auditRepository, ObjectMapper objectMapper) {
        this.auditRepository = auditRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Record an audit entry for a document action.
     *
     * @param documentId the UUID of the document
     * @param action     the action performed (e.g., UPLOAD, VIEW, DOWNLOAD, ARCHIVE, REVOKE, DELETE)
     * @param ctx        the trust context from the current request
     * @param details    additional details about the action
     */
    @Transactional
    public void recordAudit(UUID documentId, String action, TrustContext ctx, Map<String, Object> details) {
        DocumentAuditEntity audit = new DocumentAuditEntity();
        audit.setDocumentId(documentId);
        audit.setTenantId(ctx.tenantId());
        audit.setAction(action);
        audit.setActorId(ctx.actorId());
        audit.setActorType(ctx.actorType());
        audit.setPurposeOfUse(ctx.purposeOfUse());
        audit.setDeviceFingerprint(ctx.deviceFingerprint());
        audit.setCorrelationId(ctx.correlationId());

        if (details != null && !details.isEmpty()) {
            try {
                audit.setDetails(objectMapper.writeValueAsString(details));
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize audit details for document {}: {}", documentId, e.getMessage());
                audit.setDetails("{}");
            }
        }

        auditRepository.save(audit);
        log.debug("Recorded audit: action={}, documentId={}, actor={}", action, documentId, ctx.actorId());
    }

    /**
     * Retrieve the full audit trail for a document.
     */
    public List<DocumentAuditEntity> getAuditTrail(UUID documentId) {
        return auditRepository.findByDocumentIdOrderByCreatedAtDesc(documentId);
    }
}
