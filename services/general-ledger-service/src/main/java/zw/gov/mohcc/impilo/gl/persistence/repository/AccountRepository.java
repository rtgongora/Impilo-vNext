package zw.gov.mohcc.impilo.gl.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.gl.persistence.entity.AccountEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {

    Optional<AccountEntity> findByTenantIdAndAccountCode(UUID tenantId, String accountCode);

    List<AccountEntity> findByTenantIdOrderByAccountCodeAsc(UUID tenantId);
}
