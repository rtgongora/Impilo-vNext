package zw.gov.mohcc.impilo.varapi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.api.dto.CreateCouncilRequest;
import zw.gov.mohcc.impilo.varapi.api.dto.ImportTriggerRequest;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CouncilEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ReconciliationCaseEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.CouncilRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ReconciliationCaseRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Council management and import trigger service.
 *
 * Manages professional regulatory councils (e.g., MDPCZ, NMCZ, PCAZ)
 * that oversee healthcare provider registration and licensing.
 */
@Service
public class CouncilService {

    private static final Logger log = LoggerFactory.getLogger(CouncilService.class);

    private final CouncilRepository councilRepository;
    private final ReconciliationCaseRepository reconciliationCaseRepository;

    public CouncilService(CouncilRepository councilRepository,
                          ReconciliationCaseRepository reconciliationCaseRepository) {
        this.councilRepository = councilRepository;
        this.reconciliationCaseRepository = reconciliationCaseRepository;
    }

    // ---- Public API ----

    @Transactional
    public CouncilEntity createCouncil(CreateCouncilRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Creating council: code={}, name={}, actor={}",
                request.councilCode(), request.name(), ctx.actorId());

        CouncilEntity council = new CouncilEntity();
        council.setTenantId(ctx.tenantId());
        council.setCouncilCode(request.councilCode());
        council.setName(request.name());
        council.setCouncilType(request.councilType());
        council.setDescription(request.description());
        council.setWebsite(request.website());
        council.setEmail(request.email());
        council.setPhone(request.phone());
        council.setStatus("ACTIVE");

        council = councilRepository.save(council);
        log.info("Council created: id={}, code={}", council.getId(), council.getCouncilCode());

        return council;
    }

    @Transactional(readOnly = true)
    public CouncilEntity getCouncil(Long id) {
        TrustContextHolder.require();
        log.debug("Fetching council: id={}", id);

        return councilRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Council not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<CouncilEntity> listCouncils() {
        TrustContext ctx = TrustContextHolder.require();
        log.debug("Listing councils: tenantId={}", ctx.tenantId());

        return councilRepository.findByTenantId(ctx.tenantId());
    }

    @Transactional
    public void triggerImport(Long councilId, ImportTriggerRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Triggering import: councilId={}, importType={}, actor={}",
                councilId, request.importType(), ctx.actorId());

        CouncilEntity council = councilRepository.findById(councilId)
                .orElseThrow(() -> new IllegalArgumentException("Council not found: " + councilId));

        ReconciliationCaseEntity reconCase = new ReconciliationCaseEntity();
        reconCase.setTenantId(ctx.tenantId());
        reconCase.setCouncil(council);
        reconCase.setSourceSystem(council.getCouncilCode());
        reconCase.setSourceRef("IMPORT-" + System.currentTimeMillis());
        reconCase.setCaseType(request.importType().name());
        reconCase.setStatus("OPEN");

        Map<String, Object> payloadMap = new HashMap<>();
        payloadMap.put("importType", request.importType().name());
        payloadMap.put("payload", request.payload());
        payloadMap.put("triggeredBy", ctx.actorId());
        reconCase.setPayload(payloadMap);

        reconciliationCaseRepository.save(reconCase);
        log.info("Import reconciliation case created for council: councilId={}", councilId);
    }
}
