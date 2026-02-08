package zw.gov.mohcc.impilo.costa.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.costa.domain.entity.TariffEntity;
import zw.gov.mohcc.impilo.costa.domain.repository.TariffRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class TariffService {

    private static final Logger log = LoggerFactory.getLogger(TariffService.class);

    private final TariffRepository tariffRepository;

    public TariffService(TariffRepository tariffRepository) {
        this.tariffRepository = tariffRepository;
    }

    public Page<TariffEntity> listTariffs(UUID tenantId, Pageable pageable) {
        return tariffRepository.findByTenantIdAndStatus(tenantId, "ACTIVE", pageable);
    }

    @Transactional
    public List<TariffEntity> importCsv(InputStream csvStream) {
        TrustContext ctx = TrustContextHolder.require();
        List<TariffEntity> imported = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csvStream))) {
            String header = reader.readLine(); // Skip header
            if (header == null) return imported;

            String line;
            while ((line = reader.readLine()) != null) {
                String[] cols = line.split(",", -1);
                if (cols.length < 5) continue;

                TariffEntity tariff = new TariffEntity();
                tariff.setTenantId(ctx.tenantId());
                tariff.setTariffCode(cols[0].trim());
                tariff.setMsikaCode(cols[1].trim().isEmpty() ? null : cols[1].trim());
                tariff.setDescription(cols[2].trim());
                tariff.setPrice(new BigDecimal(cols[3].trim()));
                tariff.setCurrency(cols.length > 4 && !cols[4].trim().isEmpty() ? cols[4].trim() : "USD");
                tariff.setFacilityCategory(cols.length > 5 && !cols[5].trim().isEmpty() ? cols[5].trim() : null);
                tariff.setPatientCategory(cols.length > 6 && !cols[6].trim().isEmpty() ? cols[6].trim() : null);
                tariff.setEffectiveFrom(cols.length > 7 && !cols[7].trim().isEmpty()
                        ? LocalDate.parse(cols[7].trim()) : LocalDate.now());

                tariff = tariffRepository.save(tariff);
                imported.add(tariff);
            }
        } catch (Exception e) {
            log.error("CSV import failed", e);
            throw new RuntimeException("CSV import failed: " + e.getMessage(), e);
        }

        log.info("Imported {} tariffs for tenant {}", imported.size(), ctx.tenantId());
        return imported;
    }
}
