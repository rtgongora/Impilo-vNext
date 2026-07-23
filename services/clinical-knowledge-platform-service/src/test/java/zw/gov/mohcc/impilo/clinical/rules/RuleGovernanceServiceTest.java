package zw.gov.mohcc.impilo.clinical.rules;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.clinical.persistence.entity.RuleDefinitionEntity;
import zw.gov.mohcc.impilo.clinical.persistence.repository.RuleDefinitionRepository;
import zw.gov.mohcc.impilo.clinical.rules.model.RuleAlert;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** OF-B3 governance seam: metadata overrides + effective-window retirement + fail-open reads. */
@ExtendWith(MockitoExtension.class)
class RuleGovernanceServiceTest {

    @Mock private RuleDefinitionRepository repository;

    private RuleAlert alert(String code) {
        return new RuleAlert(code, "HIGH", "msg", "expl", true, true);
    }

    private RuleDefinitionEntity def(String code) {
        RuleDefinitionEntity d = new RuleDefinitionEntity();
        d.setCode(code);
        return d;
    }

    @Test
    void noGovernanceRowPassesThroughUnchanged() {
        when(repository.findAll()).thenReturn(List.of(def("OTHER_RULE")));
        var out = new RuleGovernanceService(repository).apply(List.of(alert("DRUG_ALLERGY_INTERACTION")));
        assertThat(out).hasSize(1);
        assertThat(out.get(0).severity()).isEqualTo("HIGH");
    }

    @Test
    void severityAndPostureOverriddenByDefinition() {
        RuleDefinitionEntity d = def("DRUG_ALLERGY_INTERACTION");
        d.setSeverity("CRITICAL");
        d.setOverrideAllowed(false);
        when(repository.findAll()).thenReturn(List.of(d));

        var out = new RuleGovernanceService(repository).apply(List.of(alert("DRUG_ALLERGY_INTERACTION")));
        assertThat(out.get(0).severity()).isEqualTo("CRITICAL");
        assertThat(out.get(0).overrideAllowed()).isFalse();
        assertThat(out.get(0).interruptive()).isTrue(); // untouched
    }

    @Test
    void retiredRuleSuppressedOutsideEffectiveWindow() {
        RuleDefinitionEntity d = def("STEWARDSHIP_REVIEW");
        d.setEffectiveEnd(LocalDate.now().minusDays(1));
        when(repository.findAll()).thenReturn(List.of(d));

        var out = new RuleGovernanceService(repository)
                .apply(List.of(alert("STEWARDSHIP_REVIEW"), alert("DRUG_ALLERGY_INTERACTION")));
        assertThat(out).extracting(RuleAlert::code).containsExactly("DRUG_ALLERGY_INTERACTION");
    }

    @Test
    void unreadableGovernanceTableFailsOpenNeverSuppresses() {
        when(repository.findAll()).thenThrow(new RuntimeException("db down"));
        var alerts = List.of(alert("DRUG_ALLERGY_INTERACTION"));
        var out = new RuleGovernanceService(repository).apply(alerts);
        assertThat(out).isSameAs(alerts);
    }
}
