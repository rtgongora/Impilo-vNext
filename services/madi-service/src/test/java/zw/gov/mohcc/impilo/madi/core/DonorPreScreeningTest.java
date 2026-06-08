package zw.gov.mohcc.impilo.madi.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.madi.domain.PreScreeningOutcome;
import zw.gov.mohcc.impilo.madi.events.MadiEventEmitter;
import zw.gov.mohcc.impilo.madi.persistence.entity.DonorPreScreeningEntity;
import zw.gov.mohcc.impilo.madi.persistence.entity.DonorProfileEntity;
import zw.gov.mohcc.impilo.madi.persistence.repository.*;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DonorPreScreeningTest {

    @Mock private DonorProfileRepository donorProfileRepository;
    @Mock private DonorEligibilityScreeningRepository screeningRepository;
    @Mock private DonorDeferralRepository deferralRepository;
    @Mock private DonorCommunicationPreferenceRepository preferenceRepository;
    @Mock private DonorFeedbackRepository feedbackRepository;
    @Mock private DonorPreScreeningRepository preScreeningRepository;
    @Mock private MadiEventEmitter eventEmitter;

    private DonorService donorService;
    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        donorService = new DonorService(donorProfileRepository, screeningRepository, deferralRepository,
                preferenceRepository, feedbackRepository, preScreeningRepository, eventEmitter);
    }

    @Test
    void submitPreScreening_proceedWhenAllClear() {
        UUID donorId = UUID.randomUUID();
        stubDonor(donorId);
        when(preScreeningRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DonorPreScreeningEntity result = donorService.submitPreScreening(
                TENANT_ID, donorId, Map.of("recent_illness", false), null);

        assertThat(result.getOutcome()).isEqualTo(PreScreeningOutcome.PROCEED_TO_DRIVE.name());
        assertThat(result.getSafeMessage()).contains("proceed");
    }

    @Test
    void submitPreScreening_contactStaffForPregnancy() {
        UUID donorId = UUID.randomUUID();
        stubDonor(donorId);
        when(preScreeningRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DonorPreScreeningEntity result = donorService.submitPreScreening(
                TENANT_ID, donorId, Map.of("pregnant_or_recent_birth", "yes"), null);

        assertThat(result.getOutcome()).isEqualTo(PreScreeningOutcome.CONTACT_STAFF.name());
    }

    @Test
    void submitPreScreening_deferForRecentIllness() {
        UUID donorId = UUID.randomUUID();
        stubDonor(donorId);
        when(preScreeningRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DonorPreScreeningEntity result = donorService.submitPreScreening(
                TENANT_ID, donorId, Map.of("recent_illness", true), null);

        assertThat(result.getOutcome()).isEqualTo(PreScreeningOutcome.DEFER_SAFELY.name());
    }

    @Test
    void evaluatePreScreeningOutcome_isDeterministic() {
        assertThat(DonorService.evaluatePreScreeningOutcome(Map.of()))
                .isEqualTo(PreScreeningOutcome.PROCEED_TO_DRIVE);
        assertThat(DonorService.evaluatePreScreeningOutcome(Map.of("recent_tattoo", true)))
                .isEqualTo(PreScreeningOutcome.DEFER_SAFELY);
    }

    private void stubDonor(UUID donorId) {
        DonorProfileEntity donor = new DonorProfileEntity();
        donor.setDonorId(donorId);
        when(donorProfileRepository.findByDonorIdAndTenantId(donorId, TENANT_ID)).thenReturn(Optional.of(donor));
    }
}
