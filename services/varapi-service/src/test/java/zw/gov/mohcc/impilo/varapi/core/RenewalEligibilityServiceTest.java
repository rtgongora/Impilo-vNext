package zw.gov.mohcc.impilo.varapi.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.varapi.core.CpdService.CpdSummary;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RenewalEligibilityServiceTest {

    @Mock ProviderRepository providerRepository;
    @Mock CpdService cpdService;

    private RenewalEligibilityService service() {
        return new RenewalEligibilityService(providerRepository, cpdService);
    }

    @Test
    void eligibleWhenCpdRequirementMet() {
        when(cpdService.getCpdSummary("PROV1")).thenReturn(new CpdSummary(null, List.of(), 30, 30, true));
        var e = service().evaluate("PROV1");
        assertThat(e.eligible()).isTrue();
        assertThat(e.cpdRequirementMet()).isTrue();
        assertThat(e.outstanding()).isEmpty();
    }

    @Test
    void blockedWithShortfallWhenCpdNotMet() {
        when(cpdService.getCpdSummary("PROV2")).thenReturn(new CpdSummary(null, List.of(), 12, 30, false));
        var e = service().evaluate("PROV2");
        assertThat(e.eligible()).isFalse();
        assertThat(e.cpdRequirementMet()).isFalse();
        assertThat(e.outstanding()).anyMatch(s -> s.contains("18"));
    }
}
