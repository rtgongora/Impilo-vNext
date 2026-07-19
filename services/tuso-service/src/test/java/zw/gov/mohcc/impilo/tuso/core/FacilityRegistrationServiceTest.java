package zw.gov.mohcc.impilo.tuso.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityRegistrationCaseEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRegistrationCaseRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FJ2/D-L5: the registrant receipt is IDENTICAL on the silent-block and
 * provisional paths (no enumeration); a credible match creates NO facility; a
 * provisional facility is never ACTIVE/discoverable; evidence is mandatory.
 */
@ExtendWith(MockitoExtension.class)
class FacilityRegistrationServiceTest {

    @Mock private FacilityRepository facilityRepository;
    @Mock private FacilityRegistrationCaseRepository caseRepository;
    @Mock private FacilityMatchService matchService;
    @Mock private EventOutboxRepository outboxRepository;

    private FacilityRegistrationService service;

    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new FacilityRegistrationService(
                facilityRepository, caseRepository, matchService, outboxRepository, new ObjectMapper());
        TrustContextHolder.set(new TrustContext(
                tenantId, "citizen-1", "CITIZEN", "REGISTRATION",
                null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
        lenient().when(caseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(facilityRepository.save(any())).thenAnswer(inv -> {
            FacilityEntity f = inv.getArgument(0);
            if (f.getFacilityUuid() == null) {
                f.setFacilityUuid(UUID.randomUUID());
            }
            return f;
        });
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private FacilityRegistrationService.RegisterFacilityRequest request() {
        return new FacilityRegistrationService.RegisterFacilityRequest(
                "HID-9", "Bangure Clinic", "CLINIC", "Harare", "Harare",
                "PRIVATE", null, null, null, "doc://licence/1", null);
    }

    @Test
    void credibleMatchSilentlyBlocksWithIdenticalReceiptShape() {
        FacilityEntity existing = new FacilityEntity();
        existing.setFacilityUuid(UUID.randomUUID());
        when(matchService.matchForRegistration(any(), any(), any(), any(), any()))
                .thenReturn(new FacilityMatchService.MatchResult(true,
                        List.of(new FacilityMatchService.MatchCandidate(existing, "NAME_SAME_DISTRICT", 0.9))));

        FacilityRegistrationService.RegistrationReceipt blocked = service.register(request());

        // No facility is ever created on the blocked path.
        verify(facilityRepository, never()).save(any());
        ArgumentCaptor<FacilityRegistrationCaseEntity> captor =
                ArgumentCaptor.forClass(FacilityRegistrationCaseEntity.class);
        verify(caseRepository).save(captor.capture());
        assertThat(captor.getValue().getOutcome())
                .isEqualTo(FacilityRegistrationCaseEntity.OUTCOME_DUPLICATE_REVIEW);
        assertThat(captor.getValue().getMatchContext()).contains("NAME_SAME_DISTRICT");

        // The registrant-facing receipt discloses nothing about the match.
        assertThat(blocked.status()).isEqualTo("RECEIVED");
        assertThat(blocked.message()).doesNotContain("duplicate", "match", "exists");
    }

    @Test
    void noMatchCreatesProvisionalNeverDiscoverableFacility() {
        when(matchService.matchForRegistration(any(), any(), any(), any(), any()))
                .thenReturn(FacilityMatchService.MatchResult.none());

        FacilityRegistrationService.RegistrationReceipt receipt = service.register(request());

        ArgumentCaptor<FacilityEntity> facility = ArgumentCaptor.forClass(FacilityEntity.class);
        verify(facilityRepository).save(facility.capture());
        assertThat(facility.getValue().getStatus())
                .isEqualTo(FacilityRegistrationService.STATUS_PROVISIONAL);
        assertThat(facility.getValue().getRegulatoryStatus())
                .isEqualTo(zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityRegulatoryStatus.DRAFT);
        assertThat(receipt.status()).isEqualTo("RECEIVED");
    }

    @Test
    void bothPathsReturnTheSameShapeAndMessage() {
        when(matchService.matchForRegistration(any(), any(), any(), any(), any()))
                .thenReturn(FacilityMatchService.MatchResult.none());
        FacilityRegistrationService.RegistrationReceipt provisional = service.register(request());

        FacilityEntity existing = new FacilityEntity();
        existing.setFacilityUuid(UUID.randomUUID());
        when(matchService.matchForRegistration(any(), any(), any(), any(), any()))
                .thenReturn(new FacilityMatchService.MatchResult(true,
                        List.of(new FacilityMatchService.MatchCandidate(existing, "FACILITY_CODE", 1.0))));
        FacilityRegistrationService.RegistrationReceipt blocked = service.register(request());

        assertThat(blocked.status()).isEqualTo(provisional.status());
        assertThat(blocked.message()).isEqualTo(provisional.message());
    }

    @Test
    void evidenceIsMandatory() {
        assertThatThrownBy(() -> service.register(
                new FacilityRegistrationService.RegisterFacilityRequest(
                        "HID-9", "Bangure Clinic", "CLINIC", "Harare", "Harare",
                        "PRIVATE", null, null, null, null, null)))
                .isInstanceOf(ResponseStatusException.class);
        verify(caseRepository, never()).save(any());
    }
}
