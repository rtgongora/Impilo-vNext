package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.TenantConfigDefaultEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantConfigDefaultRepository extends JpaRepository<TenantConfigDefaultEntity, Long> {

    Optional<TenantConfigDefaultEntity> findByTenantId(UUID tenantId);
}
