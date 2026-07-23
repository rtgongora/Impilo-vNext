package zw.gov.mohcc.impilo.pharmacy.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.pharmacy.domain.ClarificationStatus;
import zw.gov.mohcc.impilo.pharmacy.domain.DispenseItemStatus;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.ClarificationRequestEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.DispenseItemEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.ClarificationRequestRepository;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.DispenseItemRepository;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * OF-B15 — the substitution → prescriber clarification loop (§8.10.1):
 * auto-allowed substitutions apply immediately; approval-required rules create
 * a PENDING clarification + {@code pharmacy.dispense.clarification.v1} event;
 * approve applies exactly once; decline is fail-closed (never applied).
 */
@ExtendWith(MockitoExtension.class)
class ClarificationServiceTest {

    @Mock ClarificationRequestRepository clarificationRepository;
    @Mock DispenseItemRepository itemRepository;
    @Mock SubstitutionEngine substitutionEngine;
    @Mock EventOutboxRepository outboxRepository;

    private ClarificationService service;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ITEM_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ClarificationService(clarificationRepository, itemRepository,
                substitutionEngine, outboxRepository, new ObjectMapper());
        TrustContextHolder.set(new TrustContext(TENANT_ID, "pharmacist-1", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private DispenseItemEntity item() {
        DispenseItemEntity item = new DispenseItemEntity();
        item.setItemId(ITEM_ID);
        item.setDispenseOrderId(ORDER_ID);
        item.setDrugCode("BRAND-PARA-500");
        item.setStatus(DispenseItemStatus.PENDING);
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
        return item;
    }

    @Test
    @DisplayName("auto-allowed tier applies immediately, no clarification created")
    void autoAllowedAppliesImmediately() {
        DispenseItemEntity item = item();
        when(substitutionEngine.evaluateSubstitution(ITEM_ID, "GEN-PARA-500"))
                .thenReturn(new SubstitutionEngine.SubstitutionResult(true, false, "ok"));
        when(substitutionEngine.applySubstitution(ITEM_ID, "GEN-PARA-500", "out of stock"))
                .thenReturn(item);

        ClarificationService.ProposalOutcome outcome =
                service.propose(ITEM_ID, "GEN-PARA-500", "out of stock", true);

        assertThat(outcome.autoApplied()).isTrue();
        verify(clarificationRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    @DisplayName("approval-required tier creates PENDING clarification + outbox event")
    void stepUpCreatesPendingClarification() {
        item();
        when(substitutionEngine.evaluateSubstitution(ITEM_ID, "GEN-PARA-500"))
                .thenReturn(new SubstitutionEngine.SubstitutionResult(true, true, "step-up"));
        when(clarificationRepository.existsByDispenseItemIdAndStatus(ITEM_ID, ClarificationStatus.PENDING))
                .thenReturn(false);
        when(clarificationRepository.save(any())).thenAnswer(inv -> {
            ClarificationRequestEntity c = inv.getArgument(0);
            c.setClarificationId(UUID.randomUUID());
            return c;
        });

        ClarificationService.ProposalOutcome outcome =
                service.propose(ITEM_ID, "GEN-PARA-500", "brand unavailable", true);

        assertThat(outcome.autoApplied()).isFalse();
        assertThat(outcome.clarification().getStatus()).isEqualTo(ClarificationStatus.PENDING);
        assertThat(outcome.clarification().isPatientConsent()).isTrue();
        assertThat(outcome.clarification().getSourceDrugCode()).isEqualTo("BRAND-PARA-500");
        verify(substitutionEngine, never()).applySubstitution(any(), any(), any());
        verify(outboxRepository).save(argThat(e ->
                "DISPENSE_CLARIFICATION_REQUESTED".equals(e.getEventType())));
    }

    @Test
    @DisplayName("disallowed substitution proposal is rejected outright")
    void disallowedProposalRejected() {
        item();
        when(substitutionEngine.evaluateSubstitution(ITEM_ID, "OTHER"))
                .thenReturn(new SubstitutionEngine.SubstitutionResult(false, false, "no rule"));

        assertThatThrownBy(() -> service.propose(ITEM_ID, "OTHER", "reason", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    @DisplayName("second proposal while one is pending is refused")
    void duplicatePendingRefused() {
        item();
        when(substitutionEngine.evaluateSubstitution(ITEM_ID, "GEN-PARA-500"))
                .thenReturn(new SubstitutionEngine.SubstitutionResult(true, true, "step-up"));
        when(clarificationRepository.existsByDispenseItemIdAndStatus(ITEM_ID, ClarificationStatus.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> service.propose(ITEM_ID, "GEN-PARA-500", "reason", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already pending");
    }

    private ClarificationRequestEntity pending(UUID id) {
        ClarificationRequestEntity c = new ClarificationRequestEntity();
        c.setClarificationId(id);
        c.setTenantId(TENANT_ID);
        c.setDispenseOrderId(ORDER_ID);
        c.setDispenseItemId(ITEM_ID);
        c.setSourceDrugCode("BRAND-PARA-500");
        c.setTargetDrugCode("GEN-PARA-500");
        c.setReason("brand unavailable");
        c.setStatus(ClarificationStatus.PENDING);
        c.setRequestedBy("pharmacist-1");
        when(clarificationRepository.findById(id)).thenReturn(Optional.of(c));
        return c;
    }

    @Test
    @DisplayName("prescriber approve applies the substitution and emits resolved event")
    void approveAppliesSubstitution() {
        UUID id = UUID.randomUUID();
        ClarificationRequestEntity c = pending(id);
        when(clarificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ClarificationRequestEntity resolved = service.resolve(id, true, "approved — generic ok");

        assertThat(resolved.getStatus()).isEqualTo(ClarificationStatus.APPROVED);
        assertThat(resolved.getResolvedBy()).isEqualTo("pharmacist-1");
        verify(substitutionEngine).applySubstitution(ITEM_ID, "GEN-PARA-500", c.getReason());
        verify(outboxRepository).save(argThat(e ->
                "DISPENSE_CLARIFICATION_RESOLVED".equals(e.getEventType())));
    }

    @Test
    @DisplayName("prescriber decline is fail-closed: substitution never applied")
    void declineIsFailClosed() {
        UUID id = UUID.randomUUID();
        pending(id);
        when(clarificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ClarificationRequestEntity resolved = service.resolve(id, false, "keep the brand");

        assertThat(resolved.getStatus()).isEqualTo(ClarificationStatus.DECLINED);
        verify(substitutionEngine, never()).applySubstitution(any(), any(), any());
        verify(outboxRepository).save(argThat(e ->
                "DISPENSE_CLARIFICATION_RESOLVED".equals(e.getEventType())
                        && e.getPayload().contains("DECLINED")));
    }

    @Test
    @DisplayName("resolving a non-pending clarification is refused")
    void resolveNonPendingRefused() {
        UUID id = UUID.randomUUID();
        ClarificationRequestEntity c = pending(id);
        c.setStatus(ClarificationStatus.DECLINED);

        assertThatThrownBy(() -> service.resolve(id, true, "note"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not pending");
    }
}
