package zw.gov.mohcc.impilo.costa.api;

import zw.gov.mohcc.impilo.costa.domain.entity.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** Stable JSON shapes for the managed-budget REST surface. */
final class BudgetJsonMapper {

    private BudgetJsonMapper() {}

    static Map<String, Object> budget(BudgetEntity b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("budgetId", b.getBudgetId().toString());
        m.put("facilityId", b.getFacilityId() != null ? b.getFacilityId().toString() : null);
        m.put("scopeLevel", b.getScopeLevel());
        m.put("periodYear", b.getPeriodYear());
        m.put("periodStart", b.getPeriodStart() != null ? b.getPeriodStart().toString() : null);
        m.put("periodEnd", b.getPeriodEnd() != null ? b.getPeriodEnd().toString() : null);
        m.put("title", b.getTitle());
        m.put("grantId", b.getGrantId());
        m.put("programmeCode", b.getProgrammeCode());
        m.put("fundingSourceId", b.getFundingSourceId() != null ? b.getFundingSourceId().toString() : null);
        m.put("currency", b.getCurrency());
        m.put("status", b.getStatus());
        m.put("currentVersionId", b.getCurrentVersionId() != null ? b.getCurrentVersionId().toString() : null);
        m.put("notes", b.getNotes());
        m.put("createdBy", b.getCreatedBy());
        m.put("createdAt", b.getCreatedAt() != null ? b.getCreatedAt().toString() : null);
        m.put("updatedAt", b.getUpdatedAt() != null ? b.getUpdatedAt().toString() : null);
        return m;
    }

    static Map<String, Object> version(BudgetVersionEntity v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("versionId", v.getVersionId().toString());
        m.put("budgetId", v.getBudgetId().toString());
        m.put("versionNo", v.getVersionNo());
        m.put("current", v.isCurrent());
        m.put("basis", v.getBasis());
        m.put("effectiveFrom", v.getEffectiveFrom() != null ? v.getEffectiveFrom().toString() : null);
        m.put("totalAmount", v.getTotalAmount());
        m.put("status", v.getStatus());
        m.put("supersededByVersionId", v.getSupersededByVersionId() != null ? v.getSupersededByVersionId().toString() : null);
        return m;
    }

    static Map<String, Object> line(BudgetLineEntity l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("lineId", l.getLineId().toString());
        m.put("versionId", l.getVersionId().toString());
        m.put("budgetId", l.getBudgetId().toString());
        m.put("costCenterId", l.getCostCenterId() != null ? l.getCostCenterId().toString() : null);
        m.put("departmentId", l.getDepartmentId());
        m.put("budgetCategory", l.getBudgetCategory());
        m.put("fundingSourceId", l.getFundingSourceId() != null ? l.getFundingSourceId().toString() : null);
        m.put("programmeCode", l.getProgrammeCode());
        m.put("grantId", l.getGrantId());
        m.put("districtCode", l.getDistrictCode());
        m.put("glAccountRef", l.getGlAccountRef());
        m.put("allocatedAmount", l.getAllocatedAmount());
        m.put("allocationId", l.getAllocationId() != null ? l.getAllocationId().toString() : null);
        m.put("lineOrder", l.getLineOrder());
        return m;
    }

    static Map<String, Object> approval(BudgetApprovalEntity a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("approvalId", a.getApprovalId().toString());
        m.put("budgetId", a.getBudgetId().toString());
        m.put("versionId", a.getVersionId() != null ? a.getVersionId().toString() : null);
        m.put("stepNo", a.getStepNo());
        m.put("action", a.getAction());
        m.put("decidedBy", a.getDecidedBy());
        m.put("decidedAt", a.getDecidedAt() != null ? a.getDecidedAt().toString() : null);
        m.put("decisionReason", a.getDecisionReason());
        m.put("fromStatus", a.getFromStatus());
        m.put("toStatus", a.getToStatus());
        return m;
    }

    static Map<String, Object> commitment(BudgetCommitmentEntity c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("commitmentId", c.getCommitmentId().toString());
        m.put("budgetId", c.getBudgetId().toString());
        m.put("budgetLineId", c.getBudgetLineId().toString());
        m.put("ownerSystem", c.getOwnerSystem());
        m.put("ownerReference", c.getOwnerReference());
        m.put("description", c.getDescription());
        m.put("committedAmount", c.getCommittedAmount());
        m.put("obligatedAmount", c.getObligatedAmount());
        m.put("liquidatedAmount", c.getLiquidatedAmount());
        m.put("outstanding", c.outstanding());
        m.put("status", c.getStatus());
        m.put("committedAt", c.getCommittedAt() != null ? c.getCommittedAt().toString() : null);
        m.put("expiresAt", c.getExpiresAt() != null ? c.getExpiresAt().toString() : null);
        return m;
    }

    static Map<String, Object> actual(BudgetActualReferenceEntity a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("actualRefId", a.getActualRefId().toString());
        m.put("budgetId", a.getBudgetId().toString());
        m.put("budgetLineId", a.getBudgetLineId().toString());
        m.put("source", a.getSource());
        m.put("sourceReference", a.getSourceReference());
        m.put("commitmentId", a.getCommitmentId() != null ? a.getCommitmentId().toString() : null);
        m.put("actualAmount", a.getActualAmount());
        m.put("ingestSource", a.getIngestSource());
        m.put("reversed", a.isReversed());
        m.put("postedAt", a.getPostedAt() != null ? a.getPostedAt().toString() : null);
        return m;
    }

