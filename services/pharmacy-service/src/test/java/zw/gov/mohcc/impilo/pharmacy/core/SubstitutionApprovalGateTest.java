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
import zw.gov.mohcc.impilo.pharmacy.domain.SubstitutionRuleType;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.ClarificationRequestEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.DispenseItemEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.SubstitutionRuleEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.ClarificationRequestRepository;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.DispenseItemRepository;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.FormularyRepository;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.SubstitutionRuleRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * OF-B15 fail-closed approval gate on the REAL substitution engine: a
 * step-up rule can NEVER apply unilaterally — only an unconsumed APPROVED
 * clarification for exactly that item+target unlocks it, and the approval is
 * consumed exactly once (§8.10.1: "never unilateral").
 */
@ExtendWith(MockitoExtension.class)
class SubstitutionApprovalGateTest {

    @Mock SubstitutionRuleRepository ruleRepository;
    @Mock DispenseItemRepository itemRepository;
    @Mock FormularyRepository formularyRepository;
    @Mock ClarificationRequestRepository clarificationRepository;

    private SubstitutionEngineImpl engine;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID FACILITY_ID = UUID.randomUUID();
    private static final UUID ITEM_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        engine = new SubstitutionEngineImpl(ruleRepository, itemRepository, formularyRepository,
                new ObjectMapper(), clarificationRepository);
        TrustContextHolder.set(new TrustContext(TENANT_ID, "pharmacist-1", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), FACILITY_ID, null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private DispenseItemEntity item() {
        DispenseItemEntity item = new DispenseItemEntity();
        item.setItemId(ITEM_ID);
        item.setDispenseOrderId(UUID.randomUUID());
        item.setDrugCode("BRAND-PARA-500");
        item.setStatus(DispenseItemStatus.PENDING);
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
        return item;
    }

    private void stubRule(boolean requiresStepUp) {
        SubstitutionRuleEntity rule = new SubstitutionRuleEntity();
        rule.setTenantId(TENANT_ID);
        rule.setRuleType(SubstitutionRuleType.GENERIC);
        rule.setSourceCode("BRAND-PARA-500");
        rule.setTargetCode("GEN-PARA-500");
        rule.setRequiresStepUp(requiresStepUp);
        rule.setEnabled(true);
        when(ruleRepository.findByTenantIdAndFacilityIdAndSourceCodeAndEnabledTrue(
                TENANT_ID, FACILITY_ID, "BRAND-PARA-500")).thenReturn(List.of(rule));
    }

    @Test
    @DisplayName("step-up rule without approved clarification is refused (fail-closed)")
    void stepUpWithoutApprovalRefused() {
        item();
        stubRule(true);
        when(clarificationRepository
                .findFirstByDispenseItemIdAndTargetDrugCodeAndStatusAndAppliedAtIsNull(
                        ITEM_ID, "GEN-PARA-500", ClarificationStatus.APPROVED))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> engine.applySubstitution(ITEM_ID, "GEN-PARA-500", "brand out"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requires prescriber clarification");

        verify(itemRepository, never()).save(any());
    }

    @Test
    @DisplayName("approved clarification unlocks the substitution and is consumed once")
    void approvedClarificationUnlocksAndIsConsumed() {
        item();
        stubRule(true);
        ClarificationRequestEntity approval = new ClarificationRequestEntity();
        approval.setClarificationId(UUID.randomUUID());
        approval.setDispenseItemId(ITEM_ID);
        approval.setTargetDrugCode("GEN-PARA-500");
        approval.setStatus(ClarificationStatus.APPROVED);
        approval.setResolvedBy("dr-mapfumo");
        approval.setPatientConsent(true);
        when(clarificationRepository
                .findFirstByDispenseItemIdAndTargetDrugCodeAndStatusAndAppliedAtIsNull(
                        ITEM_ID, "GEN-PARA-500", ClarificationStatus.APPROVED))
                .thenReturn(Optional.of(approval));
        when(itemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DispenseItemEntity applied = engine.applySubstitution(ITEM_ID, "GEN-PARA-500", "brand out");

        assertThat(applied.getStatus()).isEqualTo(DispenseItemStatus.SUBSTITUTED);
        assertThat(applied.getDrugCode()).isEqualTo("GEN-PARA-500");
        assertThat(applied.getSubstitution())
                .contains(approval.getClarificationId().toString())
                .contains("\"approvedBy\":\"dr-mapfumo\"")
                .contains("\"patientConsent\":true");
        assertThat(approval.getAppliedAt()).as("approval consumed exactly once").isNotNull();
        verify(clarificationRepository).save(approval);
    }

    @Test
    @DisplayName("auto-allowed rule (no step-up) still applies without any clarification")
    void autoAllowedRuleUnaffected() {
        item();
        stubRule(false);
        when(itemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DispenseItemEntity applied = engine.applySubstitution(ITEM_ID, "GEN-PARA-500", "brand out");

        assertThat(applied.getStatus()).isEqualTo(DispenseItemStatus.SUBSTITUTED);
        verify(clarificationRepository, never())
                .findFirstByDispenseItemIdAndTargetDrugCodeAndStatusAndAppliedAtIsNull(any(), any(), any());
    }
}
