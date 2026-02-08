package zw.gov.mohcc.impilo.mushex.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.mushex.domain.entity.PaymentAttemptEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttemptEntity, String> {

    List<PaymentAttemptEntity> findByIntentId(String intentId);

    Optional<PaymentAttemptEntity> findByAdapterRef(String adapterRef);
}
