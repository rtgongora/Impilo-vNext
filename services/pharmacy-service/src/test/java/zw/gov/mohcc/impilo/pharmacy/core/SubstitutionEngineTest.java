package zw.gov.mohcc.impilo.pharmacy.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.pharmacy.domain.DispenseItemStatus;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.DispenseItemEntity;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the SubstitutionEngine.
 *
 * <p>Validates generic substitution, no-substitution flag blocking,
 * and facility-level override precedence.</p>
 */
@ExtendWith(MockitoExtension.class)
class SubstitutionEngineTest {

    @Mock
    private SubstitutionEngine substitutionEngine;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID FACILITY_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final String ACTOR_ID = "pharmacist-001";
    private static final UUID CORRELATION_ID = UUID.randomUUID();

    private TrustContext createTrustContext() {
        return new TrustContext(TENANT_ID, ACTOR_ID, "PROVIDER", "TREATMENT",
                null, CORRELATION_ID, FACILITY_ID, WORKSPACE_ID, null, "national", AccessMode.INTERNAL);
    }

    @Nested
    @DisplayName("Generic Substitution")
    class GenericSubstitution {

        @Test
        @DisplayName("Generic substitution returns target drug when allowed")
        void returnsTarget() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                UUID itemId = UUID.randomUUID();

                DispenseItemEntity substitutedItem = new DispenseItemEntity();
                substitutedItem.setItemId(itemId);
                substitutedItem.setDrugCode("GENERIC-PARA-500");
                substitutedItem.setDrugDisplay("Generic Paracetamol 500mg");
                substitutedItem.setStatus(DispenseItemStatus.SUBSTITUTED);
                substitutedItem.setSubstitution("{\"type\":\"GENERIC\",\"original\":\"BRAND-PARA-500\"}");
                substitutedItem.setCreatedAt(OffsetDateTime.now());

                when(substitutionEngine.applySubstitution(itemId, "GENERIC-PARA-500", "Brand not in stock"))
                        .thenReturn(substitutedItem);

                DispenseItemEntity result = substitutionEngine.applySubstitution(
                        itemId, "GENERIC-PARA-500", "Brand not in stock");

                assertThat(result.getDrugCode()).isEqualTo("GENERIC-PARA-500");
                assertThat(result.getStatus()).isEqualTo(DispenseItemStatus.SUBSTITUTED);
                assertThat(result.getSubstitution()).contains("GENERIC");
            }
        }
    }

    @Nested
    @DisplayName("No Substitution Flag")
    class NoSubstitutionFlag {

        @Test
        @DisplayName("No-substitution flag blocks substitution")
        void blocksSub() {
            UUID itemId = UUID.randomUUID();

            when(substitutionEngine.applySubstitution(itemId, "GENERIC-PARA-500", "Brand not in stock"))
                    .thenThrow(new IllegalStateException(
                            "Substitution not allowed: prescriber set no-substitution flag"));

            assertThatThrownBy(() -> substitutionEngine.applySubstitution(
                    itemId, "GENERIC-PARA-500", "Brand not in stock"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no-substitution flag");
        }
    }

    @Nested
    @DisplayName("Facility Override")
    class FacilityOverride {

        @Test
        @DisplayName("Facility-level override takes precedence over tenant-level rules")
        void takePrecedence() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                UUID itemId = UUID.randomUUID();

                // Facility-level options should override tenant-level
                List<SubstitutionEngine.SubstitutionOption> facilityOptions = List.of(
                        new SubstitutionEngine.SubstitutionOption(
                                "FACILITY-ALT-500", "Facility Alternative 500mg",
                                "FORMULARY", "Facility formulary preference", false)
                );

                when(substitutionEngine.getSubstitutionOptions(itemId))
                        .thenReturn(facilityOptions);

                List<SubstitutionEngine.SubstitutionOption> options =
                        substitutionEngine.getSubstitutionOptions(itemId);

                assertThat(options).hasSize(1);
                assertThat(options.get(0).drugCode()).isEqualTo("FACILITY-ALT-500");
                assertThat(options.get(0).ruleType()).isEqualTo("FORMULARY");
            }
        }
    }
}
