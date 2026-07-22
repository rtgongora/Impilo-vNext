package zw.gov.mohcc.impilo.orgregistry.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.AppointmentRoleEntity;

import java.util.List;

public interface AppointmentRoleRepository extends JpaRepository<AppointmentRoleEntity, String> {
    List<AppointmentRoleEntity> findAllByOrderBySortOrderAsc();
}
