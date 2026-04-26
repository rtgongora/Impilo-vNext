package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.CostaTariffListEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CostaTariffListRepository extends JpaRepository<CostaTariffListEntity, Long> {

    @Query("SELECT t FROM CostaTariffListEntity t WHERE t.tenantId IS NULL OR t.tenantId = :tenantId ORDER BY t.name")
    List<CostaTariffListEntity> findVisibleForTenant(@Param("tenantId") UUID tenantId);

    @Query("SELECT t FROM CostaTariffListEntity t WHERE t.id = :id AND (t.tenantId IS NULL OR t.tenantId = :tenantId)")
    Optional<CostaTariffListEntity> findByIdForTenant(@Param("id") Long id, @Param("tenantId") UUID tenantId);

    Optional<CostaTariffListEntity> findFirstByExternalCodeAndTenantIdIsNull(String externalCode);
}
