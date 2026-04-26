package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.CostaTariffListItemEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface CostaTariffListItemRepository extends JpaRepository<CostaTariffListItemEntity, Long> {

    List<CostaTariffListItemEntity> findByTariffListIdOrderByItemCodeAsc(long tariffListId);

    Optional<CostaTariffListItemEntity> findByTariffListIdAndItemCode(long tariffListId, String itemCode);
}
