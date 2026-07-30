package zw.gov.mohcc.impilo.surgery.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.surgery.integration.PctMdtDecisionClient;
import zw.gov.mohcc.impilo.surgery.integration.PctMdtDecisionClient.MdtLookup;
import zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalDecisionEntity;
import zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalEpisodeEntity;
import zw.gov.mohcc.impilo.surgery.persistence.repository.SurgicalDecisionRepository;
import zw.gov.mohcc.impilo.surgery.persistence.repository.SurgicalEpisodeRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Surgical demonstration 3 — was this decision made by one surgeon or by a board? (V012)
 *
 * <p>A cancer resection agreed by a multidisciplinary team was recorded identically to a decision
 * one surgeon made alone in a clinic: one {@code decided_by}, one {@code decided_at}. The forum
 * was invisible, and with it the audit question "who agreed to this operation".</p>
 *
 * <p>Surgery references PCT's board record and does not become a third MDT system of record — PCT
 * already has two, an inherited duplication this programme names rather than repeats. Because
 * there are two, the reference is a pair: the id and the table it points into.</p>
 *
 * <p>The assertions that carry the weight are the three-way distinction in the lookup. Found,
 * definitively absent, and unreachable are three different answers, and collapsing the third into
 * either of the others is how a service starts lying.</p>
 */
@ExtendWith(MockitoExtension.class)
class SurgicalDecisionForumTest {

    @Mock SurgicalDecisionRepository repository;
    @Mock SurgicalEpisodeRepository episodeRepository;
    @Mock PctMdtDecisionClient mdtClient;

    private SurgicalDecisionService service;
    private final UUID tenant = UUID.randomUUID();
    private final UUID episodeId = UUID.randomUUID();
    private final UUID boardRef = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SurgicalDecisionService(repository, episodeRepository, mdtClient);
        TrustContextHolder.set(new TrustContext(tenant, "actor-surgeon", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL));

