package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.BillLineEntity;

import java.util.List;

@Repository
public interface BillLineRepository extends JpaRepository<BillLineEntity, String> {
    List<BillLineEntity> findByBillIdAndVoidedFalse(String billId);
    List<BillLineEntity> findByBillId(String billId);
    List<BillLineEntity> findByBillIdAndSourceRef(String billId, String sourceRef);
}
