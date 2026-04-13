package zw.gov.mohcc.impilo.gl.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.gl.persistence.entity.JournalEntryEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntryEntity, UUID> {

    List<JournalEntryEntity> findByTenantIdAndFiscalPeriodIdOrderByEntryDateDesc(UUID tenantId, UUID fiscalPeriodId);

    Optional<JournalEntryEntity> findByTenantIdAndSourceModuleAndSourceRef(
            UUID tenantId, String sourceModule, String sourceRef);
}
