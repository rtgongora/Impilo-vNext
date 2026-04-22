package zw.gov.mohcc.impilo.varapi.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.varapi.api.dto.ProviderLifecycleSummary;
import zw.gov.mohcc.impilo.varapi.core.*;
import zw.gov.mohcc.impilo.varapi.persistence.entity.*;

import java.util.List;
import java.util.Map;

/**
 * Dashboard controller for provider lifecycle overview and analytics.
 */
@RestController
@RequestMapping("/v1/internal/providers/lifecycle")
public class ProviderLifecycleController {

    private final ProviderApplicationService applicationService;
    private final AffiliationService affiliationService;
    private final PractitionerInChargeService picService;
    private final QualificationService qualificationService;
    private final PracticeContextService practiceContextService;
    private final ComplianceService complianceService;
    private final CertificateService certificateService;

    public ProviderLifecycleController(
            ProviderApplicationService applicationService,
            AffiliationService affiliationService,
            PractitionerInChargeService picService,
            QualificationService qualificationService,
            PracticeContextService practiceContextService,
            ComplianceService complianceService,
            CertificateService certificateService) {
        this.applicationService = applicationService;
        this.affiliationService = affiliationService;
        this.picService = picService;
        this.qualificationService = qualificationService;
        this.practiceContextService = practiceContextService;
        this.complianceService = complianceService;
        this.certificateService = certificateService;
    }

    @GetMapping("/provider/{providerId}/summary")
    public ResponseEntity<ProviderLifecycleSummary> getProviderSummary(@PathVariable Long providerId) {
        List<ProviderApplicationEntity> applications = applicationService.getApplicationsByProvider(providerId);
        List<ProviderAffiliationEntity> affiliations = affiliationService.getActiveAffiliationsByProvider(providerId);
        List<ProviderQualificationEntity> qualifications = qualificationService.getVerifiedQualifications(providerId);
        List<ProviderPracticeContextEntity> contexts = practiceContextService.getActiveContextsByProvider(providerId);
        List<ProviderComplianceActionEntity> outstandingActions = complianceService.getOutstandingActions(providerId);
        ProviderCertificateEntity currentCertificate = certificateService.getCurrentCertificate(providerId);
        PractitionerInChargeAssignmentEntity picAssignment = picService.getActiveAssignmentByProvider(providerId);

        return ResponseEntity.ok(new ProviderLifecycleSummary(
                providerId,
                applications,
                affiliations,
                qualifications,
                contexts,
                outstandingActions,
                currentCertificate,
                picAssignment
        ));
    }

    @GetMapping("/provider/{providerId}/applications")
    public ResponseEntity<List<ProviderApplicationEntity>> getApplications(@PathVariable Long providerId) {
        return ResponseEntity.ok(applicationService.getApplicationsByProvider(providerId));
    }

    @GetMapping("/provider/{providerId}/affiliations")
    public ResponseEntity<List<ProviderAffiliationEntity>> getAffiliations(@PathVariable Long providerId) {
        return ResponseEntity.ok(affiliationService.getAffiliationsByProvider(providerId));
    }

    @GetMapping("/provider/{providerId}/qualifications")
    public ResponseEntity<List<ProviderQualificationEntity>> getQualifications(@PathVariable Long providerId) {
        return ResponseEntity.ok(qualificationService.getQualificationsByProvider(providerId));
    }

    @GetMapping("/provider/{providerId}/practice-contexts")
    public ResponseEntity<List<ProviderPracticeContextEntity>> getPracticeContexts(@PathVariable Long providerId) {
        return ResponseEntity.ok(practiceContextService.getActiveContextsByProvider(providerId));
    }

    @GetMapping("/provider/{providerId}/compliance")
    public ResponseEntity<List<ProviderComplianceActionEntity>> getComplianceActions(@PathVariable Long providerId) {
        return ResponseEntity.ok(complianceService.getComplianceActionsByProvider(providerId));
    }

    @GetMapping("/provider/{providerId}/certificates")
    public ResponseEntity<List<ProviderCertificateEntity>> getCertificates(@PathVariable Long providerId) {
        return ResponseEntity.ok(certificateService.getCertificatesByProvider(providerId));
    }

    @GetMapping("/dashboard/pending-applications")
    public ResponseEntity<List<ProviderApplicationEntity>> getPendingApplications(
            @RequestParam(required = false) String workflowState) {
        return ResponseEntity.ok(applicationService.getOpenApplications(workflowState));
    }

    @GetMapping("/dashboard/pending-affiliations")
    public ResponseEntity<Map<String, Object>> getPendingAffiliations() {
        List<ProviderAffiliationEntity> pending = affiliationService.getPendingAffiliations();
        return ResponseEntity.ok(Map.of(
                "count", pending.size(),
                "affiliations", pending
        ));
    }

    @GetMapping("/dashboard/pending-qualifications")
    public ResponseEntity<Map<String, Object>> getPendingQualifications() {
        List<ProviderQualificationEntity> pending = qualificationService.getPendingVerification();
        return ResponseEntity.ok(Map.of(
                "count", pending.size(),
                "qualifications", pending
        ));
    }

    @GetMapping("/dashboard/pending-cpd")
    public ResponseEntity<List<ProviderComplianceActionEntity>> getPendingCpdCompliance() {
        return ResponseEntity.ok(complianceService.getActionsDueWithin(90));
    }

    @GetMapping("/dashboard/expiring-certificates")
    public ResponseEntity<List<ProviderCertificateEntity>> getExpiringCertificates() {
        return ResponseEntity.ok(certificateService.getExpiringCertificates(60));
    }

    @GetMapping("/dashboard/expiring-practice-contexts")
    public ResponseEntity<List<ProviderPracticeContextEntity>> getExpiringPracticeContexts() {
        return ResponseEntity.ok(practiceContextService.getExpiringContexts(60));
    }

    @GetMapping("/dashboard/overdue-compliance")
    public ResponseEntity<List<ProviderComplianceActionEntity>> getOverdueCompliance() {
        return ResponseEntity.ok(complianceService.getAllOverdueActions());
    }
}