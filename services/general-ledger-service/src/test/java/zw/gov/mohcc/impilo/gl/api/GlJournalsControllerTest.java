package zw.gov.mohcc.impilo.gl.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import zw.gov.mohcc.impilo.gl.core.ChartOfAccountsService;
import zw.gov.mohcc.impilo.gl.core.FiscalPeriodService;
import zw.gov.mohcc.impilo.gl.core.JournalService;
import zw.gov.mohcc.impilo.gl.persistence.entity.JournalEntryEntity;
import zw.gov.mohcc.impilo.gl.persistence.repository.JournalEntryRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The national general ledger had no authorization on its three write endpoints, and recorded who
 * posted an entry from the request body — defaulting to the literal string "api".
 *
 * <p>No test class existed for this controller at all, which is part of why both survived.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GlJournalsControllerTest {

    @Mock private JournalService journalService;
    @Mock private JournalEntryRepository journalEntryRepository;
    @Mock private ChartOfAccountsService chartOfAccountsService;
    @Mock private FiscalPeriodService fiscalPeriodService;

    private MockMvc mockMvc;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID entryId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new GlJournalsController(
                journalService, journalEntryRepository, chartOfAccountsService, fiscalPeriodService))
                .build();
        callerIs("finance-clerk-1", "OPERATOR");
        when(journalService.postEntry(any(), any(), anyString())).thenReturn(new JournalEntryEntity());
        when(journalService.reverseEntry(any(), any(), anyString())).thenReturn(new JournalEntryEntity());
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private void callerIs(String actorId, String actorType) {
        TrustContextHolder.set(new TrustContext(
                tenantId, actorId, actorType, "PAYMENT", "dev",
                UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
    }

    // ── Authorization ──

    @ParameterizedTest
    @ValueSource(strings = {"CITIZEN", "PROVIDER", "CAREGIVER"})
    void aPersonalOrClinicalSessionMayNotPostToTheNationalLedger(String actorType) throws Exception {
        callerIs("someone", actorType);

        mockMvc.perform(post("/internal/v1/gl/journals/" + entryId + "/post")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        verify(journalService, never()).postEntry(any(), any(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CITIZEN", "PROVIDER", "CAREGIVER"})
    void aPersonalOrClinicalSessionMayNotReverseAJournal(String actorType) throws Exception {
        callerIs("someone", actorType);

        mockMvc.perform(post("/internal/v1/gl/journals/" + entryId + "/reverse")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        verify(journalService, never()).reverseEntry(any(), any(), anyString());
    }

    @Test
    void aPersonalSessionMayNotCreateAJournal() throws Exception {
        callerIs("someone", "CITIZEN");

        mockMvc.perform(post("/internal/v1/gl/journals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryDate\":\"2026-08-07\",\"lines\":[]}"))
                .andExpect(status().isForbidden());

        verify(journalService, never()).createDraft(any(), any(), any(), any(), any(), any(), any());
    }

    /**
     * Checked before the entry is loaded, so the endpoint cannot be used to find out which journal
     * ids exist.
     */
    @Test
    void theRefusalDoesNotDependOnWhetherTheEntryExists() throws Exception {
        callerIs("someone", "CITIZEN");

        mockMvc.perform(post("/internal/v1/gl/journals/" + UUID.randomUUID() + "/post")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        verify(journalEntryRepository, never()).findById(any());
    }

    @Test
    void aCallerWithNoActorIdIsRefusedBecauseTheLedgerRecordsWhoPosted() throws Exception {
        callerIs(null, "OPERATOR");

        mockMvc.perform(post("/internal/v1/gl/journals/" + entryId + "/post")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        verify(journalService, never()).postEntry(any(), any(), anyString());
    }

    // ── Attribution ──

    /** The positive control, and the point of the change in one assertion. */
    @Test
    void theEntryIsPostedAsTheCallerAndNotAsWhoeverTheBodyNames() throws Exception {
        mockMvc.perform(post("/internal/v1/gl/journals/" + entryId + "/post")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"postedBy\":\"the-chief-accountant\"}"))
                .andExpect(status().isOk());

        verify(journalService).postEntry(eq(tenantId), eq(entryId), eq("finance-clerk-1"));
        verify(journalService, never()).postEntry(any(), any(), eq("the-chief-accountant"));
    }

    @Test
    void theReversalIsAttributedToTheCallerAndNotToTheBody() throws Exception {
        mockMvc.perform(post("/internal/v1/gl/journals/" + entryId + "/reverse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reversedBy\":\"somebody-else\"}"))
                .andExpect(status().isOk());

        verify(journalService).reverseEntry(eq(tenantId), eq(entryId), eq("finance-clerk-1"));
        verify(journalService, never()).reverseEntry(any(), any(), eq("somebody-else"));
    }

    /**
     * The old default. With no {@code postedBy} in the body the ledger used to record the literal
     * string "api" as the poster of a national journal entry.
     */
    @Test
    void anOmittedPostedByNoLongerRecordsTheWordApi() throws Exception {
        mockMvc.perform(post("/internal/v1/gl/journals/" + entryId + "/post")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        verify(journalService, never()).postEntry(any(), any(), eq("api"));
        verify(journalService).postEntry(eq(tenantId), eq(entryId), eq("finance-clerk-1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SYSTEM", "SERVICE", "OPERATOR"})
    void machineAndBackOfficeCallersAreAdmitted(String actorType) throws Exception {
        callerIs("caller-1", actorType);

        mockMvc.perform(post("/internal/v1/gl/journals/" + entryId + "/post")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }
}
