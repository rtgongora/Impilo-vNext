package zw.gov.mohcc.impilo.tshepo.identity.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.entity.ProvisionalCpidEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProvisionalCpidRepository extends JpaRepository<ProvisionalCpidEntity, Long> {

    Optional<ProvisionalCpidEntity> findByTenantIdAndOCpid(UUID tenantId, UUID oCpid);

    List<ProvisionalCpidEntity> findByTenantIdAndStatus(UUID tenantId, String status);
}
