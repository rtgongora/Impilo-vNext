package zw.gov.mohcc.impilo.msika.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.msika.api.dto.FulfillmentPolicyDtos;
import zw.gov.mohcc.impilo.msika.api.dto.OfferingDtos;
import zw.gov.mohcc.impilo.msika.persistence.entity.ListingEntity;
import zw.gov.mohcc.impilo.msika.persistence.entity.OfferingEntity;
import zw.gov.mohcc.impilo.msika.persistence.repository.CatalogItemRepository;
import zw.gov.mohcc.impilo.msika.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.msika.persistence.repository.ListingRepository;
import zw.gov.mohcc.impilo.msika.persistence.repository.OfferingRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfferingServiceTest {

    @Mock private OfferingRepository offeringRepository;
    @Mock private CatalogItemRepository itemRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private FulfillmentPolicyService fulfillmentPolicyService;
    @Mock private EventOutboxRepository outboxRepository;

    private OfferingService offeringService;

    @BeforeEach
    void setUp() {
        offeringService = new OfferingService(offeringRepository, itemRepository, listingRepository,
                fulfillmentPolicyService, outboxRepository, new ObjectMapper());
    }

    private OfferingEntity offering(String id, String itemId) {
        OfferingEntity e = new OfferingEntity();
        e.setOfferingId(id);
        e.setCatalogItemId(itemId);
        return e;
    }

    @Test
    void resolve_byListing_returnsOfferingAndOfferingLevelPolicy() {
        ListingEntity listing = new ListingEntity();
        listing.setListingId("L1");
        listing.setOfferingId("OFF1");
        when(listingRepository.findById("L1")).thenReturn(Optional.of(listing));
        when(offeringRepository.findById("OFF1")).thenReturn(Optional.of(offering("OFF1", "ITEM1")));
        FulfillmentPolicyDtos.FulfillmentPolicyView policy = mock(FulfillmentPolicyDtos.FulfillmentPolicyView.class);
        when(fulfillmentPolicyService.listForOffering("OFF1", true)).thenReturn(List.of(policy));

        OfferingDtos.ResolvedOfferingView resolved = offeringService.resolve(null, "L1");
        assertEquals("OFF1", resolved.offering().offeringId());
        assertSame(policy, resolved.fulfillmentPolicy());
    }

    @Test
    void resolve_byListing_fallsBackToItemLevelPolicy() {
        ListingEntity listing = new ListingEntity();
        listing.setListingId("L1");
        listing.setOfferingId("OFF1");
        when(listingRepository.findById("L1")).thenReturn(Optional.of(listing));
        when(offeringRepository.findById("OFF1")).thenReturn(Optional.of(offering("OFF1", "ITEM1")));
        when(fulfillmentPolicyService.listForOffering("OFF1", true)).thenReturn(List.of());
        FulfillmentPolicyDtos.FulfillmentPolicyView itemPolicy = mock(FulfillmentPolicyDtos.FulfillmentPolicyView.class);
        when(fulfillmentPolicyService.listForItem("ITEM1", true)).thenReturn(List.of(itemPolicy));

        OfferingDtos.ResolvedOfferingView resolved = offeringService.resolve(null, "L1");
        assertSame(itemPolicy, resolved.fulfillmentPolicy());
    }

    @Test
    void resolve_byListing_noPolicyAnywhere_returnsNullPolicy() {
        ListingEntity listing = new ListingEntity();
        listing.setListingId("L1");
        listing.setOfferingId("OFF1");
        when(listingRepository.findById("L1")).thenReturn(Optional.of(listing));
        when(offeringRepository.findById("OFF1")).thenReturn(Optional.of(offering("OFF1", "ITEM1")));
        when(fulfillmentPolicyService.listForOffering("OFF1", true)).thenReturn(List.of());
        when(fulfillmentPolicyService.listForItem("ITEM1", true)).thenReturn(List.of());

        OfferingDtos.ResolvedOfferingView resolved = offeringService.resolve(null, "L1");
        assertNotNull(resolved.offering());
        assertNull(resolved.fulfillmentPolicy());
    }

    @Test
    void resolve_byListing_withoutOfferingAttached_is404() {
        ListingEntity listing = new ListingEntity();
        listing.setListingId("L1");
        when(listingRepository.findById("L1")).thenReturn(Optional.of(listing));
        assertThrows(IllegalArgumentException.class, () -> offeringService.resolve(null, "L1"));
    }

    @Test
    void resolve_byCatalogItem_picksFirstActiveOffering() {
        when(offeringRepository.findByCatalogItemIdAndActiveOrderByUpdatedAtDesc("ITEM1", true))
                .thenReturn(List.of(offering("OFF2", "ITEM1"), offering("OFF3", "ITEM1")));
        when(fulfillmentPolicyService.listForOffering("OFF2", true)).thenReturn(List.of());
        when(fulfillmentPolicyService.listForItem("ITEM1", true)).thenReturn(List.of());

        OfferingDtos.ResolvedOfferingView resolved = offeringService.resolve("ITEM1", null);
        assertEquals("OFF2", resolved.offering().offeringId());
    }

    @Test
    void resolve_byCatalogItem_noneActive_is404() {
        when(offeringRepository.findByCatalogItemIdAndActiveOrderByUpdatedAtDesc("ITEM1", true))
                .thenReturn(List.of());
        assertThrows(IllegalArgumentException.class, () -> offeringService.resolve("ITEM1", null));
    }

    @Test
    void resolve_withoutAnyIdentifier_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> offeringService.resolve(null, null));
    }
}
