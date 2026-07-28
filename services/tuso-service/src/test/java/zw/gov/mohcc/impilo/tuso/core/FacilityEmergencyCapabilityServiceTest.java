package zw.gov.mohcc.impilo.tuso.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEmergencyCapabilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityEmergencyCapabilityRepository;

/**
 * Hand-declared emergency capability per facility (V200, W10) — NOT a trauma-centre-level
 * classification. Proves the upsert semantics (a re-declaration updates the same row) and the
 * absence-vs-false distinction (an undeclared facility reports {@code declared: false}, never a
 * fabricated "no" for every capability).
 */
@ExtendWith(MockitoExtension.class)
class FacilityEmergencyCapabilityServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final Long FACILITY = 42L;

    @Mock FacilityEmergencyCapabilityRepository repository;
    private FacilityEmergencyCapabilityService service;

    @BeforeEach
    void setUp() {
        service = new FacilityEmergencyCapabilityService(repository);
    }

    @Test
    @DisplayName("an undeclared facility reports declared=false, never a fabricated capability set")
    void undeclaredFacilityReportsAbsence() {
        when(repository.findByTenantIdAndFacilityId(TENANT, FACILITY)).thenReturn(Optional.empty());
        var result = service.get(TENANT, FACILITY);
        assertThat(result.get("declared")).isEqualTo(false);
    }

    @Test
    @DisplayName("declaring a capability profile persists exactly the supplied fields")
    void declareSetsSuppliedFields() {
        when(repository.findByTenantIdAndFacilityId(TENANT, FACILITY)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.declare(TENANT, FACILITY,
                Map.of("has24hTheatre", "true", "hasBloodBank", "false"), "dr-x");

        assertThat(result.get("has24hTheatre")).isEqualTo(true);
        assertThat(result.get("hasBloodBank")).isEqualTo(false);
        assertThat(result.get("hasIcu")).isNull(); // never supplied — stays absent, not defaulted to false
        assertThat(result.get("declaredBy")).isEqualTo("dr-x");
    }

    @Test
    @DisplayName("re-declaring updates the SAME row (upsert), not a second one")
    void redeclareUpdatesSameRow() {
        FacilityEmergencyCapabilityEntity existing = new FacilityEmergencyCapabilityEntity();
        existing.setFacilityId(FACILITY);
        existing.setTenantId(TENANT);
        existing.setHas24hTheatre(false);
        when(repository.findByTenantIdAndFacilityId(TENANT, FACILITY)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.declare(TENANT, FACILITY, Map.of("has24hTheatre", "true"), "dr-y");

        assertThat(result.get("has24hTheatre")).isEqualTo(true);
        assertThat(result.get("declaredBy")).isEqualTo("dr-y");
    }
}
