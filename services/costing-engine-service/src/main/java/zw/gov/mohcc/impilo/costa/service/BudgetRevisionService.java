package zw.gov.mohcc.impilo.costa.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetLineEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetRevisionEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetVersionEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.BudgetStatus;
import zw.gov.mohcc.impilo.costa.domain.enums.BudgetVersionBasis;
import zw.gov.mohcc.impilo.costa.domain.enums.RevisionType;
import zw.gov.mohcc.impilo.costa.domain.repository.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Applies budget revisions / reprogramming. A revision produces a new current
 * version (superseding the previous one) with adjusted line amounts. VIREMENT
 * revisions must net to zero across lines; SUPPLEMENTARY / RESCISSION /
 * REPROGRAMMING may change the total. The availability projection is resynced.
 */
@Service
public class BudgetRevisionService {

    private final BudgetRepository budgetRepository;
    private final BudgetVersionRepository versionRepository;
    private final BudgetLineRepository lineRepository;
    private final BudgetRevisionRepository revisionRepository;
    private final BudgetProjectionSyncer projectionSyncer;
    private final BudgetEventEmitter emitter;

    public BudgetRevisionService(BudgetRepository budgetRepository,
                                 BudgetVersionRepository versionRepository,
                                 BudgetLineRepository lineRepository,
                                 BudgetRevisionRepository revisionRepository,
                                 BudgetProjectionSyncer projectionSyncer,
                                 BudgetEventEmitter emitter) {
        this.budgetRepository = budgetRepository;
        this.versionRepository = versionRepository;
        this.lineRepository = lineRepository;
        this.revisionRepository = revisionRepository;
        this.projectionSyncer = projectionSyncer;
        this.emitter = emitter;
    }

    public record LineAdjustment(UUID lineId, BigDecimal newAmount) {}
    public record RevisionRequest(String revisionType, String reason, List<LineAdjustment> adjustments) {}

    @Transactional
    public BudgetRevisionEntity apply(UUID tenantId, UUID budgetId, String actorId, RevisionRequest req) {
        BudgetEntity b = budgetRepository.findByBudgetIdAndTenantId(budgetId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Budget not found"));
        if (!BudgetStatus.ACTIVE.name().equals(b.getStatus())) {
            throw new IllegalStateException("Only an ACTIVE budget can be revised");
        }
        RevisionType type = RevisionType.valueOf(req.revisionType());
        if (req.adjustments() == null || req.adjustments().isEmpty()) {
            throw new IllegalArgumentException("At least one line adjustment is required");
        }

        BudgetVersionEntity v0 = versionRepository.findByBudgetIdAndTenantIdAndCurrentTrue(budgetId, tenantId)
                .orElseThrow(() -> new IllegalStateException("Budget has no current version"));
        List<BudgetLineEntity> lines0 =
                lineRepository.findByVersionIdAndTenantIdOrderByLineOrderAscCreatedAtAsc(v0.getVersionId(), tenantId);
        Map<UUID, BudgetLineEntity> byLineId = new LinkedHashMap<>();
        for (BudgetLineEntity l : lines0) byLineId.put(l.getLineId(), l);

        Map<UUID, BigDecimal> newAmounts = new LinkedHashMap<>();
        BigDecimal netChange = BigDecimal.ZERO;
        for (LineAdjustment adj : req.adjustments()) {
            BudgetLineEntity l = byLineId.get(adj.lineId());
            if (l == null) throw new IllegalArgumentException("Line not in current version: " + adj.lineId());
            if (adj.newAmount() == null || adj.newAmount().signum() < 0) {
                throw new IllegalArgumentException("newAmount must not be negative");
            }
            netChange = netChange.add(adj.newAmount().subtract(l.getAllocatedAmount()));
            newAmounts.put(adj.lineId(), adj.newAmount());
        }
        if (type == RevisionType.VIREMENT && netChange.signum() != 0) {
            throw new IllegalArgumentException("A VIREMENT must net to zero across lines (net change "
                    + netChange.toPlainString() + ")");
        }

        // spawn new current version
        BudgetVersionEntity v1 = new BudgetVersionEntity();
        v1.setBudgetId(budgetId);
        v1.setTenantId(tenantId);
        v1.setVersionNo(v0.getVersionNo() + 1);
        v1.setCurrent(true);
        v1.setBasis(type == RevisionType.REPROGRAMMING
                ? BudgetVersionBasis.REPROGRAMMING.name() : BudgetVersionBasis.REVISION.name());
        v1.setEffectiveFrom(v0.getEffectiveFrom());
        v1.setStatus(BudgetStatus.ACTIVE.name());
        versionRepository.save(v1);

        BigDecimal total = BigDecimal.ZERO;
        for (BudgetLineEntity src : lines0) {
            BigDecimal amount = newAmounts.getOrDefault(src.getLineId(), src.getAllocatedAmount());
            BudgetLineEntity copy = new BudgetLineEntity();
            copy.setVersionId(v1.getVersionId());
            copy.setBudgetId(budgetId);
            copy.setTenantId(tenantId);
            copy.setCostCenterId(src.getCostCenterId());
            copy.setDepartmentId(src.getDepartmentId());
            copy.setBudgetCategory(src.getBudgetCategory());
            copy.setFundingSourceId(src.getFundingSourceId());
            copy.setProgrammeCode(src.getProgrammeCode());
            copy.setGrantId(src.getGrantId());
            copy.setDistrictCode(src.getDistrictCode());
            copy.setGlAccountRef(src.getGlAccountRef());
            copy.setAllocatedAmount(amount);
            copy.setLineOrder(src.getLineOrder());
            lineRepository.save(copy);
            total = total.add(amount);
        }
        v1.setTotalAmount(total);
        versionRepository.save(v1);

        v0.setCurrent(false);
        v0.setSupersededByVersionId(v1.getVersionId());
        versionRepository.save(v0);
        b.setCurrentVersionId(v1.getVersionId());
        budgetRepository.save(b);

        BudgetRevisionEntity rev = new BudgetRevisionEntity();
        rev.setBudgetId(budgetId);
        rev.setTenantId(tenantId);
        rev.setFromVersionId(v0.getVersionId());
        rev.setToVersionId(v1.getVersionId());
        rev.setRevisionType(type.name());
        rev.setReason(req.reason());
        rev.setNetChange(netChange);
        rev.setRequestedBy(actorId);
        rev.setApprovedBy(actorId);
        rev.setStatus("APPLIED");
        revisionRepository.save(rev);

        projectionSyncer.sync(tenantId, b, v1.getVersionId());
        emitter.emit("BUDGET", budgetId.toString(), "BUDGET_REVISED", tenantId, payload(rev, total));
        return rev;
    }

    public List<BudgetRevisionEntity> list(UUID tenantId, UUID budgetId) {
        return revisionRepository.findByBudgetIdAndTenantIdOrderByCreatedAtDesc(budgetId, tenantId);
    }

    private Map<String, Object> payload(BudgetRevisionEntity r, BigDecimal newTotal) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("revisionId", r.getRevisionId().toString());
        m.put("budgetId", r.getBudgetId().toString());
        m.put("tenantId", r.getTenantId().toString());
        m.put("revisionType", r.getRevisionType());
        m.put("netChange", r.getNetChange());
        m.put("toVersionId", r.getToVersionId().toString());
        m.put("newTotal", newTotal);
        return m;
    }
}
