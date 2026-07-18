package zw.gov.mohcc.impilo.tshepo.authz.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.tshepo.authz.service.PolicyCacheService;
import zw.gov.mohcc.impilo.tshepo.authz.service.ProviderPrivilegeRevocationStore;

import static org.mockito.Mockito.*;

/**
 * PJ15 (Identity Journey Doctrine §5): a lapsed/suspended licence must revoke a
 * provider's practice privileges. Regression for the bug where
 * {@code varapi.provider.lapsed} (payload {@code newLifecycle=LAPSED}, no
 * {@code newStatus}) silently failed to revoke.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PrivilegeRevocationConsumer")
class PrivilegeRevocationConsumerTest {

    @Mock private PolicyCacheService policyCacheService;
    @Mock private ProviderPrivilegeRevocationStore revocationStore;

    private PrivilegeRevocationConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new PrivilegeRevocationConsumer(new ObjectMapper(), policyCacheService, revocationStore);
    }

    private ConsumerRecord<String, String> event(String type, String payload) {
        String envelope = "{\"event_type\":\"" + type + "\",\"payload\":" + payload + "}";
        return new ConsumerRecord<>("varapi.provider", 0, 0L, "PROV-1", envelope);
    }

    @Test
    @DisplayName("licence LAPSE revokes privileges (the PJ15 fix)")
    void lapsedRevokes() {
        consumer.onVarapiEvent(event("varapi.provider.lapsed",
                "{\"providerPublicId\":\"PROV-1\",\"previousLifecycle\":\"LICENCED_ACTIVE\","
                        + "\"newLifecycle\":\"LAPSED\",\"licenceValidTo\":\"2026-01-01\"}"));
        verify(revocationStore).markRevoked("PROV-1");
        verify(policyCacheService).evictForProvider(any(), eq("PROV-1"));
    }

    @Test
    @DisplayName("SUSPENDED lifecycle revokes")
    void suspendedRevokes() {
        consumer.onVarapiEvent(event("varapi.provider.status_changed",
                "{\"providerPublicId\":\"PROV-1\",\"newLifecycle\":\"SUSPENDED\"}"));
        verify(revocationStore).markRevoked("PROV-1");
    }

    @Test
    @DisplayName("licence DUE_FOR_RENEWAL is a warning — does NOT revoke")
    void dueForRenewalDoesNotRevoke() {
        consumer.onVarapiEvent(event("varapi.provider.licence_due",
                "{\"providerPublicId\":\"PROV-1\",\"previousLifecycle\":\"LICENCED_ACTIVE\","
                        + "\"newLifecycle\":\"LICENCE_DUE_FOR_RENEWAL\"}"));
        verify(revocationStore, never()).markRevoked(any());
    }

    @Test
    @DisplayName("return to LICENCED_ACTIVE reactivates (clears revocation)")
    void reactivationClears() {
        consumer.onVarapiEvent(event("varapi.provider.status_changed",
                "{\"providerPublicId\":\"PROV-1\",\"previousLifecycle\":\"LAPSED\","
                        + "\"newLifecycle\":\"LICENCED_ACTIVE\"}"));
        verify(revocationStore).clearRevoked("PROV-1");
        verify(revocationStore, never()).markRevoked(any());
    }
}
