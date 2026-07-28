package zw.gov.mohcc.impilo.tuso.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityLegitimacySource;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityLegitimacyStatus;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilitySourceLegitimacyEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilitySourceLegitimacyRepository;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FCV-W1 — an expiry that nothing reads is worse than no expiry at all: the facility keeps its
 * platform allow while the record claims the permission lapsed.
 */
@ExtendWith(MockitoExtension.class)
class FacilityLegitimacyExpirySweepTest {

    @Mock private JdbcTemplate jdbc;
    @Mock private FacilitySourceLegitimacyRepository legitimacyRepository;

    private final UUID facilityUuid = UUID.randomUUID();
    private final UUID decisionId = UUID.randomUUID();

    private FacilityLegitimacyExpirySweep sweep() {
        return new FacilityLegitimacyExpirySweep(jdbc, legitimacyRepository);
    }

    private Map<String, Object> lapsedRow() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", decisionId);
        m.put("facility_uuid", facilityUuid);
        m.put("verdict", "LEGITIMATE_CONDITIONALLY_OPERATIONAL");
        m.put("expires_at", LocalDate.now().minusDays(1));
        return m;
    }

    private FacilitySourceLegitimacyEntity allowingPlatformRow() {
        FacilitySourceLegitimacyEntity r = new FacilitySourceLegitimacyEntity();
        r.setFacilityId(facilityUuid);
        r.setSource(FacilityLegitimacySource.PLATFORM_OPERATIONAL);
        r.setStatus(FacilityLegitimacyStatus.GOVERNMENT_OPERATIONAL_EXCEPTION);
        r.setAllowedOnPlatform(true);
        r.setDecisionId(decisionId);
        return r;
    }

    @Test
    void anExpiredDecisionHasItsPlatformAllowWithdrawn() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(lapsedRow()));
        FacilitySourceLegitimacyEntity platform = allowingPlatformRow();
        when(legitimacyRepository.findByFacilityIdAndSource(
                facilityUuid, FacilityLegitimacySource.PLATFORM_OPERATIONAL))
                .thenReturn(Optional.of(platform));
        when(legitimacyRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThat(sweep().expireLapsed(LocalDate.now())).isEqualTo(1);

        assertThat(platform.isAllowedOnPlatform())
                .as("the facility must stop being operational when its permission lapses")
                .isFalse();
        assertThat(platform.getStatus()).isEqualTo(FacilityLegitimacyStatus.EXPIRED);
        assertThat(platform.getReason()).contains("expired");
    }

    @Test
    void expiryWithdrawsPermissionButDoesNotJudgeTheFacilityIllegitimate() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(lapsedRow()));
        when(legitimacyRepository.findByFacilityIdAndSource(
                facilityUuid, FacilityLegitimacySource.PLATFORM_OPERATIONAL))
                .thenReturn(Optional.of(allowingPlatformRow()));
        when(legitimacyRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        sweep().expireLapsed(LocalDate.now());

        // Only the platform row is touched. The Ministry's recognition is a finding of fact and is
        // not withdrawn by the clock — what lapsed is a time-bounded permission to operate.
        verify(legitimacyRepository, never())
                .findByFacilityIdAndSource(facilityUuid, FacilityLegitimacySource.MINISTRY_OPERATIONAL);
    }

    @Test
    void nothingLapsedMeansNothingWritten() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        assertThat(sweep().expireLapsed(LocalDate.now())).isZero();
        verify(legitimacyRepository, never()).save(any());
    }
}
