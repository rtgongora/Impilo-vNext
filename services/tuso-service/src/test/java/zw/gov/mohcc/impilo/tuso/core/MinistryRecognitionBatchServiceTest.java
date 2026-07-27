package zw.gov.mohcc.impilo.tuso.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityLegitimacySource;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilitySourceLegitimacyEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilitySourceLegitimacyRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FCV-W3 — the Ministry reasserting recognition over its own master list.
 *
 * <p>The property that matters: <b>a bulk act must never become a bulk opening.</b> 1,774 facilities
 * are one allowing row away from going live, so this batch has to be provably incapable of granting
 * platform operation.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MinistryRecognitionBatchServiceTest {

    @Mock private JdbcTemplate jdbc;
    @Mock private FacilityRepository facilityRepository;
    @Mock private FacilitySourceLegitimacyRepository legitimacyRepository;
    @Mock private MinistryAuthorityService ministryAuthority;

    private MinistryRecognitionBatchService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID facilityUuid = UUID.randomUUID();
    private FacilityEntity facility;

    @BeforeEach
    void setUp() {
        service = new MinistryRecognitionBatchService(jdbc, facilityRepository, legitimacyRepository,
                ministryAuthority);
        TrustContextHolder.set(new TrustContext(tenantId, "HID-REGISTRY-ADMIN", "OPERATOR",
                "ADMINISTRATION", null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
        facility = new FacilityEntity();
        facility.setId(1L);
        facility.setFacilityUuid(facilityUuid);
        facility.setTenantId(tenantId);
        when(facilityRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(facility)));
        when(facilityRepository.findByFacilityUuid(facilityUuid)).thenReturn(Optional.of(facility));
        when(legitimacyRepository.findByFacilityIdAndSource(any(), any())).thenReturn(Optional.empty());
        when(legitimacyRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(jdbc.queryForObject(anyString(), eqClass(), any(Object[].class))).thenReturn(0);
    }

    @SuppressWarnings("unchecked")
    private static Class<Integer> eqClass() {
        return (Class<Integer>) org.mockito.ArgumentMatchers.<Class<Integer>>any();
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private void stubRegistryAdmin() {
        when(ministryAuthority.authorityOver(anyString(), any(), any()))
                .thenReturn(Optional.of(new MinistryAuthorityService.Authority(
                        "MOHCC_REGISTRY_ADMINISTRATOR", "NATIONAL")));
    }

    private void stubCandidates() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("facility_uuid", facilityUuid);
        row.put("facility_code", "ZW010125");
        when(jdbc.queryForList(anyString(), any(UUID.class), anyInt())).thenReturn(List.of(row));
    }

    // ── the property this wave exists to guarantee ──────────────────────────────────────────

    @Test
    void recognitionNeverGrantsPlatformOperation() {
        stubRegistryAdmin();
        stubCandidates();

        service.assertRecognition("MASTER_HEALTH_FACILITY_2024_07_23", "Ministry reassertion", 10, false);

        ArgumentCaptor<FacilitySourceLegitimacyEntity> rows =
                ArgumentCaptor.forClass(FacilitySourceLegitimacyEntity.class);
        verify(legitimacyRepository, org.mockito.Mockito.times(2)).save(rows.capture());

        var platform = rows.getAllValues().stream()
                .filter(r -> r.getSource() == FacilityLegitimacySource.PLATFORM_OPERATIONAL)
                .findFirst().orElseThrow();
        var ministry = rows.getAllValues().stream()
                .filter(r -> r.getSource() == FacilityLegitimacySource.MINISTRY_OPERATIONAL)
                .findFirst().orElseThrow();

        assertThat(ministry.isAllowedOnPlatform()).as("the Ministry recognises the facility").isTrue();
        assertThat(platform.isAllowedOnPlatform())
                .as("but a bulk act must never open a facility — that is a verifier's decision")
                .isFalse();
    }

    @Test
    void aDryRunWritesNoLegitimacy() {
        stubRegistryAdmin();
        stubCandidates();

        var result = service.assertRecognition("MASTER_PACK", "preview", 10, true);

        assertThat(result.dryRun()).isTrue();
        assertThat(result.recognised()).isEqualTo(1);
        verify(legitimacyRepository, never()).save(any());
    }

    @Test
    void aVerifierAppointmentIsNotEnoughToRunTheBatch() {
        // Separation of duties: whoever can bulk-assert recognition must not thereby be able to
        // open facilities. The batch demands the registry-administrator role, not a verifier one.
        when(ministryAuthority.authorityOver(anyString(), any(),
                org.mockito.ArgumentMatchers.eq(MinistryAuthorityService.REGISTRY_ADMIN_ROLES)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assertRecognition("MASTER_PACK", "reason", 10, false))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(((ResponseStatusException) t).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
        verify(legitimacyRepository, never()).save(any());
    }

    @Test
    void aReasonAndSourceLabelAreMandatory() {
        assertThatThrownBy(() -> service.assertRecognition("MASTER_PACK", "  ", 10, false))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.assertRecognition("  ", "reason", 10, false))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void aGovernedDecisionOwnsItsRowAndTheBatchLeavesItAlone() {
        stubRegistryAdmin();
        stubCandidates();
        FacilitySourceLegitimacyEntity decisionBound = new FacilitySourceLegitimacyEntity();
        decisionBound.setSource(FacilityLegitimacySource.PLATFORM_OPERATIONAL);
        decisionBound.setAllowedOnPlatform(true);
        decisionBound.setDecisionId(UUID.randomUUID());
        when(legitimacyRepository.findByFacilityIdAndSource(
                facilityUuid, FacilityLegitimacySource.PLATFORM_OPERATIONAL))
                .thenReturn(Optional.of(decisionBound));

        service.assertRecognition("MASTER_PACK", "reassertion", 10, false);

        assertThat(decisionBound.isAllowedOnPlatform())
                .as("a bulk assertion must never override a verifier's decision")
                .isTrue();
    }
}
