package zw.gov.mohcc.impilo.tshepo.federation.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PodRegistrationRepository extends JpaRepository<PodRegistrationEntity, Long> {

    Optional<PodRegistrationEntity> findByPodId(UUID podId);

    Optional<PodRegistrationEntity> findByRegistrationId(UUID registrationId);

    List<PodRegistrationEntity> findByStatus(PodStatus status);

    boolean existsByPodId(UUID podId);

    List<PodRegistrationEntity> findByStatusNot(PodStatus status);
}
