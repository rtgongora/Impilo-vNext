package zw.gov.mohcc.impilo.wallet.api.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import zw.gov.mohcc.impilo.wallet.api.dto.CreateWalletRequest;
import zw.gov.mohcc.impilo.wallet.core.WalletService;
import zw.gov.mohcc.impilo.wallet.persistence.entity.WalletEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletControllerContractSurfaceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000013");

    @Mock
    private WalletService walletService;

    @InjectMocks
    private WalletController controller;

    @Test
    void createWalletReturnsCreatedEnvelope() {
        WalletEntity wallet = new WalletEntity();
        wallet.setWalletId(UUID.randomUUID());
        when(walletService.createWallet(eq(TENANT), eq("PERSON"), eq("owner-1"), eq("Primary"), eq("ZWL")))
                .thenReturn(wallet);

        var response = controller.createWallet(
                TENANT.toString(),
                "corr-1",
                new CreateWalletRequest("PERSON", "owner-1", "Primary", "ZWL"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isSameAs(wallet);
    }
}
