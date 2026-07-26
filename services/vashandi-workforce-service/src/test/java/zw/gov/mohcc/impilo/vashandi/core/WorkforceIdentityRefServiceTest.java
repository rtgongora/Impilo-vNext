package zw.gov.mohcc.impilo.vashandi.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceProfileEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceProfileRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The join key, and only the join key. This lookup exists so the composition layer can ask the
 * identity registries who is on the rota; if it ever starts returning names, vashandi has become
 * the second identity store the staffing surface was built to avoid.
 */
@ExtendWith(MockitoExtension.class)
class WorkforceIdentityRefServiceTest {

    @Mock private WorkforceProfileRepository profileRepository;

    private WorkforceIdentityRefService service;

    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new WorkforceIdentityRefService(profileRepository);
    }

    private static WorkforceProfileEntity profile(UUID id, String healthId, String workerId) {
        WorkforceProfileEntity p = new WorkforceProfileEntity();
        p.setId(id);
        p.setHealthId(healthId);
        p.setProviderWorkerId(workerId);
        return p;
    }

    @Test
    void mapsProfileIdsToThePersonAnchor() {
        UUID profileId = UUID.randomUUID();
        String healthId = UUID.randomUUID().toString();
        when(profileRepository.findByTenantIdAndIdIn(eq(tenantId), any()))
                .thenReturn(List.of(profile(profileId, healthId, "EC-4471")));

        List<Map<String, Object>> rows = service.identityRefs(tenantId, profileId.toString());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("workforce_profile_id", profileId.toString())
                .containsEntry("health_id", healthId)
                .containsEntry("provider_worker_id", "EC-4471");
    }

    @Test
    void carriesNoNameAndNoTelephoneNumber() {
        UUID profileId = UUID.randomUUID();
        when(profileRepository.findByTenantIdAndIdIn(eq(tenantId), any()))
                .thenReturn(List.of(profile(profileId, UUID.randomUUID().toString(), "EC-4471")));

        List<Map<String, Object>> rows = service.identityRefs(tenantId, profileId.toString());

        assertThat(rows.get(0).keySet())
                .containsExactly("workforce_profile_id", "health_id", "provider_worker_id");
    }

    @Test
    void unknownAndMalformedIdsAreSimplyAbsent() {
        UUID known = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();
        when(profileRepository.findByTenantIdAndIdIn(eq(tenantId), any()))
                .thenReturn(List.of(profile(known, UUID.randomUUID().toString(), "EC-1")));

        List<Map<String, Object>> rows = service.identityRefs(
                tenantId, known + "," + unknown + ",not-a-uuid");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("workforce_profile_id", known.toString());
    }

    @Test
    void anEmptyOrAllMalformedRequestAsksTheDatabaseNothing() {
        assertThat(service.identityRefs(tenantId, null)).isEmpty();
        assertThat(service.identityRefs(tenantId, "  ")).isEmpty();
        assertThat(service.identityRefs(tenantId, "nonsense,,also-nonsense")).isEmpty();
        verifyNoInteractions(profileRepository);
    }

    @Test
    void refusesAnUnboundedBatch() {
        String tooMany = java.util.stream.IntStream.range(0, WorkforceIdentityRefService.MAX_BATCH + 1)
                .mapToObj(i -> UUID.randomUUID().toString())
                .collect(java.util.stream.Collectors.joining(","));

        assertThatThrownBy(() -> service.identityRefs(tenantId, tooMany))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("profile ids");
    }
}
