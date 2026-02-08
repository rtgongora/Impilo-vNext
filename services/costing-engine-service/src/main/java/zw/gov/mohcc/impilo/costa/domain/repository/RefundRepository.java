package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.RefundEntity;

import java.util.List;

@Repository
public interface RefundRepository extends JpaRepository<RefundEntity, Long> {
    List<RefundEntity> findByBillId(String billId);
}
