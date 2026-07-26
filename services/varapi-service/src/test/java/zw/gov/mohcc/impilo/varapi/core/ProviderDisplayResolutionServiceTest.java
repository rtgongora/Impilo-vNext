package zw.gov.mohcc.impilo.varapi.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * The rota composition contract: display facts for people the rostering service only knows by
 * reference, with a miss that says so instead of inventing anybody.
 */
@ExtendWith(MockitoExtension.class)
class ProviderDisplayResolutionServiceTest {

    @Mock private ProviderRepository providerRepository;

    private ProviderDisplayResolutionService service;

    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ProviderDisplayResolutionService(providerRepository);
        TrustContextHolder.set(new TrustContext(
                tenantId, "svc-caller", "SYSTEM", "SYSTEM", "device",
                UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private static ProviderEntity provider(UUID healthId, String title, String given, String family) {
        ProviderEntity p = new ProviderEntity();
        p.setImpiloHealthId(healthId);
        p.setProviderPublicId("PUB-" + healthId.toString().substring(0, 8));
        p.setTitle(title);
        p.setGivenName(given);
        p.setFamilyName(family);
        p.setProfession("MEDICAL_PRACTITIONER");
        p.setCadre("REGISTRAR");
        p.setPhone("+263771234567");
        return p;
    }

    @Test
    void resolvesDisplayNameAndPhoneForAKnownProvider() {
        UUID healthId = UUID.randomUUID();
        when(providerRepository.findByTenantIdAndImpiloHealthIdIn(eq(tenantId), any()))
                .thenReturn(List.of(provider(healthId, "Dr", "Tendai", "Moyo")));

        List<Map<String, Object>> rows = service.resolveDisplayFacts(List.of(healthId.toString()));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("resolved")).isEqualTo(true);
        assertThat(rows.get(0).get("displayName")).isEqualTo("Dr Tendai Moyo");
        assertThat(rows.get(0).get("phone")).isEqualTo("+263771234567");
        assertThat(rows.get(0).get("cadre")).isEqualTo("REGISTRAR");
    }

    @Test
    void unknownAndMalformedIdsShareTheShapeOfAHitAndCarryNoName() {
        UUID known = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();
        when(providerRepository.findByTenantIdAndImpiloHealthIdIn(eq(tenantId), any()))
                .thenReturn(List.of(provider(known, "Dr", "Tendai", "Moyo")));

        List<Map<String, Object>> rows = service.resolveDisplayFacts(
                List.of(known.toString(), unknown.toString(), "not-a-uuid"));

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).keySet()).containsExactlyElementsOf(rows.get(1).keySet());
        assertThat(rows.get(1).keySet()).containsExactlyElementsOf(rows.get(2).keySet());
        assertThat(rows.get(1).get("resolved")).isEqualTo(false);
        assertThat(rows.get(2).get("resolved")).isEqualTo(false);
        assertThat(rows.get(1).get("displayName")).isNull();
        assertThat(rows.get(2).get("displayName")).isNull();
        assertThat(rows.get(2).get("phone")).isNull();
    }

    @Test
    void everyRequestedIdComesBackAndOrderIsPreserved() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(providerRepository.findByTenantIdAndImpiloHealthIdIn(eq(tenantId), any()))
                .thenReturn(List.of(provider(b, "Sr", "Rudo", "Ncube")));

        List<Map<String, Object>> rows = service.resolveDisplayFacts(List.of(a.toString(), b.toString()));

        assertThat(rows).extracting(r -> r.get("healthId"))
                .containsExactly(a.toString(), b.toString());
    }

    @Test
    void aRegistryRowWithNoNameResolvesToNoDisplayName() {
        UUID healthId = UUID.randomUUID();
        when(providerRepository.findByTenantIdAndImpiloHealthIdIn(eq(tenantId), any()))
                .thenReturn(List.of(provider(healthId, "Dr", "  ", null)));

        List<Map<String, Object>> rows = service.resolveDisplayFacts(List.of(healthId.toString()));

        // A title alone is not a name: the caller must fall back to the worker reference rather
        // than render a rota entry reading "Dr".
        assertThat(rows.get(0).get("resolved")).isEqualTo(true);
        assertThat(rows.get(0).get("displayName")).isNull();
    }

    @Test
    void anEmptyRequestAsksTheRegistryNothing() {
        assertThat(service.resolveDisplayFacts(List.of())).isEmpty();
        assertThat(service.resolveDisplayFacts(null)).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(providerRepository);
    }

    @Test
    void refusesAnUnboundedBatch() {
        List<String> tooMany = java.util.stream.IntStream
                .range(0, ProviderDisplayResolutionService.MAX_BATCH + 1)
                .mapToObj(i -> UUID.randomUUID().toString())
                .toList();

        assertThatThrownBy(() -> service.resolveDisplayFacts(tooMany))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("health ids");
    }
}
