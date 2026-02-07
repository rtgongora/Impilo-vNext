package zw.gov.mohcc.impilo.msika.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.msika.persistence.entity.ExternalMappingEntity;

import java.util.List;

public interface ExternalMappingRepository extends JpaRepository<ExternalMappingEntity, String> {
    Page<ExternalMappingEntity> findByStatus(String status, Pageable pageable);
    List<ExternalMappingEntity> findBySourceId(String sourceId);
    List<ExternalMappingEntity> findBySourceIdAndStatus(String sourceId, String status);
    List<ExternalMappingEntity> findByExternalCode(String externalCode);
}
