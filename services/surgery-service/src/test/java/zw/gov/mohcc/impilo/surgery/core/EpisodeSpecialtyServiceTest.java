package zw.gov.mohcc.impilo.surgery.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalEpisodeEntity;
import zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalEpisodeSpecialtyEntity;
import zw.gov.mohcc.impilo.surgery.persistence.repository.SurgicalEpisodeRepository;
import zw.gov.mohcc.impilo.surgery.persistence.repository.SurgicalEpisodeSpecialtyRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Surgical demonstration 4 — shared-specialty care (V011).
 *
 * <p>A retroperitoneal sarcoma resection is surgical oncology, vascular and urology in one
 * operation. The single {@code surgical_episode.specialty} column could name one of them, so the
 * other teams were invisible to the waiting list, to the booking and to the audit that asks who
 * operated.</p>
 *
 * <p>The invariant these tests defend is the awkward one: that column STAYS, meaning the lead
 * specialty, so every reader written before V011 still gets a correct answer. It is therefore
 * written in the same transaction as the LEAD row, and the assertions below check the pair every
 * time the lead moves.</p>
 */
@ExtendWith(MockitoExtension.class)
class EpisodeSpecialtyServiceTest {

    @Mock SurgicalEpisodeRepository episodeRepository;
    @Mock SurgicalEpisodeSpecialtyRepository specialtyRepository;

    private EpisodeSpecialtyService service;
    private final UUID tenant = UUID.randomUUID();
    private final UUID episodeId = UUID.randomUUID();
    private SurgicalEpisodeEntity episode;

