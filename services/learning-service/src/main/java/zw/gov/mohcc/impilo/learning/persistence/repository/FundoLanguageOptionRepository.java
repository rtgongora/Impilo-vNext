package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.FundoLanguageOptionEntity;

public interface FundoLanguageOptionRepository extends JpaRepository<FundoLanguageOptionEntity, String> {
    List<FundoLanguageOptionEntity> findByActiveTrueOrderBySortOrderAscLabelAsc();
}
