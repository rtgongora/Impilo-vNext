package zw.gov.mohcc.impilo.tshepo.identity.core;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Generates CPIDs (Clinical Patient Identifiers).
 *
 * <p>Per the Identity Contract (docs/architecture/identity-trust-contract.md §1, §7),
 * a CPID is an <b>independent random UUID v4</b> — never derived from the Health ID,
 * CRID, tenant, or any other identifier. The only Health&nbsp;ID ↔ CPID link is the
 * {@code tshepo_identity.id_mapping} row, and uniqueness is guaranteed by the
 * database constraints, not by the generation scheme.</p>
 *
 * <h3>Canonical CPID</h3>
 * <p>Random UUID v4, persisted via {@code id_mapping}. Idempotency is provided by
 * the mapping table (unique on {@code (tenant_id, health_id)}), not by determinism.</p>
 *
 * <h3>Provisional O-CPID</h3>
 * <p>Random UUID v4 generated for offline use, tracked in {@code provisional_cpid}
 * and reconciled to a canonical CPID once the facility reconnects.</p>
 */
@Component
public class CpidGenerator {

    /**
     * Generate a new canonical CPID: an independent random UUID v4.
     *
     * <p>Callers must persist the value through the {@code id_mapping} table
     * (see {@code IdResolutionService.findOrCreateMapping}); the raw value is
     * meaningless until mapped.</p>
     */
    public UUID generateCpid() {
        return UUID.randomUUID();
    }

    /**
     * Generate a random provisional O-CPID (UUID v4).
     *
     * <p>The "O-" prefix convention is tracked at the database level in the
     * provisional_cpid table, not embedded in the UUID itself.</p>
     */
    public UUID generateProvisionalCpid() {
        return UUID.randomUUID();
    }
}
