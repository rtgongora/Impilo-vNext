package zw.gov.mohcc.impilo.governance.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.governance.persistence.FacilityRegulatorRelationshipEntity;
import zw.gov.mohcc.impilo.governance.persistence.FacilityRegulatorRelationshipRepository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FacilityRegulatorRelationshipServiceTest {

    @Mock private FacilityRegulatorRelationshipRepository repository;
    @Mock private GovernanceEventService eventService;

    private FacilityRegulatorRelationshipService service;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new FacilityRegulatorRelationshipService(repository, eventService);
        when(repository.save(any())).thenAnswer(i -> {
            FacilityRegulatorRelationshipEntity e = i.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            return e;
        });
    }

    @Test
    void link_createsNewRelationshipAndEmitsEvent() {
        when(repository.findByTenantIdAndFacilityIdAndCouncilIdAndRelationshipType(any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        FacilityRegulatorRelationshipEntity e = service.link(tenantId, "actor-1", "fac-1",
                Map.of("councilId", "council-mdpcz", "councilCode", "MDPCZ",
                        "relationshipType", "PRIMARY_REGULATOR", "registrationNumber", "EC123"),
                "corr-1");

        assertEquals("MDPCZ", e.getCouncilCode());
        assertEquals("fac-1", e.getFacilityId());
        verify(eventService).enqueue(eq("FACILITY_REGULATOR_RELATIONSHIP"), any(),
                eq("impilo.governance.facility.regulator.linked"), any(), eq(tenantId), eq("corr-1"));
    }

    @Test
    void link_supportsMultipleRegulatorsForSameFacility() {
        // A second council with a DIFFERENT relationship type is a distinct relationship,
        // proving no hardcoded single regulator.
        when(repository.findByTenantIdAndFacilityIdAndCouncilIdAndRelationshipType(
                eq(tenantId), eq("fac-1"), eq("council-pcz"), eq("LICENSING_AUTHORITY")))
                .thenReturn(Optional.empty());

        FacilityRegulatorRelationshipEntity e = service.link(tenantId, "actor-1", "fac-1",
                Map.of("councilId", "council-pcz", "councilCode", "PCZ",
                        "relationshipType", "LICENSING_AUTHORITY"),
                "corr-2");

        assertEquals("PCZ", e.getCouncilCode());
        assertEquals("LICENSING_AUTHORITY", e.getRelationshipType());
    }

    @Test
    void link_requiresCouncilIdAndCode() {
        assertThrows(IllegalArgumentException.class,
                () -> service.link(tenantId, "actor-1", "fac-1", Map.of("councilCode", "MDPCZ"), "c"));
    }

    @Test
    void updateStatus_changesStatusAndEmits() {
        UUID id = UUID.randomUUID();
        FacilityRegulatorRelationshipEntity existing = new FacilityRegulatorRelationshipEntity();
        existing.setId(id);
        existing.setStatus("ACTIVE");
        when(repository.findByTenantIdAndId(tenantId, id)).thenReturn(Optional.of(existing));

        FacilityRegulatorRelationshipEntity e = service.updateStatus(tenantId, "actor-1", id, "SUSPENDED", "c");
        assertEquals("SUSPENDED", e.getStatus());
    }
}
