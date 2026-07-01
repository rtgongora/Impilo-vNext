package zw.gov.mohcc.impilo.costa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.costa.domain.BudgetLifecycle;
import zw.gov.mohcc.impilo.costa.domain.entity.*;
import zw.gov.mohcc.impilo.costa.domain.enums.BudgetApprovalAction;
import zw.gov.mohcc.impilo.costa.domain.enums.BudgetScopeLevel;
import zw.gov.mohcc.impilo.costa.domain.enums.BudgetStatus;
import zw.gov.mohcc.impilo.costa.domain.repository.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the managed-budget lifecycle: create → submit → review → approve →
 * activate → freeze → close, plus draft line editing. Every transition is
 * validated by {@link BudgetLifecycle}, logged to {@code costa_budget_approval}
 * and emitted to the COSTA outbox. On ACTIVATE the flat
 * {@code costa_budget_allocations} availability projection is resynced.
 *
 * Segregation of duties: the actor that approves a budget must differ from its
 * creator (configurable via {@code costa.budget.enforce-segregation-of-duties}).
 */
@Service
public class BudgetLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(BudgetLifecycleService.class);

    private final BudgetRepository budgetRepository;
    private final BudgetVersionRepository versionRepository;
    private final BudgetLineRepository lineRepository;
    private final BudgetApprovalRepository approvalRepository;
    private final BudgetAllocationRepository allocationRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final boolean enforceSegregationOfDuties;

    public BudgetLifecycleService(BudgetRepository budgetRepository,
                                  BudgetVersionRepository versionRepository,
                                  BudgetLineRepository lineRepository,
                                  BudgetApprovalRepository approvalRepository,
                                  BudgetAllocationRepository allocationRepository,
                                  EventOutboxRepository outboxRepository,
                                  ObjectMapper objectMapper,
                                  @Value("${costa.budget.enforce-segregation-of-duties:true}")
                                  boolean enforceSegregationOfDuties) {
        this.budgetRepository = budgetRepository;
        this.versionRepository = versionRepository;
        this.lineRepository = lineRepository;
        this.approvalRepository = approvalRepository;
        this.allocationRepository = allocationRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.enforceSegregationOfDuties = enforceSegregationOfDuties;
    }

    // ----- commands ---------------------------------------------------------

    public record NewBudget(UUID facilityId, String scopeLevel, int periodYear,
                            LocalDate periodStart, LocalDate periodEnd, String title,
                            String grantId, String programmeCode, UUID fundingSourceId,
                            String currency, String notes) {}

    public record NewLine(UUID costCenterId, String departmentId, String budgetCategory,
                          UUID fundingSourceId, String programmeCode, String grantId,
                          String districtCode, String glAccountRef, BigDecimal allocatedAmount,
                          Integer lineOrder) {}

    // ----- create -----------------------------------------------------------

    @Transactional
    public BudgetEntity createBudget(UUID tenantId, String actorId, NewBudget cmd) {
        if (cmd.title() == null || cmd.title().isBlank()) {
            throw new IllegalArgumentException("Budget title is required");
        }
        String scope = cmd.scopeLevel() == null ? "FACILITY" : cmd.scopeLevel();
        BudgetScopeLevel.valueOf(scope); // validate
        if ("FACILITY".equals(scope) && cmd.facilityId() == null) {
            throw new IllegalArgumentException("facilityId is required for FACILITY scope");
        }

        BudgetEntity b = new BudgetEntity();
        b.setTenantId(tenantId);
        b.setFacilityId(cmd.facilityId());
        b.setScopeLevel(scope);
        b.setPeriodYear(cmd.periodYear());
        b.setPeriodStart(cmd.periodStart());
        b.setPeriodEnd(cmd.periodEnd());
        b.setTitle(cmd.title());
        b.setGrantId(cmd.grantId());
        b.setProgrammeCode(cmd.programmeCode());
        b.setFundingSourceId(cmd.fundingSourceId());
        if (cmd.currency() != null && !cmd.currency().isBlank()) b.setCurrency(cmd.currency());
        b.setNotes(cmd.notes());
        b.setStatus(BudgetStatus.DRAFT.name());
        b.setCreatedBy(actorId);
        budgetRepository.save(b);

        BudgetVersionEntity v = new BudgetVersionEntity();
        v.setBudgetId(b.getBudgetId());
        v.setTenantId(tenantId);
        v.setVersionNo(1);
        v.setCurrent(true);
        v.setBasis("ORIGINAL");
        v.setEffectiveFrom(cmd.periodStart());
        v.setTotalAmount(BigDecimal.ZERO);
        v.setStatus(BudgetStatus.DRAFT.name());
        v.setCreatedBy(actorId);
        versionRepository.save(v);

        b.setCurrentVersionId(v.getVersionId());
        budgetRepository.save(b);

        emit("BUDGET", b.getBudgetId().toString(), "BUDGET_CREATED", tenantId, budgetPayload(b, v));
        return b;
    }

    // ----- line editing (draft only) ---------------------------------------

    @Transactional
    public BudgetLineEntity addLine(UUID tenantId, UUID budgetId, NewLine cmd) {
        BudgetEntity b = requireBudget(tenantId, budgetId);
        if (!BudgetStatus.DRAFT.name().equals(b.getStatus())) {
            throw new IllegalStateException("Lines can only be edited while the budget is DRAFT");
        }
        BudgetVersionEntity v = requireCurrentVersion(tenantId, budgetId);
        if (cmd.budgetCategory() == null || cmd.budgetCategory().isBlank()) {
            throw new IllegalArgumentException("budgetCategory is required");
        }
        BigDecimal amount = cmd.allocatedAmount() != null ? cmd.allocatedAmount() : BigDecimal.ZERO;
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("allocatedAmount must not be negative");
        }

        BudgetLineEntity line = new BudgetLineEntity();
        line.setVersionId(v.getVersionId());
        line.setBudgetId(budgetId);
        line.setTenantId(tenantId);
        line.setCostCenterId(cmd.costCenterId());
        line.setDepartmentId(cmd.departmentId());
        line.setBudgetCategory(cmd.budgetCategory());
        line.setFundingSourceId(cmd.fundingSourceId() != null ? cmd.fundingSourceId() : b.getFundingSourceId());
        line.setProgrammeCode(cmd.programmeCode() != null ? cmd.programmeCode() : b.getProgrammeCode());
        line.setGrantId(cmd.grantId() != null ? cmd.grantId() : b.getGrantId());
        line.setDistrictCode(cmd.districtCode());
        line.setGlAccountRef(cmd.glAccountRef());
        line.setAllocatedAmount(amount);
        line.setLineOrder(cmd.lineOrder() != null ? cmd.lineOrder() : 0);
        lineRepository.save(line);

        recomputeVersionTotal(tenantId, v);
        return line;
    }

    private void recomputeVersionTotal(UUID tenantId, BudgetVersionEntity v) {
        BigDecimal total = lineRepository
                .findByVersionIdAndTenantIdOrderByLineOrderAscCreatedAtAsc(v.getVersionId(), tenantId)
                .stream()
                .map(BudgetLineEntity::getAllocatedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        v.setTotalAmount(total);
        versionRepository.save(v);
    }

    // ----- lifecycle transitions -------------------------------------------

    @Transactional
    public BudgetEntity submit(UUID tenantId, UUID budgetId, String actorId, String reason) {
        return transition(tenantId, budgetId, actorId, BudgetApprovalAction.SUBMIT, reason);
    }

    @Transactional
    public BudgetEntity review(UUID tenantId, UUID budgetId, String actorId, String reason) {
        return transition(tenantId, budgetId, actorId, BudgetApprovalAction.REVIEW, reason);
    }

    @Transactional
    public BudgetEntity approve(UUID tenantId, UUID budgetId, String actorId, String reason) {
        BudgetEntity b = requireBudget(tenantId, budgetId);
        if (enforceSegregationOfDuties && actorId != null && actorId.equals(b.getCreatedBy())) {
            throw new IllegalStateException(
                    "Segregation of duties: the budget creator cannot approve their own budget");
        }
        return transition(tenantId, budgetId, actorId, BudgetApprovalAction.APPROVE, reason);
    }

    @Transactional
    public BudgetEntity reject(UUID tenantId, UUID budgetId, String actorId, String reason) {
        return transition(tenantId, budgetId, actorId, BudgetApprovalAction.REJECT, reason);
    }

    @Transactional
    public BudgetEntity returnToDraft(UUID tenantId, UUID budgetId, String actorId, String reason) {
        return transition(tenantId, budgetId, actorId, BudgetApprovalAction.RETURN, reason);
    }

    @Transactional
    public BudgetEntity activate(UUID tenantId, UUID budgetId, String actorId, String reason) {
        BudgetEntity b = transition(tenantId, budgetId, actorId, BudgetApprovalAction.ACTIVATE, reason);
        // mark current version ACTIVE and resync the availability projection
        BudgetVersionEntity v = requireCurrentVersion(tenantId, budgetId);
        v.setStatus(BudgetStatus.ACTIVE.name());
        if (v.getEffectiveFrom() == null) v.setEffectiveFrom(LocalDate.now());
        versionRepository.save(v);
        syncAllocations(tenantId, b, v);
        emit("BUDGET", budgetId.toString(), "BUDGET_ACTIVATED", tenantId, budgetPayload(b, v));
        return b;
    }

    @Transactional
    public BudgetEntity freeze(UUID tenantId, UUID budgetId, String actorId, String reason) {
        BudgetEntity b = transition(tenantId, budgetId, actorId, BudgetApprovalAction.FREEZE, reason);
        emit("BUDGET", budgetId.toString(), "BUDGET_FROZEN", tenantId, statusPayload(b));
        return b;
    }

    @Transactional
    public BudgetEntity close(UUID tenantId, UUID budgetId, String actorId, String reason) {
        BudgetEntity b = transition(tenantId, budgetId, actorId, BudgetApprovalAction.CLOSE, reason);
        emit("BUDGET", budgetId.toString(), "BUDGET_CLOSED", tenantId, statusPayload(b));
        return b;
    }

    private BudgetEntity transition(UUID tenantId, UUID budgetId, String actorId,
                                    BudgetApprovalAction action, String reason) {
        BudgetEntity b = requireBudget(tenantId, budgetId);
        BudgetStatus from = BudgetStatus.valueOf(b.getStatus());
        BudgetStatus to = BudgetLifecycle.require(from, action);
        b.setStatus(to.name());
        budgetRepository.save(b);

        BudgetApprovalEntity a = new BudgetApprovalEntity();
        a.setBudgetId(budgetId);
        a.setVersionId(b.getCurrentVersionId());
        a.setTenantId(tenantId);
        a.setStepNo((int) approvalRepository.findByBudgetIdAndTenantIdOrderByDecidedAtDesc(budgetId, tenantId).size());
        a.setAction(action.name());
        a.setDecidedBy(actorId);
        a.setDecidedAt(OffsetDateTime.now());
        a.setDecisionReason(reason);
        a.setFromStatus(from.name());
        a.setToStatus(to.name());
        approvalRepository.save(a);

        // lifecycle events for the significant SUBMIT/APPROVE stages (activate emits its own)
        if (action == BudgetApprovalAction.SUBMIT) {
            emit("BUDGET", budgetId.toString(), "BUDGET_SUBMITTED", tenantId, statusPayload(b));
        } else if (action == BudgetApprovalAction.APPROVE) {
            emit("BUDGET", budgetId.toString(), "BUDGET_APPROVED", tenantId, statusPayload(b));
        }
        return b;
    }

    /**
     * Resync the flat availability-control projection from the active version's
     * lines. Facility-scoped only (the projection is facility-grained); lines on
     * non-facility budgets are skipped and left to the rich model.
     */
    private void syncAllocations(UUID tenantId, BudgetEntity b, BudgetVersionEntity v) {
        if (b.getFacilityId() == null) {
            log.info("Budget {} has no facility scope; skipping allocation projection sync", b.getBudgetId());
            return;
        }
        List<BudgetLineEntity> lines =
                lineRepository.findByVersionIdAndTenantIdOrderByLineOrderAscCreatedAtAsc(v.getVersionId(), tenantId);
        // aggregate by (department, category)
        Map<String, BigDecimal> byKey = new LinkedHashMap<>();
        Map<String, BudgetLineEntity> sample = new LinkedHashMap<>();
        for (BudgetLineEntity line : lines) {
            String key = (line.getDepartmentId() == null ? "" : line.getDepartmentId())
                    + "|" + line.getBudgetCategory();
            byKey.merge(key, line.getAllocatedAmount(), BigDecimal::add);
            sample.putIfAbsent(key, line);
        }
        for (Map.Entry<String, BigDecimal> e : byKey.entrySet()) {
            BudgetLineEntity s = sample.get(e.getKey());
            BudgetAllocationEntity alloc = allocationRepository.findForSpendLine(
                    tenantId, b.getFacilityId(), s.getDepartmentId(), s.getBudgetCategory(), b.getPeriodYear())
                    .orElseGet(() -> {
                        BudgetAllocationEntity a = new BudgetAllocationEntity();
                        a.setTenantId(tenantId);
                        a.setFacilityId(b.getFacilityId());
                        a.setDepartmentId(s.getDepartmentId());
                        a.setBudgetCategory(s.getBudgetCategory());
                        a.setPeriodYear(b.getPeriodYear());
                        a.setSpentAmount(BigDecimal.ZERO);
                        a.setCommittedAmount(BigDecimal.ZERO);
                        return a;
                    });
            alloc.setAllocatedAmount(e.getValue());
            alloc.recomputeAvailable();
            allocationRepository.save(alloc);
            // link the sampled line back to its projection row
            if (s.getAllocationId() == null && alloc.getAllocationId() != null) {
                s.setAllocationId(alloc.getAllocationId());
                lineRepository.save(s);
            }
        }
    }

    // ----- reads ------------------------------------------------------------

    public BudgetEntity requireBudget(UUID tenantId, UUID budgetId) {
        return budgetRepository.findByBudgetIdAndTenantId(budgetId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Budget not found"));
    }

    private BudgetVersionEntity requireCurrentVersion(UUID tenantId, UUID budgetId) {
        return versionRepository.findByBudgetIdAndTenantIdAndCurrentTrue(budgetId, tenantId)
                .orElseThrow(() -> new IllegalStateException("Budget has no current version"));
    }

    public List<BudgetEntity> listBudgets(UUID tenantId, UUID facilityId, Integer year) {
        if (facilityId != null && year != null) {
            return budgetRepository.findByTenantIdAndFacilityIdAndPeriodYearOrderByCreatedAtDesc(tenantId, facilityId, year);
        }
        if (year != null) {
            return budgetRepository.findByTenantIdAndPeriodYearOrderByCreatedAtDesc(tenantId, year);
        }
        return budgetRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    public List<BudgetVersionEntity> versions(UUID tenantId, UUID budgetId) {
        return versionRepository.findByBudgetIdAndTenantIdOrderByVersionNoDesc(budgetId, tenantId);
    }

    public List<BudgetLineEntity> currentLines(UUID tenantId, UUID budgetId) {
        BudgetVersionEntity v = requireCurrentVersion(tenantId, budgetId);
        return lineRepository.findByVersionIdAndTenantIdOrderByLineOrderAscCreatedAtAsc(v.getVersionId(), tenantId);
    }

    public List<BudgetApprovalEntity> approvals(UUID tenantId, UUID budgetId) {
        return approvalRepository.findByBudgetIdAndTenantIdOrderByDecidedAtDesc(budgetId, tenantId);
    }

    // ----- payload + outbox helpers ----------------------------------------

    private Map<String, Object> statusPayload(BudgetEntity b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("budgetId", b.getBudgetId().toString());
        m.put("tenantId", b.getTenantId().toString());
        m.put("status", b.getStatus());
        m.put("periodYear", b.getPeriodYear());
        if (b.getFacilityId() != null) m.put("facilityId", b.getFacilityId().toString());
        return m;
    }

    private Map<String, Object> budgetPayload(BudgetEntity b, BudgetVersionEntity v) {
        Map<String, Object> m = statusPayload(b);
        m.put("title", b.getTitle());
        m.put("scopeLevel", b.getScopeLevel());
        m.put("currency", b.getCurrency());
        if (v != null) {
            m.put("versionId", v.getVersionId().toString());
            m.put("versionNo", v.getVersionNo());
            m.put("totalAmount", v.getTotalAmount());
        }
        return m;
    }

    private void emit(String aggregateType, String aggregateId, String eventType,
                      UUID tenantId, Map<String, Object> payload) {
        try {
            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType(aggregateType);
            event.setAggregateId(aggregateId);
            event.setEventType(eventType);
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setTenantId(tenantId);
            outboxRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to write budget outbox event: {}", eventType, e);
        }
    }
}
