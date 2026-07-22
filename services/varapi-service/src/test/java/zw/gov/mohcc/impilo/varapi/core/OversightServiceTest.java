package zw.gov.mohcc.impilo.varapi.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.varapi.persistence.repository.OversightEscalationGrantRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OversightServiceTest {

    @Mock OversightEscalationGrantRepository grantRepository;

    private final UUID tenant = UUID.randomUUID();
    private final UUID hpaOrg = UUID.fromString("a5000000-0000-4000-8000-000000000001");

    private OversightService service() {
        lenient().when(grantRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        return new OversightService(grantRepository);
    }

    @Test
    void hpaCannotSeeACaseWithoutAGrant() {
        when(grantRepository.existsByTenantIdAndSubjectTypeAndSubjectRefAndGrantedToOrgIdAndStatus(
                tenant, "DISCIPLINARY_CASE", "42", hpaOrg, "ACTIVE")).thenReturn(false);
        assertThat(service().oversightCanSeeCase(tenant, "DISCIPLINARY_CASE", "42", hpaOrg)).isFalse();
    }

    @Test
    void hpaSeesACaseOnlyOnceGranted() {
        when(grantRepository.existsByTenantIdAndSubjectTypeAndSubjectRefAndGrantedToOrgIdAndStatus(
                tenant, "DISCIPLINARY_CASE", "42", hpaOrg, "ACTIVE")).thenReturn(true);
        assertThat(service().oversightCanSeeCase(tenant, "DISCIPLINARY_CASE", "42", hpaOrg)).isTrue();
    }

    @Test
    void grantIsRecordedActive() {
        var g = service().grant(tenant, 3L, "DISCIPLINARY_CASE", "42", hpaOrg, "cross-council pattern", "hpa-officer");
        assertThat(g.getStatus()).isEqualTo("ACTIVE");
        assertThat(g.getGrantedToOrgId()).isEqualTo(hpaOrg);
    }
}
