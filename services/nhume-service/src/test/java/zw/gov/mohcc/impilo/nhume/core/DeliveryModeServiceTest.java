package zw.gov.mohcc.impilo.nhume.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.nhume.domain.AutonomousMissionEntity;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryPackageEntity;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryRequestEntity;
import zw.gov.mohcc.impilo.nhume.domain.ModeCorridorEntity;
import zw.gov.mohcc.impilo.nhume.repository.AutonomousMissionRepository;
import zw.gov.mohcc.impilo.nhume.repository.DeliveryPackageRepository;
import zw.gov.mohcc.impilo.nhume.repository.ModeCorridorRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * OF-B20 §12.9 — governed mode-matrix gate at dispatch (journey #67) and the
 * CONFIG-ONLY honesty law: the gate can plan and retain missions, but NO path
 * may ever record a flown mission.
 */
class DeliveryModeServiceTest {

    private ModeCorridorRepository corridorRepo;
    private DeliveryPackageRepository packageRepo;
    private final List<AutonomousMissionEntity> missions = new ArrayList<>();
    private DeliveryModeService service;

    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        corridorRepo = mock(ModeCorridorRepository.class);
        packageRepo = mock(DeliveryPackageRepository.class);
        AutonomousMissionRepository missionRepo = mock(AutonomousMissionRepository.class);
        when(missionRepo.save(any(AutonomousMissionEntity.class))).thenAnswer(inv -> {
            AutonomousMissionEntity m = inv.getArgument(0);
            missions.add(m);
            return m;
        });
        service = new DeliveryModeService(corridorRepo, missionRepo, packageRepo);
    }

    private DeliveryRequestEntity delivery() {
        DeliveryRequestEntity d = new DeliveryRequestEntity(UUID.randomUUID(), tenantId,
                "NHM-TEST", "TEST");
        // Harare CBD → Mt Pleasant, roughly 6 km.
        d.setOriginLat(-17.8292); d.setOriginLng(31.0522);
        d.setDestinationLat(-17.7690); d.setDestinationLng(31.0510);
        d.setOriginLabel("Vendor pharmacy");
        d.setDestinationLabel("Patient home");
        return d;
    }

    private ModeCorridorEntity corridor(String code, String weather) {
        ModeCorridorEntity c = new ModeCorridorEntity();
        c.setCorridorId(UUID.randomUUID());
        c.setTenantId(tenantId);
        c.setCorridorCode(code);
        c.setMode("DRONE");
        c.setEnabled(true);
        c.setMaxPayloadGrams(new BigDecimal("2000"));
        c.setMaxDistanceKm(new BigDecimal("25"));
        c.setWeatherStatus(weather);
        return c;
    }

    private void corridors(ModeCorridorEntity... cs) {
        when(corridorRepo.findByTenantIdAndModeAndEnabledTrueOrderByCorridorCodeAsc(tenantId, "DRONE"))
                .thenReturn(List.of(cs));
    }

    private void packages(BigDecimal... weights) {
        List<DeliveryPackageEntity> list = new ArrayList<>();
        for (BigDecimal w : weights) {
            DeliveryPackageEntity p = new DeliveryPackageEntity();
            p.setPackageId(UUID.randomUUID());
            p.setWeightGrams(w);
            list.add(p);
        }
        when(packageRepo.findByDeliveryId(any(UUID.class))).thenReturn(list);
    }

    // ─── Gate matrix ────────────────────────────────────────────────────────

    @Test
    void eligibleCorridor_clearWeather_plansDroneMission_notFlown() {
        corridors(corridor("HRE-C1", "CLEAR"));
        packages(new BigDecimal("500"));

        DeliveryModeService.ModeDecision decision = service.decideAtDispatch(delivery());

        assertThat(decision.mode()).isEqualTo("DRONE");
        assertThat(decision.corridorCode()).isEqualTo("HRE-C1");
        assertThat(decision.fallbackReason()).isNull();
        assertThat(decision.missionId()).isNotNull();
        assertThat(missions).hasSize(1);
        assertThat(missions.get(0).getStatus()).isEqualTo("NOT_FLOWN");
        assertThat(missions.get(0).isSimulationMode())
                .as("nothing on this estate ever flies").isTrue();
        assertThat(missions.get(0).getCorridorCode()).isEqualTo("HRE-C1");
    }

    @Test
    void weatherNotClear_governedFallbackToRoad_missionRetainedNotFlown() {
        corridors(corridor("HRE-C1", "UNKNOWN"));
        packages(new BigDecimal("500"));

        DeliveryModeService.ModeDecision decision = service.decideAtDispatch(delivery());

        assertThat(decision.mode()).isEqualTo("ROAD");
        assertThat(decision.fallbackReason()).isEqualTo("WEATHER_GATE_FAILED:UNKNOWN");
        assertThat(decision.droneConsidered()).isTrue();
        // Journey #67 — the planned mission is RETAINED with its reason, never flown.
        assertThat(decision.missionId()).isNotNull();
        assertThat(missions).hasSize(1);
        assertThat(missions.get(0).getStatus()).isEqualTo("NOT_FLOWN");
        assertThat(missions.get(0).getFallbackReason()).isEqualTo("WEATHER_GATE_FAILED:UNKNOWN");
    }

    @Test
    void weightExceeded_fallsBack_noMission() {
        corridors(corridor("HRE-C1", "CLEAR"));
        packages(new BigDecimal("1500"), new BigDecimal("900")); // 2.4 kg > 2 kg

        DeliveryModeService.ModeDecision decision = service.decideAtDispatch(delivery());

        assertThat(decision.mode()).isEqualTo("ROAD");
        assertThat(decision.fallbackReason()).startsWith("WEIGHT_EXCEEDED");
        assertThat(missions).isEmpty();
    }

    @Test
    void unknownWeight_failsClosed() {
        corridors(corridor("HRE-C1", "CLEAR"));
        when(packageRepo.findByDeliveryId(any(UUID.class))).thenReturn(List.of());

        DeliveryModeService.ModeDecision decision = service.decideAtDispatch(delivery());

        assertThat(decision.mode()).isEqualTo("ROAD");
        assertThat(decision.fallbackReason()).isEqualTo("WEIGHT_UNKNOWN");
    }

    @Test
    void distanceExceeded_fallsBack() {
        ModeCorridorEntity shortRange = corridor("HRE-C1", "CLEAR");
        shortRange.setMaxDistanceKm(new BigDecimal("3"));
        corridors(shortRange);
        packages(new BigDecimal("500"));

        DeliveryModeService.ModeDecision decision = service.decideAtDispatch(delivery());

        assertThat(decision.mode()).isEqualTo("ROAD");
        assertThat(decision.fallbackReason()).startsWith("DISTANCE_EXCEEDED");
    }

    @Test
    void controlledAndUncertifiedColdCargo_blocked() {
        corridors(corridor("HRE-C1", "CLEAR"));
        packages(new BigDecimal("500"));

        DeliveryRequestEntity controlled = delivery();
        controlled.setControlledItem(true);
        assertThat(service.decideAtDispatch(controlled).fallbackReason())
                .isEqualTo("CARGO_CLASS_BLOCKED:CONTROLLED");

        DeliveryRequestEntity cold = delivery();
        cold.setColdChainRequired(true);
        assertThat(service.decideAtDispatch(cold).fallbackReason())
                .isEqualTo("CARGO_CLASS_BLOCKED:COLD_CHAIN_UNCERTIFIED");

        // A cold-certified corridor lifts the cold restriction.
        ModeCorridorEntity certified = corridor("HRE-C2", "CLEAR");
        certified.setColdChainAllowed(true);
        corridors(certified);
        assertThat(service.decideAtDispatch(cold).mode()).isEqualTo("DRONE");
    }

    @Test
    void noEnabledCorridor_roadWithReason_droneNotConsidered() {
        when(corridorRepo.findByTenantIdAndModeAndEnabledTrueOrderByCorridorCodeAsc(tenantId, "DRONE"))
                .thenReturn(List.of());

        DeliveryModeService.ModeDecision decision = service.decideAtDispatch(delivery());

        assertThat(decision.mode()).isEqualTo("ROAD");
        assertThat(decision.fallbackReason()).isEqualTo("NO_ENABLED_DRONE_CORRIDOR");
        assertThat(decision.droneConsidered()).isFalse();
        assertThat(missions).isEmpty();
    }

    // ─── HONESTY LAW: no code path may record a flown mission ───────────────

    @Test
    void missionEntity_rejectsEveryFlightClaimStatus() {
        AutonomousMissionEntity m = new AutonomousMissionEntity();
        for (String claim : List.of("FLOWN", "flown", "IN_FLIGHT", "In_Flight",
                "AIRBORNE", "LANDED", "FLIGHT_COMPLETED", "FLIGHT_IN_PROGRESS")) {
            assertThatThrownBy(() -> m.setStatus(claim))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("MISSION_FLIGHT_CLAIM_FORBIDDEN");
        }
        // Honest planning statuses remain recordable.
        m.setStatus("NOT_FLOWN");
        m.setStatus("CANCELLED");
        assertThat(m.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void sourceTree_containsNoFlownStatusAssignment() throws Exception {
        // Belt-and-braces beside the runtime guard: no main-source code sets a
        // mission status to a flight claim.
        java.nio.file.Path root = java.nio.file.Path.of("src/main/java");
        try (var walk = java.nio.file.Files.walk(root)) {
            List<java.nio.file.Path> offenders = walk
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        try {
                            String src = java.nio.file.Files.readString(p);
                            return src.contains("setStatus(\"FLOWN\")")
                                    || src.contains("setStatus(\"IN_FLIGHT\")")
                                    || src.contains("setStatus(\"AIRBORNE\")");
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .toList();
            assertThat(offenders).isEmpty();
        }
    }
}
