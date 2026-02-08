package zw.gov.mohcc.impilo.mushex.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.mushex.domain.entity.ReceiptEntity;

import java.util.Optional;

@Repository
public interface ReceiptRepository extends JpaRepository<ReceiptEntity, String> {

    Optional<ReceiptEntity> findByIntentId(String intentId);
}
