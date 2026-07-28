package zw.gov.mohcc.impilo.inventory.core;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import zw.gov.mohcc.impilo.inventory.persistence.entity.EmergencyKitEntity;
import zw.gov.mohcc.impilo.inventory.persistence.repository.EmergencyKitCheckRepository;
import zw.gov.mohcc.impilo.inventory.persistence.repository.EmergencyKitRepository;

/**
 * Emergency kit / resus trolley registry (V200, W10). Proves the discrepancy-required rule at the
 * application layer (mirrors chk_iekc_discrepancies_required) and that "never checked" is reported
 * distinctly from "checked and complete".
 */
@ExtendWith(MockitoExtension.class)
class EmergencyKitServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID FACILITY = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID KIT = UUID.fromString("00000000-0000-4000-8000-000000000003");

    @Mock EmergencyKitRepository kitRepository;
    @Mock EmergencyKitCheckRepository checkRepository;
    private EmergencyKitService service;

    @BeforeEach
    void setUp() {
        service = new EmergencyKitService(kitRepository, checkRepository, new ObjectMapper());
    }

    private EmergencyKitEntity seededKit() {
        EmergencyKitEntity kit = new EmergencyKitEntity();
        kit.setKitId(KIT);
        kit.setTenantId(TENANT);
        kit.setFacilityId(FACILITY);
        kit.setName("Resus Trolley — ED Bay 1");
        return kit;
    }

    @Test
    @DisplayName("registering a kit with no facilityId is refused")
    void missingFacilityIdRefused() {
        assertThatThrownBy(() -> service.registerKit(TENANT, Map.of("name", "Trolley")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("a correct registration succeeds")
    void registerSucceeds() {
        when(kitRepository.save(any())).thenAnswer(inv -> {
            EmergencyKitEntity k = inv.getArgument(0);
            k.setKitId(KIT);
            return k;
        });
        var result = service.registerKit(TENANT, Map.of("facilityId", FACILITY.toString(), "name", "Trolley"));
        assertThat(result.get("kit_id")).isEqualTo(KIT.toString());
        assertThat(result.get("latest_check_complete")).isNull(); // never checked
    }

    @Test
    @DisplayName("recording complete=false with no discrepancies is refused")
    void discrepanciesRequiredWhenIncomplete() {
        when(kitRepository.findByTenantIdAndKitId(TENANT, KIT)).thenReturn(Optional.of(seededKit()));
        assertThatThrownBy(() -> service.recordCheck(TENANT, KIT, Map.of("checkedBy", "nurse-A", "complete", false)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not auditable");
    }

    @Test
    @DisplayName("recording complete=false WITH discrepancies succeeds")
    void discrepanciesSupplied() {
        when(kitRepository.findByTenantIdAndKitId(TENANT, KIT)).thenReturn(Optional.of(seededKit()));
        when(checkRepository.save(any())).thenAnswer(inv -> {
            var c = (zw.gov.mohcc.impilo.inventory.persistence.entity.EmergencyKitCheckEntity) inv.getArgument(0);
            if (c.getCheckId() == null) c.setCheckId(UUID.randomUUID());
            return c;
        });
        var result = service.recordCheck(TENANT, KIT, Map.of(
                "checkedBy", "nurse-A", "complete", false,
                "discrepancies", List.of(Map.of("itemCode", "ADRENALINE-1MG", "expectedQty", 5, "foundQty", 3))));
        assertThat(result.get("complete")).isEqualTo(false);
        assertThat(result.get("discrepancies")).isNotNull();
    }

    @Test
    @DisplayName("recording complete=true needs no discrepancies")
    void completeCheckSucceedsWithoutDiscrepancies() {
        when(kitRepository.findByTenantIdAndKitId(TENANT, KIT)).thenReturn(Optional.of(seededKit()));
        when(checkRepository.save(any())).thenAnswer(inv -> {
            var c = (zw.gov.mohcc.impilo.inventory.persistence.entity.EmergencyKitCheckEntity) inv.getArgument(0);
            if (c.getCheckId() == null) c.setCheckId(UUID.randomUUID());
            return c;
        });
        var result = service.recordCheck(TENANT, KIT, Map.of("checkedBy", "nurse-A", "complete", true));
        assertThat(result.get("complete")).isEqualTo(true);
    }

    @Test
    @DisplayName("checking an unknown kit is refused")
    void unknownKitRefused() {
        when(kitRepository.findByTenantIdAndKitId(TENANT, KIT)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.recordCheck(TENANT, KIT, Map.of("checkedBy", "nurse-A", "complete", true)))
                .isInstanceOf(ResponseStatusException.class);
    }
}
