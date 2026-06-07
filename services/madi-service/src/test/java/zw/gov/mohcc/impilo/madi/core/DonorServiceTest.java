package zw.gov.mohcc.impilo.madi.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.madi.domain.DonorStatus;
import zw.gov.mohcc.impilo.madi.domain.ScreeningResult;
import zw.gov.mohcc.impilo.madi.events.MadiEventEmitter;
import zw.gov.mohcc.impilo.madi.persistence.entity.DonorProfileEntity;
import zw.gov.mohcc.impilo.madi.persistence.repository.*;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DonorServiceTest {

    @Mock private DonorProfileRepository donorProfileRepository;
    @Mock private DonorEligibilityScreeningRepository screeningRepository;
    @Mock private DonorDeferralRepository deferralRepository;
    @Mock private DonorCommunicationPreferenceRepository preferenceRepository;
    @Mock private DonorFeedbackRepository feedbackRepository;
    @Mock private MadiEventEmitter eventEmitter;

    private DonorService donorService;
    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        donorService = new DonorService(donorProfileRepository, screeningRepository, deferralRepository,
                preferenceRepository, feedbackRepository, eventEmitter);
    }

    @Test
    void register_createsDonorProfile() {
        when(donorProfileRepository.findByPersonCpidAndTenantId("CPID-1", TENANT_ID)).thenReturn(Optional.empty());
        when(donorProfileRepository.save(any())).thenAnswer(inv -> {
            DonorProfileEntity d = inv.getArgument(0);
            d.setDonorId(UUID.randomUUID());
            return d;
        });

        DonorProfileEntity result = donorService.register(TENANT_ID, "CPID-1", "O+", "POS", null, "staff-1");

        assertThat(result.getPersonCpid()).isEqualTo("CPID-1");
        assertThat(result.getStatus()).isEqualTo(DonorStatus.REGISTERED.name());
        verify(eventEmitter).emit(eq("DONOR"), anyString(), eq("DONOR_REGISTERED"), anyString(), anyString(), anyMap(), eq(TENANT_ID));
    }

    @Test
    void register_rejectsDuplicate() {
        when(donorProfileRepository.findByPersonCpidAndTenantId("CPID-1", TENANT_ID))
                .thenReturn(Optional.of(new DonorProfileEntity()));

        assertThatThrownBy(() -> donorService.register(TENANT_ID, "CPID-1", "O+", "POS", null, "staff-1"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void screen_updatesDonorStatusOnPass() {
        UUID donorId = UUID.randomUUID();
        DonorProfileEntity donor = new DonorProfileEntity();
        donor.setDonorId(donorId);
        donor.setStatus(DonorStatus.REGISTERED.name());
        when(donorProfileRepository.findByDonorIdAndTenantId(donorId, TENANT_ID)).thenReturn(Optional.of(donor));
        when(screeningRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(donorProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        donorService.screen(TENANT_ID, donorId, null, ScreeningResult.PASS, null, "nurse-1", null);

        assertThat(donor.getStatus()).isEqualTo(DonorStatus.ACTIVE.name());
    }
}
