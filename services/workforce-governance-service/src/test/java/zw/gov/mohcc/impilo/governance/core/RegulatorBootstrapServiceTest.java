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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The regulator bootstrap request over the REAL two-person rail (RB-3).
 *
 * <p>Uses live {@link PlatformOriginService}, {@link TwoPersonApprovalService} and
 * {@link AccessRequestService} over in-memory repository mocks, so the four-eyes guarantees are
 * exercised rather than stubbed: this is where "nobody self-approves a founding appointment" is
 * actually proven, not asserted.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegulatorBootstrapServiceTest {

    @Mock private PlatformOriginRepository platformOriginRepository;
    @Mock private CountryOperationRepository countryOperationRepository;
    @Mock private NationalAppointmentRepository nationalAppointmentRepository;
    @Mock private AccessRequestRepository accessRequestRepository;
    @Mock private PlatformActionApprovalRepository approvalRepository;
    @Mock private GovernanceEventService governanceEventService;
    @Mock private RegulatorBootstrapRequestRepository bootstrapRepository;

    private RegulatorBootstrapService bootstrapService;

    private final UUID tenant = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();

    private final Map<UUID, AccessRequestEntity> accessRequests = new LinkedHashMap<>();
    private final List<PlatformActionApprovalEntity> approvals = new ArrayList<>();
    private final Map<UUID, RegulatorBootstrapRequestEntity> bootstraps = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        TwoPersonApprovalService twoPerson = new TwoPersonApprovalService(approvalRepository, objectMapper);
        AccessRequestService accessRequestService =
                new AccessRequestService(accessRequestRepository, governanceEventService, objectMapper, twoPerson);
        PlatformOriginService platformOriginService = new PlatformOriginService(
                platformOriginRepository, countryOperationRepository, nationalAppointmentRepository,
                accessRequestService, twoPerson, governanceEventService, objectMapper);
        bootstrapService = new RegulatorBootstrapService(bootstrapRepository, platformOriginService);

        // AccessRequestService.create reads the acting tenant/actor from the trust context, exactly
        // as PlatformOriginServiceTest does; without it the real rail cannot mint an access request.
        TrustContextHolder.set(new TrustContext(tenant, "founder-hid", "OPERATOR",
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
                approvals.stream().filter(a -> a.getAccessRequestId().equals(i.getArgument(0))).toList());
        when(approvalRepository.existsByAccessRequestIdAndApproverUserId(any(), any())).thenAnswer(i ->
                approvals.stream().anyMatch(a -> a.getAccessRequestId().equals(i.getArgument(0))
                        && a.getApproverUserId().equals(i.getArgument(1))));

        when(bootstrapRepository.save(any())).thenAnswer(i -> {
            RegulatorBootstrapRequestEntity e = i.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            // No @PrePersist here (that is JPA's); createdAt stays null, and view() null-guards it.
            bootstraps.put(e.getId(), e);
            return e;
        });
        when(bootstrapRepository.findByAccessRequestId(any())).thenAnswer(i ->
                bootstraps.values().stream()
                        .filter(b -> i.getArgument(0).equals(b.getAccessRequestId())).findFirst());
        when(bootstrapRepository.existsByTenantIdAndOrganizationIdAndStatusIn(any(), any(), any()))
                .thenAnswer(i -> bootstraps.values().stream().anyMatch(b ->
                        b.getTenantId().equals(i.getArgument(0))
                        && b.getOrganizationId().equals(i.getArgument(1))
                        && ((List<?>) i.getArgument(2)).contains(b.getStatus())));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
        accessRequests.clear();
        approvals.clear();
        bootstraps.clear();
    }

    private RegulatorBootstrapRequestEntity submit() {
        return bootstrapService.submit(tenant, orgId, "founder-hid",
                RegulatorBootstrapService.FOUNDING_ROLE, "doc:gazette-1",
                Map.of("source", "GAZETTE"), "founder-hid");
    }

    // ── the happy path, over the real rail ──────────────────────────────────

    @Test
    void twoDistinctApproversActivateTheFoundingAppointment() {
        RegulatorBootstrapRequestEntity req = submit();
        assertThat(req.getPublicId()).startsWith("RBR-");
        assertThat(req.getStatus()).isEqualTo("SUBMITTED");
        assertThat(req.getAccessRequestId()).isNotNull();
        UUID vehicle = req.getAccessRequestId();

        bootstrapService.approve(vehicle, "approver-1", "PLATFORM_ORIGIN_ADMINISTRATOR", "APPROVE", null);
        assertThat(bootstraps.get(req.getId()).getStatus()).isEqualTo("PENDING_SECOND_APPROVAL");

        bootstrapService.approve(vehicle, "approver-2", "PLATFORM_ORIGIN_ADMINISTRATOR", "APPROVE", null);
        RegulatorBootstrapRequestEntity activated = bootstrapService.activate(vehicle, "national-admin");

        assertThat(activated.getStatus()).isEqualTo("ACTIVATED");
        assertThat(activated.getActivatedAt()).isNotNull();
        // The event org-registry consumes to create and verify the appointment.
        verify(governanceEventService).enqueue(eq("REGULATOR_BOOTSTRAP"), any(), any(), any(), eq(tenant), any());
    }

    // ── the four-eyes guarantees, exercised not stubbed ─────────────────────

    /** The whole point of the rail: one person cannot found a regulator alone. */
    @Test
    void oneApprovalIsNotEnoughToActivate() {
        RegulatorBootstrapRequestEntity req = submit();
        bootstrapService.approve(req.getAccessRequestId(), "approver-1", "PLATFORM_ORIGIN_ADMINISTRATOR", "APPROVE", null);

        assertThatThrownBy(() -> bootstrapService.activate(req.getAccessRequestId(), "national-admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APPROVED");
        assertThat(bootstraps.get(req.getId()).getStatus()).isEqualTo("PENDING_SECOND_APPROVAL");
    }

    /**
     * The same person approving twice is one approval, not two — TwoPersonApprovalService's
     * per-approver uniqueness. Without it a single actor could found a regulator by clicking twice.
     */
    @Test
    void theSameApproverTwiceIsRejectedOutright() {
        RegulatorBootstrapRequestEntity req = submit();
        bootstrapService.approve(req.getAccessRequestId(), "approver-1", "PLATFORM_ORIGIN_ADMINISTRATOR", "APPROVE", null);

        // The rail does not silently dedupe a repeat approver — it refuses the second decision, so
        // a single actor cannot inch a founding appointment toward approval by clicking twice.
        assertThatThrownBy(() -> bootstrapService.approve(
                req.getAccessRequestId(), "approver-1", "PLATFORM_ORIGIN_ADMINISTRATOR", "APPROVE", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already recorded");
    }

    // ── domain guards ───────────────────────────────────────────────────────

    @Test
    void onlyTheFoundingRoleMayBeBootstrapped() {
        assertThatThrownBy(() -> bootstrapService.submit(tenant, orgId, "founder-hid",
                "REGULATOR_BUSINESS_OWNER", "doc:1", null, "founder-hid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only FOUNDING_REGULATOR_ADMINISTRATOR");
    }

    @Test
    void aRequestNeedsAClaimant() {
        assertThatThrownBy(() -> bootstrapService.submit(tenant, orgId, "  ",
                RegulatorBootstrapService.FOUNDING_ROLE, "doc:1", null, "actor"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("claimant");
    }

    /**
     * One live request per regulator: two founders each approved by a different pair would both
     * activate and produce two founding administrators, defeating the one-founder cap.
     */
    @Test
    void aSecondLiveRequestForTheSameRegulatorIsRefused() {
        submit();
        assertThatThrownBy(this::submit)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already open");
    }

    @Test
    void theViewNeverLeaksTheClaimantOrEvidence() {
        Map<String, Object> view = bootstrapService.view(submit());
        assertThat(view).containsKey("status").containsKey("publicId");
        assertThat(view).doesNotContainKey("claimantHealthId");
        assertThat(view).doesNotContainKey("evidenceRef");
        assertThat(view).containsEntry("hasEvidence", true);
    }

    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
