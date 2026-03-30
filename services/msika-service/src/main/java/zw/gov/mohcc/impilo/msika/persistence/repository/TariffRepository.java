package zw.gov.mohcc.impilo.msika.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.msika.persistence.entity.TariffEntity;

import java.util.Optional;
import java.util.UUID;

public interface TariffRepository extends JpaRepository<TariffEntity, String> {
    Optional<TariffEntity> findByTariffCode(String tariffCode);
    Page<TariffEntity> findByStatus(String status, Pageable pageable);
    Page<TariffEntity> findByTenantIdOrTenantIdIsNull(UUID tenantId, Pageable pageable);
}
