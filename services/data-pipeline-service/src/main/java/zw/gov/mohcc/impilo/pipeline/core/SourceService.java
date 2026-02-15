package zw.gov.mohcc.impilo.pipeline.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pipeline.api.dto.CreateSourceRequest;
import zw.gov.mohcc.impilo.pipeline.domain.SourceType;
import zw.gov.mohcc.impilo.pipeline.persistence.entity.IngestionSourceEntity;
import zw.gov.mohcc.impilo.pipeline.persistence.repository.IngestionSourceRepository;

import java.util.List;
import java.util.UUID;

/**
 * Manages ingestion source registration and retrieval.
 */
@Service
public class SourceService {

    private static final Logger log = LoggerFactory.getLogger(SourceService.class);

    private final IngestionSourceRepository sourceRepository;

    public SourceService(IngestionSourceRepository sourceRepository) {
        this.sourceRepository = sourceRepository;
    }

    /**
     * Register a new ingestion source.
     */
    @Transactional
    public IngestionSourceEntity createSource(CreateSourceRequest request, UUID tenantId) {
        if (sourceRepository.findByTenantIdAndSourceId(tenantId, request.sourceId()).isPresent()) {
            throw new IllegalArgumentException("Source already exists: " + request.sourceId());
        }

        IngestionSourceEntity source = new IngestionSourceEntity();
        source.setSourceId(request.sourceId());
        source.setTenantId(tenantId);
        source.setDisplayName(request.displayName());
        source.setDescription(request.description());
        source.setSourceType(parseSourceType(request.sourceType()));
        source.setEnabled(true);

        sourceRepository.save(source);
        log.info("Registered ingestion source: {} (tenant={})", request.sourceId(), tenantId);
        return source;
    }

    /**
     * List all sources for a tenant.
     */
    public List<IngestionSourceEntity> listSources(UUID tenantId) {
        return sourceRepository.findByTenantId(tenantId);
    }

    private SourceType parseSourceType(String type) {
        if (type == null || type.isBlank()) {
            return SourceType.SERVICE;
        }
        try {
            return SourceType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SourceType.SERVICE;
        }
    }
}
