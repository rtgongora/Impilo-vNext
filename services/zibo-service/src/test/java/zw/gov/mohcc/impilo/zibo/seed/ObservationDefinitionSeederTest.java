package zw.gov.mohcc.impilo.zibo.seed;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.zibo.core.ArtifactService;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactType;
import zw.gov.mohcc.impilo.zibo.persistence.repository.ArtifactRepository;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests {@link ObservationDefinitionSeeder} against the real committed seed resource: it idempotently
 * creates the governed DRAFT ObservationDefinitions and skips any already present.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ObservationDefinitionSeederTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void seedsAllDefinitions_whenNonePresent() {
        ArtifactRepository repo = mock(ArtifactRepository.class);
        ArtifactService service = mock(ArtifactService.class);
        when(repo.existsByTenantIdAndCanonicalUrlAndVersion(any(), anyString(), anyString())).thenReturn(false);

        var seeder = new ObservationDefinitionSeeder(service, repo, new ObjectMapper(), TENANT.toString());
        seeder.run(null);

        // The committed vitals seed has 7 ObservationDefinitions.
        verify(service, times(7)).createDraft(
                eq(ArtifactType.OBSERVATION_DEFINITION), anyString(), anyString(),
                any(), any(), any(), anyString(), any());
    }

    @Test
    void isIdempotent_skipsExisting() {
        ArtifactRepository repo = mock(ArtifactRepository.class);
        ArtifactService service = mock(ArtifactService.class);
        when(repo.existsByTenantIdAndCanonicalUrlAndVersion(any(), anyString(), anyString())).thenReturn(true);

        var seeder = new ObservationDefinitionSeeder(service, repo, new ObjectMapper(), TENANT.toString());
        seeder.run(null);

        verify(service, never()).createDraft(any(), anyString(), anyString(), any(), any(), any(), anyString(), any());
    }
}
