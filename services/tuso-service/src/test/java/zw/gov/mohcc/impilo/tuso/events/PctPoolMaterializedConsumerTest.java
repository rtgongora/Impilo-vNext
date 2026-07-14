package zw.gov.mohcc.impilo.tuso.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.tuso.core.VirtualServiceRegistryService;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PctPoolMaterializedConsumerTest {

    @Mock private VirtualServiceRegistryService registryService;

    private PctPoolMaterializedConsumer consumer() {
        return new PctPoolMaterializedConsumer(new ObjectMapper(), registryService);
    }

    private final UUID tenant = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void flatPayloadFlipsTheEntityActive() {
        consumer().onPoolMaterialized(
                "{\"vsUid\":\"vh-provincial-zw-ha\",\"tenantId\":\"" + tenant + "\",\"poolId\":\"PROVINCIAL_ZW_HA\"}");

        verify(registryService).markActiveFromMaterialization(tenant, "vh-provincial-zw-ha",
                "pct:pool-materialization");
    }

    @Test
    void envelopedPayloadIsUnwrapped() {
        consumer().onPoolMaterialized(
                "{\"eventType\":\"POOL_MATERIALIZED\",\"payload\":{\"vsUid\":\"vh-x\",\"tenantId\":\""
                        + tenant + "\"}}");

        verify(registryService).markActiveFromMaterialization(tenant, "vh-x", "pct:pool-materialization");
    }

    @Test
    void missingIdentityIsSkippedNotFlipped() {
        consumer().onPoolMaterialized("{\"poolId\":\"P\"}");
        consumer().onPoolMaterialized("not-json");

        verify(registryService, never()).markActiveFromMaterialization(any(), any(), any());
    }

    @Test
    void malformedTenantNeverThrowsOutOfTheListener() {
        consumer().onPoolMaterialized("{\"vsUid\":\"vh-x\",\"tenantId\":\"not-a-uuid\"}");

        verify(registryService, never()).markActiveFromMaterialization(any(), eq("vh-x"), any());
    }
}