    static Map<String, Object> variance(BudgetVarianceSnapshotEntity v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("varianceId", v.getVarianceId() != null ? v.getVarianceId().toString() : null);
        m.put("budgetId", v.getBudgetId().toString());
        m.put("budgetLineId", v.getBudgetLineId() != null ? v.getBudgetLineId().toString() : null);
        m.put("asOfDate", v.getAsOfDate() != null ? v.getAsOfDate().toString() : null);
        m.put("periodScope", v.getPeriodScope());
        m.put("budgeted", v.getBudgeted());
        m.put("committed", v.getCommitted());
        m.put("actual", v.getActual());
        m.put("available", v.getAvailable());
        m.put("varianceAmount", v.getVarianceAmount());
        m.put("variancePct", v.getVariancePct());
        m.put("classification", v.getClassification());
        m.put("driverNote", v.getDriverNote());
        return m;
    }

    static Map<String, Object> forecast(BudgetForecastSnapshotEntity f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("forecastId", f.getForecastId() != null ? f.getForecastId().toString() : null);
        m.put("budgetId", f.getBudgetId().toString());
        m.put("budgetLineId", f.getBudgetLineId() != null ? f.getBudgetLineId().toString() : null);
        m.put("asOfDate", f.getAsOfDate() != null ? f.getAsOfDate().toString() : null);
        m.put("method", f.getMethod());
        m.put("elapsedFraction", f.getElapsedFraction());
        m.put("burnRate", f.getBurnRate());
        m.put("projectedYearEnd", f.getProjectedYearEnd());
        m.put("projectedOverrun", f.getProjectedOverrun());
        m.put("confidenceNote", f.getConfidenceNote());
        m.put("inputs", f.getInputsJson());
        return m;
    }

    static Map<String, Object> revision(BudgetRevisionEntity r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("revisionId", r.getRevisionId().toString());
        m.put("budgetId", r.getBudgetId().toString());
        m.put("fromVersionId", r.getFromVersionId() != null ? r.getFromVersionId().toString() : null);
        m.put("toVersionId", r.getToVersionId() != null ? r.getToVersionId().toString() : null);
        m.put("revisionType", r.getRevisionType());
        m.put("reason", r.getReason());
        m.put("netChange", r.getNetChange());
        m.put("requestedBy", r.getRequestedBy());
        m.put("approvedBy", r.getApprovedBy());
        m.put("status", r.getStatus());
        return m;
    }

    static Map<String, Object> recommendation(BudgetRecommendationEntity r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("recommendationId", r.getRecommendationId().toString());
        m.put("budgetId", r.getBudgetId().toString());
        m.put("budgetLineId", r.getBudgetLineId() != null ? r.getBudgetLineId().toString() : null);
        m.put("ruleCode", r.getRuleCode());
        m.put("severity", r.getSeverity());
        m.put("title", r.getTitle());
        m.put("rationale", r.getRationale());
        m.put("evidence", r.getEvidenceJson());
        m.put("suggestedAction", r.getSuggestedAction());
        m.put("status", r.getStatus());
        m.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
        return m;
    }

    static Map<String, Object> threshold(BudgetThresholdEntity t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("thresholdId", t.getThresholdId().toString());
        m.put("budgetId", t.getBudgetId() != null ? t.getBudgetId().toString() : null);
        m.put("budgetLineId", t.getBudgetLineId() != null ? t.getBudgetLineId().toString() : null);
        m.put("metric", t.getMetric());
        m.put("warnAt", t.getWarnAt());
        m.put("hardStopAt", t.getHardStopAt());
        m.put("allowEmergencyOverride", t.isAllowEmergencyOverride());
        m.put("active", t.isActive());
        return m;
    }

    static Map<String, Object> reconciliation(BudgetReconciliationExceptionEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("exceptionId", e.getExceptionId().toString());
        m.put("budgetId", e.getBudgetId() != null ? e.getBudgetId().toString() : null);
        m.put("budgetLineId", e.getBudgetLineId() != null ? e.getBudgetLineId().toString() : null);
        m.put("exceptionType", e.getExceptionType());
        m.put("source", e.getSource());
        m.put("sourceReference", e.getSourceReference());
        m.put("expected", e.getExpected());
        m.put("observed", e.getObserved());
        m.put("delta", e.getDelta());
        m.put("status", e.getStatus());
        m.put("resolutionNote", e.getResolutionNote());
        return m;
    }

    static Map<String, Object> periodClose(BudgetPeriodCloseEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("closeId", e.getCloseId().toString());
        m.put("budgetId", e.getBudgetId().toString());
        m.put("financialPeriodId", e.getFinancialPeriodId().toString());
        m.put("status", e.getStatus());
        m.put("closedBy", e.getClosedBy());
        m.put("closedAt", e.getClosedAt() != null ? e.getClosedAt().toString() : null);
        m.put("carryForwardAmount", e.getCarryForwardAmount());
        m.put("notes", e.getNotes());
        return m;
    }

    static Map<String, Object> fundingSource(FundingSourceEntity f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fundingSourceId", f.getFundingSourceId().toString());
        m.put("name", f.getName());
        m.put("code", f.getCode());
        m.put("sourceType", f.getSourceType());
        m.put("donorName", f.getDonorName());
        m.put("grantReference", f.getGrantReference());
        m.put("ceilingAmount", f.getCeilingAmount());
        m.put("absorbedAmount", f.getAbsorbedAmount());
        m.put("startDate", f.getStartDate() != null ? f.getStartDate().toString() : null);
        m.put("endDate", f.getEndDate() != null ? f.getEndDate().toString() : null);
        m.put("status", f.getStatus());
        return m;
    }
}
