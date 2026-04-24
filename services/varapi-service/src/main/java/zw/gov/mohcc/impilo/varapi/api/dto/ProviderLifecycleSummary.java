package zw.gov.mohcc.impilo.varapi.api.dto;

import zw.gov.mohcc.impilo.varapi.persistence.entity.*;
import java.util.List;

public record ProviderLifecycleSummary(
        Long providerId,
        List<ProviderApplicationEntity> applications,
        List<ProviderAffiliationEntity> affiliations,
        List<ProviderQualificationEntity> qualifications,
        List<ProviderPracticeContextEntity> practiceContexts,
        List<ProviderComplianceActionEntity> outstandingCompliance,
        ProviderCertificateEntity currentCertificate,
        PractitionerInChargeAssignmentEntity picAssignment,
        ProviderEligibilityResponse eligibility
) {
}
