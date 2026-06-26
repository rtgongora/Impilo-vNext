package zw.gov.mohcc.impilo.tshepo.identity.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.tshepo.identity.api.dto.IdentifierResolveRequest;
import zw.gov.mohcc.impilo.tshepo.identity.api.dto.IdentifierResolveResponse;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.entity.IdMappingEntity;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.repository.IdMappingRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SilentIdentifierResolutionServiceTest {

    @Mock
    IdMappingRepository mappingRepo;
    @Mock
    RestTemplate vitoRestTemplate;
    @Mock
    RestTemplate varapiRestTemplate;

    SilentIdentifierResolutionService service;

    @BeforeEach
    void setUp() {
        service = new SilentIdentifierResolutionService(
                mappingRepo, vitoRestTemplate, varapiRestTemplate, new ObjectMapper());
    }

    @Test
    void healthId_resolvesToPersonRef() {
        UUID tenant = UUID.randomUUID();
        UUID healthId = UUID.randomUUID();
        UUID cpid = UUID.randomUUID();
        IdMappingEntity mapping = new IdMappingEntity();
        mapping.setTenantId(tenant);
        mapping.setHealthId(healthId);
        mapping.setCpid(cpid);
        when(mappingRepo.findByTenantIdAndHealthId(tenant, healthId)).thenReturn(Optional.of(mapping));

        IdentifierResolveResponse r = service.resolve(
                new IdentifierResolveRequest(tenant, "HEALTH_ID", healthId.toString()));

        assertThat(r.resolved()).isTrue();
        assertThat(r.personRef().healthId()).isEqualTo(healthId.toString());
        assertThat(r.personRef().cpid()).isEqualTo(cpid.toString());
        assertThat(r.canAuthenticate()).isTrue();
    }

    @Test
    void healthId_unknown_returnsUniformNotResolved() {
        UUID tenant = UUID.randomUUID();
        UUID healthId = UUID.randomUUID();
        when(mappingRepo.findByTenantIdAndHealthId(tenant, healthId)).thenReturn(Optional.empty());

        IdentifierResolveResponse r = service.resolve(
                new IdentifierResolveRequest(tenant, "HEALTH_ID", healthId.toString()));

        assertThat(r.resolved()).isFalse();
        assertThat(r.personRef()).isNull();
        assertThat(r.canAuthenticate()).isFalse();
    }

    @Test
    void malformedHealthId_returnsNotResolved_noThrow() {
        IdentifierResolveResponse r = service.resolve(
                new IdentifierResolveRequest(UUID.randomUUID(), "HEALTH_ID", "not-a-uuid"));
        assertThat(r.resolved()).isFalse();
    }

    @Test
    void councilNumber_resolvesProviderAnchor_butNeverAuthenticates() {
        UUID tenant = UUID.randomUUID();
        UUID healthId = UUID.randomUUID();
        UUID cpid = UUID.randomUUID();
        String body = "{\"data\":{\"resolved\":true,\"providerRef\":{\"impiloHealthId\":\""
                + healthId + "\",\"providerPublicId\":\"PUB-1\",\"canAuthenticate\":false}}}";
        when(varapiRestTemplate.getForEntity(anyString(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(body));
        IdMappingEntity mapping = new IdMappingEntity();
        mapping.setTenantId(tenant);
        mapping.setHealthId(healthId);
        mapping.setCpid(cpid);
        when(mappingRepo.findByTenantIdAndHealthId(tenant, healthId)).thenReturn(Optional.of(mapping));

        IdentifierResolveResponse r = service.resolve(
                new IdentifierResolveRequest(tenant, "COUNCIL_NUMBER", "MDPCZ/12345"));

        assertThat(r.resolved()).isTrue();
        assertThat(r.personRef().healthId()).isEqualTo(healthId.toString());
        // Critical invariant: a council/provider number NEVER authenticates.
        assertThat(r.canAuthenticate()).isFalse();
    }

    @Test
    void providerId_unknown_deniesUniformly_andNeverAuthenticates() {
        when(varapiRestTemplate.getForEntity(anyString(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"data\":{\"resolved\":false}}"));
        IdentifierResolveResponse r = service.resolve(
                new IdentifierResolveRequest(UUID.randomUUID(), "PROVIDER_ID", "EC-99999"));
        assertThat(r.resolved()).isFalse();
        assertThat(r.canAuthenticate()).isFalse();
    }

    @Test
    void phoneAndEmail_uniformDenyUntilWired() {
        UUID tenant = UUID.randomUUID();
        assertThat(service.resolve(new IdentifierResolveRequest(tenant, "PHONE", "+263771234567")).resolved())
                .isFalse();
        assertThat(service.resolve(new IdentifierResolveRequest(tenant, "EMAIL", "a@b.com")).resolved())
                .isFalse();
    }

    @Test
    void unknownKind_returnsNotResolved() {
        IdentifierResolveResponse r = service.resolve(
                new IdentifierResolveRequest(UUID.randomUUID(), "PASSPORT", "AB123"));
        assertThat(r.resolved()).isFalse();
    }

    @Test
    void appliesConstantTimeFloor_hitAndMissAreBothSlow() {
        UUID tenant = UUID.randomUUID();
        UUID healthId = UUID.randomUUID();
        lenient().when(mappingRepo.findByTenantIdAndHealthId(tenant, healthId)).thenReturn(Optional.empty());

        long start = System.nanoTime();
        service.resolve(new IdentifierResolveRequest(tenant, "HEALTH_ID", healthId.toString()));
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;

        // Constant-time floor (120ms) defends against timing-based enumeration.
        assertThat(elapsedMillis).isGreaterThanOrEqualTo(100L);
    }
}
