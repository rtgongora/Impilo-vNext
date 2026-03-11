package zw.gov.mohcc.impilo.experience.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.experience.domain.Patient;

import java.util.Optional;
import java.util.UUID;

public interface PatientRepository extends JpaRepository<Patient, UUID> {

    @Query("""
        SELECT p FROM Patient p
        WHERE p.tenantId = :tenantId
        AND (:status IS NULL OR p.status = :status)
        AND (:search IS NULL OR LOWER(p.givenName) LIKE LOWER(CONCAT('%', :search, '%'))
             OR LOWER(p.familyName) LIKE LOWER(CONCAT('%', :search, '%'))
             OR LOWER(p.cpid) LIKE LOWER(CONCAT('%', :search, '%'))
             OR LOWER(p.nationalId) LIKE LOWER(CONCAT('%', :search, '%')))
        """)
    Page<Patient> findByFilters(
            @Param("tenantId") String tenantId,
            @Param("search") String search,
            @Param("status") String status,
            Pageable pageable);

    Optional<Patient> findByIdAndTenantId(UUID id, String tenantId);
}
