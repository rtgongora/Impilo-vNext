package zw.gov.mohcc.impilo.msika.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.msika.domain.UlidGenerator;
import zw.gov.mohcc.impilo.msika.persistence.entity.ListingAuditEntity;
import zw.gov.mohcc.impilo.msika.persistence.repository.ListingAuditRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;

import java.util.Map;

/**
 * Writes immutable storefront-lane audit rows for sensitive/regulated/cross-person/
 * admin actions. Separate from the catalog change-log so the marketplace lane has a
 * dedicated, queryable audit trail (listing approval, regulated view, seller
 * validation, Rito link, Khuluma request, policy denial).
 */
@Service
public class ListingAuditService {

    private final ListingAuditRepository auditRepository;
    private final ObjectMapper objectMapper;

    public ListingAuditService(ListingAuditRepository auditRepository, ObjectMapper objectMapper) {
        this.auditRepository = auditRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void record(TrustContext ctx, String action, String listingId, String subjectRef,
                       String riskClassification, String decision, Map<String, Object> detail) {
        ListingAuditEntity e = new ListingAuditEntity();
        e.setId(UlidGenerator.generate());
        e.setTenantId(ctx != null ? ctx.tenantId() : null);
        e.setActorId(ctx != null && ctx.actorId() != null ? ctx.actorId() : "SYSTEM");
        e.setActorType(ctx != null ? ctx.actorType() : null);
        e.setAction(action);
        e.setListingId(listingId);
        e.setSubjectRef(subjectRef);
        e.setRiskClassification(riskClassification);
        e.setDecision(decision);
        e.setDetailJson(JsonSupport.toJsonSafe(objectMapper, detail != null ? detail : Map.of(), "{}"));
        e.setCorrelationId(ctx != null && ctx.correlationId() != null ? ctx.correlationId().toString() : null);
        auditRepository.save(e);
    }
}
