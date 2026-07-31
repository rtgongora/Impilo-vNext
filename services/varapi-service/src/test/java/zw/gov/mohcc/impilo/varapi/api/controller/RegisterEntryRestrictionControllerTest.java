package zw.gov.mohcc.impilo.varapi.api.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CouncilEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.CouncilRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.RegisterEntryRestrictionRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterEntryRestrictionControllerTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock RegisterEntryRestrictionRepository restrictionRepository;
    @Mock CouncilRepository councilRepository;
    @InjectMocks RegisterEntryRestrictionController controller;

    @Test
    void listsCouncilScopedRestrictions() {
        CouncilEntity council = new CouncilEntity();
        council.setId(7L);
        council.setTenantId(TENANT);
        when(councilRepository.findById(7L)).thenReturn(Optional.of(council));
        Object[] row = new Object[]{1L, "CONDITION", "Supervised practice", "ACTIVE",
                null, null, "panel", "DET-1", 99L, "NCZ/1", 42L, "NCZ_GENERAL", "General"};
        when(restrictionRepository.findCouncilRestrictions(TENANT, 7L))
                .thenReturn(List.<Object[]>of(row));

        List<Map<String, Object>> rows = controller.list(TENANT, 7L);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("restrictionType")).isEqualTo("CONDITION");
        assertThat(rows.get(0).get("registrationNumber")).isEqualTo("NCZ/1");
        assertThat(rows.get(0).get("registerCode")).isEqualTo("NCZ_GENERAL");
    }

    @Test
    void refusesUnknownCouncil() {
        when(councilRepository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> controller.list(TENANT, 9L))
                .isInstanceOf(ResponseStatusException.class);
    }
}
