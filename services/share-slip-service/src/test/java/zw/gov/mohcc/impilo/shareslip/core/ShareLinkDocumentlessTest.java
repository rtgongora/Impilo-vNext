package zw.gov.mohcc.impilo.shareslip.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shareslip.config.ShareSlipProperties;
import zw.gov.mohcc.impilo.shareslip.dto.CreateShareLinkRequest;
import zw.gov.mohcc.impilo.shareslip.persistence.entity.ShareLinkEntity;
import zw.gov.mohcc.impilo.shareslip.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.shareslip.persistence.repository.ShareAuditRepository;
import zw.gov.mohcc.impilo.shareslip.persistence.repository.ShareLinkRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verifies the document-less share-link enhancement: a link (e.g. a DIAGNOSTIC_ORDER QR) can be
 * created with no documentIds, and the entity stores an empty list rather than null.
 */
@ExtendWith(MockitoExtension.class)
class ShareLinkDocumentlessTest {

    @Mock private ShareLinkRepository shareLinkRepository;
    @Mock private ShareAuditRepository shareAuditRepository;
    @Mock private EventOutboxRepository eventOutboxRepository;
    @Mock private OtpService otpService;

    @Test
    @DisplayName("createShareLink with null documentIds stores an empty list and succeeds")
    void documentLessLink() {
        ShareLinkService service = new ShareLinkService(
                shareLinkRepository, shareAuditRepository, eventOutboxRepository,
                otpService, new ShareSlipProperties(), new ObjectMapper());

        when(otpService.generateOtp(anyInt())).thenReturn("123456");
        when(otpService.hashOtp(any())).thenReturn("hash");
        when(shareLinkRepository.save(any(ShareLinkEntity.class))).thenAnswer(i -> {
            ShareLinkEntity e = i.getArgument(0);
            if (e.getLinkId() == null) e.setLinkId(UUID.randomUUID());
            return e;
        });

        TrustContext ctx = new TrustContext(UUID.randomUUID(), "actor-1", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, AccessMode.INTERNAL);

        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(ctx);

            CreateShareLinkRequest request = new CreateShareLinkRequest(
                    "DIAGNOSTIC_ORDER", "ORD-1", "Order ORD-1", null, "QR order intake",
                    "OTP", 72, 3);

            service.createShareLink(request);

            ArgumentCaptor<ShareLinkEntity> captor = ArgumentCaptor.forClass(ShareLinkEntity.class);
            verify(shareLinkRepository).save(captor.capture());
            assertThat(captor.getValue().getDocumentIds()).isNotNull().isEmpty();
            assertThat(captor.getValue().getSubjectType()).isEqualTo("DIAGNOSTIC_ORDER");
        }
    }
}
