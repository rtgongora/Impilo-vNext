package zw.gov.mohcc.impilo.tshepo.authz.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.FederationPodEntity;

import java.util.Optional;

@Repository
public interface FederationPodRepository extends JpaRepository<FederationPodEntity, Long> {

    Optional<FederationPodEntity> findByPodId(String podId);

    boolean existsByPodId(String podId);
}
