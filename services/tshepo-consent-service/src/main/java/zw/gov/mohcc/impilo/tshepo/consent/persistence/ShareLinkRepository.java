package zw.gov.mohcc.impilo.tshepo.consent.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShareLinkRepository extends JpaRepository<ShareLinkEntity, UUID> {

    /**
     * Look up a share link by its SHA-256 token hash.
     */
    Optional<ShareLinkEntity> findByTokenHash(String tokenHash);

    /**
     * Check if a token hash already exists (collision detection).
     */
    boolean existsByTokenHash(String tokenHash);
}
