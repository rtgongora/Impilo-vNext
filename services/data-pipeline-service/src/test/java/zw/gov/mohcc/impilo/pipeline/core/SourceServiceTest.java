package zw.gov.mohcc.impilo.pipeline.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.pipeline.api.dto.CreateSourceRequest;
import zw.gov.mohcc.impilo.pipeline.domain.SourceType;
import zw.gov.mohcc.impilo.pipeline.persistence.entity.IngestionSourceEntity;
import zw.gov.mohcc.impilo.pipeline.persistence.repository.IngestionSourceRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SourceService}.
 */
@ExtendWith(MockitoExtension.class)
class SourceServiceTest {

    @Mock private IngestionSourceRepository sourceRepository;

    private SourceService sourceService;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        sourceService = new SourceService(sourceRepository);
    }

    @Nested
    @DisplayName("Source Creation")
    class SourceCreation {

        @Test
        @DisplayName("Creates a new source with correct fields")
        void createsNewSource() {
            CreateSourceRequest request = new CreateSourceRequest(
                    "pct-service", "PCT Service", "Patient Care Tracker", "SERVICE");

            when(sourceRepository.findByTenantIdAndSourceId(TENANT_ID, "pct-service"))
                    .thenReturn(Optional.empty());
            when(sourceRepository.save(any(IngestionSourceEntity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            IngestionSourceEntity result = sourceService.createSource(request, TENANT_ID);

            assertThat(result.getSourceId()).isEqualTo("pct-service");
            assertThat(result.getTenantId()).isEqualTo(TENANT_ID);
            assertThat(result.getDisplayName()).isEqualTo("PCT Service");
            assertThat(result.getSourceType()).isEqualTo(SourceType.SERVICE);
            assertThat(result.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("Defaults to SERVICE type when sourceType is null")
        void defaultsToServiceType() {
            CreateSourceRequest request = new CreateSourceRequest(
                    "test-source", "Test", null, null);

            when(sourceRepository.findByTenantIdAndSourceId(TENANT_ID, "test-source"))
                    .thenReturn(Optional.empty());
            when(sourceRepository.save(any(IngestionSourceEntity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            IngestionSourceEntity result = sourceService.createSource(request, TENANT_ID);

            assertThat(result.getSourceType()).isEqualTo(SourceType.SERVICE);
        }

        @Test
        @DisplayName("Throws when source already exists")
        void throwsOnDuplicateSource() {
            CreateSourceRequest request = new CreateSourceRequest(
                    "pct-service", "PCT", null, "SERVICE");

            IngestionSourceEntity existing = new IngestionSourceEntity();
            existing.setSourceId("pct-service");
            when(sourceRepository.findByTenantIdAndSourceId(TENANT_ID, "pct-service"))
                    .thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> sourceService.createSource(request, TENANT_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists");
        }
    }

    @Nested
    @DisplayName("Source Listing")
    class SourceListing {

        @Test
        @DisplayName("Returns all sources for a tenant")
        void returnsAllSources() {
            IngestionSourceEntity s1 = new IngestionSourceEntity();
            s1.setSourceId("pct-service");
            IngestionSourceEntity s2 = new IngestionSourceEntity();
            s2.setSourceId("oros-service");

            when(sourceRepository.findByTenantId(TENANT_ID))
                    .thenReturn(List.of(s1, s2));

            List<IngestionSourceEntity> result = sourceService.listSources(TENANT_ID);

            assertThat(result).hasSize(2);
        }
    }
}
