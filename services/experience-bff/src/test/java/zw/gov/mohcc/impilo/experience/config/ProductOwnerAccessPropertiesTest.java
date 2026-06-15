package zw.gov.mohcc.impilo.experience.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProductOwnerAccessPropertiesTest {

    @Test
    void isEffective_blocksProductionEnvironment() {
        ProductOwnerAccessProperties props = new ProductOwnerAccessProperties();
        props.setEnabled(true);
        assertFalse(props.isEffective("production"));
        assertFalse(props.isEffective("prod"));
        assertTrue(props.isEffective("preview"));
    }

    @Test
    void isAllowlisted_matchesHealthIdAndEmail() {
        ProductOwnerAccessProperties props = new ProductOwnerAccessProperties();
        assertTrue(props.isAllowlisted("b0000000-0000-4000-8000-000000000010", null));
        assertTrue(props.isAllowlisted(null, "superadmin@impilo.gov.zw"));
        assertFalse(props.isAllowlisted("other-actor", "other@example.com"));
    }

    @Test
    void pairedHealthIdForEmail_returnsHealthIdForAllowlistedPreviewEmail() {
        ProductOwnerAccessProperties props = new ProductOwnerAccessProperties();
        assertEquals(
                "b0000000-0000-4000-8000-000000000010",
                props.pairedHealthIdForEmail("superadmin@impilo.gov.zw").orElseThrow());
        assertTrue(props.pairedHealthIdForEmail("unknown@example.com").isEmpty());
    }
}
