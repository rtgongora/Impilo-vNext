package zw.gov.mohcc.impilo.experience.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.experience.domain.RegistryProvider;

import java.util.Optional;
import java.util.UUID;

public interface RegistryProviderRepository extends JpaRepository<RegistryProvider, UUID> {

    @Query("""
        SELECT r FROM RegistryProvider r
        WHERE r.tenantId = :tenantId
        AND (:pattern IS NULL OR LOWER(r.givenName) LIKE :pattern
             OR LOWER(r.familyName) LIKE :pattern
             OR LOWER(r.providerNumber) LIKE :pattern)
        """)
    Page<RegistryProvider> findByFilters(
            @Param("tenantId") String tenantId,
            @Param("pattern") String pattern,
            Pageable pageable);

    Optional<RegistryProvider> findByIdAndTenantId(UUID id, String tenantId);
}
