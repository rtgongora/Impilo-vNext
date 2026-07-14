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
import zw.gov.mohcc.impilo.inventory.api.dto.RecordControlledDrugRequest;
import zw.gov.mohcc.impilo.inventory.api.dto.RecordControlledDrugResponse;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ControlledDrugRegisterEntity;
import zw.gov.mohcc.impilo.inventory.persistence.repository.ControlledDrugRegisterRepository;
import zw.gov.mohcc.impilo.inventory.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ControlledDrugRegisterServiceTest {

    @Mock private ControlledDrugRegisterRepository registerRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private ObjectMapper objectMapper;
    @InjectMocks private ControlledDrugRegisterService service;

    private static final UUID TENANT = UUID.randomUUID();
    private static final String ITEM = "MORPH-10";
    private static final String REF = "CASE-1";

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
    @DisplayName("ADMINISTER without a witness_provider is rejected with 400")
    void administer_withoutWitness_throws400() {
        RecordControlledDrugRequest req = new RecordControlledDrugRequest(
                null, null, ITEM, "Morphine 10mg", "ADMINISTER", new BigDecimal("2"),
                "mg", "provider-1", null, "THEATRE_CASE", REF, null, null);

        assertThatThrownBy(() -> service.record(req))
                .hasMessageContaining("400")
                .hasMessageContaining("witness_provider is required");
    }

    @Test
    @DisplayName("ISSUE(+10) then ADMINISTER(-2 with witness) leaves running_balance 8")
    void issueThenAdminister_carriesBalance() {
        when(registerRepository.save(any(ControlledDrugRegisterEntity.class))).thenAnswer(inv -> {
            ControlledDrugRegisterEntity e = inv.getArgument(0);
            if (e.getEntryId() == null) {
                e.setEntryId(UUID.randomUUID());
            }
            return e;
        });

        // First movement: no prior entry -> balance starts at 0, ISSUE(+10) = 10.
        when(registerRepository.findFirstByTenantIdAndItemCodeAndRefIdOrderByCreatedAtDesc(
                eq(TENANT), eq(ITEM), eq(REF))).thenReturn(Optional.empty());

        RecordControlledDrugRequest issue = new RecordControlledDrugRequest(
                null, null, ITEM, "Morphine 10mg", "ISSUE", new BigDecimal("10"),
                "mg", null, null, "THEATRE_CASE", REF, null, null);
        RecordControlledDrugResponse issued = service.record(issue);
        assertThat(issued.runningBalance()).isEqualByComparingTo("10");

        // Second movement: prior latest entry now carries balance 10.
        ControlledDrugRegisterEntity prior = new ControlledDrugRegisterEntity();
        prior.setRunningBalance(new BigDecimal("10"));
        lenient().when(registerRepository.findFirstByTenantIdAndItemCodeAndRefIdOrderByCreatedAtDesc(
                eq(TENANT), eq(ITEM), eq(REF))).thenReturn(Optional.of(prior));

        RecordControlledDrugRequest admin = new RecordControlledDrugRequest(
                null, null, ITEM, "Morphine 10mg", "ADMINISTER", new BigDecimal("2"),
                "mg", "provider-1", "witness-9", "THEATRE_CASE", REF, null, null);
        RecordControlledDrugResponse administered = service.record(admin);

        assertThat(administered.runningBalance()).isEqualByComparingTo("8");
        assertThat(administered.witnessProvider()).isEqualTo("witness-9");
        assertThat(administered.action()).isEqualTo("ADMINISTER");
    }
}
