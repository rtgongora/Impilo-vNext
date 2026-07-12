package zw.gov.mohcc.impilo.msika.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.msika.api.dto.ListingDtos;
import zw.gov.mohcc.impilo.msika.api.dto.StorefrontDtos;
import zw.gov.mohcc.impilo.msika.core.ListingService;
import zw.gov.mohcc.impilo.msika.core.StorefrontService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MsikaFlowVendorConsumerTest {

    @Mock private StorefrontService storefrontService;
    @Mock private ListingService listingService;

    private MsikaFlowVendorConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new MsikaFlowVendorConsumer(storefrontService, listingService, new ObjectMapper());
        TrustContextHolder.clear();
    }

    @AfterEach
    void clearCtx() {
        TrustContextHolder.clear();
    }

    private StorefrontDtos.StorefrontView storefront(String id, String status) {
        return new StorefrontDtos.StorefrontView(id, null, "VENDOR", "VEND-1", "Acme Medical",
                null, status, null, null, null, null, null, "SYSTEM", null, null);
    }

    @Test
    void vendorApproved_createsUnverifiedStorefront_whenAbsent() {
        when(storefrontService.findBySeller("VENDOR", "VEND-1")).thenReturn(Optional.empty());
        when(storefrontService.create(any())).thenReturn(storefront("SF1", "UNVERIFIED"));

        consumer.onVendorEvent(
                "{\"vendorId\":\"VEND-1\",\"vendorName\":\"Acme Medical\"}",
                MsikaFlowVendorConsumer.TOPIC_VENDOR_APPROVED);

        ArgumentCaptor<StorefrontDtos.CreateStorefrontRequest> captor =
                ArgumentCaptor.forClass(StorefrontDtos.CreateStorefrontRequest.class);
        verify(storefrontService).create(captor.capture());
        assertEquals("VENDOR", captor.getValue().sellerType());
        assertEquals("VEND-1", captor.getValue().sellerId());
        assertEquals("Acme Medical", captor.getValue().displayName());
        // Verification is a human/licence action — approval must NOT verify.
        verify(storefrontService, never()).verify(any(), any());
    }

    @Test
    void vendorApproved_existingStorefront_isIdempotent() {
        when(storefrontService.findBySeller("VENDOR", "VEND-1"))
                .thenReturn(Optional.of(storefront("SF1", "VERIFIED")));

        consumer.onVendorEvent(
                "{\"vendorId\":\"VEND-1\",\"vendorName\":\"Acme Medical\"}",
                MsikaFlowVendorConsumer.TOPIC_VENDOR_APPROVED);

        verify(storefrontService, never()).create(any());
        verify(storefrontService, never()).verify(any(), any());
    }

    @Test
    void vendorApproved_envelopeWrappedPayload_isUnwrapped() {
        when(storefrontService.findBySeller("VENDOR", "VEND-9")).thenReturn(Optional.empty());
        when(storefrontService.create(any())).thenReturn(storefront("SF9", "UNVERIFIED"));

        consumer.onVendorEvent(
                "{\"eventType\":\"VENDOR_APPROVED\",\"payload\":{\"vendorId\":\"VEND-9\",\"name\":\"Wrapped Vendor\"}}",
                MsikaFlowVendorConsumer.TOPIC_VENDOR_APPROVED);

        ArgumentCaptor<StorefrontDtos.CreateStorefrontRequest> captor =
                ArgumentCaptor.forClass(StorefrontDtos.CreateStorefrontRequest.class);
        verify(storefrontService).create(captor.capture());
        assertEquals("VEND-9", captor.getValue().sellerId());
        assertEquals("Wrapped Vendor", captor.getValue().displayName());
    }

    @Test
    void vendorSuspended_suspendsStorefront_andPublishedListingsOnly() {
        when(storefrontService.findBySeller("VENDOR", "VEND-1"))
                .thenReturn(Optional.of(storefront("SF1", "VERIFIED")));

        ListingDtos.ListingView published = mock(ListingDtos.ListingView.class);
        when(published.status()).thenReturn("PUBLISHED");
        when(published.listingId()).thenReturn("L-PUB");
        ListingDtos.ListingView draft = mock(ListingDtos.ListingView.class);
        when(draft.status()).thenReturn("DRAFT");
        when(listingService.listForSeller("VENDOR", "VEND-1")).thenReturn(List.of(published, draft));

        consumer.onVendorEvent(
                "{\"vendorId\":\"VEND-1\"}",
                MsikaFlowVendorConsumer.TOPIC_VENDOR_SUSPENDED);

        ArgumentCaptor<StorefrontDtos.VerifyStorefrontRequest> verifyCaptor =
                ArgumentCaptor.forClass(StorefrontDtos.VerifyStorefrontRequest.class);
        verify(storefrontService).verify(eq("SF1"), verifyCaptor.capture());
        assertEquals("SUSPENDED", verifyCaptor.getValue().verificationStatus());
        verify(listingService).suspend(eq("L-PUB"), any());
        verify(listingService, times(1)).suspend(any(), any());
    }

    @Test
    void vendorSuspended_noStorefront_isNoOp() {
        when(storefrontService.findBySeller("VENDOR", "VEND-2")).thenReturn(Optional.empty());

        consumer.onVendorEvent(
                "{\"vendorId\":\"VEND-2\"}",
                MsikaFlowVendorConsumer.TOPIC_VENDOR_SUSPENDED);

        verify(storefrontService, never()).verify(any(), any());
        verifyNoInteractions(listingService);
    }

    @Test
    void missingVendorId_isIgnored() {
        consumer.onVendorEvent("{\"somethingElse\":true}",
                MsikaFlowVendorConsumer.TOPIC_VENDOR_APPROVED);
        verifyNoInteractions(storefrontService, listingService);
    }

    @Test
    void malformedJson_doesNotThrow() {
        assertDoesNotThrow(() -> consumer.onVendorEvent("not-json{{{",
                MsikaFlowVendorConsumer.TOPIC_VENDOR_SUSPENDED));
        verifyNoInteractions(storefrontService, listingService);
    }

    @Test
    void trustContextIsClearedAfterProcessing() {
        when(storefrontService.findBySeller("VENDOR", "VEND-1"))
                .thenReturn(Optional.of(storefront("SF1", "UNVERIFIED")));
        consumer.onVendorEvent("{\"vendorId\":\"VEND-1\",\"vendorName\":\"A\"}",
                MsikaFlowVendorConsumer.TOPIC_VENDOR_APPROVED);
        assertNull(TrustContextHolder.get());
    }
}
