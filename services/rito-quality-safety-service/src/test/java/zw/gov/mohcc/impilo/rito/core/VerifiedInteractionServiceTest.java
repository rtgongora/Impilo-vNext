package zw.gov.mohcc.impilo.rito.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.rito.persistence.entity.VerifiedInteractionEntity;
import zw.gov.mohcc.impilo.rito.persistence.repository.VerifiedInteractionRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class VerifiedInteractionServiceTest {

    @Autowired private VerifiedInteractionService service;
    @Autowired private VerifiedInteractionRepository repository;

    @Test
    void recordsPctEncounterAndIsIdempotent() {
        UUID tenant = UUID.randomUUID();
        UUID facility = UUID.randomUUID();
        String encounterRef = UUID.randomUUID().toString();

        Map<String, Object> payload = new HashMap<>();
        payload.put("encounterRef", encounterRef);
        payload.put("attendingProviderId", "PROV12345678901234567890AB");
        payload.put("patientCpid", "CPID-001");
        payload.put("facilityId", facility.toString());
        payload.put("encounterType", "OUTPATIENT");
        payload.put("modality", "PHYSICAL");
        payload.put("endedAt", "2026-07-21T10:15:30Z");

        VerifiedInteractionEntity first = service.recordFromPct(tenant, payload);
        assertThat(first).isNotNull();
        assertThat(first.getAttendingProviderId()).isEqualTo("PROV12345678901234567890AB");
        assertThat(first.getFacilityId()).isEqualTo(facility);
        assertThat(first.getModality()).isEqualTo("PHYSICAL");
        assertThat(first.getSourceSystem()).isEqualTo("PCT");

        // Re-consuming the same ENCOUNTER_COMPLETED event is a no-op — one row per encounter.
        VerifiedInteractionEntity second = service.recordFromPct(tenant, payload);
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(repository.findByTenantIdAndEncounterRef(tenant, encounterRef)).isPresent();
        assertThat(repository.existsByTenantIdAndEncounterRef(tenant, encounterRef)).isTrue();
    }

    @Test
    void skipsPayloadWithoutEncounterRef() {
        assertThat(service.recordFromPct(UUID.randomUUID(), Map.of("patientCpid", "CPID-x"))).isNull();
    }
}
