package zw.gov.mohcc.impilo.governance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BootstrapAccountRepository extends JpaRepository<BootstrapAccountEntity, UUID> {}
