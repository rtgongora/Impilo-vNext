package zw.gov.mohcc.impilo.mushex.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.mushex.domain.entity.PayoutItemEntity;

import java.util.List;

@Repository
public interface PayoutItemRepository extends JpaRepository<PayoutItemEntity, String> {

    List<PayoutItemEntity> findByBatchId(String batchId);
}
