package zw.gov.mohcc.impilo.datagovernance.core;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TenantKeysTest {

    @Test
    void uuidTenantsParseUnchanged() {
        UUID raw = UUID.randomUUID();
        assertEquals(raw, TenantKeys.tenantUuid(raw.toString()));
    }

    @Test
    void canonicalStringTenantsMapDeterministically() {
        assertEquals(TenantKeys.tenantUuid("moh-zw"), TenantKeys.tenantUuid("moh-zw"));
        assertNotEquals(TenantKeys.tenantUuid("moh-zw"), TenantKeys.tenantUuid("default"));
    }

    @Test
    void blankTenantRejected() {
        assertThrows(IllegalArgumentException.class, () -> TenantKeys.tenantUuid(" "));
        assertThrows(IllegalArgumentException.class, () -> TenantKeys.tenantUuid(null));
    }
}
