package zw.gov.mohcc.impilo.zibo.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.zibo.persistence.entity.MappingIndexEntity;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link MappingIndexEntity} persistence operations.
 * Provides fast cross-coding lookups from the flattened mapping index.
 */
@Repository
public interface MappingIndexRepository extends JpaRepository<MappingIndexEntity, UUID> {

    /**
     * Finds mappings by source system and code within a tenant.
     */
    List<MappingIndexEntity> findByTenantIdAndSourceSystemAndSourceCode(UUID tenantId, String sourceSystem, String sourceCode);

    /**
     * Finds reverse mappings by target system and code within a tenant.
     */
    List<MappingIndexEntity> findByTenantIdAndTargetSystemAndTargetCode(UUID tenantId, String targetSystem, String targetCode);

    /**
     * Finds all mapping entries derived from a specific ConceptMap artifact.
     */
    List<MappingIndexEntity> findByConceptmapRef(UUID conceptmapRef);

    /**
     * Deletes all mapping entries derived from a specific ConceptMap artifact.
     * Used when re-indexing a ConceptMap after update.
     */
    void deleteByConceptmapRef(UUID conceptmapRef);
}
