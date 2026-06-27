package zw.gov.mohcc.impilo.oros.core;

import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Reserves RIS-style accession numbers for diagnostic/imaging orders.
 *
 * <p>Format: {@code ACC-{year}-{facility8}-{seq6}} — e.g. {@code ACC-2026-AB12CD34-000042}.
 * Facility-scoped and year-scoped, sequential within (tenant, facility, year) via
 * {@link AccessionSequenceAllocator}. The (tenant, accession_number) pair is unique
 * (enforced by {@code uq_oros_orders_accession}).</p>
 */
@Service
public class AccessionNumberService {

    private final AccessionSequenceAllocator allocator;

    public AccessionNumberService(AccessionSequenceAllocator allocator) {
        this.allocator = allocator;
    }

    /** Reserve the next accession number for the given facility, using the current UTC year. */
    public String reserve(UUID tenantId, UUID facilityId) {
        return reserve(tenantId, facilityId, OffsetDateTime.now(ZoneOffset.UTC).getYear());
    }

    /** Reserve the next accession number for an explicit year (deterministic for tests). */
    public String reserve(UUID tenantId, UUID facilityId, int year) {
        long seq = allocator.nextSequence(tenantId, facilityId, year);
        return format(facilityId, year, seq);
    }

    static String format(UUID facilityId, int year, long seq) {
        String fac8 = facilityId.toString().replace("-", "").substring(0, 8).toUpperCase();
        return String.format("ACC-%d-%s-%06d", year, fac8, seq);
    }
}
