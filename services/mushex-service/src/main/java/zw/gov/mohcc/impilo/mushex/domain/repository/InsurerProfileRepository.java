package zw.gov.mohcc.impilo.mushex.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.mushex.domain.entity.InsurerProfileEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface InsurerProfileRepository extends JpaRepository<InsurerProfileEntity, String> {

    List<InsurerProfileEntity> findByTenantIdAndEnabled(UUID tenantId, boolean enabled);

    List<InsurerProfileEntity> findByTenantId(UUID tenantId);
}
