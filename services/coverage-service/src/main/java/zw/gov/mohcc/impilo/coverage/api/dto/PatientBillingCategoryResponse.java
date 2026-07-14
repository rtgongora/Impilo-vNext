package zw.gov.mohcc.impilo.coverage.api.dto;

/**
 * Resolved billing category for a patient, used by downstream costing (COSTA charging rules).
 *
 * <p>Precedence: an active exemption-carrying subsidy enrolment (cv_subsidy_enrolments)
 * wins — its exemption category (e.g. INDIGENT, ELDERLY) drives waivers; otherwise the
 * active coverage plan's {@code planType} is the billing category (e.g. PRIVATE, PUBLIC);
 * otherwise {@code CASH} (self-pay).</p>
 *
 * @param patientCpid the patient CPID the category was resolved for
 * @param category    the billing category string (matches COSTA tariff/rule patient_category)
 * @param source      how the category was derived (SUBSIDY_ENROLLMENT | COVERAGE_PLAN | DEFAULT_SELF_PAY)
 * @param planCode    the active plan code the category came from, or null
 */
public record PatientBillingCategoryResponse(
        String patientCpid,
        String category,
        String source,
        String planCode
) {}
