package zw.gov.mohcc.impilo.tshepo.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BiometricPolicyRuleRepository extends JpaRepository<BiometricPolicyRuleEntity, Long> {

    List<BiometricPolicyRuleEntity> findByActiveFlagTrueOrderByPriorityDesc();
}
