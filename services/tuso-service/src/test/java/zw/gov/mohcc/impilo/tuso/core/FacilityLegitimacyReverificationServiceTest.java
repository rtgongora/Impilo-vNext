package zw.gov.mohcc.impilo.tuso.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityLegitimacySource;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityLegitimacyStatus;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilitySourceLegitimacyEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilitySourceLegitimacyRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FCV-W1 — a verdict is a statement about a particular facility. Move it, or change what it is,
 * and the verdict no longer describes what is in front of you.
 */
@ExtendWith(MockitoExtension.class)
class FacilityLegitimacyReverificationServiceTest {

    @Mock private FacilitySourceLegitimacyRepository legitimacyRepository;

    private final UUID facilityUuid = UUID.randomUUID();

    private FacilityLegitimacyReverificationService service() {
        return new FacilityLegitimacyReverificationService(legitimacyRepository);
    }

    private FacilitySourceLegitimacyEntity decisionBackedAllow() {
        FacilitySourceLegitimacyEntity r = new FacilitySourceLegitimacyEntity();
        r.setFacilityId(facilityUuid);
        r.setSource(FacilityLegitimacySource.PLATFORM_OPERATIONAL);
        r.setStatus(FacilityLegitimacyStatus.REGISTERED_CURRENT);
        r.setAllowedOnPlatform(true);
        r.setDecisionId(UUID.randomUUID());
        return r;
    }

    @Test
    void movingAVerifiedFacilityWithdrawsItsOperatingPermission() {
        FacilitySourceLegitimacyEntity row = decisionBackedAllow();
        when(legitimacyRepository.findByFacilityIdAndSource(
                facilityUuid, FacilityLegitimacySource.PLATFORM_OPERATIONAL))
                .thenReturn(Optional.of(row));
        when(legitimacyRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThat(service().onFacilityChanged(facilityUuid, List.of("district"), "HID-EDITOR")).isTrue();

        assertThat(row.isAllowedOnPlatform())
                .as("a facility verified at one address must not stay operational after moving")
                .isFalse();
        assertThat(row.getStatus()).isEqualTo(FacilityLegitimacyStatus.PENDING_VERIFICATION);
        assertThat(row.getReason()).contains("district").contains("re-verification");
    }

    @Test
    void cosmeticEditsDoNotInvalidateAnInspection() {
        // Renaming a facility or fixing its description does not change what a verifier checked.
        assertThat(service().onFacilityChanged(facilityUuid, List.of("name", "description"), "HID-EDITOR"))
                .isFalse();
        verify(legitimacyRepository, never()).save(any());
    }

    @Test
    void aFacilityWithNoDecisionBackedAllowIsUnaffected() {
        // Nothing to demote: an unverified facility is already where the demotion would put it.
        FacilitySourceLegitimacyEntity importStamped = new FacilitySourceLegitimacyEntity();
        importStamped.setSource(FacilityLegitimacySource.PLATFORM_OPERATIONAL);
        importStamped.setAllowedOnPlatform(false);
        lenient().when(legitimacyRepository.findByFacilityIdAndSource(
                facilityUuid, FacilityLegitimacySource.PLATFORM_OPERATIONAL))
                .thenReturn(Optional.of(importStamped));

        assertThat(service().onFacilityChanged(facilityUuid, List.of("ownership"), "HID-EDITOR")).isFalse();
        verify(legitimacyRepository, never()).save(any());
    }

    @Test
    void theMaterialFieldSetIsTheOnesAVerifierPhysicallyChecks() {
        assertThat(FacilityLegitimacyReverificationService.isMaterial("province")).isTrue();
        assertThat(FacilityLegitimacyReverificationService.isMaterial("latitude")).isTrue();
        assertThat(FacilityLegitimacyReverificationService.isMaterial("ownership")).isTrue();
        assertThat(FacilityLegitimacyReverificationService.isMaterial("name")).isFalse();
        assertThat(FacilityLegitimacyReverificationService.isMaterial(null)).isFalse();
    }
}
