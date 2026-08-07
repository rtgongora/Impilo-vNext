package zw.gov.mohcc.impilo.auditledger.api;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import zw.gov.mohcc.impilo.auditledger.core.AuditLedgerService;
import zw.gov.mohcc.impilo.auditledger.domain.AuditRecordEntity;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
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
 * Authentication is not authorization.
 *
 * <p>{@code 73cc29d27} closed this service's anonymous hole — it had no security dependency and no
 * filter chain at all — but it added authentication only. The chain ends in
 * {@code anyRequest().authenticated()}, so a signed-in citizen, a real authenticated principal,
 * could still append to the national audit ledger. {@code AuditLedgerAuthenticationTest} covers the
 * anonymous half; this class covers the half it could not see, at the controller rather than
 * through the filter chain, so a refusal here can only have come from the actor-type guard.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditLedgerActorGuardTest {

    @Mock
    private AuditLedgerService auditService;

    private MockMvc mockMvc;

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String CORRELATION = "22222222-2222-4222-8222-222222222222";

    private static final String BODY = """
            {"correlationId":"%s","actorId":"citizen-42","actorType":"CITIZEN",
             "action":"READ","resourceType":"Patient"}
            """.formatted(CORRELATION);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuditLedgerController(auditService, new ObjectMapper()))
                .build();

        RequestContextHolder.set(RequestContext.of(
                TENANT.toString(), "test", UUID.randomUUID().toString(), CORRELATION,
                null, null, null));

        AuditRecordEntity saved = new AuditRecordEntity(
                UUID.randomUUID(), TENANT, 1L, UUID.fromString(CORRELATION),
                "citizen-42", "CITIZEN", "READ", "Patient", null, "SUCCESS", null,
                OffsetDateTime.now(), null, "hash");
        when(auditService.appendRecord(any(), anyString(), anyString(), any(), any()))
                .thenReturn(saved);
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
        RequestContextHolder.clear();
    }

    private void callerIs(String actorType) {
        TrustContextHolder.set(new TrustContext(
                TENANT, "actor-1", actorType, "AUDIT", "dev",
                UUID.fromString(CORRELATION), null, null, null, AccessMode.INTERNAL));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CITIZEN", "PROVIDER", "OPERATOR", "CAREGIVER"})
    void noHumanSessionMayAppendToTheLedger(String actorType) throws Exception {
        callerIs(actorType);

        mockMvc.perform(post("/internal/v1/audit/records")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());

        verify(auditService, never()).appendRecord(any(), anyString(), anyString(), any(), any());
    }

    @Test
    void aCallerPresentingNoActorTypeIsRefused() throws Exception {
        callerIs(null);

        mockMvc.perform(post("/internal/v1/audit/records")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());

        verify(auditService, never()).appendRecord(any(), anyString(), anyString(), any(), any());
    }

    @Test
    void aRequestThatNeverPassedTheTrustFilterIsRefusedRatherThanWavedThrough() throws Exception {
        TrustContextHolder.clear();

        mockMvc.perform(post("/internal/v1/audit/records")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());

        verify(auditService, never()).appendRecord(any(), anyString(), anyString(), any(), any());
    }

    /**
     * The positive control. Without it a guard that refused everything unconditionally would
     * satisfy every assertion above.
     */
    @ParameterizedTest
    @ValueSource(strings = {"SYSTEM", "SERVICE"})
    void aMachineCallerIsAdmittedAndTheRecordIsWritten(String actorType) throws Exception {
        callerIs(actorType);

        mockMvc.perform(post("/internal/v1/audit/records")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());

        verify(auditService).appendRecord(eq(TENANT), anyString(), anyString(), any(), any());
    }

    /**
     * Who the record is <em>about</em> is body-supplied on purpose: a service recording "citizen 42
     * read record X" must be able to name citizen 42. This pins that the guard did not quietly
     * rewrite the subject, which would harden nothing and destroy what the ledger says.
     */
    @Test
    void theGuardDoesNotRewriteWhoTheRecordIsAbout() throws Exception {
        callerIs("SYSTEM");

        mockMvc.perform(post("/internal/v1/audit/records")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());

        verify(auditService).appendRecord(any(), anyString(), anyString(), any(),
                org.mockito.ArgumentMatchers.argThat(r -> "citizen-42".equals(r.actorId())
                        && "CITIZEN".equals(r.actorType())));
    }
}
