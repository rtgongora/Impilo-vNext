package zw.gov.mohcc.impilo.inpatient;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.inpatient.core.TheatreCommoditiesService;
import zw.gov.mohcc.impilo.inpatient.integration.InventoryControlledDrugClient;
import zw.gov.mohcc.impilo.inpatient.integration.InventoryImplantClient;
import zw.gov.mohcc.impilo.inpatient.integration.TusoInstrumentSetClient;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedureControlledDrugEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedureEpisodeEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedureImplantEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.*;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Unit tests for Wave 3 theatre commodities: implant recall traceability + controlled-drug witness. */
class TheatreCommoditiesServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID EPISODE = UUID.fromString("00000000-0000-4000-8000-0000000000e1");

    private ProcedureEpisodeRepository episodeRepository;
    private ProcedureImplantRepository implantRepository;
    private ProcedureInstrumentSetUseRepository instrumentSetUseRepository;
    private ProcedureControlledDrugRepository controlledDrugRepository;
    private EventOutboxRepository outboxRepository;
    private InventoryImplantClient implantClient;
    private TusoInstrumentSetClient instrumentSetClient;
    private InventoryControlledDrugClient controlledDrugClient;
    private TheatreCommoditiesService service;

    @BeforeEach
    void setUp() {
        episodeRepository = mock(ProcedureEpisodeRepository.class);
        implantRepository = mock(ProcedureImplantRepository.class);
        instrumentSetUseRepository = mock(ProcedureInstrumentSetUseRepository.class);
        controlledDrugRepository = mock(ProcedureControlledDrugRepository.class);
        outboxRepository = mock(EventOutboxRepository.class);
        implantClient = mock(InventoryImplantClient.class);
        instrumentSetClient = mock(TusoInstrumentSetClient.class);
        controlledDrugClient = mock(InventoryControlledDrugClient.class);
        service = new TheatreCommoditiesService(episodeRepository, implantRepository, instrumentSetUseRepository,
                controlledDrugRepository, outboxRepository, implantClient, instrumentSetClient,
                controlledDrugClient, new ObjectMapper());

        ProcedureEpisodeEntity episode = new ProcedureEpisodeEntity();
        episode.setEpisodeId(EPISODE);
        episode.setSubjectCpid("CPID-ZW-CS-1");
        when(episodeRepository.findById(EPISODE)).thenReturn(Optional.of(episode));
        when(implantRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(controlledDrugRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        TrustContextHolder.set(new TrustContext(TENANT, "PROV-ZW-00020", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    // ── implant recall traceability ─────────────────────────────────────────────────────────────
    @Test
    void recordImplantThenRecallByLotFindsThePatientEpisode() {
        when(implantRepository.countByEpisodeId(EPISODE)).thenReturn(0);
        when(implantClient.recordImplant(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new InventoryImplantClient.ImplantView("UNIT-1", "PI-1", "UDI-XYZ",
                        "SER-9", "LOT-42", "IMPLANTED", true));

        Map<String, Object> out = service.recordImplant(EPISODE, Map.of(
                "udi", "UDI-XYZ", "serialNumber", "SER-9", "lotNumber", "LOT-42",
                "deviceType", "Hip prosthesis", "bodySite", "Hip", "laterality", "LEFT"));
        assertThat(out.get("status")).isEqualTo("IMPLANTED");
        assertThat(out.get("inventory_implant_unit_id")).isEqualTo("UNIT-1");

        // Simulate a recall: the persisted link is found by lot.
        ProcedureImplantEntity persisted = new ProcedureImplantEntity();
        persisted.setEpisodeId(EPISODE);
        persisted.setUdi("UDI-XYZ");
        persisted.setLotNumber("LOT-42");
        persisted.setBodySite("Hip");
        when(implantRepository.findByTenantIdAndLotNumber(TENANT, "LOT-42")).thenReturn(List.of(persisted));

        List<Map<String, Object>> recall = service.traceRecall(null, "LOT-42");
        assertThat(recall).hasSize(1);
        assertThat(recall.get(0).get("episode_id")).isEqualTo(EPISODE.toString());
        assertThat(recall.get(0).get("lot_number")).isEqualTo("LOT-42");
    }

    // ── controlled-drug two-person witness enforcement ──────────────────────────────────────────
    @Test
    void administerControlledDrugWithoutWitnessIs400() {
        assertThatThrownBy(() -> service.recordControlledDrug(EPISODE, Map.of(
                "itemCode", "MORPHINE-10", "action", "ADMINISTER", "quantity", 5)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400")
                .hasMessageContaining("witness");
        verify(controlledDrugRepository, never()).save(any());
        verifyNoInteractions(controlledDrugClient);
    }

    @Test
    void administerControlledDrugWithWitnessRecordsWithRunningBalance() {
        when(controlledDrugRepository.countByEpisodeId(EPISODE)).thenReturn(0);
        when(controlledDrugClient.record(any(), any(), eq("ADMINISTER"), any(), any(), any(), eq("PROV-ZW-00099"), any(), any(), any()))
                .thenReturn(new InventoryControlledDrugClient.RegisterView("ENTRY-1", "ADMINISTER",
                        new BigDecimal("5"), new BigDecimal("5"), "PROV-ZW-00099", true, false, null));

        Map<String, Object> out = service.recordControlledDrug(EPISODE, Map.of(
                "itemCode", "MORPHINE-10", "drugName", "Morphine 10mg", "action", "ADMINISTER",
                "quantity", 5, "unit", "mg", "witnessProvider", "PROV-ZW-00099"));

        assertThat(out.get("status")).isEqualTo("RECORDED");
        assertThat(out.get("witness_provider")).isEqualTo("PROV-ZW-00099");
        assertThat(out.get("inventory_register_entry_id")).isEqualTo("ENTRY-1");
        assertThat(out.get("running_balance")).isEqualTo(new BigDecimal("5"));
        verify(controlledDrugRepository).save(any(ProcedureControlledDrugEntity.class));
    }
}
