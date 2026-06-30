package zw.gov.mohcc.impilo.msika.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.msika.api.dto.ListingDtos;
import zw.gov.mohcc.impilo.msika.persistence.entity.CatalogItemEntity;
import zw.gov.mohcc.impilo.msika.persistence.entity.ListingEntity;
import zw.gov.mohcc.impilo.msika.persistence.repository.CatalogItemRepository;
import zw.gov.mohcc.impilo.msika.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.msika.persistence.repository.ListingMediaRepository;
import zw.gov.mohcc.impilo.msika.persistence.repository.ListingRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListingServiceTest {

    @Mock private ListingRepository listingRepository;
    @Mock private ListingMediaRepository mediaRepository;
    @Mock private CatalogItemRepository itemRepository;
    @Mock private StorefrontService storefrontService;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private ListingAuditService auditService;

    private ListingService listingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        listingService = new ListingService(listingRepository, mediaRepository, itemRepository,
                storefrontService, outboxRepository, auditService, objectMapper);
        TrustContext ctx = new TrustContext(
                UUID.randomUUID(), "actor-1", "PROVIDER", "TREATMENT",
                "dev", UUID.randomUUID(), null, null, null, AccessMode.INTERNAL);
        TrustContextHolder.set(ctx);
        lenient().when(mediaRepository.findByListingIdOrderBySortOrderAsc(any())).thenReturn(List.of());
    }

    private CatalogItemEntity item() {
        CatalogItemEntity it = new CatalogItemEntity();
        it.setItemId("ITEM1");
        return it;
    }

    @Test
    void create_persistsDraftAndAudits() {
        when(itemRepository.findById("ITEM1")).thenReturn(Optional.of(item()));
        when(listingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var req = new ListingDtos.CreateListingRequest(null, null, "ITEM1", null, "PROVIDER", "PROV-1",
                "Blood pressure check", "Quick BP screening", "LOW_RISK", null, null, null, "$5", "IN_PERSON", null, null);
        ListingDtos.ListingView v = listingService.create(req);

        assertNotNull(v.listingId());
        assertEquals("DRAFT", v.status());
        assertEquals("MSIKA_FLOW", v.fulfilmentOwner());
        verify(outboxRepository).save(any());
        verify(auditService).record(any(), eq("LISTING_CREATE"), any(), any(), eq("LOW_RISK"), any(), any());
    }

    @Test
    void create_unknownItem_throws() {
        when(itemRepository.findById("ITEMX")).thenReturn(Optional.empty());
        var req = new ListingDtos.CreateListingRequest(null, null, "ITEMX", null, "PROVIDER", "PROV-1",
                "x", null, null, null, null, null, null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> listingService.create(req));
    }

    @Test
    void submit_movesToAwaitingApproval() {
        ListingEntity e = listing("DRAFT", "LOW_RISK");
        when(listingRepository.findById("L1")).thenReturn(Optional.of(e));
        when(listingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ListingDtos.ListingView v = listingService.submit("L1");
        assertEquals("AWAITING_APPROVAL", v.status());
    }

    @Test
    void approve_setsApprovedAndAudits() {
        ListingEntity e = listing("AWAITING_APPROVAL", "LOW_RISK");
        when(listingRepository.findById("L1")).thenReturn(Optional.of(e));
        when(listingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ListingDtos.ListingView v = listingService.approve("L1", new ListingDtos.ModerationRequest("ok"));
        assertEquals("APPROVED", v.status());
        assertEquals("actor-1", v.approvedBy());
        verify(auditService).record(any(), eq("LISTING_APPROVE"), any(), any(), any(), eq("ALLOW"), any());
    }

    @Test
    void publish_fromApproved_lowRisk_publishes() {
        ListingEntity e = listing("APPROVED", "LOW_RISK");
        when(listingRepository.findById("L1")).thenReturn(Optional.of(e));
        when(listingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ListingDtos.ListingView v = listingService.publish("L1");
        assertEquals("PUBLISHED", v.status());
        assertNotNull(v.publishedAt());
    }

    @Test
    void publish_fromDraft_throws() {
        ListingEntity e = listing("DRAFT", "LOW_RISK");
        when(listingRepository.findById("L1")).thenReturn(Optional.of(e));
        assertThrows(IllegalStateException.class, () -> listingService.publish("L1"));
    }

    @Test
    void publish_regulated_withoutVerifiedSeller_deniesAndAudits() {
        ListingEntity e = listing("APPROVED", "REGULATED");
        when(listingRepository.findById("L1")).thenReturn(Optional.of(e));
        when(storefrontService.isSellerVerified("PROVIDER", "PROV-1")).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> listingService.publish("L1"));
        verify(auditService).record(any(), eq("POLICY_DENIED"), eq("L1"), any(), eq("REGULATED"), eq("DENY"), any());
        verify(listingRepository, never()).save(any());
    }

    @Test
    void publish_regulated_withVerifiedSeller_publishes() {
        ListingEntity e = listing("APPROVED", "REGULATED");
        when(listingRepository.findById("L1")).thenReturn(Optional.of(e));
        when(storefrontService.isSellerVerified("PROVIDER", "PROV-1")).thenReturn(true);
        when(listingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ListingDtos.ListingView v = listingService.publish("L1");
        assertEquals("PUBLISHED", v.status());
    }

    @Test
    void getForBuyer_unpublished_throws() {
        ListingEntity e = listing("DRAFT", "LOW_RISK");
        when(listingRepository.findById("L1")).thenReturn(Optional.of(e));
        assertThrows(IllegalStateException.class, () -> listingService.getForBuyer("L1"));
    }

    @Test
    void getForBuyer_regulatedPublished_writesRegulatedViewAudit() {
        ListingEntity e = listing("PUBLISHED", "HIGH_RISK");
        when(listingRepository.findById("L1")).thenReturn(Optional.of(e));
        ListingDtos.ListingView v = listingService.getForBuyer("L1");
        assertEquals("PUBLISHED", v.status());
        verify(auditService).record(any(), eq("REGULATED_VIEW"), eq("L1"), any(), eq("HIGH_RISK"), any(), any());
    }

    private ListingEntity listing(String status, String risk) {
        ListingEntity e = new ListingEntity();
        e.setListingId("L1");
        e.setStatus(status);
        e.setRiskClassification(risk);
        e.setSellerType("PROVIDER");
        e.setSellerId("PROV-1");
        e.setTitle("t");
        e.setCatalogItemId("ITEM1");
        return e;
    }
}
