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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
                "provider-42", OffsetDateTime.now(),
                null, null, null, null);

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

    // ── Wave P8 (pipeline §14): removal / revision lifecycle ──

    private PatientImplantEntity implantedLink(UUID id) {
        PatientImplantEntity link = new PatientImplantEntity();
        link.setPatientImplantId(id);
        link.setTenantId(TENANT);
        link.setImplantUnitId(UUID.randomUUID());
        link.setPatientCpid("CPID-777");
        link.setStatus("IMPLANTED");
        return link;
    }

    @Test
    @DisplayName("removeImplant marks the link REMOVED with an actor, a timestamp and the reason")
    void removeImplant_marksLinkRemoved() {
        UUID linkId = UUID.randomUUID();
        when(patientImplantRepository.findById(linkId)).thenReturn(Optional.of(implantedLink(linkId)));
        when(patientImplantRepository.save(any(PatientImplantEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PatientImplantEntity result = service.removeImplant(linkId, "Patient elected explant");

        assertThat(result.getStatus()).isEqualTo("REMOVED");
        assertThat(result.getRemovedAt()).isNotNull();
        assertThat(result.getRemovedBy()).isEqualTo("actor-1");
        assertThat(result.getRemovalReason()).isEqualTo("Patient elected explant");
    }

    @Test
    @DisplayName("removeImplant refuses a link that is not currently IMPLANTED — no double removal")
    void removeImplant_refusesAnAlreadyRemovedLink() {
        UUID linkId = UUID.randomUUID();
        PatientImplantEntity alreadyRemoved = implantedLink(linkId);
        alreadyRemoved.setStatus("REMOVED");
        when(patientImplantRepository.findById(linkId)).thenReturn(Optional.of(alreadyRemoved));

        assertThatThrownBy(() -> service.removeImplant(linkId, "again?"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("removeImplant refuses a link from another tenant")
    void removeImplant_refusesCrossTenantLink() {
        UUID linkId = UUID.randomUUID();
        PatientImplantEntity foreign = implantedLink(linkId);
        foreign.setTenantId(UUID.randomUUID());
        when(patientImplantRepository.findById(linkId)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.removeImplant(linkId, "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * The standard's own point for a revision: the OLD row becomes REVISED (not merely REMOVED)
     * and the NEW row's revisionOfPatientImplantId resolves back to it in the SAME call — a
     * caller never sees a half-completed revision.
     */
    @Test
    @DisplayName("reviseImplant marks the old link REVISED and links the new one back to it")
    void reviseImplant_linksNewImplantBackToTheOldOne() {
        UUID oldLinkId = UUID.randomUUID();
        UUID newLinkId = UUID.randomUUID();
        PatientImplantEntity old = implantedLink(oldLinkId);
        when(patientImplantRepository.findById(oldLinkId)).thenReturn(Optional.of(old));

        when(unitRepository.findByTenantIdAndUdi(eq(TENANT), eq("UDI-002"))).thenReturn(Optional.empty());
        when(unitRepository.save(any(ImplantUnitEntity.class))).thenAnswer(inv -> {
            ImplantUnitEntity u = inv.getArgument(0);
            if (u.getImplantUnitId() == null) u.setImplantUnitId(UUID.randomUUID());
            return u;
        });
        when(patientImplantRepository.save(any(PatientImplantEntity.class))).thenAnswer(inv -> {
            PatientImplantEntity l = inv.getArgument(0);
            if (l.getPatientImplantId() == null) l.setPatientImplantId(newLinkId);
            return l;
        });
        // reviseImplant re-fetches the just-created new link to attach revisionOfPatientImplantId.
        PatientImplantEntity newLinkHolder = new PatientImplantEntity();
        newLinkHolder.setPatientImplantId(newLinkId);
        newLinkHolder.setTenantId(TENANT);
        newLinkHolder.setStatus("IMPLANTED");
        when(patientImplantRepository.findById(newLinkId)).thenReturn(Optional.of(newLinkHolder));

        RecordImplantRequest newImplant = new RecordImplantRequest(
                "UDI-002", "SER-10", "LOT-B", null, "HIP-STEM", "hip prosthesis",
                "Acme", "X2", null, "CPID-777", "EP-2", "hip", "LEFT",
                "provider-99", OffsetDateTime.now(), null, null, null, null);

        RecordImplantResponse response = service.reviseImplant(oldLinkId, newImplant, "Component wear");

        assertThat(response.patientImplantId()).isEqualTo(newLinkId);
        assertThat(old.getStatus()).isEqualTo("REVISED");
        assertThat(old.getRemovedAt()).isNotNull();
        assertThat(old.getRemovalReason()).isEqualTo("Component wear");
        assertThat(newLinkHolder.getRevisionOfPatientImplantId()).isEqualTo(oldLinkId);
    }

    @Test
    @DisplayName("ImplantTraceDto.from surfaces status and removal fields rather than hiding removed history")
    void implantTraceDto_surfacesRemovalHistory() {
        PatientImplantEntity link = implantedLink(UUID.randomUUID());
        link.setStatus("REMOVED");
        link.setRemovedAt(OffsetDateTime.now());
        link.setRemovedBy("actor-1");
        link.setRemovalReason("Infection");
        link.setImplantUnitId(UUID.randomUUID());

        ImplantTraceDto dto = ImplantTraceDto.from(link, null);

        assertThat(dto.status()).isEqualTo("REMOVED");
        assertThat(dto.removedBy()).isEqualTo("actor-1");
        assertThat(dto.removalReason()).isEqualTo("Infection");
    }
}
