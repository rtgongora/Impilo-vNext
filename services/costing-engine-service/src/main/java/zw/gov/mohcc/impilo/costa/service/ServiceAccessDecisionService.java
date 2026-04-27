package zw.gov.mohcc.impilo.costa.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.costa.api.dto.ServiceAccessDecisionCreateRequest;
import zw.gov.mohcc.impilo.costa.api.dto.ServiceAccessDecisionResponse;
import zw.gov.mohcc.impilo.costa.domain.entity.ServiceAccessDecisionEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.BillingTimingMode;
import zw.gov.mohcc.impilo.costa.domain.enums.ServiceAccessStatus;
import zw.gov.mohcc.impilo.costa.domain.repository.ServiceAccessDecisionRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.UUID;

@Service
public class ServiceAccessDecisionService {

    private final ServiceAccessDecisionRepository repository;

    public ServiceAccessDecisionService(ServiceAccessDecisionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ServiceAccessDecisionResponse create(ServiceAccessDecisionCreateRequest req) {
        var ctx = TrustContextHolder.require();
        ServiceAccessStatus status = parseEnum(ServiceAccessStatus.class, req.accessStatus(), "access_status");
        BillingTimingMode timing = req.billingTimingMode() == null || req.billingTimingMode().isBlank()
                ? null
                : parseEnum(BillingTimingMode.class, req.billingTimingMode(), "billing_timing_mode");

        ServiceAccessDecisionEntity e = new ServiceAccessDecisionEntity();
        e.setTenantId(ctx.tenantId());
        e.setPatientCpid(req.patientCpid());
        e.setEncounterId(req.encounterId());
        e.setFacilityId(req.facilityId() != null ? req.facilityId() : ctx.facilityId());
        e.setWorkspaceId(req.workspaceId());
        e.setRequestedServiceId(req.requestedServiceId());
        e.setRequestedServiceName(req.requestedServiceName());
        e.setRequestedServiceCategory(req.requestedServiceCategory());
        e.setEstimatedCost(req.estimatedCost());
        e.setSelectedTariffListId(req.selectedTariffListId());
        e.setTariffedPrice(req.tariffedPrice());
        e.setBillingTimingMode(timing);
        e.setAccessStatus(status);
        e.setRequiredDepositAmount(req.requiredDepositAmount());
        e.setRequiredPaymentAmount(req.requiredPaymentAmount());
        e.setPayerId(req.payerId());
        e.setCoverageReference(req.coverageReference());
        e.setExemptionReference(req.exemptionReference());
        e.setWaiverReference(req.waiverReference());
        e.setAuthorisationReference(req.authorisationReference());
        e.setPromiseToPayReference(req.promiseToPayReference());
        e.setDecisionReason(req.decisionReason());
        e.setDecidedBy(req.decidedBy() != null && !req.decidedBy().isBlank() ? req.decidedBy() : ctx.actorId());
        e.setAuditReference(req.auditReference());

        e = repository.save(e);
        return ServiceAccessDecisionResponse.fromEntity(e);
    }

    @Transactional(readOnly = true)
    public List<ServiceAccessDecisionResponse> listRecent(String encounterId) {
        var ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();
        List<ServiceAccessDecisionEntity> rows = encounterId != null && !encounterId.isBlank()
                ? repository.findTop50ByTenantIdAndEncounterIdOrderByDecisionTimestampDesc(tenantId, encounterId)
                : repository.findTop50ByTenantIdOrderByDecisionTimestampDesc(tenantId);
        return rows.stream().map(ServiceAccessDecisionResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public ServiceAccessDecisionResponse require(UUID id) {
        var ctx = TrustContextHolder.require();
        return repository.findByServiceAccessDecisionIdAndTenantId(id, ctx.tenantId())
                .map(ServiceAccessDecisionResponse::fromEntity)
                .orElseThrow(() -> new IllegalArgumentException("Service access decision not found: " + id));
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String field) {
        try {
            return Enum.valueOf(type, raw.trim());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid " + field + ": " + raw);
        }
    }
}