    @BeforeEach
    void setUp() {
        service = new EpisodeSpecialtyService(episodeRepository, specialtyRepository);
        TrustContextHolder.set(new TrustContext(tenant, "actor-surgeon", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL));
        episode = new SurgicalEpisodeEntity();
        episode.setId(episodeId);
        episode.setTenantId(tenant);
        episode.setSubjectCpid("CPID-1");
        episode.setOperativeIndication("Retroperitoneal sarcoma resection");
        episode.setSpecialty("SURGICAL_ONCOLOGY");
        episode.setStatus("ASSESSMENT");
        lenient().when(episodeRepository.findByIdAndTenantId(episodeId, tenant)).thenReturn(Optional.of(episode));
        lenient().when(episodeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(specialtyRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(specialtyRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(specialtyRepository.findByEpisodeIdOrderByRoleAscSpecialtyAsc(episodeId))
                .thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private SurgicalEpisodeSpecialtyEntity row(String specialty, String role) {
        SurgicalEpisodeSpecialtyEntity e = new SurgicalEpisodeSpecialtyEntity();
        e.setId(UUID.randomUUID());
        e.setTenantId(tenant);
        e.setEpisodeId(episodeId);
        e.setSpecialty(specialty);
        e.setRole(role);
        e.setAddedBy("actor-surgeon");
        return e;
    }

    private SurgicalEpisodeSpecialtyEntity captureSaved() {
        ArgumentCaptor<SurgicalEpisodeSpecialtyEntity> captor =
                ArgumentCaptor.forClass(SurgicalEpisodeSpecialtyEntity.class);
        verify(specialtyRepository, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("the lead is recorded in the join table when the episode opens")
    void leadRecordedAtOpen() {
        service.recordLeadAtOpen(episode);

        SurgicalEpisodeSpecialtyEntity saved = captureSaved();
        assertEquals("SURGICAL_ONCOLOGY", saved.getSpecialty());
        assertEquals("LEAD", saved.getRole(),
                "the column and the join table must agree from the episode's first moment");
    }

    @Test
    @DisplayName("an episode opened with no specialty records no lead rather than an invented one")
    void noSpecialtyRecordsNothing() {
        episode.setSpecialty(null);

        service.recordLeadAtOpen(episode);

        verify(specialtyRepository, never()).save(any());
    }

    @Test
    @DisplayName("a second team joins the case as SHARED with its own stated contribution")
    void sharedTeamJoins() {
        Map<String, Object> added = service.addSpecialty(episodeId, "VASCULAR", "SHARED",
                "Vascular control of the inferior vena cava");

        assertEquals("VASCULAR", added.get("specialty"));
        assertEquals("SHARED", added.get("role"));
        assertEquals("Vascular control of the inferior vena cava", added.get("contribution"));
    }

    @Test
    @DisplayName("adding a team defaults to SHARED, never silently to lead")
    void roleDefaultsToShared() {
        assertEquals("SHARED", service.addSpecialty(episodeId, "UROLOGY", null, null).get("role"));
    }

    @Test
    @DisplayName("adding a team does not disturb the lead column")
    void addingSharedLeavesLeadColumnAlone() {
        service.addSpecialty(episodeId, "UROLOGY", "SHARED", "Ureteric reimplantation");

        assertEquals("SURGICAL_ONCOLOGY", episode.getSpecialty(),
                "a shared team is not the lead, and every pre-V011 reader must still see the lead");
    }

    @Test
    @DisplayName("a second lead is refused — two teams cannot both own the episode")
    void secondLeadIsRefused() {
        when(specialtyRepository.findByEpisodeIdAndRole(episodeId, "LEAD"))
                .thenReturn(Optional.of(row("SURGICAL_ONCOLOGY", "LEAD")));

        SurgeryDomainException ex = assertThrows(SurgeryDomainException.class,
                () -> service.addSpecialty(episodeId, "VASCULAR", "LEAD", null));

        assertEquals("LEAD_SPECIALTY_ALREADY_SET", ex.getCode());
        assertEquals(409, ex.getStatus());
    }

    @Test
    @DisplayName("the same team cannot be added twice")
    void duplicateSpecialtyIsRefused() {
        when(specialtyRepository.findByEpisodeIdAndSpecialty(episodeId, "VASCULAR"))
                .thenReturn(Optional.of(row("VASCULAR", "SHARED")));

        assertEquals("SPECIALTY_ALREADY_ON_EPISODE",
                assertThrows(SurgeryDomainException.class,
                        () -> service.addSpecialty(episodeId, "VASCULAR", "SHARED", null)).getCode());
    }

    @Test
    @DisplayName("an invented specialty is refused rather than stored")
    void inventedSpecialtyIsRefused() {
        assertEquals("INVALID_SPECIALTY",
                assertThrows(SurgeryDomainException.class,
                        () -> service.addSpecialty(episodeId, "KEYHOLE_WIZARDRY", "SHARED", null)).getCode());
    }

    @Test
    @DisplayName("an unrecognised role is refused")
    void inventedRoleIsRefused() {
        assertEquals("INVALID_SPECIALTY_ROLE",
                assertThrows(SurgeryDomainException.class,
                        () -> service.addSpecialty(episodeId, "PLASTICS", "ASSISTING", null)).getCode());
    }

    @Test
    @DisplayName("handing the lead over moves the column with it")
    void transferLeadMovesTheColumn() {
        SurgicalEpisodeSpecialtyEntity currentLead = row("GENERAL_SURGERY", "LEAD");
        when(specialtyRepository.findByEpisodeIdAndRole(episodeId, "LEAD")).thenReturn(Optional.of(currentLead));
        when(specialtyRepository.findByEpisodeIdAndSpecialty(episodeId, "VASCULAR")).thenReturn(Optional.empty());

        service.transferLead(episodeId, "VASCULAR", "Took over for the aortic reconstruction");

        assertEquals("VASCULAR", episode.getSpecialty(),
                "the lead column is the one thing pre-V011 readers see; it must follow the lead");
    }

    /** The outgoing team operated. It stays on the case, as SHARED. */
    @Test
    @DisplayName("the outgoing lead stays on the case as a shared team")
    void outgoingLeadIsDemotedNotRemoved() {
        SurgicalEpisodeSpecialtyEntity currentLead = row("GENERAL_SURGERY", "LEAD");
        when(specialtyRepository.findByEpisodeIdAndRole(episodeId, "LEAD")).thenReturn(Optional.of(currentLead));
        when(specialtyRepository.findByEpisodeIdAndSpecialty(episodeId, "VASCULAR")).thenReturn(Optional.empty());

        service.transferLead(episodeId, "VASCULAR", null);

        assertEquals("SHARED", currentLead.getRole(),
                "general surgery operated — handing over the lead does not erase that");
        verify(specialtyRepository, never()).delete(any());
    }

    /**
     * The partial unique index permits exactly one LEAD at a time, so the outgoing lead has to
     * step down before the incoming one is written — hence the flush, not merely a save.
     */
    @Test
    @DisplayName("the outgoing lead is flushed down before the incoming one is written")
    void demotionIsFlushedBeforePromotion() {
        SurgicalEpisodeSpecialtyEntity currentLead = row("GENERAL_SURGERY", "LEAD");
        when(specialtyRepository.findByEpisodeIdAndRole(episodeId, "LEAD")).thenReturn(Optional.of(currentLead));
        when(specialtyRepository.findByEpisodeIdAndSpecialty(episodeId, "VASCULAR")).thenReturn(Optional.empty());

        service.transferLead(episodeId, "VASCULAR", null);

        InOrderHelper.assertDemotedBeforePromoted(specialtyRepository);
    }

    @Test
    @DisplayName("a team already on the case is promoted rather than duplicated")
    void transferToAnExistingSharedTeamPromotesIt() {
        SurgicalEpisodeSpecialtyEntity currentLead = row("GENERAL_SURGERY", "LEAD");
        SurgicalEpisodeSpecialtyEntity vascular = row("VASCULAR", "SHARED");
        when(specialtyRepository.findByEpisodeIdAndRole(episodeId, "LEAD")).thenReturn(Optional.of(currentLead));
        when(specialtyRepository.findByEpisodeIdAndSpecialty(episodeId, "VASCULAR")).thenReturn(Optional.of(vascular));

        service.transferLead(episodeId, "VASCULAR", null);

        assertEquals("LEAD", vascular.getRole());
    }

    @Test
    @DisplayName("transferring the lead to the team that already holds it is refused")
    void transferToCurrentLeadIsRefused() {
        when(specialtyRepository.findByEpisodeIdAndRole(episodeId, "LEAD"))
                .thenReturn(Optional.of(row("VASCULAR", "LEAD")));

        assertEquals("LEAD_UNCHANGED",
                assertThrows(SurgeryDomainException.class,
                        () -> service.transferLead(episodeId, "VASCULAR", null)).getCode());
    }

    @Test
    @DisplayName("a shared team that turned out not to be needed can be removed")
    void sharedTeamCanBeRemoved() {
        SurgicalEpisodeSpecialtyEntity plastics = row("PLASTICS", "SHARED");
        when(specialtyRepository.findByEpisodeIdAndSpecialty(episodeId, "PLASTICS"))
                .thenReturn(Optional.of(plastics));

        service.removeSpecialty(episodeId, "PLASTICS");

        verify(specialtyRepository).delete(plastics);
    }

    @Test
    @DisplayName("the lead cannot be removed — no episode is left with nobody answering for it")
    void leadCannotBeRemoved() {
        when(specialtyRepository.findByEpisodeIdAndSpecialty(episodeId, "SURGICAL_ONCOLOGY"))
                .thenReturn(Optional.of(row("SURGICAL_ONCOLOGY", "LEAD")));

        SurgeryDomainException ex = assertThrows(SurgeryDomainException.class,
                () -> service.removeSpecialty(episodeId, "SURGICAL_ONCOLOGY"));

        assertEquals("CANNOT_REMOVE_LEAD_SPECIALTY", ex.getCode());
        verify(specialtyRepository, never()).delete(any());
    }

    @Test
    @DisplayName("removing a team that is not on the case is a not-found")
    void removingAbsentTeamIsNotFound() {
        when(specialtyRepository.findByEpisodeIdAndSpecialty(episodeId, "ENT")).thenReturn(Optional.empty());

        assertEquals(404, assertThrows(SurgeryDomainException.class,
                () -> service.removeSpecialty(episodeId, "ENT")).getStatus());
    }

    @Test
    @DisplayName("specialties on an unknown episode is a not-found, never an empty list")
    void unknownEpisodeIsNotFound() {
        UUID stranger = UUID.randomUUID();
        when(episodeRepository.findByIdAndTenantId(stranger, tenant)).thenReturn(Optional.empty());

        assertEquals("SURGICAL_EPISODE_NOT_FOUND",
                assertThrows(SurgeryDomainException.class,
                        () -> service.listSpecialties(stranger)).getCode());
    }

    /** Kept out of the test body so the ordering intent reads clearly at the call site. */
    private static final class InOrderHelper {
        static void assertDemotedBeforePromoted(SurgicalEpisodeSpecialtyRepository repo) {
            org.mockito.InOrder order = inOrder(repo);
            order.verify(repo).saveAndFlush(any());
            order.verify(repo).save(any());
        }
    }
}
