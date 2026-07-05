package zw.gov.mohcc.impilo.governance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NationalAppointmentRepository extends JpaRepository<NationalAppointmentEntity, UUID> {

    List<NationalAppointmentEntity> findByCountryOperationIdOrderByAppointedAtAsc(UUID countryOperationId);

    List<NationalAppointmentEntity> findByCountryOperationIdAndStatusOrderByAppointedAtAsc(
            UUID countryOperationId, String status);

    Optional<NationalAppointmentEntity> findByIdAndCountryOperationId(UUID id, UUID countryOperationId);
}
