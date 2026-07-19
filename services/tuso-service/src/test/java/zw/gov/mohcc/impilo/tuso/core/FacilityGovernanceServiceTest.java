package zw.gov.mohcc.impilo.tuso.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityGovernanceCaseEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityGovernanceCaseRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * FJ6/FJ7/FJ9: high-risk field classification is precise; low-risk-only
 * updates never open a case; a duplicate report resolves only to
 * merge/redirect/reject; transfer keeps the same facility identity.
 */
@ExtendWith(MockitoExtension.class)
class FacilityGovernanceServiceTest {

    @Mock private FacilityRepository facilityRepository;
    @Mock private FacilityGovernanceCaseRepository caseRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private FacilityGovernanceService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID facilityUuid = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new FacilityGovernanceService(
                facilityRepository, caseRepository, outboxRepository, new ObjectMapper());
        TrustContextHolder.set(new TrustContext(
                tenantId, "steward-1", "REGISTRY_ADMIN", "GOVERNANCE",
                null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
        FacilityEntity facility = new FacilityEntity();
        facility.setFacilityUuid(facilityUuid);
        facility.setTenantId(tenantId);
        lenient().when(facilityRepository.findByFacilityUuid(facilityUuid))
                .thenReturn(Optional.of(facility));
        lenient().when(caseRepository.save(any())).thenAnswer(inv -> {
            FacilityGovernanceCaseEntity c = inv.getArgument(0);
            if (c.getId() == null) {
                try {
                    var f = FacilityGovernanceCaseEntity.class.getDeclaredField("id");
                    f.setAccessible(true);
                    f.set(c, 7L);
                } catch (Exception ignored) {
                }
            }
            return c;
        });
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    @Test
    void fieldClassificationSeparatesHighRiskFromLowRisk() {
        assertThat(FacilityGovernanceService.highRiskFieldsIn(
                Map.of("name", "New Name", "phone", "0772", "openingHours", "8-5")))
                .containsExactly("name");
        assertThat(FacilityGovernanceService.highRiskFieldsIn(
                Map.of("phone", "0772", "email", "a@b.c", "directions", "left")))
                .isEmpty();
    }

    @Test
    void lowRiskOnlyUpdateNeverOpensACase() {
        assertThatThrownBy(() -> service.openHighRiskUpdate(facilityUuid,
                Map.of("phone", "0772", "openingHours", "8-5")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("self-service");
    }

    @Test
    void highRiskUpdateOpensCaseWithFlaggedFields() {
        FacilityGovernanceCaseEntity opened = service.openHighRiskUpdate(facilityUuid,
                Map.of("ownership", "PRIVATE", "phone", "0772"));

        assertThat(opened.getCaseType()).isEqualTo(FacilityGovernanceCaseEntity.TYPE_HIGH_RISK_UPDATE);
        assertThat(opened.getStatus()).isEqualTo(FacilityGovernanceCaseEntity.STATUS_OPEN);
        assertThat(opened.getPayload()).contains("ownership");
    }

    @Test
    void duplicateReportResolvesOnlyToMergeRedirectOrReject() {
        FacilityGovernanceCaseEntity report = new FacilityGovernanceCaseEntity();
        report.setTenantId(tenantId);
        report.setCaseType(FacilityGovernanceCaseEntity.TYPE_DUPLICATE_REPORT);
        report.setFacilityUuid(facilityUuid);
        report.setStatus(FacilityGovernanceCaseEntity.STATUS_OPEN);
        when(caseRepository.findByIdAndTenantId(3L, tenantId)).thenReturn(Optional.of(report));

        // APPROVED is not a valid resolution for a duplicate report.
        assertThatThrownBy(() -> service.decide(3L, "APPROVED", null))
                .isInstanceOf(ResponseStatusException.class);

        FacilityGovernanceCaseEntity merged = service.decide(3L, "merged", "same site");
        assertThat(merged.getStatus()).isEqualTo(FacilityGovernanceCaseEntity.STATUS_MERGED);
    }

    @Test
    void transferKeepsTheSameFacilityIdentity() {
        FacilityGovernanceCaseEntity opened = service.openTransfer(facilityUuid,
                Map.of("incomingPartyHealthId", "HID-NEW"));
        assertThat(opened.getFacilityUuid()).isEqualTo(facilityUuid);
        assertThat(opened.getCaseType()).isEqualTo(FacilityGovernanceCaseEntity.TYPE_TRANSFER);
    }
}
