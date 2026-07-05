package zw.gov.mohcc.impilo.governance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlatformOriginRepository extends JpaRepository<PlatformOriginEntity, UUID> {

    /** The singleton root record (at most one row exists, guarded by the unique singleton column). */
    Optional<PlatformOriginEntity> findFirstByOrderByCreatedAtAsc();
}
