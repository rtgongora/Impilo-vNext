package zw.gov.mohcc.impilo.inventory.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.inventory.api.dto.ImplantTraceDto;
import zw.gov.mohcc.impilo.inventory.api.dto.RecordImplantRequest;
import zw.gov.mohcc.impilo.inventory.api.dto.RecordImplantResponse;
import zw.gov.mohcc.impilo.inventory.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ImplantUnitEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.PatientImplantEntity;
import zw.gov.mohcc.impilo.inventory.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.inventory.persistence.repository.ImplantUnitRepository;
import zw.gov.mohcc.impilo.inventory.persistence.repository.PatientImplantRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImplantTraceabilityServiceTest {

    @Mock private ImplantUnitRepository unitRepository;
    @Mock private PatientImplantRepository patientImplantRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private ObjectMapper objectMapper;
    @InjectMocks private ImplantTraceabilityService service;

    private static final UUID TENANT = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TrustContextHolder.set(new TrustContext(
                TENANT, "actor-1", "STAFF", "TREATMENT", null,
                UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    @Test
    @DisplayName("recordImplant creates a new unit and a patient link")
    void recordImplant_createsUnitAndLink() {
        when(unitRepository.findByTenantIdAndUdi(eq(TENANT), eq("UDI-001"))).thenReturn(Optional.empty());
        when(unitRepository.save(any(ImplantUnitEntity.class))).thenAnswer(inv -> {
            ImplantUnitEntity u = inv.getArgument(0);
            if (u.getImplantUnitId() == null) {
                u.setImplantUnitId(UUID.randomUUID());
            }
            return u;
        });
        when(patientImplantRepository.save(any(PatientImplantEntity.class))).thenAnswer(inv -> {
            PatientImplantEntity l = inv.getArgument(0);
            if (l.getPatientImplantId() == null) {
                l.setPatientImplantId(UUID.randomUUID());
            }
            return l;
        });

        RecordImplantRequest req = new RecordImplantRequest(
                "UDI-001", "SER-9", "LOT-A", null, "HIP-STEM", "hip prosthesis",
                "Acme", "X1", null, "CPID-777", "EP-1", "hip", "LEFT",
                "provider-42", OffsetDateTime.now());

        RecordImplantResponse resp = service.recordImplant(req);

        assertThat(resp.implantUnitId()).isNotNull();
        assertThat(resp.patientImplantId()).isNotNull();
        assertThat(resp.udi()).isEqualTo("UDI-001");
        assertThat(resp.serialNumber()).isEqualTo("SER-9");
        assertThat(resp.lotNumber()).isEqualTo("LOT-A");
        assertThat(resp.status()).isEqualTo("IMPLANTED");
    }

    @Test
    @DisplayName("traceByRecall(lot) returns the implanted patient/episode — the recall query")
    void traceByRecall_byLot_returnsPatient() {
        UUID unitId = UUID.randomUUID();
        ImplantUnitEntity unit = new ImplantUnitEntity();
        unit.setImplantUnitId(unitId);
        unit.setTenantId(TENANT);
        unit.setUdi("UDI-001");
        unit.setSerialNumber("SER-9");
        unit.setLotNumber("LOT-RECALL");

        PatientImplantEntity link = new PatientImplantEntity();
        link.setPatientImplantId(UUID.randomUUID());
        link.setTenantId(TENANT);
        link.setImplantUnitId(unitId);
        link.setPatientCpid("CPID-777");
        link.setEpisodeRef("EP-1");
        link.setBodySite("hip");
        link.setLaterality("LEFT");
        link.setImplantedAt(OffsetDateTime.now());

        when(unitRepository.findByTenantIdAndLotNumber(eq(TENANT), eq("LOT-RECALL")))
                .thenReturn(List.of(unit));
        when(patientImplantRepository.findByTenantIdAndImplantUnitIdInOrderByImplantedAtDesc(
                eq(TENANT), anyCollection())).thenAnswer(inv -> {
            Collection<UUID> ids = inv.getArgument(1);
            return ids.contains(unitId) ? List.of(link) : List.of();
        });

        List<ImplantTraceDto> traces = service.traceByRecall(null, "LOT-RECALL");

        assertThat(traces).hasSize(1);
        ImplantTraceDto t = traces.get(0);
        assertThat(t.patientCpid()).isEqualTo("CPID-777");
        assertThat(t.episodeRef()).isEqualTo("EP-1");
        assertThat(t.lotNumber()).isEqualTo("LOT-RECALL");
        assertThat(t.udi()).isEqualTo("UDI-001");
        assertThat(t.serialNumber()).isEqualTo("SER-9");
        assertThat(t.bodySite()).isEqualTo("hip");
    }
}
