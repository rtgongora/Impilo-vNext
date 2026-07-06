package zw.gov.mohcc.impilo.governance.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.governance.persistence.*;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Full initiate → two-person approve → execute lifecycle for platform actions,
 * with real {@link AccessRequestService} and {@link TwoPersonApprovalService}
 * over in-memory repository mocks.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlatformOriginServiceTest {

    @Mock private PlatformOriginRepository platformOriginRepository;
    @Mock private CountryOperationRepository countryOperationRepository;
    @Mock private NationalAppointmentRepository nationalAppointmentRepository;
    @Mock private AccessRequestRepository accessRequestRepository;
    @Mock private PlatformActionApprovalRepository approvalRepository;
    @Mock private GovernanceEventService governanceEventService;

    private PlatformOriginService service;

    private final UUID platformTenant = UUID.randomUUID();
    private final UUID countryTenant = UUID.randomUUID();

    private final Map<UUID, AccessRequestEntity> accessRequests = new LinkedHashMap<>();
    private final List<PlatformActionApprovalEntity> approvals = new ArrayList<>();
    private final Map<UUID, CountryOperationEntity> countryOps = new LinkedHashMap<>();
    private final Map<UUID, NationalAppointmentEntity> appointments = new LinkedHashMap<>();
    private final Map<UUID, PlatformOriginEntity> origins = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        TwoPersonApprovalService twoPersonApprovalService =
                new TwoPersonApprovalService(approvalRepository, objectMapper);
        AccessRequestService accessRequestService = new AccessRequestService(
                accessRequestRepository, governanceEventService, objectMapper, twoPersonApprovalService);
        service = new PlatformOriginService(platformOriginRepository, countryOperationRepository,
                nationalAppointmentRepository, accessRequestService, twoPersonApprovalService,
                governanceEventService, objectMapper);

        TrustContextHolder.set(new TrustContext(platformTenant, "initiator-1", "OPERATOR",
                "OPERATIONS", "test", UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));

        when(accessRequestRepository.save(any())).thenAnswer(i -> {
            AccessRequestEntity e = i.getArgument(0);
            accessRequests.put(e.getId(), e);
            return e;
        });
        when(accessRequestRepository.findById(any())).thenAnswer(i ->
                Optional.ofNullable(accessRequests.get(i.<UUID>getArgument(0))));

        when(approvalRepository.save(any())).thenAnswer(i -> {
            PlatformActionApprovalEntity e = i.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            approvals.add(e);
            return e;
        });
        when(approvalRepository.findByAccessRequestIdOrderByDecidedAtAsc(any())).thenAnswer(i ->
                approvals.stream()
                        .filter(a -> a.getAccessRequestId().equals(i.getArgument(0)))
                        .toList());
        when(approvalRepository.existsByAccessRequestIdAndApproverUserId(any(), any())).thenAnswer(i ->
                approvals.stream().anyMatch(a -> a.getAccessRequestId().equals(i.getArgument(0))
                        && a.getApproverUserId().equals(i.getArgument(1))));

        when(countryOperationRepository.save(any())).thenAnswer(i -> {
            CountryOperationEntity e = i.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            countryOps.put(e.getId(), e);
            return e;
        });
        when(countryOperationRepository.findById(any())).thenAnswer(i ->
                Optional.ofNullable(countryOps.get(i.<UUID>getArgument(0))));
        when(countryOperationRepository.findByTenantId(any())).thenAnswer(i ->
                countryOps.values().stream()
                        .filter(op -> op.getTenantId().equals(i.getArgument(0)))
                        .findFirst());
        when(countryOperationRepository.findAllByOrderByCreatedAtAsc()).thenAnswer(i ->
                new ArrayList<>(countryOps.values()));

        when(nationalAppointmentRepository.save(any())).thenAnswer(i -> {
            NationalAppointmentEntity e = i.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            appointments.put(e.getId(), e);
            return e;
        });
        when(nationalAppointmentRepository.findByIdAndCountryOperationId(any(), any())).thenAnswer(i ->
                Optional.ofNullable(appointments.get(i.<UUID>getArgument(0)))
                        .filter(a -> a.getCountryOperationId().equals(i.getArgument(1))));
        when(nationalAppointmentRepository.findByCountryOperationIdOrderByAppointedAtAsc(any())).thenAnswer(i ->
                appointments.values().stream()
                        .filter(a -> a.getCountryOperationId().equals(i.getArgument(0)))
                        .toList());

        when(platformOriginRepository.save(any())).thenAnswer(i -> {
            PlatformOriginEntity e = i.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            origins.put(e.getId(), e);
            return e;
        });
        when(platformOriginRepository.findFirstByOrderByCreatedAtAsc()).thenAnswer(i ->
                origins.values().stream().findFirst());
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private AccessRequestEntity initiateCountryOperation() {
        return service.initiateCountryOperation(platformTenant, "initiator-1", Map.of(
                "tenantId", countryTenant.toString(),
                "isoCountryCode", "zw",
                "displayName", "Zimbabwe"));
    }

    private void approveTwice(UUID accessRequestId) {
        service.approve(accessRequestId, "approver-1", "PLATFORM_ORIGIN_ADMINISTRATOR", "APPROVE", null);
        service.approve(accessRequestId, "approver-2", "PLATFORM_ORIGIN_ADMINISTRATOR", "APPROVE", null);
    }

    // ── Country operation ──

    @Test
    void countryOperationRequiresApprovedPlatformAction() {
        AccessRequestEntity request = initiateCountryOperation();
        assertEquals("PLATFORM_ACTION", request.getRequestType());
        assertEquals("PENDING", request.getStatus());
        assertTrue(request.getApprovalsRequired().contains("PLATFORM_ORIGIN_ADMINISTRATOR"));

        // Execution before approval is refused; nothing is created.
        assertThrows(IllegalStateException.class,
                () -> service.execute(request.getId(), "initiator-1"));
        assertTrue(countryOps.isEmpty(), "no country operation may exist before approval");
    }

    @Test
    void approvedCountryOperationExecutesExactlyOnce() {
        AccessRequestEntity request = initiateCountryOperation();
        approveTwice(request.getId());
        assertEquals("APPROVED", accessRequests.get(request.getId()).getStatus());

        Map<String, Object> out = service.execute(request.getId(), "approver-1");
        assertEquals("CREATE_COUNTRY_OPERATION", out.get("action"));
        assertEquals(1, countryOps.size());
        CountryOperationEntity op = countryOps.values().iterator().next();
        assertEquals("ZW", op.getIsoCountryCode());
        assertEquals(countryTenant, op.getTenantId());
        assertEquals("ACTIVE", op.getStatus());
        assertEquals(request.getId(), op.getCreatedByPlatformAction());
        assertEquals("EXECUTED", accessRequests.get(request.getId()).getStatus());

        // Replay protection: an EXECUTED action cannot run again.
        assertThrows(IllegalStateException.class,
                () -> service.execute(request.getId(), "approver-1"));
        assertEquals(1, countryOps.size());
    }

    @Test
    void initiateRejectsDuplicateCountryOperationForTenant() {
        AccessRequestEntity request = initiateCountryOperation();
        approveTwice(request.getId());
        service.execute(request.getId(), "approver-1");

        assertThrows(IllegalArgumentException.class, this::initiateCountryOperation);
    }

    @Test
    void initiatorApprovalIsRefused() {
        AccessRequestEntity request = initiateCountryOperation();
        assertThrows(IllegalArgumentException.class, () ->
                service.approve(request.getId(), "initiator-1",
                        "PLATFORM_ORIGIN_ADMINISTRATOR", "APPROVE", null));
        assertEquals("PENDING", accessRequests.get(request.getId()).getStatus());
    }

    @Test
    void ensurePlatformOriginCreatesSingletonOnce() {
        service.ensurePlatformOrigin();
        service.ensurePlatformOrigin();
        assertEquals(1, origins.size());
        PlatformOriginEntity root = origins.values().iterator().next();
        assertNotNull(root.getRecoveryPolicy());
        assertEquals("TWO_PERSON_RECOVERY", root.getRecoveryPolicy().get("type"));
    }

    // ── Appointment lifecycle ──

    private CountryOperationEntity activeCountryOperation() {
        AccessRequestEntity request = initiateCountryOperation();
        approveTwice(request.getId());
        service.execute(request.getId(), "approver-1");
        return countryOps.values().iterator().next();
    }

    @Test
    void appointmentLifecycleIncludingRevocation() {
        CountryOperationEntity op = activeCountryOperation();

        // Appoint — itself a two-person platform action.
        AccessRequestEntity appoint = service.initiateAppointment(platformTenant, "initiator-1",
                op.getId(), Map.of(
                        "subjectUserId", "national-admin-1",
                        "role", "NATIONAL_ADMINISTRATOR",
                        "expiresAt", "2030-01-01T00:00:00Z",
                        "scope", Map.of("country", "ZW")));
        assertEquals("PLATFORM_ACTION", appoint.getRequestType());
        assertThrows(IllegalStateException.class,
                () -> service.execute(appoint.getId(), "initiator-1"));

        approveTwice(appoint.getId());
        service.execute(appoint.getId(), "approver-1");

        List<NationalAppointmentEntity> list = service.listAppointments(op.getId());
        assertEquals(1, list.size());
        NationalAppointmentEntity appointment = list.get(0);
        assertEquals("national-admin-1", appointment.getSubjectUserId());
        assertEquals("NATIONAL_ADMINISTRATOR", appointment.getRole());
        assertEquals("PLATFORM_ORIGIN", appointment.getAppointmentSource());
        assertEquals("initiator-1", appointment.getAppointedBy());
        assertNotNull(appointment.getAppointedAt());
        assertNotNull(appointment.getExpiresAt());
        assertEquals("ZW", appointment.getScope().get("country"));
        assertEquals("ACTIVE", appointment.getStatus());

        // Revoke — also a two-person platform action.
        AccessRequestEntity revoke = service.initiateRevocation(platformTenant, "initiator-1",
                op.getId(), appointment.getId(), "compromised credentials");
        assertEquals("ACTIVE", appointment.getStatus(), "revocation must not apply before approval");

        approveTwice(revoke.getId());
        service.execute(revoke.getId(), "approver-2");

        assertEquals("REVOKED", appointments.get(appointment.getId()).getStatus());
        assertEquals("compromised credentials", appointments.get(appointment.getId()).getRevocationReason());

        // A revoked appointment cannot be revoked again.
        assertThrows(IllegalStateException.class, () ->
                service.initiateRevocation(platformTenant, "initiator-1",
                        op.getId(), appointment.getId(), "again"));
    }

    @Test
    void listApprovalsReturnsWhoApprovedProjection() {
        AccessRequestEntity request = initiateCountryOperation();
        service.approve(request.getId(), "approver-1", "PLATFORM_ORIGIN_ADMINISTRATOR", "APPROVE", "first sign-off");
        service.approve(request.getId(), "approver-2", "PLATFORM_ORIGIN_ADMINISTRATOR", "APPROVE", null);

        List<PlatformActionApprovalView> views = service.listApprovals(request.getId());
        assertEquals(2, views.size());
        assertEquals("approver-1", views.get(0).approverUserId());
        assertEquals("PLATFORM_ORIGIN_ADMINISTRATOR", views.get(0).approverRole());
        assertEquals("APPROVE", views.get(0).decision());
        assertEquals("first sign-off", views.get(0).note());
        assertEquals("approver-2", views.get(1).approverUserId());
    }

    @Test
    void listApprovalsRejectsNonPlatformAction() {
        assertThrows(IllegalArgumentException.class, () -> service.listApprovals(UUID.randomUUID()));
    }

    @Test
    void appointmentRequiresExistingCountryOperation() {
        assertThrows(IllegalArgumentException.class, () ->
                service.initiateAppointment(platformTenant, "initiator-1", UUID.randomUUID(),
                        Map.of("subjectUserId", "national-admin-1")));
    }
}
