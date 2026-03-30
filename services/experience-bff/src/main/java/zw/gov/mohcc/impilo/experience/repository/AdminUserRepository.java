package zw.gov.mohcc.impilo.experience.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.experience.domain.AdminUser;

import java.util.Optional;
import java.util.UUID;

public interface AdminUserRepository extends JpaRepository<AdminUser, UUID> {

    @Query("""
        SELECT u FROM AdminUser u
        WHERE u.tenantId = :tenantId
        AND (:role IS NULL OR u.role = :role)
        AND (:status IS NULL OR u.status = :status)
        AND (:pattern IS NULL OR LOWER(u.username) LIKE :pattern
             OR LOWER(u.email) LIKE :pattern
             OR LOWER(u.displayName) LIKE :pattern)
        """)
    Page<AdminUser> findByFilters(
            @Param("tenantId") String tenantId,
            @Param("role") String role,
            @Param("status") String status,
            @Param("pattern") String pattern,
            Pageable pageable);

    Optional<AdminUser> findByIdAndTenantId(UUID id, String tenantId);
}
