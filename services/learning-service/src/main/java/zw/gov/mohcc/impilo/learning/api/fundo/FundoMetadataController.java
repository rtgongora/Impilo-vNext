package zw.gov.mohcc.impilo.learning.api.fundo;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.learning.fundo.FundoMetadataService;

/** Metadata options for native Fundo authoring forms. */
@RestController
@RequestMapping("/internal/v1/learning/fundo/metadata")
public class FundoMetadataController {

    private final FundoMetadataService metadataService;

    public FundoMetadataController(FundoMetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @GetMapping("/languages")
    public ResponseEntity<Map<String, Object>> languages() {
        return FundoApiSupport.dataEnvelope(Map.of("items", metadataService.languageOptions()));
    }
}
