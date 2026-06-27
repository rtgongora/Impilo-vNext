package zw.gov.mohcc.impilo.dags.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.dags.persistence.entity.PermitReplayEntity;

@Repository
public interface PermitReplayRepository extends JpaRepository<PermitReplayEntity, Long> {

    boolean existsByNonce(String nonce);
}
