package zw.gov.mohcc.impilo.inventory.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.inventory.persistence.entity.CommodityCategoryEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ItemEntity;
import zw.gov.mohcc.impilo.inventory.persistence.repository.CommodityCategoryRepository;
import zw.gov.mohcc.impilo.inventory.persistence.repository.ItemRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CommodityCatalogueServiceImpl}.
 */
@ExtendWith(MockitoExtension.class)
class CommodityCatalogueServiceTest {

    @Mock
    private CommodityCategoryRepository categoryRepository;
    @Mock
    private ItemRepository itemRepository;
    @InjectMocks
    private CommodityCatalogueServiceImpl service;

    private static final UUID TENANT = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TrustContextHolder.set(new TrustContext(
                TENANT, "actor-1", "STAFF", "TREATMENT", null,
                UUID.randomUUID(), null, null, null, null));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    @Test
    @DisplayName("createCategory persists a tenant-scoped category")
    void createCategory_persists() {
        when(categoryRepository.existsByTenantIdAndCode(TENANT, "VACCINES")).thenReturn(false);
        when(categoryRepository.save(any(CommodityCategoryEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        CommodityCategoryEntity result = service.createCategory(
                "VACCINES", "Vaccines & Biologics", null, "EPI", "Cold-chain biologics");

        ArgumentCaptor<CommodityCategoryEntity> captor = ArgumentCaptor.forClass(CommodityCategoryEntity.class);
        verify(categoryRepository).save(captor.capture());
        CommodityCategoryEntity saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(TENANT);
        assertThat(saved.getCode()).isEqualTo("VACCINES");
        assertThat(saved.getProgrammeArea()).isEqualTo("EPI");
        assertThat(result).isSameAs(saved);
    }

    @Test
    @DisplayName("createCategory rejects duplicate code for tenant")
    void createCategory_duplicateRejected() {
        when(categoryRepository.existsByTenantIdAndCode(TENANT, "VACCINES")).thenReturn(true);

        assertThatThrownBy(() -> service.createCategory("VACCINES", "Vaccines", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("createCategory rejects blank code")
    void createCategory_blankCodeRejected() {
        assertThatThrownBy(() -> service.createCategory("  ", "Name", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("code is required");
    }

    @Test
    @DisplayName("listCategories filters by programme area when provided")
    void listCategories_byProgramme() {
        when(categoryRepository.findByTenantIdAndProgrammeAreaOrderByName(TENANT, "EPI"))
                .thenReturn(List.of(new CommodityCategoryEntity()));

        List<CommodityCategoryEntity> result = service.listCategories("EPI");

        assertThat(result).hasSize(1);
        verify(categoryRepository).findByTenantIdAndProgrammeAreaOrderByName(TENANT, "EPI");
        verify(categoryRepository, never()).findByTenantIdOrderByName(any());
    }

    @Test
    @DisplayName("listCategories returns all when no programme area")
    void listCategories_all() {
        when(categoryRepository.findByTenantIdOrderByName(TENANT)).thenReturn(List.of());
        service.listCategories(null);
        verify(categoryRepository).findByTenantIdOrderByName(TENANT);
    }

    @Test
    @DisplayName("searchCommodities normalises blank query to null and delegates")
    void searchCommodities_delegates() {
        when(itemRepository.searchCommodities(eq(TENANT), isNull(), isNull(), isNull(), isNull(), eq(Boolean.TRUE)))
                .thenReturn(List.of(new ItemEntity()));

        List<ItemEntity> result = service.searchCommodities("   ", null, null, null, true);

        assertThat(result).hasSize(1);
        verify(itemRepository).searchCommodities(TENANT, null, null, null, null, Boolean.TRUE);
    }

    @Test
    @DisplayName("updateCommodityAttributes applies only non-null fields")
    void updateCommodityAttributes_appliesNonNull() {
        ItemEntity item = new ItemEntity();
        item.setTenantId(TENANT);
        item.setItemCode("BCG-VAC");
        item.setColdChainRequired(false);
        when(itemRepository.findByTenantIdAndItemCode(TENANT, "BCG-VAC")).thenReturn(Optional.of(item));
        when(itemRepository.save(any(ItemEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        CommodityCatalogueService.CommodityAttributes attrs =
                new CommodityCatalogueService.CommodityAttributes(
                        null, null, "Bacillus Calmette-Guérin", null, null, null, null, null, null,
                        "EPI", true, 2.0, 8.0, true, true, false, null, false, null, "12345678", null);

        ItemEntity result = service.updateCommodityAttributes("BCG-VAC", attrs);

        assertThat(result.getGenericName()).isEqualTo("Bacillus Calmette-Guérin");
        assertThat(result.getProgrammeArea()).isEqualTo("EPI");
        assertThat(result.isColdChainRequired()).isTrue();
        assertThat(result.getTemperatureMin()).isEqualTo(2.0);
        assertThat(result.getTemperatureMax()).isEqualTo(8.0);
        assertThat(result.isBatchTrackingRequired()).isTrue();
        assertThat(result.getGtin()).isEqualTo("12345678");
        // brandName left null (not supplied)
        assertThat(result.getBrandName()).isNull();
        verify(itemRepository).save(item);
    }

    @Test
    @DisplayName("updateCommodityAttributes throws when commodity missing")
    void updateCommodityAttributes_missing() {
        when(itemRepository.findByTenantIdAndItemCode(TENANT, "NOPE")).thenReturn(Optional.empty());
        CommodityCatalogueService.CommodityAttributes attrs =
                new CommodityCatalogueService.CommodityAttributes(
                        null, null, null, null, null, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.updateCommodityAttributes("NOPE", attrs))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }
}
