package zw.gov.mohcc.impilo.gl.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.gl.core.ChartOfAccountsService;
import zw.gov.mohcc.impilo.gl.persistence.entity.AccountEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/gl/accounts")
public class GlAccountsController {

    private final ChartOfAccountsService chartOfAccountsService;

    public GlAccountsController(ChartOfAccountsService chartOfAccountsService) {
        this.chartOfAccountsService = chartOfAccountsService;
    }

    @GetMapping
    public ResponseEntity<List<AccountEntity>> list() {
        UUID tenant = UUID.fromString(RequestContextHolder.require().tenantId());
        return ResponseEntity.ok(chartOfAccountsService.listAccounts(tenant));
    }

    @PostMapping("/seed")
    public ResponseEntity<Map<String, Object>> seed() {
        UUID tenant = UUID.fromString(RequestContextHolder.require().tenantId());
        int n = chartOfAccountsService.seedDefaultHealthFacilityChart(tenant);
        return ResponseEntity.ok(Map.of("seeded", n));
    }

    @PostMapping
    public ResponseEntity<AccountEntity> create(@RequestBody Map<String, Object> body) {
        UUID tenant = UUID.fromString(RequestContextHolder.require().tenantId());
        AccountEntity a = chartOfAccountsService.createAccount(
                tenant,
                (String) body.get("accountCode"),
                (String) body.get("name"),
                (String) body.get("accountType"),
                (String) body.get("normalBalance"),
                body.get("parentAccountId") != null ? UUID.fromString(body.get("parentAccountId").toString()) : null
        );
        return ResponseEntity.ok(a);
    }

    @PutMapping("/{accountId}")
    public ResponseEntity<AccountEntity> update(@PathVariable UUID accountId, @RequestBody Map<String, Object> body) {
        UUID tenant = UUID.fromString(RequestContextHolder.require().tenantId());
        return ResponseEntity.ok(chartOfAccountsService.updateAccount(
                tenant,
                accountId,
                (String) body.get("name"),
                body.get("active") == null ? null : Boolean.parseBoolean(body.get("active").toString()),
                body.get("parentAccountId") != null ? UUID.fromString(body.get("parentAccountId").toString()) : null
        ));
    }
}
