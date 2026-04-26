package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.ChargeSheetItemEntity;

import java.util.List;

@Repository
public interface ChargeSheetItemRepository extends JpaRepository<ChargeSheetItemEntity, String> {

    List<ChargeSheetItemEntity> findByChargeSheetIdOrderByCreatedAtAsc(String chargeSheetId);
}
