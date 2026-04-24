package zw.gov.mohcc.impilo.tshepo.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PatientSharePolicyRuleRepository extends JpaRepository<PatientSharePolicyRuleEntity, Long> {

    List<PatientSharePolicyRuleEntity> findByActiveFlagTrueOrderByPriorityDesc();
}
