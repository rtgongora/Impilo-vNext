package zw.gov.mohcc.impilo.mushex.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.mushex.domain.entity.LedgerEntryEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntryEntity, String> {

    Page<LedgerEntryEntity> findByTenantId(UUID tenantId, Pageable pageable);

    List<LedgerEntryEntity> findByIntentId(String intentId);
}
