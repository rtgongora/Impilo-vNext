package zw.gov.mohcc.impilo.msikaflow.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.CustodyRecordEntity;

import java.util.List;

@Repository
public interface CustodyRecordRepository extends JpaRepository<CustodyRecordEntity, String> {
    List<CustodyRecordEntity> findByFulfillmentIdOrderByFromAtDesc(String fulfillmentId);
}

