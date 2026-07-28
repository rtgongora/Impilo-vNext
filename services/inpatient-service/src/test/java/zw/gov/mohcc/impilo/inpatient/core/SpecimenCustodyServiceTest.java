package zw.gov.mohcc.impilo.inpatient.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.inpatient.integration.ButanoProcedureClient;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedureEpisodeEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedureSpecimenEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.ProcedureEpisodeRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.ProcedureSpecimenRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pipeline §13 — chain of custody, label-mismatch prevention, adequacy for a theatre specimen.
 *
 * <p>Per V301's own header, these tests prove the RECORDING logic only — none of this is called
 * by {@code TheatreService.processSpecimensFromNote}'s automatic parse-and-dispatch path, which
 * has no human confirmation step yet. That is an honest, declared PARTIAL, not something these
 * tests paper over.</p>
 */
@ExtendWith(MockitoExtension.class)
class SpecimenCustodyServiceTest {

    @Mock ProcedureSpecimenRepository specimenRepository;
    @Mock ProcedureEpisodeRepository episodeRepository;
    @Mock ButanoProcedureClient butanoClient;

    private SpecimenCustodyService service;
    private final UUID tenant = UUID.randomUUID();
    private final UUID otherTenant = UUID.randomUUID();
    private final UUID episodeId = UUID.randomUUID();
    private final UUID specimenId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SpecimenCustodyService(specimenRepository, episodeRepository, butanoClient);
        TrustContextHolder.set(new TrustContext(tenant, "actor-nurse", "STAFF", "TREATMENT",
                null, UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL));
        lenient().when(specimenRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(episodeRepository.findById(episodeId)).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private ProcedureSpecimenEntity specimen() {
        ProcedureSpecimenEntity s = new ProcedureSpecimenEntity();
        s.setSpecimenId(specimenId);
        s.setTenantId(tenant);
        s.setEpisodeId(episodeId);
        s.setSpecimenLabel("Appendix");
        s.setStatus("COLLECTED");
        return s;
    }

    @Test
    void recordCollectionStampsActorTimestampAndContainerDetails() {
        when(specimenRepository.findById(specimenId)).thenReturn(Optional.of(specimen()));

        ProcedureSpecimenEntity result = service.recordCollection(episodeId, specimenId, "formalin jar", "10% formalin");

        assertEquals("actor-nurse", result.getCollectedBy());
        assertNotNull(result.getCollectedAt());
        assertEquals("formalin jar", result.getContainerType());
        assertEquals("10% formalin", result.getFixative());
    }

    /** The finding this wave exists for — a named person attests the label matches the patient. */
    @Test
    void confirmLabelStampsTheAttestingActor() {
        when(specimenRepository.findById(specimenId)).thenReturn(Optional.of(specimen()));

        ProcedureSpecimenEntity result = service.confirmLabel(episodeId, specimenId);

        assertEquals("actor-nurse", result.getLabelConfirmedBy());
        assertNotNull(result.getLabelConfirmedAt());
    }

    @Test
    void recordReceiptStampsTheReceivingActorSeparatelyFromCollection() {
        ProcedureSpecimenEntity s = specimen();
        s.setCollectedBy("actor-nurse");
        when(specimenRepository.findById(specimenId)).thenReturn(Optional.of(s));

        ProcedureSpecimenEntity result = service.recordReceipt(episodeId, specimenId);

        assertEquals("actor-nurse", result.getReceivedBy());
        assertNotNull(result.getReceivedAt());
    }

    @Test
    void assessAdequacyAcceptsAdequate() {
        when(specimenRepository.findById(specimenId)).thenReturn(Optional.of(specimen()));

        ProcedureSpecimenEntity result = service.assessAdequacy(episodeId, specimenId, "ADEQUATE");

        assertEquals("ADEQUATE", result.getAdequacy());
        assertEquals("actor-nurse", result.getAdequacyAssessedBy());
        assertNotNull(result.getAdequacyAssessedAt());
    }

    @Test
    void assessAdequacyAcceptsInadequate() {
        when(specimenRepository.findById(specimenId)).thenReturn(Optional.of(specimen()));

        ProcedureSpecimenEntity result = service.assessAdequacy(episodeId, specimenId, "INADEQUATE");

        assertEquals("INADEQUATE", result.getAdequacy());
    }

    /** NOT_ASSESSED is the default, not something a caller asserts — it is the absence of an assessment. */
    @Test
    void assessAdequacyRejectsNotAssessedAsAnExplicitValue() {
        assertThrows(ResponseStatusException.class,
                () -> service.assessAdequacy(episodeId, specimenId, "NOT_ASSESSED"));
    }

    @Test
    void assessAdequacyRejectsAnInventedValue() {
        assertThrows(ResponseStatusException.class,
                () -> service.assessAdequacy(episodeId, specimenId, "PROBABLY_FINE"));
    }

    @Test
    void aSpecimenFromAnotherTenantIsNotFound() {
        ProcedureSpecimenEntity s = specimen();
        s.setTenantId(otherTenant);
        when(specimenRepository.findById(specimenId)).thenReturn(Optional.of(s));

        assertThrows(ResponseStatusException.class, () -> service.confirmLabel(episodeId, specimenId));
    }

    /** Defense in depth: a specimen id that belongs to a DIFFERENT episode in the same tenant is refused too. */
    @Test
    void aSpecimenFromAnotherEpisodeInTheSameTenantIsRefused() {
        ProcedureSpecimenEntity s = specimen();
        s.setEpisodeId(UUID.randomUUID());
        when(specimenRepository.findById(specimenId)).thenReturn(Optional.of(s));

        assertThrows(ResponseStatusException.class, () -> service.confirmLabel(episodeId, specimenId));
    }

    @Test
    void anUnknownSpecimenIdIsNotFound() {
        when(specimenRepository.findById(specimenId)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> service.confirmLabel(episodeId, specimenId));
    }

    @Test
    void custodyRowCarriesAllRecordedFields() {
        ProcedureSpecimenEntity s = specimen();
        s.setCollectedBy("nurse-1");
        s.setLabelConfirmedBy("nurse-1");
        s.setAdequacy("ADEQUATE");
        s.setAdequacyAssessedBy("pathologist-1");

        var row = service.custodyRow(s);

        assertEquals(specimenId.toString(), row.get("id"));
        assertEquals("nurse-1", row.get("collected_by"));
        assertEquals("nurse-1", row.get("label_confirmed_by"));
        assertEquals("ADEQUATE", row.get("adequacy"));
        assertEquals("pathologist-1", row.get("adequacy_assessed_by"));
    }

    // ── Wave P13 (pipeline §24) — the FHIR Specimen resource write on collection ──

    private ProcedureEpisodeEntity episode() {
        ProcedureEpisodeEntity e = new ProcedureEpisodeEntity();
        e.setEpisodeId(episodeId);
        e.setTenantId(tenant);
        e.setSubjectCpid("CPID-FHIR-1");
        return e;
    }

    @Test
    void recordCollectionWritesARealFhirSpecimenResourceAndStoresTheReturnedRef() {
        when(episodeRepository.findById(episodeId)).thenReturn(Optional.of(episode()));
        when(specimenRepository.findById(specimenId)).thenReturn(Optional.of(specimen()));
        when(butanoClient.writeSpecimen(eq("CPID-FHIR-1"), any(ProcedureSpecimenEntity.class)))
                .thenReturn("Specimen/specimen-abc123");

        ProcedureSpecimenEntity result = service.recordCollection(episodeId, specimenId, "formalin jar", "10% formalin");

        assertEquals("Specimen/specimen-abc123", result.getFhirSpecimenRef());
    }

    /** Best-effort: a Butano outage must not surface as an error — custody is still recorded locally. */
    @Test
    void recordCollectionStillSucceedsWhenTheFhirWriteReturnsNull() {
        when(episodeRepository.findById(episodeId)).thenReturn(Optional.of(episode()));
        when(specimenRepository.findById(specimenId)).thenReturn(Optional.of(specimen()));
        when(butanoClient.writeSpecimen(anyString(), any(ProcedureSpecimenEntity.class))).thenReturn(null);

        ProcedureSpecimenEntity result = service.recordCollection(episodeId, specimenId, "formalin jar", "10% formalin");

        assertEquals("actor-nurse", result.getCollectedBy(), "custody is still recorded even when Butano is unavailable");
        assertNull(result.getFhirSpecimenRef());
    }

    @Test
    void recordCollectionNeverCallsButanoWhenTheEpisodeCannotBeFound() {
        when(specimenRepository.findById(specimenId)).thenReturn(Optional.of(specimen()));
        // episodeRepository.findById(episodeId) defaults to Optional.empty() from setUp()

        service.recordCollection(episodeId, specimenId, "formalin jar", "10% formalin");

        verify(butanoClient, never()).writeSpecimen(anyString(), any());
    }
}
