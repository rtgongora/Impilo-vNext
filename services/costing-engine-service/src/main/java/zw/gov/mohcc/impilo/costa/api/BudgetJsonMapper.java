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
