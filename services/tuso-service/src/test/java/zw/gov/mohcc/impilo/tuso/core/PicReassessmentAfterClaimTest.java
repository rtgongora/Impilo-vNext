package zw.gov.mohcc.impilo.tuso.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.tuso.integration.VarapiClient;
import zw.gov.mohcc.impilo.tuso.persistence.entity.PicNominationEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityAuditEventRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.PicNominationRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.PractitionerInChargeAssignmentRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HAR W3 guard tests — unparking a nomination is a re-question, not a promotion.
 *
 * <p>The HPA seed left 6,180 nominations at ELIGIBILITY_CHECKED because VARAPI assessed each
 * unclaimed profile as ineligible. That was correct. When the person claims the profile the
 * question is worth asking again — but the answer still comes from VARAPI, the practitioner still
 * has to accept, and no assignment may appear on the strength of a claim.</p>
 */
@ExtendWith(MockitoExtension.class)
class PicReassessmentAfterClaimTest {

    private static final String PROVIDER = "PROV-ABC";

    @Mock private PicNominationRepository nominationRepository;
    @Mock private PractitionerInChargeAssignmentRepository assignmentRepository;
    @Mock private FacilityRepository facilityRepository;
    @Mock private FacilityAuditEventRepository auditEventRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private VarapiClient varapiClient;

    private PicNominationService service;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PicNominationService(nominationRepository, assignmentRepository,
                facilityRepository, auditEventRepository, outboxRepository, varapiClient);
    }

    private TrustContext ctx() {
        return new TrustContext(tenantId, "SYSTEM:VARAPI-CLAIM-KAFKA", "SERVICE",
                "REGULATORY_OPERATIONS", "kafka-consumer", UUID.randomUUID(), null, null, null,
                AccessMode.INTERNAL);
    }

    private PicNominationEntity parked() {
        PicNominationEntity nomination = new PicNominationEntity();
        nomination.setNominationId(UUID.randomUUID());
        nomination.setTenantId(tenantId);
        nomination.setFacilityId(101L);
        nomination.setProviderPublicId(PROVIDER);
        nomination.setState("ELIGIBILITY_CHECKED");
        nomination.setEligibilityResult("INELIGIBLE");
        return nomination;
    }

    @Test
    void aNowEligibleNominationAdvancesOnlyToAcceptancePending() {
        PicNominationEntity nomination = parked();
        when(nominationRepository.findByProviderPublicIdAndStateIn(PROVIDER, List.of("ELIGIBILITY_CHECKED")))
                .thenReturn(List.of(nomination));
        when(varapiClient.requestEligibilityAssessment(anyString(), anyString(), any(), anyString(), anyString()))
                .thenReturn(Map.of("result", "ELIGIBLE"));

        int advanced = service.reassessAfterClaim(ctx(), PROVIDER);

        assertThat(advanced).isEqualTo(1);
        assertThat(nomination.getState()).isEqualTo("PRACTITIONER_ACCEPTANCE_PENDING");
        // The practitioner still has to accept and the regulator still has to review.
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void aStillIneligibleNominationStaysParked() {
        PicNominationEntity nomination = parked();
        when(nominationRepository.findByProviderPublicIdAndStateIn(PROVIDER, List.of("ELIGIBILITY_CHECKED")))
                .thenReturn(List.of(nomination));
        when(varapiClient.requestEligibilityAssessment(anyString(), anyString(), any(), anyString(), anyString()))
                .thenReturn(Map.of("result", "INELIGIBLE"));

        int advanced = service.reassessAfterClaim(ctx(), PROVIDER);

        assertThat(advanced).isZero();
        assertThat(nomination.getState()).isEqualTo("ELIGIBILITY_CHECKED");
        verify(assignmentRepository, never()).save(any());
    }

    /** A claim in one tenant must not move another tenant's queue. */
    @Test
    void foreignTenantNominationsAreUntouched() {
        PicNominationEntity foreign = parked();
        foreign.setTenantId(UUID.randomUUID());
        when(nominationRepository.findByProviderPublicIdAndStateIn(PROVIDER, List.of("ELIGIBILITY_CHECKED")))
                .thenReturn(List.of(foreign));

        int advanced = service.reassessAfterClaim(ctx(), PROVIDER);

        assertThat(advanced).isZero();
        assertThat(foreign.getState()).isEqualTo("ELIGIBILITY_CHECKED");
        verify(varapiClient, never())
                .requestEligibilityAssessment(anyString(), anyString(), any(), anyString(), anyString());
        verify(nominationRepository, never()).save(any());
    }
}
