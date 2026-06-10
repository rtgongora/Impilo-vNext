package zw.gov.mohcc.impilo.experience.bootstrap;

import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.experience.admingovernance.AdminGovernanceAuditHelper;
import zw.gov.mohcc.impilo.experience.admingovernance.AdminGovernanceResponses;

import java.util.Map;

@Component
public class BootstrapAuditService {

    private final AdminGovernanceAuditHelper auditHelper;

    public BootstrapAuditService(AdminGovernanceAuditHelper auditHelper) {
        this.auditHelper = auditHelper;
    }

    public AuditResult emit(String eventType, String actorId, String subjectRef, Map<String, Object> payload) {
        AdminGovernanceResponses.AuditEnvelope envelope = auditHelper.emit(
                eventType,
                actorId != null ? actorId : "bootstrap",
                subjectRef,
                payload);
        return new AuditResult(envelope.auditStatus(), envelope.auditEventId());
    }

    public record AuditResult(String auditStatus, String auditEventId) {}
}
