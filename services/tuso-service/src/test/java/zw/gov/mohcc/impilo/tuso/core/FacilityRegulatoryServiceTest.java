package zw.gov.mohcc.impilo.tuso.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityRegulatoryDtos;
import zw.gov.mohcc.impilo.tuso.persistence.entity.*;
import zw.gov.mohcc.impilo.tuso.persistence.repository.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FacilityRegulatoryServiceTest {

    @Mock private FacilityRepository facilityRepository;
    @Mock private FacilityGeoRepository facilityGeoRepository;
    @Mock private FacilityApplicationRepository applicationRepository;
    @Mock private FacilityDocumentRepository documentRepository;
    @Mock private InspectionChecklistTemplateRepository checklistTemplateRepository;
    @Mock private FacilityInspectionRepository inspectionRepository;
    @Mock private InspectionFindingRepository findingRepository;
    @Mock private ComplianceActionRepository complianceActionRepository;
    @Mock private CommitteeReviewRepository committeeReviewRepository;
    @Mock private FacilityCertificateRepository certificateRepository;
    @Mock private FacilityUnitRepository facilityUnitRepository;
    @Mock private PractitionerInChargeAssignmentRepository practitionerRepository;
    @Mock private EnforcementCaseRepository enforcementCaseRepository;
    @Mock private FacilityStatusHistoryRepository statusHistoryRepository;
    @Mock private FacilityAuditEventRepository auditEventRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private RegulatoryRuleService regulatoryRuleService;
    @Mock private ApplicationGovernanceService applicationGovernanceService;
    @Mock private PremisesService premisesService;
    @Mock private InspectionContentService inspectionContentService;

    private FacilityRegulatoryService service;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        service = new FacilityRegulatoryService(
                facilityRepository,
                facilityGeoRepository,
                applicationRepository,
                documentRepository,
                checklistTemplateRepository,
                inspectionRepository,
                findingRepository,
                complianceActionRepository,
                committeeReviewRepository,
                certificateRepository,
                facilityUnitRepository,
                practitionerRepository,
                enforcementCaseRepository,
                statusHistoryRepository,
                auditEventRepository,
                outboxRepository,
                new ObjectMapper(),
                regulatoryRuleService,
                applicationGovernanceService,
                premisesService,
                inspectionContentService
        );
        // Rule-config defaults for unit tests (windows/cycle come from the rule store in prod).
        when(regulatoryRuleService.remediationWindowDays(any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(30);
        when(regulatoryRuleService.renewalCycleMonths()).thenReturn(12);
        // Manual §3.1: calendar-year validity — certificates expire 31 December of the issue year.
        when(regulatoryRuleService.certificateExpiryFrom(any()))
                .thenAnswer(inv -> LocalDate.of(((LocalDate) inv.getArgument(0)).getYear(), 12, 31));
        when(regulatoryRuleService.renewalDueWindowDays()).thenReturn(90);
        tenantId = UUID.randomUUID();
        TrustContextHolder.set(new TrustContext(
                tenantId,
                "actor-1",
                "DEVELOPER",
                "REGULATORY_WORKFLOW",
                "device-1",
                UUID.randomUUID(),
                null,
                null,
                null,
                AccessMode.INTERNAL
        ));

        when(facilityGeoRepository.findByFacilityId(anyLong())).thenReturn(Optional.empty());
        when(documentRepository.findByFacilityIdOrderByUploadedAtDesc(anyLong())).thenReturn(List.of());
        when(inspectionRepository.findByFacilityIdOrderByCreatedAtDesc(anyLong())).thenReturn(List.of());
        when(complianceActionRepository.findByFacilityIdOrderByCreatedAtDesc(anyLong())).thenReturn(List.of());
        when(committeeReviewRepository.findByFacilityIdOrderByDecidedAtDesc(anyLong())).thenReturn(List.of());
        when(certificateRepository.findByFacilityIdOrderByIssueDateDesc(anyLong())).thenReturn(List.of());
        when(facilityUnitRepository.findByFacilityIdOrderByCreatedAtAsc(anyLong())).thenReturn(List.of());
        when(practitionerRepository.findByFacilityIdOrderByStartDateDesc(anyLong())).thenReturn(List.of());
        when(enforcementCaseRepository.findByFacilityIdOrderByOpenedAtDesc(anyLong())).thenReturn(List.of());
        when(statusHistoryRepository.findByFacilityIdOrderByChangedAtDesc(anyLong())).thenReturn(List.of());
        when(auditEventRepository.findByFacilityIdOrderByCreatedAtDesc(anyLong())).thenReturn(List.of());
        when(findingRepository.findByInspectionInspectionIdOrderByCreatedAtAsc(any())).thenReturn(List.of());

        when(statusHistoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(auditEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(complianceActionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(findingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(inspectionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(applicationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    @Test
    void createApplication_movesFacilityIntoApplicationLifecycle() {
        FacilityEntity facility = facility(101L);
        AtomicReference<FacilityApplicationEntity> savedApplication = new AtomicReference<>();

        when(facilityRepository.findById(facility.getId())).thenReturn(Optional.of(facility));
        when(applicationRepository.save(any(FacilityApplicationEntity.class))).thenAnswer(invocation -> {
            FacilityApplicationEntity entity = invocation.getArgument(0);
            if (entity.getApplicationId() == null) {
                entity.setApplicationId(UUID.randomUUID());
            }
            savedApplication.set(entity);
            return entity;
        });
        when(applicationRepository.findByFacilityIdOrderByCreatedAtDesc(facility.getId())).thenAnswer(invocation ->
                savedApplication.get() == null ? List.of() : List.of(savedApplication.get()));

        FacilityRegulatoryDtos.FacilityRegulatoryProfileResponse response = service.createApplication(
                new FacilityRegulatoryDtos.CreateFacilityApplicationRequest(
                        facility.getId(),
                        FacilityApplicationType.NEW_REGISTRATION,
                        "PRIVATE",
                        "Mavambo Health",
                        "owner@example.org",
                        "0772000000",
                        "Mavambo Health (Pvt) Ltd",
                        "HIGH",
                        "PENDING",
                        "New clinic application",
                        facility.getFacilityCode(),
                        facility.getName(),
                        List.of("Mavambo Clinic"),
                        facility.getOwnership(),
                        "Mavambo Holdings",
                        "PRIVATE_HEALTH_INSTITUTION",
                        "GENERAL",
                        facility.getFacilityType(),
                        "PRIVATE_COMPANY",
                        "PRIVATE",
                        facility.getProvince(),
                        facility.getDistrict(),
                        "Harare",
                        "Ward 7",
                        "00263",
                        "12 Nelson Mandela Ave",
                        null,
                        null,
                        null,
                        Map.of("source", "unit-test"),
                        List.of(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );

        assertEquals(FacilityRegulatoryStatus.APPLICATION_IN_PROGRESS, facility.getRegulatoryStatus());
        assertNotNull(facility.getInstitutionFileNumber());
        assertNotNull(savedApplication.get());
        assertEquals(FacilityApplicationState.DRAFT, savedApplication.get().getCurrentWorkflowState());
        assertEquals(FacilityApplicationType.NEW_REGISTRATION, savedApplication.get().getApplicationType());
        assertEquals(FacilityRegulatoryStatus.APPLICATION_IN_PROGRESS, response.master().regulatoryStatus());
        assertEquals(1, response.applications().size());

        verify(statusHistoryRepository, atLeastOnce()).save(any(FacilityStatusHistoryEntity.class));
        verify(auditEventRepository, atLeastOnce()).save(any(FacilityAuditEventEntity.class));
        verify(outboxRepository, atLeastOnce()).save(any(EventOutboxEntity.class));
    }

    @Test
    void recordInspectionOutcome_createsRectificationWorkflowForFailedCriticalFinding() {
        FacilityEntity facility = facility(202L);
        // A recorded inspection presupposes the facility reached PENDING_INSPECTION —
        // the transition guard rejects a DRAFT facility jumping to rectification.
        facility.setRegulatoryStatus(FacilityRegulatoryStatus.PENDING_INSPECTION);
        FacilityApplicationEntity application = application(facility);
        FacilityInspectionEntity inspection = inspection(facility, application);

        when(inspectionRepository.findById(inspection.getInspectionId())).thenReturn(Optional.of(inspection));
        when(applicationRepository.findByFacilityIdOrderByCreatedAtDesc(facility.getId())).thenReturn(List.of(application));

        FacilityRegulatoryDtos.InspectionView response = service.recordInspectionOutcome(
                inspection.getInspectionId(),
                new FacilityRegulatoryDtos.RecordInspectionOutcomeRequest(
                        LocalDate.of(2026, 4, 21),
                        List.of(new FacilityRegulatoryDtos.FindingInput(
                                "GEN-001",
                                "General",
                                "Premises must be fit for purpose",
                                true,
                                InspectionFindingStatus.FAIL,
                                "CRITICAL",
                                "Sterilization area incomplete",
                                LocalDate.of(2026, 5, 5),
                                List.of(Map.of("fileReference", "s3://evidence/shortfall-1.pdf")),
                                "Facility operator",
                                "FACILITY_MANAGER",
                                "RECTIFY_SHORTFALL"
                        )),
                        "Initial inspection recorded",
                        null,
                        null,
                        null
                )
        );

        ArgumentCaptor<ComplianceActionEntity> actionCaptor = ArgumentCaptor.forClass(ComplianceActionEntity.class);
        verify(complianceActionRepository).save(actionCaptor.capture());

        assertEquals(FacilityApplicationState.AWAITING_RECTIFICATION, application.getCurrentWorkflowState());
        assertEquals(FacilityRegulatoryStatus.PENDING_RECTIFICATION, facility.getRegulatoryStatus());
        assertEquals("NON_COMPLIANT_CRITICAL", response.outcome());
        assertEquals("RECTIFICATION_TRACKING", response.nextAction());
        assertEquals("RECTIFY_SHORTFALL", actionCaptor.getValue().getActionType());
        assertEquals(LocalDate.of(2026, 5, 5), actionCaptor.getValue().getDueDate());
        assertFalse(response.recommendation().isBlank());
        assertTrue(response.evidenceSummary().containsKey("failureCount"));
    }

    private FacilityEntity facility(Long facilityId) {
        FacilityEntity facility = new FacilityEntity();
        facility.setId(facilityId);
        facility.setTenantId(tenantId);
        facility.setFacilityCode("FAC-" + facilityId);
        facility.setName("Test Facility " + facilityId);
        facility.setFacilityType("CLINIC");
        facility.setProvince("Harare");
        facility.setDistrict("Harare");
        facility.setOwnership("PRIVATE");
        facility.setStatus("ACTIVE");
        facility.setRegulatoryStatus(FacilityRegulatoryStatus.DRAFT);
        facility.setOperationalStatus("NOT_YET_OPERATIONAL");
        return facility;
    }

    private FacilityApplicationEntity application(FacilityEntity facility) {
        FacilityApplicationEntity application = new FacilityApplicationEntity();
        application.setApplicationId(UUID.randomUUID());
        application.setTenantId(tenantId);
        application.setFacility(facility);
        application.setApplicationType(FacilityApplicationType.NEW_REGISTRATION);
        application.setApplicantName("Applicant");
        application.setCurrentWorkflowState(FacilityApplicationState.INSPECTION_SCHEDULED);
        application.setPriority("NORMAL");
        application.setFeeState("PAID");
        application.setCommitteeState("NOT_STARTED");
        application.setInstitutionFileNumber("HIF-001");
        return application;
    }

    private FacilityInspectionEntity inspection(FacilityEntity facility, FacilityApplicationEntity application) {
        InspectionChecklistTemplateEntity template = new InspectionChecklistTemplateEntity();
        template.setCode("CLINIC-INITIAL");
        template.setName("Clinic initial inspection");
        template.setInspectionTypeApplicability(FacilityInspectionType.INITIAL);
        template.setItemsJson(List.of(Map.of(
                "code", "GEN-001",
                "section", "General",
                "requirement", "Premises must be fit for purpose",
                "critical", true,
                "severity", "CRITICAL"
        )));

        FacilityInspectionEntity inspection = new FacilityInspectionEntity();
        inspection.setInspectionId(UUID.randomUUID());
        inspection.setTenantId(tenantId);
        inspection.setFacility(facility);
        inspection.setApplication(application);
        inspection.setInspectionType(FacilityInspectionType.INITIAL);
        inspection.setTemplate(template);
        inspection.setStatus(FacilityInspectionStatus.SCHEDULED);
        inspection.setReportStatus("DRAFT");
        inspection.setCaptureMode("ONLINE");
        return inspection;
    }
}
