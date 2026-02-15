package zw.gov.mohcc.impilo.secharden.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.secharden.persistence.entity.ScanResultEntity;

@Repository
public interface ScanResultRepository extends JpaRepository<ScanResultEntity, Long> {

    List<ScanResultEntity> findByTenantId(UUID tenantId);

    List<ScanResultEntity> findByPackId(Long packId);
}
