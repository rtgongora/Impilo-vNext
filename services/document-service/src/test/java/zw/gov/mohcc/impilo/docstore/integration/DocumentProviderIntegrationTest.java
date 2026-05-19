package zw.gov.mohcc.impilo.docstore.integration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.docstore.config.DocumentStoreProperties;
import zw.gov.mohcc.impilo.docstore.core.ocr.ExternalStubOcrProvider;
import zw.gov.mohcc.impilo.docstore.core.ocr.OcrProvider;
import zw.gov.mohcc.impilo.docstore.core.signature.ExternalStubSignatureProvider;
import zw.gov.mohcc.impilo.docstore.core.signature.SignatureProvider;
import zw.gov.mohcc.impilo.docstore.core.storage.LandelaAdapterObjectStorageProvider;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentProviderIntegrationTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void landelaStorageProviderFlowOverHttp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/v1/internal/documents", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                respond(exchange, 201, "{\"data\":{\"documentId\":\"doc-123\"}}");
            } else {
                respond(exchange, 404, "{}");
            }
        });
        server.createContext("/v1/internal/documents/doc-123/signed-url",
                exchange -> respond(exchange, 200, "{\"data\":{\"url\":\"https://signed.local/doc-123\"}}"));
        server.createContext("/v1/internal/documents/doc-123/download",
                exchange -> respondBytes(exchange, 200, "hello-binary".getBytes(StandardCharsets.UTF_8), "application/octet-stream"));
        server.createContext("/v1/internal/documents/doc-123/lifecycle",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"DELETED\"}}"));
        server.start();

        DocumentStoreProperties properties = new DocumentStoreProperties();
        properties.getLandelaAdapter().setBaseUrl("http://localhost:" + port);
        LandelaAdapterObjectStorageProvider provider = new LandelaAdapterObjectStorageProvider(properties);

        String ref = provider.putObject("unused", "tenant/key", "payload".getBytes(StandardCharsets.UTF_8), "text/plain");
        assertEquals("doc-123", ref);
        String signedUrl = provider.generateSignedUrl("unused", ref, 300);
        assertEquals("https://signed.local/doc-123", signedUrl);
        try (InputStream input = provider.getObject("unused", ref)) {
            String content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals("hello-binary", content);
        }
        provider.removeObject("unused", ref);
    }

    @Test
    void externalOcrProviderFlowOverHttp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/v1/ocr/extract", exchange ->
                respond(exchange, 200, "{\"text\":\"Extracted stub text\",\"confidence\":0.91}"));
        server.start();

        DocumentStoreProperties properties = new DocumentStoreProperties();
        properties.getOcrStub().setBaseUrl("http://localhost:" + port);
        properties.getOcrStub().setPath("/v1/ocr/extract");
        ExternalStubOcrProvider provider = new ExternalStubOcrProvider(properties);

        OcrProvider.OcrResult result = provider.extract(UUID.randomUUID(), "text-body".getBytes(StandardCharsets.UTF_8), "text/plain");
        assertEquals("Extracted stub text", result.extractedText());
        assertTrue(result.confidence() > 0.9d);
    }

    @Test
    void externalSignatureProviderFlowOverHttp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        String signedAt = OffsetDateTime.now().toString();
        server.createContext("/v1/signature/sign", exchange ->
                respond(exchange, 200, "{\"signatureRef\":\"sig-777\",\"verificationUrl\":\"https://verify.local/sig-777\",\"signedAt\":\"" + signedAt + "\"}"));
        server.start();

        DocumentStoreProperties properties = new DocumentStoreProperties();
        properties.getSignatureStub().setBaseUrl("http://localhost:" + port);
        properties.getSignatureStub().setPath("/v1/signature/sign");
        ExternalStubSignatureProvider provider = new ExternalStubSignatureProvider(properties);

        SignatureProvider.SignatureResult result = provider.sign(
                new SignatureProvider.SignatureRequest(UUID.randomUUID(), "provider-1", "tester"));
        assertEquals("sig-777", result.signatureRef());
        assertEquals("https://verify.local/sig-777", result.verificationUrl());
        assertNotNull(result.signedAt());
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void respondBytes(HttpExchange exchange, int status, byte[] body, String contentType) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }
}
