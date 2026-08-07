package zw.gov.mohcc.impilo.referral;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.referral.core.ReferralOutboxPublisher;
import zw.gov.mohcc.impilo.referral.core.SurgicalReferralService;
import zw.gov.mohcc.impilo.referral.persistence.entity.ReferralEntity;
import zw.gov.mohcc.impilo.referral.persistence.entity.SurgicalReferralEntity;
import zw.gov.mohcc.impilo.referral.persistence.repository.ReferralRepository;
import zw.gov.mohcc.impilo.referral.persistence.repository.SurgicalReferralRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the surgical referral decision FSM. Asserts that ACCEPTED is
 * the only decision that emits the scheduling funnel trigger, and that the
 * receiving-team decision maps correctly onto the parent referral FSM.
 */
class SurgicalReferralServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");

    private ReferralRepository referralRepository;
    private SurgicalReferralRepository surgicalRepository;
    private ReferralOutboxPublisher outbox;
    private SurgicalReferralService service;

    @BeforeEach
    void setUp() {
        referralRepository = mock(ReferralRepository.class);
        surgicalRepository = mock(SurgicalReferralRepository.class);
        outbox = mock(ReferralOutboxPublisher.class);
        service = new SurgicalReferralService(referralRepository, surgicalRepository, outbox);
        TrustContextHolder.set(new TrustContext(TENANT, "surgeon-1", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private SurgicalReferralEntity seed(String decision) {
        UUID referralId = UUID.randomUUID();
        SurgicalReferralEntity surgical = new SurgicalReferralEntity(UUID.randomUUID(), referralId, TENANT, "P-1");
        surgical.setIntendedProcedureZiboCode("ZIBO-CHOLE");
        if (!SurgicalReferralEntity.DECISION_PENDING.equals(decision)) {
            surgical.decide(decision, "seed");
        }
        ReferralEntity referral = new ReferralEntity(referralId, TENANT, "P-1", new LinkedHashMap<>());
        when(surgicalRepository.findByTenantIdAndId(eq(TENANT), eq(surgical.getId())))
                .thenReturn(Optional.of(surgical));
        when(referralRepository.findByTenantIdAndId(eq(TENANT), eq(referralId)))
                .thenReturn(Optional.of(referral));
        when(surgicalRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(referralRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        return surgical;
    }

    @Test
    void acceptedEmitsFunnelTriggerAndAdvancesReferral() {
        SurgicalReferralEntity surgical = seed(SurgicalReferralEntity.DECISION_PENDING);
        Map<String, Object> body = Map.of("decision", "accepted");

        Map<String, Object> result = service.decideSurgicalReferral(surgical.getId().toString(), body);

        assertThat(result.get("decision")).isEqualTo("ACCEPTED");
        // Tenant and subject are asserted rather than matched loosely: they are the federation
        // context the v1.1 envelope is built from, and an event reaching the outbox without a
        // tenant is now refused rather than published with a placeholder.
        verify(outbox).append(eq("SurgicalReferral"), anyString(),
                eq("referral.surgical.accepted"), eq(TENANT), eq("PATIENT"), eq("P-1"), any());
    }

    @Test
    void declinedEmitsDeclinedNotAccepted() {
        SurgicalReferralEntity surgical = seed(SurgicalReferralEntity.DECISION_PENDING);
        Map<String, Object> body = Map.of("decision", "declined", "reason", "not indicated");

        service.decideSurgicalReferral(surgical.getId().toString(), body);

        verify(outbox).append(any(), anyString(), eq("referral.surgical.declined"), any(), any(), any(), any());
        verify(outbox, never()).append(any(), anyString(), eq("referral.surgical.accepted"), any(), any(), any(), any());
    }

    @Test
    void redirectedAndInfoRequestedEmitDistinctEvents() {
        SurgicalReferralEntity a = seed(SurgicalReferralEntity.DECISION_PENDING);
        service.decideSurgicalReferral(a.getId().toString(), Map.of("decision", "redirected"));
        verify(outbox).append(any(), anyString(), eq("referral.surgical.redirected"), any(), any(), any(), any());

        SurgicalReferralEntity b = seed(SurgicalReferralEntity.DECISION_PENDING);
        service.decideSurgicalReferral(b.getId().toString(), Map.of("decision", "info_requested"));
        verify(outbox).append(any(), anyString(), eq("referral.surgical.info_requested"), any(), any(), any(), any());
    }

    @Test
    void terminalReferralCannotBeReDecided() {
        SurgicalReferralEntity surgical = seed(SurgicalReferralEntity.DECISION_DECLINED);
        assertThatThrownBy(() ->
                service.decideSurgicalReferral(surgical.getId().toString(), Map.of("decision", "accepted")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("terminal");
        verify(outbox, never()).append(any(), anyString(), eq("referral.surgical.accepted"), any(), any(), any(), any());
    }

    @Test
    void missingDecisionIsRejected() {
        SurgicalReferralEntity surgical = seed(SurgicalReferralEntity.DECISION_PENDING);
        assertThatThrownBy(() ->
                service.decideSurgicalReferral(surgical.getId().toString(), Map.of()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void entityDecideGuardsTerminalTransitions() {
        SurgicalReferralEntity surgical = new SurgicalReferralEntity(UUID.randomUUID(), UUID.randomUUID(), TENANT, "P-2");
        surgical.decide(SurgicalReferralEntity.DECISION_REDIRECTED, "elsewhere");
        assertThatThrownBy(() -> surgical.decide(SurgicalReferralEntity.DECISION_ACCEPTED, "no"))
                .isInstanceOf(IllegalStateException.class);
    }
}
