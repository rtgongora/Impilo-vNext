package zw.gov.mohcc.impilo.vashandi.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.vashandi.api.VashandiDtos;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.AccessRiskEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceProfileEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.AccessRiskRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceAssignmentRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceProfileRepository;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkforceAccessReviewServiceTest {

    @Mock
    private AccessRiskRepository accessRiskRepository;
    @Mock
    private WorkforceProfileRepository profileRepository;
    @Mock
    private WorkforceAssignmentRepository assignmentRepository;
    @Mock
    private VashandiOutboxWriter outboxWriter;

    private WorkforceAccessReviewService service;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new WorkforceAccessReviewService(
                accessRiskRepository, profileRepository, assignmentRepository, outboxWriter);
    }

    @Test
    void scanDetectsActiveAccessWithoutAssignment() throws Exception {
        WorkforceProfileEntity profile = new WorkforceProfileEntity();
        profile.setId(UUID.randomUUID());
        profile.setTenantId(tenantId);
        profile.setHealthId("H-001");
        profile.setCurrentStatus("active");
        profile.setAccessRiskStatus("none");
        when(profileRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(profile));
        when(assignmentRepository.findByTenantIdAndWorkforceProfileIdAndStatusIn(
                tenantId, profile.getId(), List.of("active", "approved"))).thenReturn(List.of());
        when(accessRiskRepository.save(any())).thenAnswer(inv -> {
            AccessRiskEntity entity = inv.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            return entity;
        });

        VashandiDtos.AccessRiskScanResponse response = service.scan(tenantId);
        assertTrue(response.detectedCount() >= 1);
        assertTrue(response.risks().stream()
                .anyMatch(r -> "active_access_without_assignment".equals(r.getRiskType())));
    }
}
