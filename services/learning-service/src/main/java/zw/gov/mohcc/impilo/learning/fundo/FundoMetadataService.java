package zw.gov.mohcc.impilo.learning.fundo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.learning.persistence.entity.FundoLanguageOptionEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.FundoLanguageOptionRepository;

/** Read-side metadata service for native Fundo authoring options. */
@Service
public class FundoMetadataService {

    private final FundoLanguageOptionRepository languageRepository;

    public FundoMetadataService(FundoLanguageOptionRepository languageRepository) {
        this.languageRepository = languageRepository;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> languageOptions() {
        return languageRepository.findByActiveTrueOrderBySortOrderAscLabelAsc().stream()
                .map(FundoMetadataService::toLanguageView)
                .toList();
    }

    private static Map<String, Object> toLanguageView(FundoLanguageOptionEntity row) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("code", row.getCode());
        view.put("label", row.getLabel());
        view.put("nativeLabel", row.getNativeLabel());
        return view;
    }
}
