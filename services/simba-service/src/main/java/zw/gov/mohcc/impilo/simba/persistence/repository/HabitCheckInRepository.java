package zw.gov.mohcc.impilo.simba.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.simba.persistence.entity.HabitCheckInEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HabitCheckInRepository extends JpaRepository<HabitCheckInEntity, Long> {

    List<HabitCheckInEntity> findByTenantIdAndPersonCpidOrderByCheckInDateDesc(UUID tenantId, String personCpid);

    Optional<HabitCheckInEntity> findByTenantIdAndPersonCpidAndHabitCodeAndCheckInDate(
            UUID tenantId, String personCpid, String habitCode, LocalDate checkInDate);
}
