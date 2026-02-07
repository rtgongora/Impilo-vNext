package zw.gov.mohcc.impilo.oros.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.oros.persistence.entity.IdempotencyEntity;

/**
 * Repository for {@link IdempotencyEntity} persistence operations.
 * The primary key is the idempotency key string itself.
 */
@Repository
public interface IdempotencyRepository extends JpaRepository<IdempotencyEntity, String> {
}
