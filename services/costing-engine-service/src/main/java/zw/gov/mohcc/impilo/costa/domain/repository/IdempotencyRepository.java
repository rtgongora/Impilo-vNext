package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.IdempotencyEntity;

@Repository
public interface IdempotencyRepository extends JpaRepository<IdempotencyEntity, String> {
}
