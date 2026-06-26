package zw.gov.mohcc.impilo.coverage.api.dto;

/**
 * Resolved billing category for a patient, used by downstream costing (COSTA charging rules).
 *
 * <p>Derived from the patient's active member coverage: the coverage plan's {@code planType}
 * is the billing category (e.g. PRIVATE, PUBLIC). When the patient has no active coverage the
 * category defaults to {@code CASH} (self-pay). Per-member exemption classes (e.g. INDIGENT)
 * require a member-subsidy enrolment link that does not yet exist in coverage-service.</p>
 *
 * @param patientCpid the patient CPID the category was resolved for
 * @param category    the billing category string (matches COSTA tariff/rule patient_category)
 * @param source      how the category was derived (COVERAGE_PLAN | DEFAULT_SELF_PAY)
 * @param planCode    the active plan code the category came from, or null
 */
public record PatientBillingCategoryResponse(
        String patientCpid,
        String category,
        String source,
        String planCode
) {}
