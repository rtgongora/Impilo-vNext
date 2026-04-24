package zw.gov.mohcc.impilo.msikaflow.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.LogisticsProviderEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface LogisticsProviderRepository extends JpaRepository<LogisticsProviderEntity, String> {
    List<LogisticsProviderEntity> findByTenantIdAndActiveOrderByUpdatedAtDesc(UUID tenantId, boolean active);
}