        SurgicalEpisodeEntity episode = new SurgicalEpisodeEntity();
        episode.setId(episodeId);
        episode.setTenantId(tenant);
        lenient().when(episodeRepository.findByIdAndTenantId(episodeId, tenant))
                .thenReturn(Optional.of(episode));
        lenient().when(repository.findBySurgicalEpisodeIdAndTenantId(episodeId, tenant))
                .thenReturn(Optional.empty());
        lenient().when(repository.save(any())).thenAnswer(i -> {
            SurgicalDecisionEntity e = i.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            return e;
        });
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private Map<String, Object> body(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    @Test
    @DisplayName("a decision with no stated forum stays individual, as every pre-V012 row was")
    void defaultsToIndividual() {
        Map<String, Object> out = service.recordDecision(episodeId, body("expectedBenefit", "Curative resection"));

        assertEquals("INDIVIDUAL", out.get("decisionForum"));
        assertNull(out.get("mdtDecisionRef"));
        verifyNoInteractions(mdtClient);
    }

    @Test
    @DisplayName("a board decision names the board that made it")
    void mdtDecisionReferencesTheBoard() {
        when(mdtClient.lookup(eq("PCT_MDT_DECISION"), eq(boardRef)))
                .thenReturn(MdtLookup.found(Map.of("decision", "Proceed to resection")));

        Map<String, Object> out = service.recordDecision(episodeId, body(
                "decisionForum", "MDT",
                "mdtDecisionSource", "PCT_MDT_DECISION",
                "mdtDecisionRef", boardRef.toString()));

        assertEquals("MDT", out.get("decisionForum"));
        assertEquals(boardRef.toString(), out.get("mdtDecisionRef"));
        assertEquals("PCT_MDT_DECISION", out.get("mdtDecisionSource"));
        assertEquals(true, out.get("mdtDecisionVerified"));
        assertNotNull(out.get("mdtDecisionVerifiedAt"));
    }

    @Test
    @DisplayName("the telemedicine board lane is addressable too — PCT has two, and both are real")
    void caseItemSourceIsAccepted() {
        when(mdtClient.lookup(eq("PCT_MDT_CASE_ITEM"), eq(boardRef)))
                .thenReturn(MdtLookup.found(Map.of("recommendation", "List for surgery")));

        assertEquals("PCT_MDT_CASE_ITEM", service.recordDecision(episodeId, body(
                "decisionForum", "MDT",
                "mdtDecisionSource", "PCT_MDT_CASE_ITEM",
                "mdtDecisionRef", boardRef.toString())).get("mdtDecisionSource"));
    }

    @Test
    @DisplayName("an MDT decision that names no board is refused — that is the defect being removed")
    void mdtWithoutAReferenceIsRefused() {
        SurgeryDomainException ex = assertThrows(SurgeryDomainException.class,
                () -> service.recordDecision(episodeId, body("decisionForum", "MDT")));

        assertEquals("MDT_REFERENCE_REQUIRED", ex.getCode());
        assertEquals(400, ex.getStatus());
        verify(repository, never()).save(any());
    }

    /** Two systems of record mean a bare id does not say which table it lives in. */
    @Test
    @DisplayName("a board reference with no source is refused, because PCT keeps two MDT tables")
    void referenceWithoutASourceIsRefused() {
        assertEquals("MDT_REFERENCE_REQUIRED",
                assertThrows(SurgeryDomainException.class,
                        () -> service.recordDecision(episodeId, body(
                                "decisionForum", "MDT",
                                "mdtDecisionRef", boardRef.toString()))).getCode());
    }

    @Test
    @DisplayName("an invented source is refused rather than stored as an unresolvable reference")
    void inventedSourceIsRefused() {
        assertEquals("INVALID_MDT_SOURCE",
                assertThrows(SurgeryDomainException.class,
                        () -> service.recordDecision(episodeId, body(
                                "decisionForum", "MDT",
                                "mdtDecisionSource", "SURGERY_OWN_MDT_TABLE",
                                "mdtDecisionRef", boardRef.toString()))).getCode(),
                "there is no surgical MDT table, and inventing a source name is how one starts");
    }

    @Test
    @DisplayName("an invented forum is refused")
    void inventedForumIsRefused() {
        assertEquals("INVALID_DECISION_FORUM",
                assertThrows(SurgeryDomainException.class,
                        () -> service.recordDecision(episodeId, body("decisionForum", "CORRIDOR_CHAT"))).getCode());
    }

    @Test
    @DisplayName("a reference that is not a UUID is refused before PCT is troubled with it")
    void malformedReferenceIsRefused() {
        assertEquals("INVALID_MDT_REFERENCE",
                assertThrows(SurgeryDomainException.class,
                        () -> service.recordDecision(episodeId, body(
                                "decisionForum", "MDT",
                                "mdtDecisionSource", "PCT_MDT_DECISION",
                                "mdtDecisionRef", "the tuesday meeting"))).getCode());
        verifyNoInteractions(mdtClient);
    }

    /**
     * PCT answered. The board record does not exist, so the citation is wrong and the decision
     * must not be written claiming it.
     */
    @Test
    @DisplayName("citing a board record PCT says does not exist is refused")
    void absentBoardRecordIsRefused() {
        when(mdtClient.lookup(eq("PCT_MDT_DECISION"), eq(boardRef))).thenReturn(MdtLookup.absent());

        SurgeryDomainException ex = assertThrows(SurgeryDomainException.class,
                () -> service.recordDecision(episodeId, body(
                        "decisionForum", "MDT",
                        "mdtDecisionSource", "PCT_MDT_DECISION",
                        "mdtDecisionRef", boardRef.toString())));

        assertEquals("MDT_DECISION_NOT_FOUND", ex.getCode());
        assertEquals(404, ex.getStatus());
        verify(repository, never()).save(any());
    }

    /**
     * The third answer. Nobody knows, so the decision is recorded and marked unverified — neither
     * blocked, which would let a PCT outage stop surgical decision-making, nor confirmed, which
     * would be a claim surgery never received.
     */
    @Test
    @DisplayName("an unreachable PCT records the decision unverified, neither blocked nor confirmed")
    void unreachablePctRecordsUnverified() {
        when(mdtClient.lookup(eq("PCT_MDT_DECISION"), eq(boardRef))).thenReturn(MdtLookup.unknown());

        Map<String, Object> out = service.recordDecision(episodeId, body(
                "decisionForum", "MDT",
                "mdtDecisionSource", "PCT_MDT_DECISION",
                "mdtDecisionRef", boardRef.toString()));

        assertEquals("MDT", out.get("decisionForum"), "the board still decided; that is local truth");
        assertEquals(boardRef.toString(), out.get("mdtDecisionRef"));
        assertNull(out.get("mdtDecisionVerifiedAt"));
        assertEquals(false, out.get("mdtDecisionVerified"),
                "an outage must not read as confirmation — the surface has to be able to say "
                        + "'referenced but not confirmed'");
    }

    @Test
    @DisplayName("correcting a decision back to individual clears the board reference with it")
    void revertingToIndividualClearsTheReference() {
        SurgicalDecisionEntity existing = new SurgicalDecisionEntity();
        existing.setId(UUID.randomUUID());
        existing.setTenantId(tenant);
        existing.setSurgicalEpisodeId(episodeId);
        existing.setDecisionForum("MDT");
        existing.setMdtDecisionRef(boardRef);
        existing.setMdtDecisionSource("PCT_MDT_DECISION");
        existing.setMdtDecisionVerifiedAt(java.time.OffsetDateTime.now());
        when(repository.findBySurgicalEpisodeIdAndTenantId(episodeId, tenant)).thenReturn(Optional.of(existing));

        Map<String, Object> out = service.recordDecision(episodeId, body("decisionForum", "INDIVIDUAL"));

        assertEquals("INDIVIDUAL", out.get("decisionForum"));
        assertNull(out.get("mdtDecisionRef"),
                "an individual decision carrying a board reference is a contradiction, not extra detail");
        assertNull(out.get("mdtDecisionSource"));
        assertNull(out.get("mdtDecisionVerifiedAt"));
    }

    @Test
    @DisplayName("the forum does not disturb the final-decision pairing V004 already enforced")
    void forumCoexistsWithTheFinalDecisionTriple() {
        when(mdtClient.lookup(any(), any())).thenReturn(MdtLookup.found(Map.of("decision", "Proceed")));

        Map<String, Object> out = service.recordDecision(episodeId, body(
                "decisionForum", "MDT",
                "mdtDecisionSource", "PCT_MDT_DECISION",
                "mdtDecisionRef", boardRef.toString(),
                "finalDecision", "PROCEED"));

        assertEquals("PROCEED", out.get("finalDecision"));
        assertEquals("actor-surgeon", out.get("decidedBy"));
        assertNotNull(out.get("decidedAt"));
        assertEquals("MDT", out.get("decisionForum"));
    }
}
