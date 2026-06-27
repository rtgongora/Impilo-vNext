package zw.gov.mohcc.impilo.cardprint.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import zw.gov.mohcc.impilo.cardprint.generator.CardPayloadGenerator;
import zw.gov.mohcc.impilo.cardprint.service.SecureElementKeyService;
import zw.gov.mohcc.impilo.cardprint.service.VitoCardClient;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrintJobListenerTest {

    private CardPayloadGenerator generator;
    private SecureElementKeyService secureElementKeyService;
    private VitoCardClient vitoCardClient;
    private KafkaTemplate<String, String> kafkaTemplate;
    private PrintJobListener listener;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        generator = mock(CardPayloadGenerator.class);
        secureElementKeyService = mock(SecureElementKeyService.class);
        vitoCardClient = mock(VitoCardClient.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        listener = new PrintJobListener(new ObjectMapper(), generator, secureElementKeyService,
                vitoCardClient, kafkaTemplate);
    }

    @Test
    void provisionsGeneratedPublicKeyToVitoWithTenantScope() throws Exception {
        UUID tenant = UUID.fromString("b0000000-0000-4000-8000-000000000001");
        when(generator.generate(anyLong(), any(), any())).thenReturn(new byte[]{1, 2, 3});
        when(secureElementKeyService.generatePublicKeyPem(1234L))
                .thenReturn("-----BEGIN PUBLIC KEY-----\nMFkw\n-----END PUBLIC KEY-----\n");

        String message = "{\"cardId\":1234,\"tenantId\":\"" + tenant
                + "\",\"template\":\"STANDARD\",\"did\":\"did:impilo:abc\"}";

        listener.onPrintJob(message);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(vitoCardClient).confirmPrinted(eq(tenant), eq(1234L), keyCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(keyCaptor.getValue())
                .startsWith("-----BEGIN PUBLIC KEY-----");
    }

    @Test
    void doesNotCallVitoWhenPayloadGenerationFails() throws Exception {
        when(generator.generate(anyLong(), any(), any()))
                .thenThrow(new java.io.IOException("render boom"));

        listener.onPrintJob("{\"cardId\":99,\"tenantId\":\"b0000000-0000-4000-8000-000000000001\"}");

        verify(vitoCardClient, never()).confirmPrinted(any(), anyLong(), any());
        // failure still audited
        verify(kafkaTemplate).send(eq("vito.print.audit"), eq("99"), any());
    }
}
