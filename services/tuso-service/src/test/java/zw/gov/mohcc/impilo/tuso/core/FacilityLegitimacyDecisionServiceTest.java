package zw.gov.mohcc.impilo.tuso.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FCV-W1 — the Ministry legitimacy decision.
 *
 * <p>The property these cases exist to hold: <b>a facility cannot confer legitimacy on itself</b>,
 * and only an affirmative Ministry verdict lifts the platform veto that both importers stamp.</p>
 */
@ExtendWith(MockitoExtension.class)
class FacilityLegitimacyDecisionServiceTest {

    @Mock private JdbcTemplate jdbc;
    @Mock private FacilityRepository facilityRepository;
    @Mock private FacilitySourceLegitimacyRepository legitimacyRepository;
    @Mock private MinistryAuthorityService ministryAuthority;

    private FacilityLegitimacyDecisionService service;

    private final UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID facilityUuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private FacilityEntity facility;

    @BeforeEach
    void setUp() {
        service = new FacilityLegitimacyDecisionService(jdbc, facilityRepository, legitimacyRepository,
                ministryAuthority, new ObjectMapper());
        TrustContextHolder.set(new TrustContext(
                tenantId, "HID-VERIFIER", "PROVIDER", "SYSTEM_ADMINISTRATION",
                null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
        facility = new FacilityEntity();
        facility.setId(42L);
        facility.setFacilityUuid(facilityUuid);
        facility.setTenantId(tenantId);
        facility.setProvince("Harare");
        facility.setDistrict("Harare");
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private void stubFacility() {
        when(facilityRepository.findByFacilityUuid(facilityUuid)).thenReturn(Optional.of(facility));
    }

    private void stubAuthority() {
        when(ministryAuthority.authorityOver(anyString(), any(), any()))
                .thenReturn(Optional.of(new MinistryAuthorityService.Authority(
                        "MOHCC_NATIONAL_VERIFIER", "NATIONAL")));
    }

    private void stubProjectionReadback(boolean platformAllows) {
        FacilitySourceLegitimacyEntity platform = new FacilitySourceLegitimacyEntity();
        platform.setSource(FacilityLegitimacySource.PLATFORM_OPERATIONAL);
        platform.setStatus(zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityLegitimacyStatus.REGISTERED_CURRENT);
        platform.setAllowedOnPlatform(platformAllows);
        FacilitySourceLegitimacyEntity ministry = new FacilitySourceLegitimacyEntity();
        ministry.setSource(FacilityLegitimacySource.MINISTRY_OPERATIONAL);
        ministry.setStatus(zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityLegitimacyStatus.REGISTERED_CURRENT);
        ministry.setAllowedOnPlatform(true);
        lenient().when(legitimacyRepository.findByFacilityIdOrderBySourceAsc(facilityUuid))
                .thenReturn(List.of(ministry, platform));
        lenient().when(legitimacyRepository.findByFacilityIdAndSource(any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(legitimacyRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private FacilityLegitimacyDecisionService.IssueDecisionRequest req(String verdict, String conditions) {
        return new FacilityLegitimacyDecisionService.IssueDecisionRequest(
                verdict, null, null, null, null, List.of("doc-1"), null, null, null,
                "Inspected and found operating", conditions, null, null, null);
    }

    // ── the doctrine ────────────────────────────────────────────────────────────────────────

    @Test
    void withoutAMinistryVerifierAppointment_theVerdictIsRefused() {
        stubFacility();
        when(ministryAuthority.authorityOver(anyString(), any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.issue(facilityUuid, req("LEGITIMATE_OPERATIONAL", null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(((ResponseStatusException) t).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        // Nothing was written: no decision row, no projection. A facility cannot legitimise itself.
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verify(legitimacyRepository, never()).save(any());
    }

    @Test
    void aReasonIsMandatory() {
        stubFacility();
        var noReason = new FacilityLegitimacyDecisionService.IssueDecisionRequest(
                "LEGITIMATE_OPERATIONAL", null, null, null, null, null, null, null, null,
                "  ", null, null, null, null);

        assertThatThrownBy(() -> service.issue(facilityUuid, noReason))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(((ResponseStatusException) t).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ── the projection ──────────────────────────────────────────────────────────────────────

    @Test
    void anOperationalVerdictLiftsThePlatformVeto_andBindsTheRowToTheDecision() {
        stubFacility();
        stubAuthority();
        stubProjectionReadback(true);

        var outcome = service.issue(facilityUuid, req("LEGITIMATE_OPERATIONAL", null));

        ArgumentCaptor<FacilitySourceLegitimacyEntity> rows =
                ArgumentCaptor.forClass(FacilitySourceLegitimacyEntity.class);
        verify(legitimacyRepository, org.mockito.Mockito.times(2)).save(rows.capture());

        var platform = rows.getAllValues().stream()
                .filter(r -> r.getSource() == FacilityLegitimacySource.PLATFORM_OPERATIONAL).findFirst().orElseThrow();
        assertThat(platform.isAllowedOnPlatform()).isTrue();
        assertThat(platform.getDecisionId())
                .as("bound to the decision so no import can silently overwrite it")
                .isEqualTo(outcome.decisionId());
        assertThat(outcome.platformAccessAllowed()).isTrue();
    }

    @Test
    void recognisedButNotOperating_leavesTheFacilityClosed() {
        stubFacility();
        stubAuthority();
        stubProjectionReadback(false);

        var outcome = service.issue(facilityUuid, req("LEGITIMATE_NOT_OPERATIONAL", null));

        var rows = ArgumentCaptor.forClass(FacilitySourceLegitimacyEntity.class);
        verify(legitimacyRepository, org.mockito.Mockito.times(2)).save(rows.capture());

        var ministry = rows.getAllValues().stream()
                .filter(r -> r.getSource() == FacilityLegitimacySource.MINISTRY_OPERATIONAL).findFirst().orElseThrow();
        var platform = rows.getAllValues().stream()
                .filter(r -> r.getSource() == FacilityLegitimacySource.PLATFORM_OPERATIONAL).findFirst().orElseThrow();

        assertThat(ministry.isAllowedOnPlatform()).as("the Ministry recognises it").isTrue();
        assertThat(platform.isAllowedOnPlatform()).as("but it is not operating").isFalse();
        assertThat(outcome.platformAccessAllowed()).isFalse();
    }

    @Test
    void suspensionDeniesOnBothSources_soNoOtherAllowCanUndercutIt() {
        stubFacility();
        stubAuthority();
        stubProjectionReadback(false);

        service.issue(facilityUuid, req("SUSPENDED", null));

        var rows = ArgumentCaptor.forClass(FacilitySourceLegitimacyEntity.class);
        verify(legitimacyRepository, org.mockito.Mockito.times(2)).save(rows.capture());
        assertThat(rows.getAllValues()).allSatisfy(r ->
                assertThat(r.isAllowedOnPlatform())
                        .as("a suspension must not be undercut by a surviving regulator listing")
                        .isFalse());
    }

    @Test
    void theOutcomeReportsWhenAnotherAuthorityStillBlocks() {
        // Recording a decision and the facility opening are not the same thing: an expired HPA
        // licence still vetoes, and the verifier must be told rather than shown a bare success.
        stubFacility();
        stubAuthority();
        FacilitySourceLegitimacyEntity hpaDenies = new FacilitySourceLegitimacyEntity();
        hpaDenies.setSource(FacilityLegitimacySource.HPA_LEGAL);
        hpaDenies.setStatus(zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityLegitimacyStatus.EXPIRED);
        hpaDenies.setAllowedOnPlatform(false);
        when(legitimacyRepository.findByFacilityIdOrderBySourceAsc(facilityUuid)).thenReturn(List.of(hpaDenies));
        lenient().when(legitimacyRepository.findByFacilityIdAndSource(any(), any())).thenReturn(Optional.empty());
        lenient().when(legitimacyRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var outcome = service.issue(facilityUuid, req("LEGITIMATE_OPERATIONAL", null));

        assertThat(outcome.platformAccessAllowed()).isFalse();
        assertThat(outcome.reasons()).isNotEmpty();
        assertThat(outcome.reasons().get(0)).contains("HPA_LEGAL");
    }

    @Test
    void anUnknownVerdictIsRefused() {
        stubFacility();
        stubAuthority();
        assertThatThrownBy(() -> service.issue(facilityUuid, req("DEFINITELY_FINE", null)))
                .isInstanceOf(ResponseStatusException.class);
    }
}
