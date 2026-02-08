package zw.gov.mohcc.impilo.mushex.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.mushex.domain.entity.RefundEntity;

import java.util.List;

@Repository
public interface RefundRepository extends JpaRepository<RefundEntity, String> {

    List<RefundEntity> findByIntentId(String intentId);
}
