package zw.gov.mohcc.impilo.gl.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.gl.core.ChartOfAccountsService;
import zw.gov.mohcc.impilo.gl.persistence.entity.AccountEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlAccountsControllerContractSurfaceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000010");

    @Mock
    private ChartOfAccountsService chartOfAccountsService;

    private GlAccountsController controller;

    @BeforeEach
    void setUp() {
        controller = new GlAccountsController(chartOfAccountsService);
        RequestContextHolder.set(RequestContext.of(
                TENANT.toString(), "national", "req-gl", "corr-gl", null, null, null));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void listAccountsUsesTenantFromRequestContext() {
        when(chartOfAccountsService.listAccounts(TENANT)).thenReturn(List.of(new AccountEntity()));

        var response = controller.list();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        verify(chartOfAccountsService).listAccounts(eq(TENANT));
    }

    @Test
    void seedDefaultChartReturnsCount() {
        when(chartOfAccountsService.seedDefaultHealthFacilityChart(TENANT)).thenReturn(42);

        var response = controller.seed();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("seeded", 42);
    }

    @Test
    void createAccountDelegatesToService() {
        AccountEntity created = new AccountEntity();
        when(chartOfAccountsService.createAccount(
                eq(TENANT), eq("1000"), eq("Cash"), eq("ASSET"), eq("DEBIT"), eq(null)))
                .thenReturn(created);

        var response = controller.create(Map.of(
                "accountCode", "1000",
                "name", "Cash",
                "accountType", "ASSET",
                "normalBalance", "DEBIT"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(created);
    }
}
