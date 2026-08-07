package zw.gov.mohcc.impilo.tshepo.audit.api.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tshepo.audit.api.dto.AuditEventRequest;
import zw.gov.mohcc.impilo.tshepo.audit.core.AuditChainService;
import zw.gov.mohcc.impilo.tshepo.audit.persistence.entity.AuditEventEntity;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The audit write used to take its actor from the request body while the endpoint was permitAll.
 * These tests pin the two halves of the fix: the body cannot choose who it was, and a caller with
 * no identity cannot write at all.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditIngestControllerTest {

    @Mock
    private AuditChainService auditChainService;

    private MockMvc mockMvc;

    private final UUID contextTenant = UUID.randomUUID();
    private final UUID bodyTenant = UUID.randomUUID();
    private final UUID correlationId = UUID.randomUUID();

    /** A body that claims to be the national system administrator recording a successful action. */
    private String forgedBody() {
        return """
                {
                  "tenantId": "%s",
                  "eventType": "RECORD_ACCESS",
                  "actorId": "system-administrator",
                  "actorType": "SYSTEM",
                  "action": "READ",
                  "outcome": "SUCCESS",
                  "correlationId": "%s"
                }
                """.formatted(bodyTenant, correlationId);
    }

    @BeforeEach
    void setUp() {
        setContext("citizen-42", "CITIZEN", contextTenant);
        mockMvc = MockMvcBuilders.standaloneSetup(new AuditIngestController(auditChainService)).build();

        AuditEventEntity saved = new AuditEventEntity();
        saved.setId(UUID.randomUUID());
        saved.setCorrelationId(correlationId);
        saved.setCreatedAt(Instant.now());
        when(auditChainService.appendEvent(any())).thenReturn(saved);
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private void setContext(String actorId, String actorType, UUID tenantId) {
        TrustContextHolder.set(new TrustContext(
                tenantId, actorId, actorType, "TREATMENT",
                "dev", correlationId, null, null, null, AccessMode.INTERNAL));
    }

    @Test
    void theBodyCannotChooseWhoTheActorWas() throws Exception {
        mockMvc.perform(post("/v1/audit/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(forgedBody()))
                .andExpect(status().isCreated());

        ArgumentCaptor<AuditEventRequest> captor = ArgumentCaptor.forClass(AuditEventRequest.class);
        verify(auditChainService).appendEvent(captor.capture());

        assertThat(captor.getValue().actorId())
                .as("the persisted actor must be the caller, not the body's claim")
                .isEqualTo("citizen-42");
        assertThat(captor.getValue().actorType()).isEqualTo("CITIZEN");
    }

    @Test
    void theBodyCannotChooseWhichTenantsChainItIsWrittenInto() throws Exception {
        mockMvc.perform(post("/v1/audit/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(forgedBody()))
                .andExpect(status().isCreated());

        ArgumentCaptor<AuditEventRequest> captor = ArgumentCaptor.forClass(AuditEventRequest.class);
        verify(auditChainService).appendEvent(captor.capture());

        assertThat(captor.getValue().tenantId()).isEqualTo(contextTenant);
        assertThat(captor.getValue().tenantId()).isNotEqualTo(bodyTenant);
    }

    @Test
    void whatHappenedIsStillTakenFromTheBody() throws Exception {
        mockMvc.perform(post("/v1/audit/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(forgedBody()))
                .andExpect(status().isCreated());

        ArgumentCaptor<AuditEventRequest> captor = ArgumentCaptor.forClass(AuditEventRequest.class);
        verify(auditChainService).appendEvent(captor.capture());

        assertThat(captor.getValue().eventType()).isEqualTo("RECORD_ACCESS");
        assertThat(captor.getValue().action()).isEqualTo("READ");
        assertThat(captor.getValue().outcome()).isEqualTo("SUCCESS");
    }

    @Test
    void aCallerWithNoActorIdCannotWriteAnAuditRow() throws Exception {
        setContext(null, "SYSTEM", contextTenant);

        mockMvc.perform(post("/v1/audit/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(forgedBody()))
                .andExpect(status().isForbidden());

        verify(auditChainService, never()).appendEvent(any());
    }

    @Test
    void aCallerWithNoTenantCannotWriteAnAuditRow() throws Exception {
        setContext("citizen-42", "CITIZEN", null);

        mockMvc.perform(post("/v1/audit/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(forgedBody()))
                .andExpect(status().isForbidden());

        verify(auditChainService, never()).appendEvent(any());
    }

    @Test
    void aCallerWithNoActorTypeCannotWriteAnAuditRow() throws Exception {
        setContext("citizen-42", "   ", contextTenant);

        mockMvc.perform(post("/v1/audit/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(forgedBody()))
                .andExpect(status().isForbidden());

        verify(auditChainService, never()).appendEvent(any());
    }

    @Test
    void theRefusalNamesTheHeadersThatWereMissing() throws Exception {
        setContext(null, null, null);

        mockMvc.perform(post("/v1/audit/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(forgedBody()))
                .andExpect(status().isForbidden())
                .andExpect(result -> {
                    String reason = result.getResolvedException().getMessage();
                    assertThat(reason)
                            .contains(TrustContext.H_ACTOR_ID)
                            .contains(TrustContext.H_ACTOR_TYPE)
                            .contains(TrustContext.H_TENANT_ID)
                            .contains("no tenant")
                            .contains("no actor id")
                            .contains("no actor type");
                });
    }
}
