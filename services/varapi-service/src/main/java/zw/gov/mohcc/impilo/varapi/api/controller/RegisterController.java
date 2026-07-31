package zw.gov.mohcc.impilo.varapi.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CouncilEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.CouncilRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProfessionalRegisterRepository;

import java.util.List;
import java.util.Map;

/**
 * Register browse for the registrar/register-officer side (ROM-W3). Lists the statutory registers
 * of a council. Reached on the internal lane behind the gateway; cross-council isolation is
 * enforced at the PDP (regulatory org dimension) — this endpoint only reads the catalogue.
 */
@RestController
@RequestMapping("/v1/registry/councils")
public class RegisterController {

    private final CouncilRepository councilRepository;
    private final ProfessionalRegisterRepository registerRepository;

    public RegisterController(CouncilRepository councilRepository,
                              ProfessionalRegisterRepository registerRepository) {
        this.councilRepository = councilRepository;
        this.registerRepository = registerRepository;
    }

    @GetMapping("/{councilCode}/registers")
    public ResponseEntity<Map<String, Object>> registersForCouncil(@PathVariable String councilCode) {
        TrustContext ctx = TrustContextHolder.require();
        CouncilEntity council = councilRepository.findByCouncilCode(councilCode).orElse(null);
        if (council == null) {
            return ResponseEntity.ok(Map.of("councilCode", councilCode, "registers", List.of()));
        }
        List<Map<String, Object>> registers = registerRepository
                .findByTenantIdAndCouncilIdOrderByRegisterCodeAsc(ctx.tenantId(), council.getId()).stream()
                .map(ProfessionalRegisterViews::toOperatorView).toList();
        return ResponseEntity.ok(Map.of(
                "councilCode", council.getCouncilCode(),
                "councilName", council.getName(),
                "councilId", council.getId(),
                "registers", registers));
    }
}
