package zw.gov.mohcc.impilo.coverage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.coverage.domain.AuthorisationLineEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuthorisationLineRepository extends JpaRepository<AuthorisationLineEntity, UUID> {
    List<AuthorisationLineEntity> findByAuthorisationId(UUID authorisationId);
}
